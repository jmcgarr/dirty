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
