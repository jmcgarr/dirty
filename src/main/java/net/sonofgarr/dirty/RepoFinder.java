package net.sonofgarr.dirty;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Finds git repositories (directories containing a {@code .git} entry) under a
 * root. Does not follow symbolic links, does not descend into a repository once
 * found, skips hidden directories and a small list of build-output names, and
 * reports unreadable entries through a warning callback instead of failing.
 */
public final class RepoFinder {

    public static final int DEFAULT_MAX_DEPTH = 4;
    public static final Set<String> DEFAULT_SKIP = Set.of("node_modules", "build", "target", ".gradle");

    private final int maxDepth;
    private final Set<String> skipDirectoryNames;

    public RepoFinder() {
        this(DEFAULT_MAX_DEPTH, DEFAULT_SKIP);
    }

    /**
     * @param maxDepth how many directory levels below the root to search; a repo
     *                 exactly {@code maxDepth} levels down is still found
     */
    public RepoFinder(int maxDepth, Set<String> skipDirectoryNames) {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("maxDepth must be >= 0, got " + maxDepth);
        }
        this.maxDepth = maxDepth;
        this.skipDirectoryNames = Set.copyOf(skipDirectoryNames);
    }

    public List<Path> find(Path root, Consumer<String> warn) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new NotDirectoryException(root.toString());
        }
        // Walk the real directory so a symlinked root (e.g. ~/Projects -> /Volumes/x)
        // is entered rather than treated as a leaf, but report paths under the
        // root the caller gave us.
        Path realRoot = root.toRealPath();
        List<Path> repos = new ArrayList<>();
        Files.walkFileTree(realRoot, EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(realRoot) && shouldSkip(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (isRepo(dir)) {
                    repos.add(root.resolve(realRoot.relativize(dir)));
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Directories at exactly maxDepth arrive here instead of preVisitDirectory.
                if (attrs.isDirectory() && (file.equals(realRoot) || !shouldSkip(file)) && isRepo(file)) {
                    repos.add(root.resolve(realRoot.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                warn.accept("skipped " + file + ": " + describe(exc));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    warn.accept("incomplete " + dir + ": " + describe(exc));
                }
                return FileVisitResult.CONTINUE;
            }
        });
        repos.sort(Comparator.naturalOrder());
        return repos;
    }

    private boolean shouldSkip(Path dir) {
        Path name = dir.getFileName();
        if (name == null) {
            return false;
        }
        String n = name.toString();
        return n.startsWith(".") || skipDirectoryNames.contains(n);
    }

    private static boolean isRepo(Path dir) {
        // .git may be a directory or, for worktrees and submodules, a file.
        return Files.exists(dir.resolve(".git"));
    }

    private static String describe(IOException exc) {
        return exc.getMessage() == null ? exc.getClass().getSimpleName() : exc.getClass().getSimpleName() + " " + exc.getMessage();
    }
}
