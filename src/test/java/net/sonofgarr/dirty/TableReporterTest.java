package net.sonofgarr.dirty;

import org.junit.jupiter.api.Test;
import picocli.CommandLine.Help.Ansi;

import static net.sonofgarr.dirty.Fixtures.EMPTY;
import static net.sonofgarr.dirty.Fixtures.MIXED;
import static net.sonofgarr.dirty.Fixtures.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TableReporterTest {

    private static final String NL = System.lineSeparator();
    private static final String ESC = "\u001B[";
    private static final String END = ESC + "39m" + ESC + "0m";

    private static String yellow(String s) {
        return ESC + "33m" + s + END;
    }

    private static String green(String s) {
        return ESC + "32m" + s + END;
    }

    private static String red(String s) {
        return ESC + "31m" + s + END;
    }

    @Test
    void alignedColumnsForRepositoriesNeedingAttention() {
        String expected = "REPO       MODIFIED  UNTRACKED  REMOTE  ERROR" + NL
                + "dirty             6         11  yes" + NL
                + "no,remote         0          0  no" + NL
                + "broken            -          -  -       fatal: bad \"object\" \\ HEAD" + NL;

        String table = render(new TableReporter(Ansi.OFF, false), MIXED);

        assertEquals(expected, table);
        assertFalse(table.lines().anyMatch(line -> line.endsWith(" ")), "trailing whitespace");
    }

    @Test
    void allIncludesCleanRepositories() {
        String expected = "REPO       MODIFIED  UNTRACKED  REMOTE  ERROR" + NL
                + "dirty             6         11  yes" + NL
                + "clean             0          0  yes" + NL
                + "no,remote         0          0  no" + NL
                + "broken            -          -  -       fatal: bad \"object\" \\ HEAD" + NL;

        assertEquals(expected, render(new TableReporter(Ansi.OFF, true), MIXED));
    }

    @Test
    void colorIsAppliedAfterPaddingSoColumnsStayAligned() {
        String expected = "REPO       MODIFIED  UNTRACKED  REMOTE  ERROR" + NL
                + yellow("dirty    ") + "  " + green("       6") + "  " + green("       11") + "  yes" + NL
                + yellow("no,remote") + "         0          0  " + red("no") + NL
                + yellow("broken   ") + "  " + red("       -") + "  " + red("        -") + "  " + red("-     ")
                + "  " + red("fatal: bad \"object\" \\ HEAD") + NL;

        assertEquals(expected, render(new TableReporter(Ansi.ON, false), MIXED));
    }

    @Test
    void nothingAtAllForZeroRows() {
        assertEquals("", render(new TableReporter(Ansi.OFF, false), EMPTY));
        assertEquals("", render(new TableReporter(Ansi.ON, true), EMPTY));
    }
}
