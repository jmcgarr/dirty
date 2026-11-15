# 005 – Output formats: table, JSON, CSV, and color control

**Size:** M  **Depends on:** 003, 004  **Independent of:** 006, 007

## Problem / Motivation

README asks for tabular and machine-readable output. Today the renderer is
inline string concatenation with raw escape codes (`App.java:48-61`), and
ANSI sequences are emitted unconditionally: `dirty | cat -v` shows `^[[33m`,
`grep` matches break, and non-ANSI Windows consoles print garbage (B9).
There is no way to include clean repos or to consume the output from a script.

## Proposed approach

- `@Option(names = {"-f", "--format"}, defaultValue = "text") Format format;`
  with `enum Format { text, table, json, csv }` mapped to a `Reporter`.
- `TableReporter`: computes column widths over the sorted reports; columns
  `repo | changed | remote | error`.
- `JsonReporter`: array of `{"path", "dirty", "changedFiles", "hasRemote",
  "error"}` with a hand-written escaper (~20 lines). No Jackson/Gson: both are
  reflection-heavy and cost native-image effort for 30 lines of code.
- `CsvReporter`: header row, RFC 4180 quoting.
- Color: decided once in `App` from `--color` / `--no-color`, the `NO_COLOR`
  env var, and `System.console() != null`. Passed as a boolean to text/table
  reporters. Alternative: picocli's built-in `Help.Ansi.AUTO`, which already
  honours `NO_COLOR`/`CLICOLOR`/TTY and would retire `ANSICode`; evaluate and
  pick one.
- `--all`: text/table show only dirty or errored repos by default (today's
  behaviour); `--all` includes clean ones. Machine formats emit every
  discovered repo with `dirty: true|false` by default.
- Error rows: text/table render `path: error: …`; JSON/CSV populate `error`
  and leave `changedFiles`/`hasRemote` null/empty.
- Golden-string tests per reporter, color on and off. A test-only JSON parser
  (e.g. Jackson in `testImplementation`) is fine: test deps do not affect
  native-image.
- README usage section documents all four formats.

## Scope

**In:** reporters, `App` options, README.
**Out:** ahead/behind columns (data not collected yet); paging; Unicode box
drawing; streaming output.

## Acceptance criteria

- `dirty -f json` parses as valid JSON with one object per discovered repo,
  errors included.
- `dirty -f csv | column -t -s,` is readable; fields containing commas or
  quotes are quoted correctly.
- `dirty -f text` output is unchanged from spec 003 when color is on.
- `dirty | cat -v` and `NO_COLOR=1 dirty` emit no escape sequences;
  `dirty --color | cat -v` does.
- `--all` adds clean repos to text/table output.

## Open questions (maintainer)

- Is the JSON shape a public contract? If so add a top-level `"version"` field
  and document it.
- Include clean repos by default in machine formats (recommend yes) and in
  text (recommend no)?
- Keep `ANSICode` or switch to picocli's `Ansi`?

## Decisions (2026-11-07)

- JSON is a public contract: top-level object `{"version": 1, "root": …,
  "repos": [...]}`; document it in README.
- Machine formats include clean repos by default; text/table do not (`--all`).
- Use picocli's `CommandLine.Help.Ansi` (`AUTO`, honours `NO_COLOR`, TTY) and
  retire `util/ANSICode`.
- Per spec 003, repo objects carry `modified` and `untracked` counts rather
  than a single `changedFiles`.

## Implementation notes (2026-11-09)

Specs 003/004 already delivered `Reporter`, `TextReporter(Ansi)` on picocli's
`ColorScheme`, `RepoStatus(modified, untracked, hasRemote)`,
`RepoReport(relativePath, status, error)`, `ScanResult(root, reports)` and
removed `util/ANSICode`. These notes pin what is left and override the
"Proposed approach" where they differ.

### Options and wiring (`App`)

- `enum Format { text, table, json, csv }` nested in `App` (lowercase so help
  shows the literal values). `@Option(names = {"-f", "--format"}, paramLabel =
  "<format>", defaultValue = "text", description = "Output format:
  ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")`. Case-sensitive
  (picocli default): `-f JSON` is a usage error, exit 2.
- `--all` (`boolean all`): "Include clean repositories (text and table; json
  and csv always list every repository)." Accepted as a no-op for json/csv so
  a shell alias carrying `--all` works with every format.
- `--color` / `--no-color`: two plain `boolean` options; both given →
  `ParameterException` ("--color and --no-color are mutually exclusive", exit
  2) in `call()` beside the `--max-depth` check. `--color` → `Ansi.ON`,
  `--no-color` → `Ansi.OFF`, neither → `Ansi.AUTO`. Preferred over
  `@ArgGroup(exclusive = true)`: no extra class, no reflection entry. json/csv
  ignore both and never emit ANSI.
- Help order (`sortOptions = false`): `-d`, `--max-depth`, `-f`, `--all`,
  `--color`, `--no-color`, `--exit-code`, `-v`.
- Reporter construction goes behind a nested
  `interface ReporterFactory { Reporter create(Format format, Ansi ansi, boolean all); }`
  and `static Reporter reporter(Format, Ansi, boolean)` in `App`, the single
  `switch` from enum to `TextReporter(ansi, all)`, `TableReporter(ansi, all)`,
  `JsonReporter()`, `CsvReporter()`. Production constructor passes
  `App::reporter`; the existing test constructors wrap their `Reporter` as
  `(f, a, all) -> reporter`; add `App(Path home, Git git)` using
  `App::reporter` for format-selection tests. `TextReporter(Ansi)` stays and
  delegates to `TextReporter(ansi, false)`, so its goldens are untouched.
