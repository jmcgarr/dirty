package net.sonofgarr.dirty.process;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TracingCommandRunnerTest {

    private final List<String> trace = new ArrayList<>();
    private final Path dir = Path.of("/work/repo");

    @Test
    void reportsTheCommandAndItsExitStatus() throws Exception {
        CommandRunner ok = (d, c, e) -> new CommandResult(3, "out", "");

        CommandResult result = new TracingCommandRunner(ok, trace::add).run(dir, List.of("git", "status"), Map.of());

        assertEquals(3, result.exitCode());
        assertEquals(2, trace.size());
        assertEquals("$ git status  (in " + dir + ")", trace.get(0));
        assertTrue(trace.get(1).matches("  exit 3 in \\d+ ms"), trace.get(1));
    }

    @Test
    void reportsFailuresAndRethrows() {
        CommandRunner broken = (d, c, e) -> {
            throw new IOException("boom");
        };

        assertThrows(IOException.class, () -> new TracingCommandRunner(broken, trace::add).run(dir, List.of("git")));
        assertEquals(2, trace.size());
        assertTrue(trace.get(1).matches("  failed after \\d+ ms: boom"), trace.get(1));
    }
}
