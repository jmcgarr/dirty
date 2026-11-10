# 000 – Architecture and test review (2026-11-06)

**Status:** reference document. This is the review that produced specs 001–007;
it is not itself a work item. Keep it here until all seven ship, then archive
it alongside them.

## How this was produced

Three independent reviews on `master` (b94e183) and the unmerged `adding-tests`
branch: an architecture review, a line-by-line code review with an empirical
harness (fake `git` shims, cold-JVM runs, symlink loops, `chmod 000` dirs), and
a test-strategy / build-toolchain review. The CI failure was verified against
the latest Build run (42237362152). Two bugs were also reproduced directly by
running the tool.

## Summary

`dirty` is a ~120-line picocli command that does CLI parsing, directory
walking, process execution, and ANSI rendering in one class. It works for the
happy path on one machine. It has **six real bugs**, one of which makes the
only CLI option a no-op, and **no seams for testing**. The `adding-tests`
branch splits the class sensibly but copies every bug across, adds two
regressions, and contains no test that exercises production code. CI has been
red for a different reason than previously documented.

## Findings on `master`

| # | Where | Finding | Verified how |
|---|---|---|---|
| B1 | `App.java:24-27` | `-d` is ignored. `path` is a field initializer computed before picocli injects the option, so the tool always scans `$HOME/Projects`. | `./gradlew run --args="-d <other>"` scanned `~/Projects`. |
| B2 | `App.java:92-99, 109-120` | stdout gobbler is submitted to an executor and never joined; counts are read right after `waitFor()`. Lines are lost. | Cold JVM: a repo with 2 status lines reported **0 dirty files in 25/25 runs**; 20,000 untracked files reported 16,280; a repo *with* a remote reported "No remote". Masked in the CLI only because picocli start-up warms the JVM. A native image has no warm-up. |
| B3 | same | stderr is never drained. git blocks if it writes more than the pipe buffer. | `git` shim writing 200 KB to stderr hung the scan past an 8 s watchdog. |
| B4 | same | `Executors.newSingleThreadExecutor()` per call, never shut down. Two non-daemon threads leak per repo. | Harness calling `status()` 8 times never exited. Hidden only by `System.exit` in `main`. |
| B5 | `App.java:95, 116, 103, 117-119` | `assert exitCode == 0` is dead at runtime; `IOException` swallowed; `hasNoRemote` defaults to `true`. Any git failure prints as "No remote repository". Exit code is always 0. | `git` off PATH: stack trace, then every repo "No remote repository", exit 0. Empty `.git` dir: same. |
| B6 | `App.java:66, 74` | `listFiles()` returns `null` on unreadable dirs; `Arrays.stream(null)` throws. One unreadable directory kills the whole scan. | `chmod 000` dir under root: NPE, no results. |
| B7 | `App.java:42-69` | Unbounded recursion, follows symlinks, no skip list, no depth cap. | `Projects/loop -> .` symlink reported the same repo 33 times. |
| B8 | `App.java:48-50` | Label "untracked files" but `git status -s` counts modified/staged/deleted too, and collapses an untracked dir into one line. | 1 modified + 3 untracked in a subdir printed "2 untracked files". |
| B9 | `App.java:48-61` | ANSI escapes written unconditionally, including when piped. | `dirty | cat -v` shows `^[[33m`. |
| B10 | `App.java:43` | Missing root directory exits 0 silently. | |

Build and toolchain: Java 8 target (`build.gradle:20-24`) vs JDK 11 in CI vs
Temurin 21 locally; picocli 4.1.0 (2019); `mockito-core:2.+` resolves to
2.28.2 / Byte Buddy 1.9.10, which **cannot mock on Java 17 or 21** and is a
floating version; JUnit 5.5.2 relies on test-framework auto-loading that Gradle
9 removes; `com.palantir.graal` 0.12.0 is unmaintained and downloads GraalVM
20.2.0 (Java 8, no arm64 macOS build); `build.gradle:37` has a dead
`-H:IncludeResources` option; `jacoco` is applied but unwired.

CI: **the Build workflow fails before the invalid codesign step is reached.**
`actions/setup-java@v1` with JDK `11.0.2` cannot complete TLS to Maven Central
(`peer not authenticated` fetching `picocli-codegen-4.1.0.pom`). The step-level
`uses: ./.github/workflows/codesign.yml` is still invalid and would fail next.
`nativeImage` on the `macos-15` (arm64) runner would very likely fail after
that, because palantir's pinned GraalVM has no darwin-aarch64 build.
`checkout@v2`, `setup-java@v1`, `create-release@v1`, `upload-release-asset@v1`
are deprecated or archived.

## Findings on `adding-tests`

Good: splitting into `PathInspector` (walk), `GitRepo` (git), `util/ANSICode`
(colors) is the right cut, and `@TempDir` with fabricated `.git` dirs is the
right idea for walker tests. Keep both ideas.

Wrong:

| # | Where | Finding |
|---|---|---|
| R1 | `PathInspector.java:32` | `abs.replace(abs + sep, "")` never matches. Output regressed to absolute paths; the trailing `:` and the "Finding dirty git repos in" header are gone. |
| R2 | `GitRepo.java:56-75` | `hasRemote()` returns `hasNoRemote`. Consistent today only because the caller is inverted too. |
| R3 | `GitRepo.java` | Conflates filesystem probing with process execution, the exact boundary that must be separated to test without git. |
| R4 | all of the above | B1–B10 carried over verbatim. |
| R5 | `TestPathInspector.java:11-13` | The only test is `assertEquals(1, 1)`. |
| R6 | `DirtyIntegrationTest.java` (local commit c37b158, not on origin) | `System.setOut` commented out, asserts output equals `"hello"`; fails every run. Fixture `foo/.git` is an empty dir so git exits 128. |
| R7 | `.java-version` = `17.0` | Breaks `./gradlew` for any jenv user without 17 installed (observed). Makes the Java-target decision implicitly. |
| R8 | branch base 99e0402 | Predates `CLAUDE.md`, `specs/`, and the codesign work; its `gradle.yml` regresses master's. A merge conflicts. |

