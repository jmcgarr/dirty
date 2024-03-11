package net.sonofgarr.dirty;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

public class PathInspector {

    private final File projectDirectory;

    public PathInspector(String path ) {
        this( new File( path ) );
    }

    public PathInspector( File directory ) {
        this.projectDirectory = directory;
    }

    public void inspect() {
        this.inspect( projectDirectory );
    }

    private void inspect( File file ) {
        if ( file.isDirectory() ) {
            if( Git.isGitRepo( file ) ) {
                String errors = "";
                errors += checkForDirtyFiles( file );
                errors += checkForRemote( file );
                if(!errors.isEmpty()) {
                    String directoryPath = file.getAbsolutePath().replace( file.getAbsolutePath() + File.separator, "");
                    String preface = decorateText(directoryPath, ANSICode.YELLOW, false);
                    System.out.println(preface + errors);
                }
            } else {
                Arrays.stream(Objects.requireNonNull(file.listFiles())).forEach(f -> inspect( f ) );
            }
        }
    }

    private String checkForDirtyFiles( File file ) {
        String text = "";
        int dirtyFiles = Git.status( file );
        if( dirtyFiles > 0 ) {
            text = decorateText(dirtyFiles + " untracked files", ANSICode.GREEN, true);
        }
        return text;
    }

    private String checkForRemote( File file ) {
        String text = "";
        boolean hasNoRemote = Git.hasRemote( file );
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
