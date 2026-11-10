package net.sonofgarr.dirty;

import net.sonofgarr.dirty.process.CommandResult;
import net.sonofgarr.dirty.process.CommandRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@link Git} implemented by running the {@code git} binary through a {@link CommandRunner}. */
public final class ProcessGit implements Git {

    private final CommandRunner runner;

    public ProcessGit(CommandRunner runner) {
        this.runner = runner;
    }

    @Override
    public String version(Path directory) throws GitException {
        return run(directory, Map.of(), "--version").lines().findFirst().orElse("git (unknown version)").strip();
    }

    @Override
    public RepoStatus inspect(Path repository) throws GitException {
        Map<String, String> env = environmentFor(repository);
        String status = run(repository, env, "--no-optional-locks", "status", "--porcelain");
        String remotes = run(repository, env, "--no-optional-locks", "remote");

        int modified = 0;
        int untracked = 0;
        for (String line : status.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            // Porcelain v1: two status columns then a space then the path.
            if (line.startsWith("??")) {
                untracked++;
            } else if (!line.startsWith("!!")) {
                modified++;
            }
        }
        boolean hasRemote = remotes.lines().anyMatch(l -> !l.isBlank());
        return new RepoStatus(modified, untracked, hasRemote);
    }

    /** {@code --no-optional-locks} on the status/remote calls keeps a read-only scan from ever writing the index. */
    private String run(Path directory, Map<String, String> environment, String... args) throws GitException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        try {
            CommandResult result = runner.run(directory, command, environment);
            if (!result.succeeded()) {
                throw new GitException(directory, result.exitCode(), result.stderr());
            }
            return result.stdout();
        } catch (IOException e) {
            throw new GitException(directory, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException(directory, e);
        }
    }

    /**
     * Confine git to the directory we think is the repository. Without a ceiling,
     * a bogus {@code .git} entry makes git climb into an ancestor repository and
     * report its status as this one's. Inherited GIT_DIR/GIT_WORK_TREE would
     * redirect every call to one repo.
     */
    static Map<String, String> environmentFor(Path repository) {
        Map<String, String> env = new HashMap<>();
        Path parent = repository.toAbsolutePath().normalize().getParent();
        if (parent != null) {
            env.put("GIT_CEILING_DIRECTORIES", parent.toString());
        }
        env.put("GIT_DIR", null);
        env.put("GIT_WORK_TREE", null);
        return env;
    }
}
