package net.sonofgarr.dirty;

import java.nio.file.Path;
import java.util.List;

/** Everything a scan found, with reports sorted by relative path. */
public record ScanResult(Path root, List<RepoReport> reports) {

    public ScanResult {
        reports = List.copyOf(reports);
    }

    public boolean hasErrors() {
        return reports.stream().anyMatch(RepoReport::hasError);
    }
}
