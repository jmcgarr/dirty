package net.sonofgarr.dirty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Orchestrates a scan: find repositories, inspect each, collect reports. */
public final class Scanner {

    private final RepoFinder finder;
    private final Git git;

    public Scanner(RepoFinder finder, Git git) {
        this.finder = finder;
        this.git = git;
    }

    /**
     * @throws InterruptedException if the thread was interrupted while a repository
     *                              was being inspected; the partial result is discarded
     */
    public ScanResult scan(Path root, Consumer<String> warn) throws IOException, InterruptedException {
        List<RepoReport> reports = new ArrayList<>();
        for (Path repo : finder.find(root, warn)) {
            Path relative = root.relativize(repo);
            try {
                reports.add(RepoReport.of(relative, git.inspect(repo)));
            } catch (GitException e) {
                if (Thread.interrupted()) {
                    throw new InterruptedException("interrupted while inspecting " + repo);
                }
                reports.add(RepoReport.failed(relative, e.getMessage()));
            }
        }
        reports.sort(Comparator.comparing(r -> r.relativePath().toString()));
        return new ScanResult(root, reports);
    }
}
