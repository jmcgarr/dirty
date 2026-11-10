package net.sonofgarr.dirty.process;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** The command did not finish within the runner's timeout, or its output never closed. */
public final class CommandTimeoutException extends IOException {

    private final Duration timeout;

    private CommandTimeoutException(String message, Duration timeout) {
        super(message);
        this.timeout = timeout;
    }

    /** The process was still running at the deadline and has been sent SIGKILL. */
    public static CommandTimeoutException killed(List<String> command, Duration timeout) {
        return new CommandTimeoutException(name(command) + " timed out after " + describe(timeout), timeout);
    }

    /** The process exited, but something (a grandchild, usually) kept its output pipe open. */
    public static CommandTimeoutException outputStillOpen(List<String> command, int exitCode, Duration grace) {
        return new CommandTimeoutException(name(command) + " exited with status " + exitCode
                + " but its output stayed open for " + describe(grace) + " afterwards", grace);
    }

    public Duration timeout() {
        return timeout;
    }

    private static String name(List<String> command) {
        return command.isEmpty() ? "command" : command.get(0);
    }

    private static String describe(Duration d) {
        return d.toMillis() % 1000 == 0 ? d.toSeconds() + "s" : d.toMillis() + "ms";
    }
}
