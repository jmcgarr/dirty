# 004 – Process-execution hygiene and error handling

**Size:** S/M  **Depends on:** 003  **Unblocks:** 005 (error rows), 006 (`--no-optional-locks`)

## Problem / Motivation

Every git call on `master` (`App.java:87-120`) and on `adding-tests`
(`GitRepo.java:39-75`) has the same defects, all reproduced empirically (spec
000, B2–B5):

- The stdout gobbler is never joined, so **lines are lost**: a repo with 2
  status lines reported 0 in 25/25 cold-JVM runs, and a 20,000-file repo
  reported 16,280. The shipped native binary has no JIT warm-up to mask this.
- stderr is never drained; a 200 KB stderr hung the scan indefinitely.
- A non-daemon single-thread executor leaks per call; the JVM only exits
  because `main` calls `System.exit`.
- `assert exitCode == 0` is dead at runtime; `IOException` is swallowed; the
  remote flag defaults to "no remote". **Every failure mode** (git not on PATH,
  corrupt `.git`, empty `.git` dir) is reported as "No remote repository" with
  exit code 0, and `gitStatus` dumps a stack trace into the report.
- No timeout; a hung git hangs the tool.

## Proposed approach

### `ProcessCommandRunner`
- Read stdout to EOF **on the calling thread** before `waitFor()`.
- Capture stderr separately (daemon drain thread joined before returning, or
  on Java 9+ `readAllBytes` on both with stderr first redirected to a temp
  pipe). Never `redirectErrorStream(true)`: stderr lines would be counted as
  changes.
- Decode as UTF-8 explicitly.
- `waitFor(timeout)` (default 30 s) → on expiry `destroyForcibly()` and throw
  `IOException("git timed out after …")`.
- No executors. `StreamGobbler` is deleted or confined to this class.

### `ProcessGit`
- Run `git --no-optional-locks status --porcelain` (stable, scriptable; also
  prevents index writes, a prerequisite for spec 006) and `git remote`.
- Non-zero exit → `GitException(repo, exitCode, stderr.trim())`. No `assert`.

### `Scanner`
- Catch `GitException` per repo into `RepoReport.error`; continue the scan.
- `InterruptedException` re-sets the interrupt flag and surfaces as exit 130.

### `App`
- Preflight `git --version` once through the same `CommandRunner`; if it
  fails, print `dirty: git not found on PATH` to stderr, exit 2.
- Root not a directory → `CommandLine.ParameterException` (picocli prints
  usage to stderr, returns 2). Fixes B10.
- Exit codes: `0` scan completed; `1` completed but one or more repos could
  not be inspected; `2` usage or fatal.
- All diagnostics (header, walker warnings, error summary) to stderr; stdout
  carries only report lines.

### `TextReporter`
- Error rows render as `path: error: <first line of stderr>` in red.

### `--verbose`
- Optional flag that echoes each git invocation and warning to stderr. No
  logging framework (native-image friendliness).

## Scope

**In:** `ProcessCommandRunner`, `ProcessGit`, `Scanner`, `App` exit paths,
`TextReporter` error row, tests (throwing/slow `FakeCommandRunner` for timeout;
exit-code tests via picocli; a real-process test using `java -version` guarded
by `Assumptions`).
**Out:** retries (git failures are not transient); logging framework; parallel
execution (006).

## Acceptance criteria

- A repo with a corrupt `.git` yields one `path: error: fatal: …` line on
  stdout and exit 1; the rest of the scan completes.
- `git` missing from PATH → exactly one stderr line, exit 2, no stack trace.
- `dirty | wc -l` equals the number of dirty-or-errored repos; `dirty
  2>/dev/null` contains no header or warnings.
- Thread count before and after a scan is unchanged (assert in `ScannerTest`).
- A `FakeCommandRunner` that sleeps past the timeout produces a `GitException`
  within timeout + 1 s.
- `grep -r "assert " src/main` returns nothing.
- Cold-start test: `ProcessGitIT` asserts the exact changed-file count on the
  first call in a fresh JVM (regression for the lost-lines bug).

## Open questions (maintainer)

- Exit-code policy: keep `1` = "errors occurred", or add `--exit-code` so `1`
  means "at least one dirty repo" (like `git diff --exit-code`)?
- Default timeout per git call (30 s?).
- Add `--verbose` now or defer?

## Decisions (2026-11-07)

- Exit codes: **0** all repos inspected; **1** one or more repos could not be
  inspected; **2** usage error or fatal startup (bad option, root not a
  directory, git missing); **130** on SIGINT. Matches bash/coreutils and
  picocli's defaults. No `sysexits.h` codes.
- Add `--exit-code`: with it, exit 1 when any repo is dirty, 0 when all are
  clean; errors still produce 1 (and are listed). Without it, dirtiness never
  affects the exit status.
- Default per-call git timeout 30 s.
- Add `--verbose` in this spec.

## Implementation notes (2026-11-09)

Spec 003 already delivered the joined stdout/stderr drains, `--no-optional-locks`,
`GitException` → `error:` rows, `ParameterException` for a bad root, and
diagnostics on stderr. This PR adds the rest:

- `ProcessCommandRunner(env, Duration timeout)`, default 30 s. Both streams are
  drained on daemon threads; `waitFor(timeout)` then `destroyForcibly()` and
  `CommandTimeoutException` (an `IOException`, message `git timed out after 30s`),
  which `ProcessGit` turns into a `GitException` so the repo shows as an
  `error:` row and the scan continues. After exit or kill the pipes get a 2 s
  grace: a grandchild holding stdout is reported (`exited with status N but
  its output stayed open`) rather than waited for, and a child in
  uninterruptible sleep that ignores SIGKILL cannot hang the scan either.
- `Git.version(Path)` / `ProcessGit.version(Path)`: `git --version` preflight
  in `App`, run in the validated scan root, before the header. Fails → one
  stderr line (`dirty: could not run git: …` when git did not run,
  `dirty: git --version failed: …` otherwise), exit 2.
- `--exit-code`: exit 1 when any repository `needsAttention()` (changes or no
  remote, i.e. anything printed), 0 otherwise; errors still exit 1.
- `-v`/`--verbose`: `TracingCommandRunner` decorator prints `$ git …  (in dir)`
  and `exit N in M ms` per call; `App` also prints the git version and
  `N repositories, M dirty, K errors` (disjoint counts). All on stderr. No
  logging framework.
- `Scanner.scan` now throws `InterruptedException` when an inspection was
  interrupted; `App` re-sets the flag, prints `dirty: interrupted`, exits 130.
  SIGINT on the process is still the runtime's default (128+2 = 130).
- `--help` lists the exit codes (picocli `exitCodeList`).
- `grep -r "assert " src/main` is empty; drain threads are named `dirty-*` and
  a test asserts none survive a call.
- Dropped from the spec: a `ScannerTest` thread-count assertion (covered by the
  runner-level test) and "retries" (out of scope as written).

