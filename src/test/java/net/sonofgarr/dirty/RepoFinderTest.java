package net.sonofgarr.dirty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class RepoFinderTest {

    @TempDir
    Path root;

    private final List<String> warnings = new ArrayList<>();
    private final RepoFinder finder = new RepoFinder();

    private Path repo(String relative) throws IOException {
        Path dir = root.resolve(relative);
        Files.createDirectories(dir.resolve(".git"));
        return dir;
    }

    @Test
    void findsReposAtAnyDepthUpToTheDefault() throws IOException {
        Path a = repo("a");
        Path b = repo("group/b");
        Path deep = repo("one/two/three/four");

        assertEquals(List.of(a, b, deep), finder.find(root, warnings::add));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void doesNotDescendIntoARepository() throws IOException {
        Path a = repo("a");
        repo("a/nested");

        assertEquals(List.of(a), finder.find(root, warnings::add));
    }

    @Test
    void treatsAGitFileAsARepository() throws IOException {
        Path worktree = root.resolve("wt");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), "gitdir: /elsewhere/.git/worktrees/wt\n");

        assertEquals(List.of(worktree), finder.find(root, warnings::add));
    }

    @Test
    void skipsHiddenAndBuildOutputDirectories() throws IOException {
        repo(".cache/hidden");
        repo("node_modules/pkg");
        repo("build/out");
        repo("target/out");
        repo(".gradle/x");
        Path visible = repo("visible");

        assertEquals(List.of(visible), finder.find(root, warnings::add));
    }

    @Test
    void respectsMaxDepth() throws IOException {
        Path shallow = repo("l1");
        Path atLimit = repo("l1x/l2");
        repo("l1y/l2/l3");

        assertEquals(List.of(shallow, atLimit), new RepoFinder(2, Set.of()).find(root, warnings::add));
    }

    @Test
    void reportsTheRootItselfWhenItIsARepository() throws IOException {
        Files.createDirectories(root.resolve(".git"));
        repo("ignored-because-nested");

        assertEquals(List.of(root), finder.find(root, warnings::add));
    }

    @Test
    void rejectsANonDirectoryRoot() throws IOException {
        Path file = Files.writeString(root.resolve("file"), "");

        assertThrows(NotDirectoryException.class, () -> finder.find(file, warnings::add));
        assertThrows(NotDirectoryException.class, () -> finder.find(root.resolve("missing"), warnings::add));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void doesNotFollowSymbolicLinks() throws IOException {
        Path a = repo("a");
        Files.createSymbolicLink(root.resolve("loop"), root);
        Files.createSymbolicLink(root.resolve("alias"), a);

        assertEquals(List.of(a), finder.find(root, warnings::add));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void entersASymlinkedRootAndReportsPathsUnderTheGivenRoot() throws IOException {
        Path real = Files.createDirectories(root.resolve("real"));
        Files.createDirectories(real.resolve("repoA/.git"));
        Path link = Files.createSymbolicLink(root.resolve("link"), real);

        assertEquals(List.of(link.resolve("repoA")), finder.find(link, warnings::add));
    }

    @Test
    void hiddenRootIsStillScannedAtMaxDepthZero() throws IOException {
        Path hidden = root.resolve(".dotfiles");
        Files.createDirectories(hidden.resolve(".git"));

        assertEquals(List.of(hidden), new RepoFinder(0, Set.of()).find(hidden, warnings::add));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void warnsAboutUnreadableDirectoriesAndKeepsGoing() throws IOException {
        assumeFalse("root".equals(System.getProperty("user.name")), "root can read anything");
        Files.createDirectories(root.resolve("locked/inner"));
        Path visible = repo("visible");
        Files.setPosixFilePermissions(root.resolve("locked"), PosixFilePermissions.fromString("---------"));
        try {
            assertEquals(List.of(visible), finder.find(root, warnings::add));
            assertEquals(1, warnings.size(), warnings.toString());
            assertTrue(warnings.get(0).contains("locked"), warnings.get(0));
        } finally {
            Files.setPosixFilePermissions(root.resolve("locked"), PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }
}
