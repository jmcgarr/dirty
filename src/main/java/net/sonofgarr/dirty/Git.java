package net.sonofgarr.dirty;

import java.nio.file.Path;

/** What the application needs from git. {@link ProcessGit} is the real one. */
public interface Git {

    /**
     * @param directory where to run {@code git --version}; any existing directory
     * @return the first line of the output, proving git can be run at all
     * @throws GitException if git is not installed or cannot be executed
     */
    String version(Path directory) throws GitException;

    RepoStatus inspect(Path repository) throws GitException;
}
