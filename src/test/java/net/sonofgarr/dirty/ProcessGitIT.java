package net.sonofgarr.dirty;

import net.sonofgarr.dirty.process.CommandResult;
import net.sonofgarr.dirty.process.ProcessCommandRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the real git binary against a throwaway repository. Skipped when git is not on PATH. */
@Tag("integration")
class ProcessGitIT {

    @TempDir
    Path tmp;

    private ProcessCommandRunner runner;
    private Path repo;

    @BeforeEach
    void setUp() throws IOException {
        // Isolate from the developer's global/system git config.
        Path emptyConfig = Files.createFile(tmp.resolve("empty-gitconfig"));
        runner = new ProcessCommandRunner(Map.of(
                "GIT_CONFIG_GLOBAL", emptyConfig.toString(),
                "GIT_CONFIG_NOSYSTEM", "1",
                "GIT_TERMINAL_PROMPT", "0"));
        assumeTrue(gitAvailable(), "git is not on PATH");
        repo = Files.createDirectories(tmp.resolve("repo"));
    }

    /** Needs git >= 2.32 so GIT_CONFIG_GLOBAL isolates the test from the developer's config. */
    private boolean gitAvailable() {
        try {
            CommandResult result = runner.run(tmp, List.of("git", "--version"));
            if (!result.succeeded()) {
                return false;
            }
            String[] parts = result.stdout().replaceAll("[^0-9.]", " ").trim().split("[ .]+");
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return major > 2 || (major == 2 && minor >= 32);
        } catch (IOException | InterruptedException | RuntimeException e) {
            return false;
        }
    }

    private void git(String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git",
                "-c", "user.name=dirty-test", "-c", "user.email=dirty@test", "-c", "commit.gpgsign=false"));
        command.addAll(List.of(args));
        CommandResult result = runner.run(repo, command);
        assertTrue(result.succeeded(), () -> String.join(" ", command) + " failed: " + result.stderr());
    }

    @Test
    void countsModifiedAndUntrackedAndDetectsRemotes() throws Exception {
        git("init", "-q");
        Files.writeString(repo.resolve("tracked.txt"), "v1\n");
        git("add", "tracked.txt");
        git("commit", "-q", "-m", "initial");
        ProcessGit git = new ProcessGit(runner);

        assertEquals(new RepoStatus(0, 0, false), git.inspect(repo), "fresh commit is clean");

        Files.writeString(repo.resolve("tracked.txt"), "v2\n");
        Files.writeString(repo.resolve("untracked.txt"), "new\n");
        assertEquals(new RepoStatus(1, 1, false), git.inspect(repo), "one modified, one untracked");

        git("remote", "add", "origin", "https://example.invalid/repo.git");
        assertEquals(new RepoStatus(1, 1, true), git.inspect(repo), "remote detected");
    }

    @Test
    void seesEveryLineOfALongStatusListing() throws Exception {
        // Regression for the lost-output race: the old implementation under-counted here.
        git("init", "-q");
        for (int i = 0; i < 50; i++) {
            Files.writeString(repo.resolve("file" + i + ".txt"), "x\n");
        }

        assertEquals(50, new ProcessGit(runner).inspect(repo).untracked());
    }

    @Test
    void directoryThatIsNotARepositoryRaisesInsteadOfReportingNoRemote() throws IOException {
        Path notARepo = Files.createDirectories(tmp.resolve("not-a-repo"));
        Files.createDirectories(notARepo.resolve(".git")); // an empty .git dir is not a repository
        // GIT_CEILING_DIRECTORIES keeps git from climbing into any ancestor repo of the temp dir.

        GitException e = assertThrows(GitException.class, () -> new ProcessGit(runner).inspect(notARepo));
        assertEquals(128, e.exitCode());
        assertTrue(e.getMessage().contains("not a git repository"), e.getMessage());
    }

    @Test
    void versionPreflightWorksAgainstRealGit() throws Exception {
        String version = new ProcessGit(runner).version(tmp);

        assertTrue(version.startsWith("git version "), version);
        assertEquals(1, version.lines().count());
    }
}
