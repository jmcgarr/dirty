# 003 – Refactor into testable components and add real tests

**Size:** M  **Depends on:** 002 (Java level)  **Unblocks:** 004, 005, 006
**Supersedes:** the `adding-tests` branch (do not merge it; delete after this ships)

## Problem / Motivation

All logic lives in `App.java`. `ProcessBuilder`, `System.out`, and `user.home`
are hard-wired, so nothing can be asserted without forking real git and
capturing stdout. There are zero tests on `master`. The `adding-tests` branch
split the class into `PathInspector` / `GitRepo` / `util` but kept every seam
closed (`new GitRepo` inside the recursion, `System.out` in the walker,
`ProcessBuilder` in `GitRepo`), carried over bugs B1–B10, introduced two
regressions (absolute paths and missing header, inverted `hasRemote()`), and
its only test is `assertEquals(1, 1)`. See spec 000 for the full table.

Two bugs are fixed here because they are inseparable from the restructuring:
B1 (`-d` ignored: `path` is a field initializer computed before picocli
injects the option) and R1 (root-relative path display).

## Proposed approach

Implement the target architecture from spec 000:

- `RepoFinder.find(Path root, Consumer<String> warn) -> List<Path>` using
  `Files.walkFileTree` **without** `FOLLOW_LINKS`; `maxDepth` (default 4,
  `--max-depth`); skip hidden dirs and a small default skip list
  (`node_modules`, `build`, `target`, `.gradle`); treat a `.git` *file*
  (worktree/submodule) as a repo; do not descend into a repo;
  `visitFileFailed` → `warn`, never throw. Fixes B6, B7.
- `interface CommandRunner { CommandResult run(Path dir, List<String> argv, Duration timeout) }`
  with `ProcessCommandRunner` (sequential for now; hardening is spec 004) and a
  test `FakeCommandRunner` keyed by argv.
- `interface Git { RepoStatus inspect(Path repo) throws GitException }` with
  `ProcessGit` running `git status --porcelain` and `git remote` and parsing
  the output. Pure parsing, tested against canned strings.
- Data: `RepoStatus(changedFiles, hasRemote)`, `RepoReport(relativePath,
  status | error)`, `ScanResult(root, reports sorted by path)`. Records on
  Java 17+.
- `Scanner.scan(Path root)` orchestrates finder → git → result.
- `interface Reporter { void report(ScanResult, PrintStream out) }` with
  `TextReporter(boolean color)` reproducing today's line format via
  `util/ANSICode` (lifted from `adding-tests`).
- `App`: no-arg constructor wires the real implementations; package-private
  constructor takes `(RepoFinder, Git, Reporter, PrintStream out, PrintStream err)`.
  Resolve the root **inside `call()`** (fixes B1). Accept an absolute path for
  `-d` as well as a name under `$HOME`.
- Output contract: byte-for-byte identical to `master` for successful repos
  **except** the label changes from "untracked files" to "changed files" (B8).
  The "Finding dirty git repos in …" header stays but moves to stderr so stdout
  is pipe-clean.

### Tests (JUnit 5, no Mockito)

| Class | Covers |
|---|---|
| `RepoFinderTest` | `@TempDir` trees: repo at depth 1; repo under non-repo dir; nested repo not descended; `.git` file; hidden dir skipped; `maxDepth`; symlink loop (skipped on Windows); unreadable dir → warning not exception (skipped as root). |
| `ProcessGitTest` | `FakeCommandRunner`: porcelain 0/1/N lines, trailing-newline variants; `git remote` 0/1/2 remotes; exit 128 → `GitException` carrying stderr; `IOException` → `GitException`. |
| `ScannerTest` | `FakeGit` map: mixed success/error, deterministic ordering. |
| `TextReporterTest` | Golden strings, color on and off; pins the current format before anything else changes it. |
| `AppTest` | `new CommandLine(app).execute("-d", "x")` with fakes and `setOut/setErr`; asserts exit code, captured streams, and that `-d` changes the root (regression for B1); missing root → exit 2 (B10). |
| `ProcessGitIT` | `@Tag("integration")`, `assumeTrue(gitOnPath())`: real `git init`, commit, modify, untracked file, `remote add`; env `GIT_CONFIG_GLOBAL=/dev/null`, `GIT_CONFIG_NOSYSTEM=1`. Also asserts a non-repo dir raises `GitException` rather than "No remote". |

Illustrative sketch:

