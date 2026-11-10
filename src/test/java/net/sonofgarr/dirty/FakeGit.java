package net.sonofgarr.dirty;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Statuses and failures keyed by absolute repository path. */
final class FakeGit implements Git {

    private final Map<Path, RepoStatus> statuses = new HashMap<>();
    private final Map<Path, GitException> failures = new HashMap<>();

    FakeGit status(Path repo, int modified, int untracked, boolean hasRemote) {
        statuses.put(repo.toAbsolutePath().normalize(), new RepoStatus(modified, untracked, hasRemote));
        return this;
    }

    FakeGit failing(Path repo, String stderr) {
        failures.put(repo.toAbsolutePath().normalize(), new GitException(repo, 128, stderr));
        return this;
    }

    @Override
    public RepoStatus inspect(Path repository) throws GitException {
        Path key = repository.toAbsolutePath().normalize();
        if (failures.containsKey(key)) {
            throw failures.get(key);
        }
        RepoStatus status = statuses.get(key);
        if (status == null) {
            throw new AssertionError("unexpected repository: " + repository);
        }
        return status;
    }
}
