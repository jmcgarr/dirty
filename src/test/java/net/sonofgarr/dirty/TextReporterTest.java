package net.sonofgarr.dirty;

import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextReporterTest {

    private static final String ESC = "\u001B[";
    private static final String END = ESC + "39m" + ESC + "0m";

    private final TextReporter plain = new TextReporter(Ansi.OFF);
    private final TextReporter color = new TextReporter(Ansi.ON);

    @Test
    void modifiedAndUntrackedAndNoRemote() {
        RepoReport report = RepoReport.of(Path.of("foo"), new RepoStatus(3, 2, false));

        assertEquals("foo: 3 modified, 2 untracked No remote repository", plain.format(report));
        assertEquals(ESC + "33mfoo:" + END + " " + ESC + "32m3 modified, 2 untracked" + END + " "
                + ESC + "31mNo remote repository" + END, color.format(report));
    }

    @Test
    void neverInterpretsRepoNamesOrGitMessagesAsMarkup() {
        RepoReport named = RepoReport.of(Path.of("foo|@bar"), new RepoStatus(0, 0, false));
        RepoReport errored = RepoReport.failed(Path.of("x"), "fatal: @|red weird|@ path");

        assertEquals("foo|@bar: No remote repository", plain.format(named));
        assertEquals(ESC + "33mfoo|@bar:" + END + " " + ESC + "31mNo remote repository" + END, color.format(named));
        assertEquals("x: error: fatal: @|red weird|@ path", plain.format(errored));
    }

    @Test
    void omitsZeroCounts() {
        assertEquals("a: 1 untracked", plain.format(RepoReport.of(Path.of("a"), new RepoStatus(0, 1, true))));
        assertEquals("b: 4 modified", plain.format(RepoReport.of(Path.of("b"), new RepoStatus(4, 0, true))));
        assertEquals("c: No remote repository", plain.format(RepoReport.of(Path.of("c"), new RepoStatus(0, 0, false))));
    }

    @Test
    void errorRowShowsOnlyTheFirstLineOfTheMessage() {
        RepoReport report = RepoReport.failed(Path.of("bad"), "fatal: not a git repository\nhint: more\n");

        assertEquals("bad: error: fatal: not a git repository", plain.format(report));
        assertEquals(ESC + "33mbad:" + END + " " + ESC + "31merror: fatal: not a git repository" + END, color.format(report));
    }

    @Test
    void allListsCleanRepositoriesToo() {
        ScanResult result = new ScanResult(Path.of("/root"), List.of(
                RepoReport.of(Path.of("clean"), new RepoStatus(0, 0, true)),
                RepoReport.of(Path.of("dirty"), new RepoStatus(1, 0, true))
        ));

        assertEquals("clean: clean" + System.lineSeparator() + "dirty: 1 modified" + System.lineSeparator(),
                Fixtures.render(new TextReporter(Ansi.OFF, true), result));
        assertEquals(ESC + "33mclean:" + END + " " + ESC + "32mclean" + END + System.lineSeparator()
                + ESC + "33mdirty:" + END + " " + ESC + "32m1 modified" + END + System.lineSeparator(),
                Fixtures.render(new TextReporter(Ansi.ON, true), result));
    }

    @Test
    void rootRepositoryIsShownAsDot() {
        assertEquals(".: 1 modified", plain.format(RepoReport.of(Path.of(""), new RepoStatus(1, 0, true))));
    }

    @Test
    void reportPrintsOnlyRepositoriesThatNeedAttention() {
        ScanResult result = new ScanResult(Path.of("/root"), List.of(
                RepoReport.of(Path.of("clean"), new RepoStatus(0, 0, true)),
                RepoReport.of(Path.of("dirty"), new RepoStatus(1, 0, true)),
                RepoReport.failed(Path.of("broken"), "boom")
        ));
        StringWriter out = new StringWriter();

        plain.report(result, new PrintWriter(out));

        assertEquals("dirty: 1 modified" + System.lineSeparator() + "broken: error: boom" + System.lineSeparator(), out.toString());
    }
}
