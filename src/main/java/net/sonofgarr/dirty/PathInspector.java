package net.sonofgarr.dirty;

import net.sonofgarr.dirty.util.ANSICode;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class PathInspector {

    private final File projectDirectory;

    public PathInspector( String path ) {
        this( new File( path ) );
    }

    public PathInspector( File directory ) {
        this.projectDirectory = directory;
    }

    public void recursiveInspection() {
        this.recursiveInspection( projectDirectory );
    }

    private void recursiveInspection( File directory ) {
        GitRepo repo = new GitRepo(directory);
        if ( repo.isGitRepo() ) {
            String errors = "";
            errors += dirtyFilesTextFormat(repo.status());
            errors += remoteTextFormat(repo.hasRemote());
            if (!errors.isEmpty()) {
                String directoryPath = directory.getAbsolutePath().replace(directory.getAbsolutePath() + File.separator, "");
                String preface = decorateText(directoryPath, ANSICode.YELLOW, false);
                System.out.println(preface + errors);
            }
        } else if ( repo.isDirectory() ) {
            Arrays.stream( directory.listFiles() ).forEach( f -> recursiveInspection( f ) );
        }
    }

    private String dirtyFilesTextFormat( int numberOfDirtyFiles ) {
        String text = "";
        if( numberOfDirtyFiles > 0 ) {
            text = decorateText(numberOfDirtyFiles + " untracked files", ANSICode.GREEN, true);
        }
        return text;
    }

    private String remoteTextFormat( boolean hasNoRemote ) {
        String text = "";
        if( hasNoRemote ) {
            text = decorateText("No remote repository", ANSICode.RED, true);
        }
        return text;
    }

    private String decorateText( String text, ANSICode decoration, boolean indent ) {
        String decoratedText = indent ? " " : "";
        decoratedText += (char)27 + "[" + decoration.getCode() + "m";
        decoratedText += text;
        decoratedText += (char)27 + "[0m";
        return decoratedText;
    }
}