- Banner, warnings and the `-v` summary stay on stderr for every format.

### JSON (`JsonReporter`)

- `{"version": 1, "root": "<absolute path>", "repos": [...]}`. Every repo
  object has exactly these keys, in this order: `path` (string,
  `displayName()`, platform separators as-is), `dirty` (boolean), `modified`
  (int), `untracked` (int), `hasRemote` (boolean), `error` (string). Error
  row: the four status fields are `null`, `error` is the full `strip()`ed git
  message (not just the first line); status row: `error` is `null`. `dirty`
  is `null` rather than `true` on error rows because the status is unknown;
  `jq '.repos[] | select(.dirty or .error)'` still finds everything.
- 2-space pretty print, one key per line (what `jq .` prints); empty scan →
  `"repos": []`; single trailing `\n`. Line endings are `\n` on every platform
  (build in a `StringBuilder`, `out.print`, `out.flush()`) so goldens and
  consumers see identical bytes everywhere.
- Escaper `static String quote(String)`: `"` → `\"`, `\` → `\\`, `\n` `\r`
  `\t` `\b` `\f` → short escapes, other chars below U+0020 → `\u00XX`;
  everything else including non-ASCII passes through (the `PrintWriter`
  encodes it). No `/` escaping.

### CSV (`CsvReporter`)

- Header always, even for an empty scan: `path,dirty,modified,untracked,hasRemote,error`.
- RFC 4180 quoting: wrap in `"` when a field contains `,`, `"`, CR or LF;
  double embedded `"`. Nothing else is quoted.
- `\n` line endings, not the RFC's `\r\n`: `column`, `cut`, `awk`, `grep`
  show or trip over `^M`; Excel, Numbers and Python's `csv` accept `\n`.
- `true`/`false`, plain integers, `path` = `displayName()`. Error row: four
  empty cells, then the full stripped message, e.g.
  `broken,,,,,fatal: bad object HEAD`. Trailing `\n` after the last row.

### Table (`TableReporter`)

- Columns `REPO  MODIFIED  UNTRACKED  REMOTE  ERROR`, two-space separator,
  plain uppercase header, always all five. `REPO`/`ERROR` left-aligned,
  counts right-aligned, `REMOTE` is `yes`/`no`. Error rows show `-` in the
  three status cells and the first line of the message. Last column never
  padded, no trailing whitespace. Widths computed over the printed rows only.
  ```
  REPO    MODIFIED  UNTRACKED  REMOTE  ERROR
  dirty          6         11  yes
  budget         0          0  no
  broken         -          -  -       fatal: bad object HEAD
  ```
- Color uses the text roles, applied after padding and measured on the plain
  string: `REPO` yellow, non-zero counts green, `no`, `-` and `ERROR` red;
  header and zeros plain. `Ansi.OFF` output is byte-equal to the layout above.
- Zero rows: print nothing (no header), like text, so `dirty -f table | wc -l`
  stays meaningful. Platform line separator (`println`), like text.

### Rows per format

text/table: `needsAttention()` only, or every repo with `--all`; a clean row
in text is `<path>: clean` with `clean` green like the counts. json/csv: every
repo, always; `dirty` and `error` tell them apart.

### Tests

- `JsonReporterTest`: golden for a dirty, a clean and an error repo whose path
  and message contain `"`, `\`, a newline and a non-ASCII char; golden for an
  empty scan; a table test for `quote()`. No test-scope JSON parser (the
  Jackson allowance above is withdrawn: the golden bytes already pin the
  document). Structural check: starts with `{\n  "version": 1,`, ends with
  `}\n`, no raw char below U+0020 except `\n`. Validate the goldens once with
  `jq .` while writing them and say so in a test comment.
- `CsvReporterTest`: golden for the same fixture (comma in a path, quote and
  newline in an error); header-only for an empty scan.
- `TableReporterTest`: goldens for `Ansi.OFF` and `Ansi.ON` on the mixed
  fixture; `--all` with a clean row; zero rows → `""`; no trailing whitespace.
- `TextReporterTest`: existing assertions untouched; add one for
  `new TextReporter(Ansi.OFF, true)` printing the `clean` row.
- `AppTest` via `new App(home, git)`: `-f json` starts with `{` and contains
  `"version": 1`; `-f csv` starts with the header; `-f bogus` → 2 and stderr
  names the four values; `--color --no-color` → 2; `--all` adds a clean text
  row; `--no-color` and `-f json --color` contain no `\u001B`; `--help` lists
  `--format`.
- Native image: picocli-codegen 4.7.7's `ReflectionConfigGenerator` registers
  each option's declared type by name (`visitTypeInfo`; only primitives, boxed
  types and basic `java.lang` classes are excluded), so `App$Format` lands in
  `build/classes/java/main/META-INF/native-image/picocli-generated/dirty/reflect-config.json`
  with no extra config. Check with `grep 'App$Format'` on that file after
  `compileJava`, then `./gradlew nativeTest`. The reporters use no reflection.

### README

- Usage lines: `dirty -f table`, `dirty -f json | jq '.repos[] | select(.dirty)'`,
  `dirty -f csv > repos.csv`, `dirty --all`, `dirty --no-color`.
- New "Output formats" paragraph: text/table show only repos needing attention
  unless `--all`; json/csv list every repo; the JSON document as an example;
  contract note: "`version` is 1; keys are never removed or renamed within a
  version, new keys may be added; a breaking change bumps `version`". Color
  paragraph gains `--color` / `--no-color` next to `NO_COLOR` and the TTY check.
- Tick the TODOs "Make output tabular" and "Option to output machine readable
  formats (csv, json)".
