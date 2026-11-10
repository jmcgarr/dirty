package net.sonofgarr.dirty;

import net.sonofgarr.dirty.process.ProcessCommandRunner;
import net.sonofgarr.dirty.process.TracingCommandRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

@Command(
        name = "dirty",
        description = "How many of my git repos are dirty?",
        version = "dirty " + Version.VERSION,
        mixinStandardHelpOptions = true,
        sortOptions = false,
        exitCodeListHeading = "%nExit codes:%n",
        exitCodeList = {
                "0:All repositories were inspected (with --exit-code: and none needs attention)",
                "1:One or more repositories could not be inspected (with --exit-code: or needs attention)",
                "2:Usage error, or git is not available",
                "130:Interrupted"
        }
)
public class App implements Callable<Integer> {

    /** Exit status when the scan is interrupted, as a shell reports a SIGINT-killed process (128 + 2). */
    static final int EXIT_INTERRUPTED = 130;

    @Spec
    CommandSpec spec;

    @Option(names = {"-d", "--directory"}, paramLabel = "<dir>",
            description = "Directory to scan: an absolute path, or a name under your home directory (default: ${DEFAULT-VALUE}).")
    String directory = "Projects";

    @Option(names = "--max-depth", paramLabel = "<n>",
            description = "How many directory levels below the root to search (default: ${DEFAULT-VALUE}).")
    int maxDepth = RepoFinder.DEFAULT_MAX_DEPTH;

    @Option(names = {"-f", "--format"}, paramLabel = "<format>", defaultValue = "text",
            description = "Output format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    Format format;

    @Option(names = "--all",
            description = "Include clean repositories (text and table; json and csv always list every repository).")
    boolean all;

    @Option(names = "--color", description = "Force ANSI colors on, even when piped.")
    boolean color;

    @Option(names = "--no-color", description = "Force ANSI colors off (NO_COLOR and a non-terminal stdout already do this).")
    boolean noColor;

    @Option(names = "--exit-code",
            description = "Exit 1 when any repository needs attention, like git diff --exit-code.")
    boolean exitCode;

    @Option(names = {"-v", "--verbose"},
            description = "Show every git command, its exit status, and a summary on stderr.")
    boolean verbose;

    /** Output formats, in the spelling {@code --format} accepts. */
    enum Format { text, table, json, csv }

    /** Builds the reporter once the options are parsed; tests substitute a fixed {@link Reporter}. */
    interface ReporterFactory {
        Reporter create(Format format, Ansi ansi, boolean all);
    }

    private final Path home;
    private final Function<Consumer<String>, Git> gitFactory;
    private final ReporterFactory reporterFactory;

    /** Production wiring. */
    public App() {
        this(Path.of(System.getProperty("user.home")),
                trace -> new ProcessGit(new TracingCommandRunner(new ProcessCommandRunner(), trace)),
                App::reporter);
    }

    /** Test wiring with a ready-made {@link Git} and the real format selection. */
    App(Path home, Git git) {
        this(home, trace -> git, App::reporter);
    }

    /** Test wiring with a ready-made {@link Git} and a fixed reporter, whatever the options say. */
    App(Path home, Git git, Reporter reporter) {
        this(home, trace -> git, (format, ansi, all) -> reporter);
    }

    /** Test wiring with a fixed reporter; the factory receives the sink that {@code --verbose} writes to. */
    App(Path home, Function<Consumer<String>, Git> gitFactory, Reporter reporter) {
        this(home, gitFactory, (format, ansi, all) -> reporter);
    }

    private App(Path home, Function<Consumer<String>, Git> gitFactory, ReporterFactory reporterFactory) {
        this.home = home;
        this.gitFactory = gitFactory;
        this.reporterFactory = reporterFactory;
    }

    /** The one place a format name becomes a reporter. Machine formats never emit ANSI. */
    static Reporter reporter(Format format, Ansi ansi, boolean all) {
        return switch (format) {
            case text -> new TextReporter(ansi, all);
            case table -> new TableReporter(ansi, all);
            case json -> new JsonReporter();
            case csv -> new CsvReporter();
        };
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args));
    }

    @Override
    public Integer call() throws IOException {
        if (maxDepth < 0) {
            throw new ParameterException(spec.commandLine(), "--max-depth must be >= 0");
        }
        if (color && noColor) {
            throw new ParameterException(spec.commandLine(), "--color and --no-color are mutually exclusive");
        }
        Ansi ansi = color ? Ansi.ON : noColor ? Ansi.OFF : Ansi.AUTO;
        Reporter reporter = reporterFactory.create(format, ansi, all);
        Path root = resolveRoot();
        if (!Files.isDirectory(root)) {
            throw new ParameterException(spec.commandLine(), "Not a directory: " + root);
        }
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        Consumer<String> trace = verbose ? line -> {
            err.println(line);
            err.flush();
        } : line -> { };

        Git git = gitFactory.apply(trace);
        try {
            trace.accept(git.version(root));
        } catch (GitException e) {
            err.println(e.exitCode() < 0
                    ? "dirty: could not run git: " + e.getMessage()
                    : "dirty: git --version failed: " + e.getMessage());
            err.flush();
            return CommandLine.ExitCode.USAGE;
        }

        err.println("Finding dirty git repos in " + root);
        err.flush();

        Scanner scanner = new Scanner(new RepoFinder(maxDepth, RepoFinder.DEFAULT_SKIP), git);
        ScanResult result;
        try {
            result = scanner.scan(root, warning -> {
                err.println("warning: " + warning);
                err.flush();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println("dirty: interrupted");
            err.flush();
            return EXIT_INTERRUPTED;
        }
        reporter.report(result, out);

        long needAttention = result.reports().stream().filter(RepoReport::needsAttention).count();
        long errors = result.reports().stream().filter(RepoReport::hasError).count();
        trace.accept(result.reports().size() + " repositories, " + (needAttention - errors) + " dirty, " + errors + " errors");

        if (result.hasErrors()) {
            return CommandLine.ExitCode.SOFTWARE;
        }
        return exitCode && needAttention > 0 ? CommandLine.ExitCode.SOFTWARE : CommandLine.ExitCode.OK;
    }

    /** Resolved in {@code call()}, after picocli has injected the option; a field initializer would run too early. */
    Path resolveRoot() {
        Path path = Path.of(directory);
        return (path.isAbsolute() ? path : home.resolve(path)).normalize();
    }
}
