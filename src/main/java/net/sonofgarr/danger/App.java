package net.sonofgarr.danger;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
        name = "danger",
        description = "How many of my git repos are dirty?",
        version = "danger 0.1.0",
        mixinStandardHelpOptions = true
)
public class App implements Callable<Integer> {

    @Option( names = "-d", description="Directory to search, within user's home" )
    private String directory = "Projects";

    private String path = System.getProperty("user.home") + File.separator + directory;

    /**
     * Main process for the application. Kicks off the whole process.
     * @param args command line arguments
     */
    public static void main( String[] args ) {
        int exitCode = new CommandLine( new App() ).execute( args );
        System.exit( exitCode );
    }

    @Override
    public Integer call() throws Exception {
        System.out.println("Listing projects in " + path );
        File projectDir = new File( path );
        inspect( projectDir );
        return 0;
    }

    private void inspect( File file ) {
        if ( file.isDirectory() ) {
            if( isGitRepo( file ) ) {
                System.out.println( (char)27 + "[31m" + file );
            } else {
                Arrays.stream(file.listFiles()).forEach( f -> inspect( f ) );
            }
        }
    }

    private boolean isGitRepo( File file ) {
        boolean isGitRepo = false;
        if( file.isDirectory() ) {
            File[] files = file.listFiles();
            List<File> gitRepos = Arrays.stream(files).filter( f -> f.getName().equals(".git") ).collect(Collectors.toList());
            if( gitRepos.size() > 0 )
                isGitRepo = true;
        }
        return isGitRepo;
    }
}
