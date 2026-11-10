package net.sonofgarr.dirty.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Decorator that reports every command and its outcome to a sink (the {@code --verbose} flag). */
public final class TracingCommandRunner implements CommandRunner {

    private final CommandRunner delegate;
    private final Consumer<String> sink;

    public TracingCommandRunner(CommandRunner delegate, Consumer<String> sink) {
        this.delegate = delegate;
        this.sink = sink;
    }

    @Override
    public CommandResult run(Path workingDirectory, List<String> command, Map<String, String> environment)
            throws IOException, InterruptedException {
        sink.accept("$ " + String.join(" ", command) + "  (in " + workingDirectory + ")");
        long started = System.nanoTime();
        try {
            CommandResult result = delegate.run(workingDirectory, command, environment);
            sink.accept("  exit " + result.exitCode() + " in " + millisSince(started) + " ms");
            return result;
        } catch (IOException | InterruptedException e) {
            sink.accept("  failed after " + millisSince(started) + " ms: " + e.getMessage());
            throw e;
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
