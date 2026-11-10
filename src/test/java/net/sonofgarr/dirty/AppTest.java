package net.sonofgarr.dirty;

import net.sonofgarr.dirty.process.TracingCommandRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppTest {

    @TempDir
    Path home;

    private final FakeGit git = new FakeGit();
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    private int run(String... args) {
        CommandLine cmd = new CommandLine(new App(home, git, new TextReporter(Ansi.OFF)));
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
        return cmd.execute(args);
    }

    private Path repo(Path parent, String name) throws IOException {
        Path dir = parent.resolve(name);
        Files.createDirectories(dir.resolve(".git"));
        return dir;
    }

    @Test
    void defaultsToProjectsUnderHome() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "a"), 2, 0, true);

        assertEquals(0, run());
        assertEquals("a: 2 modified" + System.lineSeparator(), out.toString());
        assertEquals("Finding dirty git repos in " + projects + System.lineSeparator(), err.toString());
    }

    @Test
    void dashDSelectsADifferentDirectoryUnderHome() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "wrong"), 9, 9, false);
        Path code = Files.createDirectories(home.resolve("Code"));
        git.status(repo(code, "right"), 0, 1, true);

        assertEquals(0, run("-d", "Code"));
        assertEquals("right: 1 untracked" + System.lineSeparator(), out.toString());
        assertTrue(err.toString().contains(code.toString()), err.toString());
    }

    @Test
    void dashDAcceptsAnAbsolutePath(@TempDir Path elsewhere) throws IOException {
        git.status(repo(elsewhere, "x"), 0, 0, false);

        assertEquals(0, run("--directory", elsewhere.toString()));
        assertEquals("x: No remote repository" + System.lineSeparator(), out.toString());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void symlinkedProjectsDirectoryIsScanned(@TempDir Path volume) throws IOException {
        repo(volume, "ext");
        Files.createSymbolicLink(home.resolve("Projects"), volume);
        git.status(home.resolve("Projects").resolve("ext"), 1, 0, true); // reported under the path the user gave

        assertEquals(0, run());
        assertEquals("ext: 1 modified" + System.lineSeparator(), out.toString());
    }

    @Test
    void missingRootIsAUsageError() {
        assertEquals(2, run("-d", "nope"));
        assertTrue(err.toString().startsWith("Not a directory: " + home.resolve("nope")), err.toString());
        assertEquals("", out.toString());
    }

    @Test
    void negativeMaxDepthIsAUsageError() throws IOException {
        Files.createDirectories(home.resolve("Projects"));

        assertEquals(2, run("--max-depth", "-1"));
        assertTrue(err.toString().startsWith("--max-depth must be >= 0"), err.toString());
    }

    @Test
    void maxDepthLimitsTheSearch() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "shallow"), 1, 0, true);
        git.status(repo(projects, "a/b/deep"), 1, 0, true);

        assertEquals(0, run("--max-depth", "1"));
        assertEquals("shallow: 1 modified" + System.lineSeparator(), out.toString());
    }

    @Test
    void repositoryErrorsAreReportedAndExitOne() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.failing(repo(projects, "broken"), "fatal: bad object HEAD");
        git.status(repo(projects, "fine"), 0, 0, true);

        assertEquals(1, run());
        assertEquals("broken: error: fatal: bad object HEAD" + System.lineSeparator(), out.toString());
    }

    @Test
    void versionAndHelp() {
        assertEquals(0, run("--version"));
        assertEquals("dirty 0.1.0" + System.lineSeparator(), out.toString());
        assertEquals(0, run("--help"));
        assertTrue(out.toString().contains("--max-depth"), out.toString());
    }

    @Test
    void missingGitIsFatalWithOneStderrLineAndExitTwo() throws IOException {
        Files.createDirectories(home.resolve("Projects"));
        git.notInstalled();

        assertEquals(2, run());
        assertEquals("", out.toString());
        assertEquals(1, err.toString().lines().count(), err.toString());
        assertTrue(err.toString().startsWith("dirty: could not run git: Cannot run program \"git\""), err.toString());
    }

    @Test
    void exitCodeFlagMakesAnyRepositoryNeedingAttentionExitOne() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "clean"), 0, 0, true);
        git.status(repo(projects, "noremote"), 0, 0, false);

        assertEquals(1, run("--exit-code"));
        assertEquals("noremote: No remote repository" + System.lineSeparator(), out.toString());
    }

    @Test
    void exitCodeFlagExitsZeroWhenEverythingIsClean() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "clean"), 0, 0, true);

        assertEquals(0, run("--exit-code"));
        assertEquals("", out.toString());
    }

    @Test
    void withoutExitCodeFlagDirtinessDoesNotAffectTheExitStatus() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "messy"), 3, 3, false);

        assertEquals(0, run());
    }

    @Test
    void verbosePrintsGitVersionAndASummaryToStderrOnly() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "a"), 1, 0, true);
        git.status(repo(projects, "b"), 0, 0, true);
        git.failing(repo(projects, "c"), "fatal: nope");

        assertEquals(1, run("--verbose"));
        String stderr = err.toString();
        assertTrue(stderr.startsWith("git version 0.0.0 (fake)" + System.lineSeparator()), stderr);
        assertTrue(stderr.contains("3 repositories, 1 dirty, 1 errors"), stderr);
        assertFalse(out.toString().contains("git version"), out.toString());
    }

    @Test
    void quietByDefault() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "a"), 0, 0, true);

        assertEquals(0, run());
        assertEquals("Finding dirty git repos in " + projects + System.lineSeparator(), err.toString());
    }

    @Test
    void verboseTracesEveryGitCommandThroughTheRealProcessGit() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        repo(projects, "a");
        FakeCommandRunner commands = new FakeCommandRunner()
                .on("--version", "git version 9.9\n")
                .on("status --porcelain", "?? x\n")
                .on("remote", "origin\n");
        CommandLine cmd = new CommandLine(new App(home,
                trace -> new ProcessGit(new TracingCommandRunner(commands, trace)),
                new TextReporter(Ansi.OFF)));
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));

        assertEquals(0, cmd.execute("-v"));
        String stderr = err.toString();
        assertTrue(stderr.contains("$ git --version  (in " + projects + ")"), stderr);
        assertTrue(stderr.contains("$ git --no-optional-locks status --porcelain  (in " + projects.resolve("a") + ")"), stderr);
        assertTrue(stderr.contains("$ git --no-optional-locks remote"), stderr);
        assertEquals("a: 1 untracked" + System.lineSeparator(), out.toString());
    }

    @Test
    void interruptedScanExitsOneThirty() throws IOException {
        Path projects = Files.createDirectories(home.resolve("Projects"));
        git.status(repo(projects, "a"), 1, 0, true);
        git.interruptingAt(repo(projects, "b"));

        int code;
        boolean flagged;
        try {
            code = run();
        } finally {
            flagged = Thread.interrupted(); // clear it so later tests are unaffected
        }
        assertEquals(130, code);
        assertTrue(flagged, "App must leave the interrupt flag set for its caller");
        assertEquals("", out.toString());
        assertTrue(err.toString().endsWith("dirty: interrupted" + System.lineSeparator()), err.toString());
    }

    @Test
    void helpListsExitCodes() {
        assertEquals(0, run("--help"));
        assertTrue(out.toString().contains("Exit codes:"), out.toString());
        assertTrue(out.toString().contains("130"), out.toString());
    }
}