**Test evaluation:** there is effectively no test coverage. The one test on
origin is a tautology. Nothing in either tree can be asserted without forking
real git and capturing `System.out`, because `ProcessBuilder`, `System.out`,
and `user.home` are hard-wired with no seams.

**Verdict:** do not merge `adding-tests`. Re-do the split on a fresh branch off
`master` per spec 003, borrowing `ANSICode` and the `@TempDir` approach, then
delete the branch.

## Target architecture

One Gradle module, one package plus `util`, ~8 small classes, no new runtime
dependencies.

```
App (picocli)        parse options, wire deps, pick Reporter, map result -> exit code
  └─ Scanner         RepoFinder -> (optionally parallel) Git.inspect -> ScanResult
       ├─ RepoFinder          Files.walkFileTree; only class that touches the FS walk
       ├─ Git (interface)
       │    └─ ProcessGit     parses `git status --porcelain` / `git remote`
       │         └─ CommandRunner (interface)
       │              ├─ ProcessCommandRunner   only class that touches ProcessBuilder
       │              └─ FakeCommandRunner      (test) canned stdout/exit per argv
       └─ Reporter (interface)   ScanResult -> text on an injected PrintStream
            ├─ TextReporter (today's ANSI one-liner)
            ├─ TableReporter / JsonReporter / CsvReporter   (spec 005)
```

Data types: `CommandResult(exitCode, stdout, stderr)`, `RepoStatus(changedFiles,
hasRemote)`, `RepoReport(relativePath, status | error)`, `ScanResult(root,
reports sorted by path)`. Records if the Java target is 17+.

Rules: only `ProcessCommandRunner` touches `ProcessBuilder`; only `RepoFinder`
touches the filesystem; only `Reporter`s and `App` write to the injected
out/err streams. Everything in between is data in, data out, and unit-testable
with 10–20 line hand-written fakes. Mockito is not needed.

Why no JGit / Jackson / Gson: all three are reflection-heavy and cost real
native-image effort for a tool whose JSON output is ~30 lines of code.

## Roadmap

| Spec | Title | Size | Depends on |
|---|---|---|---|
| 001 | Make the Build workflow green and valid | S | – |
| 002 | Build and dependency modernization | S (+ maintainer decisions) | 001 |
| 003 | Refactor into testable components; real tests | M | 002 (Java target) |
| 004 | Process-execution hygiene and error handling | S/M | 003 |
| 005 | Output formats and color control | M | 003, 004 |
| 006 | Parallel scanning | S | 004 |
| 007 | Release pipeline: multi-platform, signing, Homebrew | L (maintainer decisions) | 002 |

001 makes CI trustworthy, 002 settles the toolchain the new code is written
against, 003 creates the seams everything else slots into. 005/006/007 are
independent of each other.

Later, unscheduled: "how far behind remote" (`git rev-list --left-right --count
@{u}...HEAD`) is two more fields on `RepoStatus` once 003 lands.

## Decisions that belong to the maintainer

1. Java target: 17 or 21 (recommendation: 21, see 002).
2. `com.palantir.graal` vs official `org.graalvm.buildtools.native` (recommendation: switch, see 002). Changes the local prerequisite to "a GraalVM JDK installed".
3. Exit-code policy: should dirty repos produce a non-zero exit (`--exit-code`)? See 004.
4. Is the JSON output a public contract? See 005.
5. Apple Developer ID signing + notarization vs Homebrew-tap-only distribution. See 007.
6. Which platforms are actually supported (Intel mac? Windows?). See 007.

## Decisions (2026-11-07, maintainer)

1. **Java 21**, skipping 17.
2. **Switch to `org.graalvm.buildtools.native`** (shipped with spec 002).
3. **Exit codes follow the bash/coreutils convention**: 0 success, 1 runtime
   errors (repos that could not be inspected), 2 usage/fatal, 130 on SIGINT.
   Opt-in `--exit-code` makes 1 mean "at least one dirty repo". No `sysexits.h`.
4. JSON output is a public contract with a `"version": 1` field. Machine
   formats include clean repos by default; text does not.
5. **Both** Apple Developer ID signing + notarization **and** a Homebrew tap.
6. Platforms: Linux x86_64, macOS arm64, Windows x86_64. Intel mac and Linux
   arm64 optional, not required.
7. Native image built on every push to `master`, one Linux runner; full
   matrix on tags only. No nightly artifacts.
8. Walk: default depth 4; skip `node_modules`, `build`, `target`, `.gradle`,
   hidden dirs; nested repos/submodules unreported.
9. Header line kept, on stderr. Counts reported as "N modified, M untracked"
   from porcelain XY columns, not one aggregate.
10. Git timeout 30 s; add `--verbose` now.
11. Color via picocli's built-in `Ansi`; retire `ANSICode`.
12. Virtual threads for parallel scanning, no default job cap.
13. Dependabot for Gradle and Actions added.
14. Mockito dropped (2026-11-08); tests use hand-written fakes.
