package net.sonofgarr.dirty.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link CommandRunner} backed by {@link ProcessBuilder}.
 *
 * <p>stdout is read to EOF on the calling thread and stderr is drained on a
 * daemon thread; both finish before the result is built, so no output is lost
 * and a chatty child cannot block on a full pipe.
 */
public final class ProcessCommandRunner implements CommandRunner {

    private final Map<String, String> environment;

    public ProcessCommandRunner() {
        this(Map.of());
    }

    /** @param environment extra variables layered over the inherited environment. */
    public ProcessCommandRunner(Map<String, String> environment) {
        this.environment = Map.copyOf(environment);
    }

    @Override
    public CommandResult run(Path workingDirectory, List<String> command, Map<String, String> callEnvironment)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile());
        Map<String, String> env = builder.environment();
        env.putAll(environment);
        callEnvironment.forEach((key, value) -> {
            if (value == null) {
                env.remove(key);
            } else {
                env.put(key, value);
            }
        });
        Process process = builder.start();
        process.getOutputStream().close();

        AtomicReference<byte[]> stderr = new AtomicReference<>(new byte[0]);
        Thread drain = new Thread(() -> {
            try (InputStream err = process.getErrorStream()) {
                stderr.set(err.readAllBytes());
            } catch (IOException ignored) {
                // The process is gone; whatever we have is all there is.
            }
        }, "dirty-stderr-drain");
        drain.setDaemon(true);
        drain.start();

        byte[] stdout;
        int exitCode;
        try {
            try (InputStream out = process.getInputStream()) {
                stdout = out.readAllBytes();
            }
            exitCode = process.waitFor();
            drain.join();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            throw e;
        }

        return new CommandResult(exitCode,
                new String(stdout, StandardCharsets.UTF_8),
                new String(stderr.get(), StandardCharsets.UTF_8));
    }
}
