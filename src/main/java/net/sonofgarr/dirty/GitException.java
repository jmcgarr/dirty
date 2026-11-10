package net.sonofgarr.dirty;

import java.nio.file.Path;

/** git could not be run, or exited non-zero, for one repository. */
public final class GitException extends Exception {

    private final Path repository;
    private final int exitCode;

    public GitException(Path repository, int exitCode, String stderr) {
        super(stderr == null || stderr.isBlank() ? "git exited with status " + exitCode : stderr.strip());
        this.repository = repository;
        this.exitCode = exitCode;
    }

    public GitException(Path repository, Throwable cause) {
        super(cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage(), cause);
        this.repository = repository;
        this.exitCode = -1;
    }

    public Path repository() {
        return repository;
    }

    /** The process exit code, or -1 if git did not run at all. */
    public int exitCode() {
        return exitCode;
    }
}
