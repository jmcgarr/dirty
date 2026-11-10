package net.sonofgarr.dirty.process;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs an external command to completion. The only seam through which the
 * application touches processes; tests substitute a fake.
 */
public interface CommandRunner {

    /**
     * @param environment variables layered over the inherited environment for
     *                    this call; a {@code null} value removes the variable
     */
    CommandResult run(Path workingDirectory, List<String> command, Map<String, String> environment)
            throws IOException, InterruptedException;

    default CommandResult run(Path workingDirectory, List<String> command) throws IOException, InterruptedException {
        return run(workingDirectory, command, Map.of());
    }
}
