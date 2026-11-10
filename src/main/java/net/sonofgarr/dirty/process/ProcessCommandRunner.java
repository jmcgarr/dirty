package net.sonofgarr.dirty.process;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link CommandRunner} backed by {@link ProcessBuilder}.
 *
 * <p>stdout and stderr are each drained to EOF on a daemon thread, so a chatty
 * child can never block on a full pipe, and both drains finish before the result
 * is built, so no output is lost. A child that outlives the timeout is killed
 * and reported as a {@link CommandTimeoutException}.
 */
public final class ProcessCommandRunner implements CommandRunner {

    /** Long enough for {@code git status} on a huge cold checkout; short enough that a wedged git does not hang the scan. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * How long to wait for pipes to close after the child has exited or been
     * killed. A child in uninterruptible sleep (dead network mount) ignores
     * SIGKILL, and a grandchild may keep the pipe open; neither may hang us.
     */
    static final Duration GRACE = Duration.ofSeconds(2);

    private final Map<String, String> environment;
    private final Duration timeout;

    public ProcessCommandRunner() {
        this(Map.of(), DEFAULT_TIMEOUT);
    }

    /** @param environment extra variables layered over the inherited environment. */
    public ProcessCommandRunner(Map<String, String> environment) {
        this(environment, DEFAULT_TIMEOUT);
    }

    public ProcessCommandRunner(Map<String, String> environment, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        this.environment = Map.copyOf(environment);
        this.timeout = timeout;
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

        Drain stdout = Drain.start(process.getInputStream(), "dirty-stdout-drain");
        Drain stderr = Drain.start(process.getErrorStream(), "dirty-stderr-drain");

        try {
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                kill(process, stdout, stderr);
                throw CommandTimeoutException.killed(command, timeout);
            }
            // The child has exited; its pipes close with it unless a grandchild
            // inherited them. Give the drains a short grace, not the whole timeout again.
            long deadline = System.nanoTime() + GRACE.toNanos();
            if (!stdout.join(deadline) || !stderr.join(deadline)) {
                throw CommandTimeoutException.outputStillOpen(command, process.exitValue(), GRACE);
            }
        } catch (InterruptedException e) {
            kill(process, stdout, stderr);
            throw e;
        }

        return new CommandResult(process.exitValue(),
                new String(stdout.bytes(), StandardCharsets.UTF_8),
                new String(stderr.bytes(), StandardCharsets.UTF_8));
    }

    /** Best effort: a process stuck in the kernel may ignore SIGKILL, so never wait on it unboundedly. */
    private static void kill(Process process, Drain stdout, Drain stderr) throws InterruptedException {
        process.destroyForcibly();
        long deadline = System.nanoTime() + GRACE.toNanos();
        process.waitFor(GRACE.toNanos(), TimeUnit.NANOSECONDS);
        stdout.join(deadline);
        stderr.join(deadline);
    }

    /** Reads one stream to EOF on a daemon thread. */
    private static final class Drain {
        private final Thread thread;
        private final AtomicReference<byte[]> bytes = new AtomicReference<>(new byte[0]);

        private Drain(InputStream stream, String name) {
            this.thread = new Thread(() -> {
                try (stream) {
                    bytes.set(stream.readAllBytes());
                } catch (IOException ignored) {
                    // The process is gone; whatever we have is all there is.
                }
            }, name);
            thread.setDaemon(true);
        }

        static Drain start(InputStream stream, String name) {
            Drain drain = new Drain(stream, name);
            drain.thread.start();
            return drain;
        }

        /** @return false if the stream was still open at the deadline */
        boolean join(long deadlineNanos) throws InterruptedException {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining > 0) {
                thread.join(Duration.ofNanos(remaining));
            }
            return !thread.isAlive();
        }

        byte[] bytes() {
            return bytes.get();
        }
    }
}
