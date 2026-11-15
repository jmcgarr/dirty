package net.sonofgarr.dirty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.List;

import static net.sonofgarr.dirty.Fixtures.EMPTY;
import static net.sonofgarr.dirty.Fixtures.MIXED;
import static net.sonofgarr.dirty.Fixtures.render;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvReporterTest {

    private final CsvReporter reporter = new CsvReporter();

    @Test
    void everyRepositoryUnderAFixedHeader() {
        String expected = """
                path,dirty,modified,untracked,hasRemote,error
                dirty,true,6,11,true,
                clean,false,0,0,true,
                "no,remote",true,0,0,false,
                broken,,,,,"fatal: bad ""object"" \\ HEAD
                hint: café"
                """;

        assertEquals(expected, render(reporter, MIXED));
    }

    @Test
    void rootRepositoryIsShownAsDot() {
        ScanResult result = new ScanResult(Path.of("/root"), List.of(RepoReport.of(Path.of(""), new RepoStatus(1, 0, true))));

        assertEquals(CsvReporter.HEADER + "\n.,true,1,0,true,\n", render(reporter, result));
    }

    @Test
    void emptyScanIsHeaderOnly() {
        assertEquals("path,dirty,modified,untracked,hasRemote,error\n", render(reporter, EMPTY));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            plain      | plain
            a b        | a b
            a,b        | "a,b"
            say "hi" ok | "say ""hi"" ok"
            """)
    void quoteFollowsRfc4180(String input, String expected) {
        assertEquals(expected, CsvReporter.quote(input));
    }

    @Test
    void quoteWrapsFieldsContainingLineBreaks() {
        assertEquals("\"a\nb\"", CsvReporter.quote("a\nb"));
        assertEquals("\"a\rb\"", CsvReporter.quote("a\rb"));
    }
}
