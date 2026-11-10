package net.sonofgarr.dirty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerTest {

    @TempDir
    Path root;

    private Path repo(String relative) throws IOException {
        Path dir = root.resolve(relative);
        Files.createDirectories(dir.resolve(".git"));
        return dir;
    }

    @Test
    void collectsStatusesAndErrorsSortedByRelativePath() throws Exception {
        Path zeta = repo("zeta");
        Path alpha = repo("group/alpha");
        Path broken = repo("broken");
        FakeGit git = new FakeGit()
                .status(zeta, 1, 0, true)
                .status(alpha, 0, 0, false)
                .failing(broken, "fatal: bad object HEAD");

        ScanResult result = new Scanner(new RepoFinder(), git).scan(root, w -> {});

        assertEquals(root, result.root());
        assertEquals(List.of(
                RepoReport.failed(Path.of("broken"), "fatal: bad object HEAD"),
                RepoReport.of(Path.of("group/alpha"), new RepoStatus(0, 0, false)),
                RepoReport.of(Path.of("zeta"), new RepoStatus(1, 0, true))
        ), result.reports());
        assertTrue(result.hasErrors());
    }

    @Test
    void cleanRepositoriesAreStillReportedButNeedNoAttention() throws Exception {
        Path clean = repo("clean");

        ScanResult result = new Scanner(new RepoFinder(), new FakeGit().status(clean, 0, 0, true)).scan(root, w -> {});

        assertEquals(1, result.reports().size());
        assertFalse(result.reports().get(0).needsAttention());
        assertFalse(result.hasErrors());
    }

    @Test
    void rootThatIsItselfARepositoryGetsAnEmptyRelativePath() throws Exception {
        Files.createDirectories(root.resolve(".git"));

        ScanResult result = new Scanner(new RepoFinder(), new FakeGit().status(root, 0, 2, true)).scan(root, w -> {});

        assertEquals(1, result.reports().size());
        assertEquals(".", result.reports().get(0).displayName());
    }

    @Test
    void anInterruptedInspectionAbortsTheScanAndKeepsTheInterruptVisible() throws IOException {
        Path first = repo("a-first");
        Path second = repo("b-second");
        FakeGit git = new FakeGit().status(first, 1, 0, true).interruptingAt(second);

        boolean flagged;
        try {
            assertThrows(InterruptedException.class, () -> new Scanner(new RepoFinder(), git).scan(root, w -> {}));
        } finally {
            flagged = Thread.interrupted();
        }
        assertFalse(flagged, "Scanner clears the flag, as InterruptedException convention requires; App re-sets it");
    }
}
