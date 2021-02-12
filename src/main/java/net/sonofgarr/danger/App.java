package net.sonofgarr.danger;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

@Command(
        name = "danger",
        description = "How many of my git repos are dirty?",
        version = "danger 0.1.0",
        mixinStandardHelpOptions = true
)
public class App implements Callable<Integer> {

    @Parameters( index="0", description="The name of the app to create." )
    private String appName;

    @Override
    public Integer call() throws Exception {
        System.out.println("This is working");
        return 0;
    }

    /**
     * Main process for the application. Kicks off the whole process.
     * @param args command line arguments
     */
    public static void main( String[] args ) {
        int exitCode = new CommandLine( new App() ).execute( args );
        System.exit( exitCode );
    }
}
