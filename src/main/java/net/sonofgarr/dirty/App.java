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

    @Option(names = "--exit-code",
            description = "Exit 1 when any repository needs attention, like git diff --exit-code.")
    boolean exitCode;

    @Option(names = {"-v", "--verbose"},
            description = "Show every git command, its exit status, and a summary on stderr.")
    boolean verbose;

    private final Path home;
    private final Function<Consumer<String>, Git> gitFactory;
    private final Reporter reporter;

    /** Production wiring. */
    public App() {
        this(Path.of(System.getProperty("user.home")),
                trace -> new ProcessGit(new TracingCommandRunner(new ProcessCommandRunner(), trace)),
                new TextReporter(Ansi.AUTO));
    }

    /** Test wiring with a ready-made {@link Git}. */
    App(Path home, Git git, Reporter reporter) {
        this(home, trace -> git, reporter);
    }

    /** Test wiring; the factory receives the sink that {@code --verbose} writes to. */
    App(Path home, Function<Consumer<String>, Git> gitFactory, Reporter reporter) {
        this.home = home;
        this.gitFactory = gitFactory;
        this.reporter = reporter;
    }

    public static void main(String[] args) {
        System.exit(new CommandLine(new App()).execute(args));
    }

    @Override
    public Integer call() throws IOException {
        if (maxDepth < 0) {
            throw new ParameterException(spec.commandLine(), "--max-depth must be >= 0");
        }
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
