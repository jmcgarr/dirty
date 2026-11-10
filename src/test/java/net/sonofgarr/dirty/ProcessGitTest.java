package net.sonofgarr.dirty;

import org.junit.jupiter.api.Test;

import net.sonofgarr.dirty.process.CommandTimeoutException;

import java.io.IOException;
import java.time.Duration;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessGitTest {

    private final Path repo = Path.of("/tmp/some-repo");
    private final FakeCommandRunner runner = new FakeCommandRunner();
    private final ProcessGit git = new ProcessGit(runner);

    @Test
    void cleanRepoWithRemote() throws GitException {
        runner.on("status --porcelain", "").on("remote", "origin\n");

        assertEquals(new RepoStatus(0, 0, true), git.inspect(repo));
    }

    @Test
    void countsUntrackedSeparatelyFromModifiedStagedDeletedAndRenamed() throws GitException {
        runner.on("status --porcelain", """
                 M src/App.java
                A  new.txt
                D  gone.txt
                R  old.txt -> renamed.txt
                MM both.txt
                ?? scratch/
                ?? notes.md
                """).on("remote", "origin\nupstream\n");

        assertEquals(new RepoStatus(5, 2, true), git.inspect(repo));
    }

    @Test
    void ignoresIgnoredEntriesAndBlankLinesAndWindowsLineEndings() throws GitException {
        runner.on("status --porcelain", "!! build/\r\n M a.txt\r\n\r\n?? b.txt\r\n").on("remote", "");

        assertEquals(new RepoStatus(1, 1, false), git.inspect(repo));
    }

    @Test
    void blankRemoteOutputMeansNoRemote() throws GitException {
        runner.on("status --porcelain", "").on("remote", "\n");

        assertFalse(git.inspect(repo).hasRemote());
    }

    @Test
    void nonZeroExitBecomesGitExceptionCarryingStderr() {
        runner.on("status --porcelain", 128, "", "fatal: not a git repository (or any of the parent directories): .git\n");

        GitException e = assertThrows(GitException.class, () -> git.inspect(repo));
        assertEquals(128, e.exitCode());
        assertEquals(repo, e.repository());
        assertTrue(e.getMessage().startsWith("fatal: not a git repository"), e.getMessage());
    }

    @Test
    void nonZeroExitWithoutStderrStillHasAMessage() {
        runner.on("status --porcelain", 1, "", "");

        assertEquals("git exited with status 1", assertThrows(GitException.class, () -> git.inspect(repo)).getMessage());
    }

    @Test
    void ioFailureBecomesGitException() {
        runner.failingWith(new IOException("Cannot run program \"git\": error=2, No such file or directory"));

        GitException e = assertThrows(GitException.class, () -> git.inspect(repo));
        assertEquals(-1, e.exitCode());
        assertTrue(e.getMessage().contains("Cannot run program"), e.getMessage());
    }

    @Test
    void runsGitInTheRepositoryWithoutTakingOptionalLocks() throws GitException {
        runner.on("status --porcelain", "").on("remote", "origin\n");

        git.inspect(repo);

        assertEquals(List.of("git", "--no-optional-locks", "status", "--porcelain"), runner.invocations.get(0));
        assertEquals(List.of("git", "--no-optional-locks", "remote"), runner.invocations.get(1));
    }

    @Test
    void confinesGitToTheRepositoryAndIgnoresInheritedGitDir() throws GitException {
        runner.on("status --porcelain", "").on("remote", "origin\n");

        git.inspect(repo);

        var env = runner.environments.get(0);
        assertEquals(repo.toAbsolutePath().getParent().toString(), env.get("GIT_CEILING_DIRECTORIES"));
        assertTrue(env.containsKey("GIT_DIR") && env.get("GIT_DIR") == null, "GIT_DIR must be unset");
        assertNull(env.get("GIT_WORK_TREE"));
        assertTrue(env.containsKey("GIT_WORK_TREE"));
    }

    @Test
    void versionIsTheFirstLineOfGitVersion() throws GitException {
        runner.on("--version", "git version 2.47.1\nextra\n");

        assertEquals("git version 2.47.1", git.version(repo));
        assertEquals(List.of("git", "--version"), runner.invocations.get(0));
    }

    @Test
    void missingGitSurfacesFromVersionAsAGitExceptionThatDidNotRun() {
        runner.failingWith(new IOException("Cannot run program \"git\": error=2, No such file or directory"));

        GitException e = assertThrows(GitException.class, () -> git.version(repo));
        assertEquals(-1, e.exitCode());
    }

    @Test
    void timeoutBecomesAGitExceptionNamingTheLimit() {
        runner.failingWith(CommandTimeoutException.killed(List.of("git", "status"), Duration.ofSeconds(30)));

        GitException e = assertThrows(GitException.class, () -> git.inspect(repo));
        assertEquals("git timed out after 30s", e.getMessage());
        assertEquals(-1, e.exitCode());
    }
}
