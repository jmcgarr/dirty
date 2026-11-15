package net.sonofgarr.dirty;

import java.nio.file.Path;
import java.util.List;

/** The scan the reporter golden tests share: dirty, clean, no remote, and an error with awkward characters. */
final class Fixtures {

    static final String ERROR = "fatal: bad \"object\" \\ HEAD\nhint: café\n";

    static final ScanResult MIXED = new ScanResult(Path.of("/root"), List.of(
            RepoReport.of(Path.of("dirty"), new RepoStatus(6, 11, true)),
            RepoReport.of(Path.of("clean"), new RepoStatus(0, 0, true)),
            RepoReport.of(Path.of("no,remote"), new RepoStatus(0, 0, false)),
            RepoReport.failed(Path.of("broken"), ERROR)
    ));

    static final ScanResult EMPTY = new ScanResult(Path.of("/root"), List.of());

    static String render(Reporter reporter, ScanResult result) {
        java.io.StringWriter out = new java.io.StringWriter();
        reporter.report(result, new java.io.PrintWriter(out));
        return out.toString();
    }

    private Fixtures() {
    }
}
