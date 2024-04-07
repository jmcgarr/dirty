package net.sonofgarr.dirty;

import net.sonofgarr.dirty.util.StreamGobbler;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

// TODO Should we extend the File class?
public class GitRepo {

    private File file;

    public GitRepo( File file ) {
        this.file = file;
    }

    public boolean isDirectory() {
        return file.isDirectory();
    }

    public boolean isGitRepo() {
        boolean isGitRepo = false;
        if( file.isDirectory() ) {
            List<File> gitRepos = Arrays.stream( file.listFiles() )
                    .filter( f -> f.getName().equals(".git") )
                    .collect( Collectors.toList() );
            if( !gitRepos.isEmpty() )
                isGitRepo = true;
        }
        return isGitRepo;
    }

    public int status() {
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

    public boolean hasRemote() {
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
