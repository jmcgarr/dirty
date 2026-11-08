package net.sonofgarr.dirty;

import java.nio.file.Path;

/**
 * One discovered repository: its path relative to the scan root and either a
 * status or the reason it could not be inspected.
 */
public record RepoReport(Path relativePath, RepoStatus status, String error) {

    public static RepoReport of(Path relativePath, RepoStatus status) {
        return new RepoReport(relativePath, status, null);
    }

    public static RepoReport failed(Path relativePath, String error) {
        return new RepoReport(relativePath, null, error);
    }

    public boolean hasError() {
        return error != null;
    }

    /** True when the line is worth showing: dirty, missing a remote, or errored. */
    public boolean needsAttention() {
        return hasError() || status.isDirty();
    }

    /** Display name: the relative path, or {@code .} when the root itself is the repo. */
    public String displayName() {
        String s = relativePath.toString();
        return s.isEmpty() ? "." : s;
    }
}