```java
@Test void nestedRepoIsNotDescended(@TempDir Path root) throws IOException {
    Files.createDirectories(root.resolve("a/.git"));
    Files.createDirectories(root.resolve("a/nested/.git"));
    Files.createDirectories(root.resolve("group/b/.git"));
    assertEquals(List.of(root.resolve("a"), root.resolve("group/b")),
                 finder.find(root, w -> {}).stream().sorted().toList());
}
```

## Scope

**In:** `src/main`, `src/test`, CLAUDE.md "What this project is" paragraph,
README usage for `-d`.
**Out:** process hardening (timeouts, stderr, exit codes: 004); new formats
(005); parallelism (006); `.java-version` (002). Deleting `adding-tests` is a
maintainer action after merge.

## Acceptance criteria

- `./gradlew test` runs ≥ 15 tests and passes **without** git on PATH
  (integration test skipped via `Assumptions` or tag).
- `AppTest` proves `-d other` scans `other`, not `Projects`.
- Running the jar against a real directory prints the same lines as `master`
  for repos git can read, modulo the label change and root-relative paths.
- No class other than `ProcessCommandRunner` references `ProcessBuilder`; no
  class other than `App`/reporters references `System.out`.
- JaCoCo report generated; line coverage of `RepoFinder`, `ProcessGit`,
  `TextReporter` ≥ 80 % (soft target, not a gate).

## Open questions (maintainer)

- Default `--max-depth` (4?) and default skip list contents.
- Should nested repos / submodules ever be reported? (Today: no.)
- Keep the header line at all? (Recommend yes, on stderr.)
- Accept the "untracked files" → "changed files" label change, or parse the
  porcelain XY columns and report untracked and modified separately?

## Decisions (2026-11-07)

- Default `--max-depth` 4; skip list `node_modules`, `build`, `target`,
  `.gradle` plus hidden directories.
- Nested repos and submodules are not reported (unchanged).
- Header line kept, moved to stderr.
- Parse porcelain XY columns; `RepoStatus` carries `modified` and `untracked`
  separately and the text line reads "N modified, M untracked" (omit a zero
  term). Supersedes the "changed files" wording above.
- Java 21: use records; no Mockito in the new tests (hand-written fakes).

## Implementation notes (2026-11-08)

- Layout: `App`, `Scanner`, `RepoFinder`, `Git`/`ProcessGit`/`GitException`,
  `RepoStatus`/`RepoReport`/`ScanResult`, `Reporter`/`TextReporter`, and
  `process/CommandRunner`/`CommandResult`/`ProcessCommandRunner`.
  `StreamGobbler` and the `adding-tests` `ANSICode` are gone; colors come from
  picocli `Help.Ansi` (decision 11), `Ansi.AUTO` in production.
- Pulled forward from spec 004 because they fell out naturally: stdout read to
  EOF on the caller thread and stderr drained on a daemon thread (fixes the
  lost-output race, the hang, and the executor leak); `--no-optional-locks`;
  non-zero exit → `GitException` → `error:` row and exit 1; `-d` to a missing
  directory → picocli usage error, exit 2. Still for 004: per-call timeout,
  `git --version` preflight, `--verbose`, `--exit-code`.
- Tests: 47 (7 classes). `ProcessGitIT` is tagged `integration` and skips
  itself when git is not on PATH or is older than 2.32 (verified with a
  stripped PATH: 3 skipped, 0 failures). `nativeTest` runs the suite inside a
  native image (44 pass; the 3 tests that need a JDK launcher abort on an
  assumption).
- Fixed from code review before merge: a symlinked scan root (common on macOS)
  was treated as a leaf and found nothing; a bogus `.git` let git climb into an
  ancestor repository (now `GIT_CEILING_DIRECTORIES` per call, inherited
  `GIT_DIR`/`GIT_WORK_TREE` dropped); repo names and git messages were parsed as
  picocli markup (now styled programmatically); hidden root dropped at
  `--max-depth 0`; child process destroyed on interrupt.
  Needed `--initialize-at-build-time=org.junit.platform.engine.TestTag` on the
  test binary. CI now runs `nativeCompile nativeTest`.
- JaCoCo: 92 % instructions / 91 % branches overall.
- Output vs `master`: same repositories, now sorted by path; counts split into
  "N modified, M untracked"; header on stderr; ANSI bytes differ (picocli emits
  `ESC[39m ESC[0m` as the reset) but render identically.

