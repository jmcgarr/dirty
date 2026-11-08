package net.sonofgarr.dirty;

import net.sonofgarr.dirty.process.ProcessCommandRunner;
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

@Command(
        name = "dirty",
        description = "How many of my git repos are dirty?",
        version = "dirty 0.1.0",
        mixinStandardHelpOptions = true,
        sortOptions = false
)
public class App implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = {"-d", "--directory"}, paramLabel = "<dir>",
            description = "Directory to scan: an absolute path, or a name under your home directory (default: ${DEFAULT-VALUE}).")
    String directory = "Projects";

    @Option(names = "--max-depth", paramLabel = "<n>",
            description = "How many directory levels below the root to search (default: ${DEFAULT-VALUE}).")
    int maxDepth = RepoFinder.DEFAULT_MAX_DEPTH;

    private final Path home;
    private final Git git;
    private final Reporter reporter;

    /** Production wiring. */
    public App() {
        this(Path.of(System.getProperty("user.home")),
                new ProcessGit(new ProcessCommandRunner()),
                new TextReporter(Ansi.AUTO));
    }

    /** Test wiring. */
    App(Path home, Git git, Reporter reporter) {
        this.home = home;
        this.git = git;
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

        err.println("Finding dirty git repos in " + root);
        err.flush();

        Scanner scanner = new Scanner(new RepoFinder(maxDepth, RepoFinder.DEFAULT_SKIP), git);
        ScanResult result = scanner.scan(root, warning -> {
            err.println("warning: " + warning);
            err.flush();
        });
        reporter.report(result, out);
        return result.hasErrors() ? 1 : 0;
    }

    /** Resolved in {@code call()}, after picocli has injected the option; a field initializer would run too early. */
    Path resolveRoot() {
        Path path = Path.of(directory);
        return (path.isAbsolute() ? path : home.resolve(path)).normalize();
    }
}
