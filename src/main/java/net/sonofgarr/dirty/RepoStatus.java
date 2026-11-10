package net.sonofgarr.dirty;

/** What git reports about one repository. */
public record RepoStatus(int modified, int untracked, boolean hasRemote) {

    public int changedFiles() {
        return modified + untracked;
    }

    public boolean isDirty() {
        return changedFiles() > 0 || !hasRemote;
    }
}
