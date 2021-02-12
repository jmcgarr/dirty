package net.sonofgarr.dirty;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        File projectDir = new File( path );
        inspect( projectDir );
        return 0;
    }

    private void inspect( File file ) {
        if ( file.isDirectory() ) {
            if( isGitRepo( file ) ) {
                int dirtyFiles = gitStatus( file );
                if( dirtyFiles > 0 ) {
                    String repo = file.getAbsolutePath().replace( path + File.separator, "");
                    System.out.println( (char)27 + "[32m"
                            + repo + ": "
                            + (char)27 + "[0m"
                            + (char)27 + "[31m"
                            + dirtyFiles + " untracked files"
                            + (char)27 + "[0m"
                    );
                }

            } else {
                Arrays.stream(file.listFiles()).forEach( f -> inspect( f ) );
            }
        }
    }

    private boolean isGitRepo( File file ) {
        boolean isGitRepo = false;
        if( file.isDirectory() ) {
            List<File> gitRepos = Arrays.stream( file.listFiles() )
                    .filter( f -> f.getName().equals(".git") )
                    .collect( Collectors.toList() );
            if( gitRepos.size() > 0 )
                isGitRepo = true;
        }
        return isGitRepo;
    }

    private int gitStatus( File file ) {
        AtomicInteger dirtyFiles = new AtomicInteger();
        ProcessBuilder builder = new ProcessBuilder();
        builder.command( "git", "status", "-s" );
        builder.directory( file );
        try {
            Process process = builder.start();
            StreamGobbler streamGobbler = new StreamGobbler( process.getInputStream(), s -> dirtyFiles.getAndIncrement());
            Executors.newSingleThreadExecutor().submit( streamGobbler );
            int exitCode = process.waitFor();
            assert exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return dirtyFiles.get();
    }
}

class StreamGobbler implements Runnable {
    private InputStream inputStream;
    private Consumer<String> consumer;

    public StreamGobbler( InputStream inputStream, Consumer<String> consumer ) {
        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines()
                .forEach(consumer);
    }
}