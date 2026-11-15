package net.sonofgarr.dirty;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static net.sonofgarr.dirty.Fixtures.EMPTY;
import static net.sonofgarr.dirty.Fixtures.MIXED;
import static net.sonofgarr.dirty.Fixtures.render;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReporterTest {

    private final JsonReporter reporter = new JsonReporter();

    /** Validated once with {@code jq .}; the bytes below are the version 1 contract. */
    @Test
    void everyRepositoryWithFixedKeysAndNullsOnErrorRows() {
        String expected = """
                {
                  "version": 1,
                  "root": "/root",
                  "repos": [
                    {
                      "path": "dirty",
                      "dirty": true,
                      "modified": 6,
                      "untracked": 11,
                      "hasRemote": true,
                      "error": null
                    },
                    {
                      "path": "clean",
                      "dirty": false,
                      "modified": 0,
                      "untracked": 0,
                      "hasRemote": true,
                      "error": null
                    },
                    {
                      "path": "no,remote",
                      "dirty": true,
                      "modified": 0,
                      "untracked": 0,
                      "hasRemote": false,
                      "error": null
                    },
                    {
                      "path": "broken",
                      "dirty": null,
                      "modified": null,
                      "untracked": null,
                      "hasRemote": null,
                      "error": "fatal: bad \\"object\\" \\\\ HEAD\\nhint: café"
                    }
                  ]
                }
                """;

        String json = render(reporter, MIXED);

        assertEquals(expected, json);
        assertTrue(json.startsWith("{\n  \"version\": 1,"));
        assertTrue(json.endsWith("}\n"));
        assertFalse(json.chars().anyMatch(c -> c < 0x20 && c != '\n'), "raw control characters");
    }

    @Test
    void rootRepositoryIsShownAsDot() {
        ScanResult result = new ScanResult(Path.of("/root"), List.of(RepoReport.of(Path.of(""), new RepoStatus(1, 0, true))));

        assertTrue(render(reporter, result).contains("\"path\": \".\",\n"));
    }

    @Test
    void emptyScanHasAnEmptyReposArray() {
        assertEquals("{\n  \"version\": 1,\n  \"root\": \"/root\",\n  \"repos\": []\n}\n", render(reporter, EMPTY));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            plain          | "plain"
            say "hi"       | "say \\"hi\\""
            back\\slash     | "back\\\\slash"
            tab\there      | "tab\\there"
            naïve          | "naïve"
            """)
    void quoteEscapesJsonMetacharacters(String input, String expected) {
        assertEquals(expected, JsonReporter.quote(input));
    }

    @Test
    void quoteEscapesControlCharactersAsUnicode() {
        assertEquals("\"a\\u0001b\\nc\\rd\\be\\ff\"", JsonReporter.quote("a\u0001b\nc\rd\be\ff"));
    }
}
