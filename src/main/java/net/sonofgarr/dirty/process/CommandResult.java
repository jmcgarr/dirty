package net.sonofgarr.dirty.process;

/** Outcome of running an external command: exit code plus fully-read stdout and stderr. */
public record CommandResult(int exitCode, String stdout, String stderr) {

    public boolean succeeded() {
        return exitCode == 0;
    }
}
