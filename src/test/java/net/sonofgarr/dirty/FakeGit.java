package net.sonofgarr.dirty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Statuses and failures keyed by absolute repository path. */
final class FakeGit implements Git {

    private final Map<Path, RepoStatus> statuses = new HashMap<>();
    private final Map<Path, GitException> failures = new HashMap<>();
    private final Set<Path> interrupting = new HashSet<>();
    private boolean installed = true;

    FakeGit status(Path repo, int modified, int untracked, boolean hasRemote) {
        statuses.put(key(repo), new RepoStatus(modified, untracked, hasRemote));
        return this;
    }

    FakeGit failing(Path repo, String stderr) {
        failures.put(key(repo), new GitException(repo, 128, stderr));
        return this;
    }

    /** Inspecting this repository interrupts the calling thread, as a real {@code InterruptedException} would. */
    FakeGit interruptingAt(Path repo) {
        interrupting.add(key(repo));
        return this;
    }

    /** Simulate git missing from PATH. */
    FakeGit notInstalled() {
        installed = false;
        return this;
    }

    @Override
    public String version(Path directory) throws GitException {
        if (!installed) {
            throw new GitException(Path.of("."), new IOException("Cannot run program \"git\": error=2, No such file or directory"));
        }
        return "git version 0.0.0 (fake)";
    }

    @Override
    public RepoStatus inspect(Path repository) throws GitException {
        Path key = key(repository);
        if (interrupting.contains(key)) {
            Thread.currentThread().interrupt();
            throw new GitException(repository, new InterruptedException());
        }
        if (failures.containsKey(key)) {
            throw failures.get(key);
        }
        RepoStatus status = statuses.get(key);
        if (status == null) {
            throw new AssertionError("unexpected repository: " + repository);
        }
        return status;
    }

    private static Path key(Path repo) {
        return repo.toAbsolutePath().normalize();
    }
}
