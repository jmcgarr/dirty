package net.sonofgarr.dirty;

import java.nio.file.Path;

/** Answers questions about a git repository. Tests substitute a fake. */
public interface Git {

    RepoStatus inspect(Path repository) throws GitException;
}
