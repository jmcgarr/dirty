package net.sonofgarr.dirty;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.concurrent.Callable;

@Command(
        name = "dirty",
        description = "How many of my git repos are dirty?",
        version = "dirty 0.1.0",
        mixinStandardHelpOptions = true
)
public class App implements Callable<Integer> {

    @Option( names = "-d", description="Directory to search, within user's home" )
    private String directory = "Projects";

    private String path = System.getProperty("user.home") + File.separator + directory;

    public static void main( String[] args ) {
        int exitCode = new CommandLine( new App() ).execute( args );
        System.exit( exitCode );
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Finding dirty git repos in " + path );
        new PathInspector( path ).recursiveInspection();
        return 0;
    }
}

