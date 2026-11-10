package net.sonofgarr.dirty.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProcessCommandRunnerTest {

    @TempDir
    Path dir;

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    /** The JDK's java launcher; absent inside a native image, where these tests are skipped. */
    private static String java() {
        String home = System.getProperty("java.home");
        assumeTrue(home != null && !home.isBlank(), "no JDK available (running as a native image)");
        Path launcher = Path.of(home, "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        assumeTrue(Files.isExecutable(launcher), "no java launcher at " + launcher);
        return launcher.toString();
    }

    @Test
    void capturesStdoutAndExitCode() throws Exception {
        CommandResult result = runner.run(dir, List.of(java(), "--version"));

        assertTrue(result.succeeded());
        assertTrue(result.stdout().contains("Runtime Environment") || result.stdout().toLowerCase().contains("openjdk"), result.stdout());
        assertFalse(result.stderr().contains("version"), "version banner must not leak to stderr: " + result.stderr());
    }

    @Test
    void capturesStderrSeparately() throws Exception {
        CommandResult result = runner.run(dir, List.of(java(), "-version"));

        assertTrue(result.succeeded());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("version"), result.stderr());
        assertFalse(result.stdout().contains("version"));
    }

    @Test
    void reportsNonZeroExit() throws Exception {
        CommandResult result = runner.run(dir, List.of(java(), "-cp", dir.toString(), "no.such.Main"));

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("no.such.Main"), result.stderr());
    }

    @Test
    void missingExecutableIsAnIOException() {
        assertThrows(IOException.class, () -> runner.run(dir, List.of("definitely-not-a-real-command-xyz")));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void doesNotDeadlockOnOutputLargerThanThePipeBuffer() throws Exception {
        String script = "yes stdout-line | head -c 300000; yes stderr-line | head -c 300000 1>&2";

        CommandResult result = runner.run(dir, List.of("sh", "-c", script));

        assertTrue(result.succeeded());
        assertEquals(300000, result.stdout().length());
        assertEquals(300000, result.stderr().length());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void runsInTheGivenDirectoryWithExtraEnvironment() throws Exception {
        ProcessCommandRunner withEnv = new ProcessCommandRunner(java.util.Map.of("DIRTY_TEST_VAR", "hello", "DIRTY_GONE", "x"));
        java.util.Map<String, String> perCall = new java.util.HashMap<>();
        perCall.put("DIRTY_GONE", null);
        perCall.put("DIRTY_CALL", "call");

        CommandResult result = withEnv.run(dir, List.of("sh", "-c", "pwd; printf %s \"$DIRTY_TEST_VAR-$DIRTY_CALL-${DIRTY_GONE:-unset}\""), perCall);

        assertTrue(result.stdout().startsWith(dir.toRealPath().toString()), result.stdout());
        assertTrue(result.stdout().endsWith("hello-call-unset"), result.stdout());
    }
}
