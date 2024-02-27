package net.sonofgarr.dirty;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
                String errors = "";
                int dirtyFiles = gitStatus( file );
                if( dirtyFiles > 0 ) {
                    errors += " " + (char)27 + "[32m"
                            + dirtyFiles + " untracked files"
                            + (char)27 + "[0m";
                }
                boolean hasNoRemote = gitRemote( file );
                if( hasNoRemote ) {
                    errors += " " + (char)27 + "[31m"
                            + "No remote repository"
                            + (char)27 + "[0m";
                }
                if( errors.length() > 0 ) {
                    System.out.println( (char)27 + "[33m"
                            + file.getAbsolutePath().replace( path + File.separator, "")
                            + ":" + (char)27 + "[0m"
                            + errors );
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

    /*******************/

    private int gitStatus( File file ) {
        AtomicInteger dirtyFiles = new AtomicInteger();
        ProcessBuilder builder = new ProcessBuilder()
                .command( "git", "status", "-s" )
                .directory( file );
        try {
            Process process = builder.start();
            StreamGobbler streamGobbler = new StreamGobbler( process.getInputStream(), s -> dirtyFiles.getAndIncrement());
            Executors.newSingleThreadExecutor().submit( streamGobbler );
            int exitCode = process.waitFor();
            assert exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(); // TODO do better than this.
        }
        return dirtyFiles.get();
    }

    private boolean gitRemote( File file ) {
        AtomicBoolean hasNoRemote = new AtomicBoolean( true );
        ProcessBuilder builder = new ProcessBuilder()
                .command( "git", "remote" )
                .directory( file );
        try {
            Process process = builder.start();
            StreamGobbler streamGobbler = new StreamGobbler( process.getInputStream(), s -> {
                if( s != null && !s.isEmpty() ) {
                    hasNoRemote.getAndSet( false );
                }
            } );
            Executors.newSingleThreadExecutor().submit( streamGobbler );
            int exitCode = process.waitFor();
            assert exitCode == 0;
        } catch (IOException | InterruptedException e ) {

        }
        return hasNoRemote.get();
    }
}

