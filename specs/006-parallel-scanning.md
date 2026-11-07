# 006 – Parallel scanning

**Size:** S  **Depends on:** 004 (`--no-optional-locks`, per-repo error isolation)  **Independent of:** 005, 007

## Problem / Motivation

Scan time is dominated by N sequential `git status` process spawns, one per
repo. On a machine with hundreds of checkouts this is tens of seconds of
mostly idle waiting. README lists "Parallelize the scanning of git repos" as a
wanted feature.

## Proposed approach

- `Scanner.scan(root)`: run `RepoFinder.find` first (fast, single-threaded),
  then submit one `Callable<RepoReport>` per repo to an `ExecutorService`,
  `invokeAll`, `shutdown()` in a `finally`, sort reports by path, return
  `ScanResult`.
- `--jobs` / `-j` option; default `min(repoCount, availableProcessors())`;
  `-j 1` restores sequential behaviour exactly.
- On Java 21, `Executors.newVirtualThreadPerTaskExecutor()` is a one-line
  alternative to a fixed pool.
- Because every `RepoReport` is immutable and rendering happens after the
  barrier, no reporter needs to be thread-safe and output order is
  deterministic.
- `--no-optional-locks` (spec 004) is a hard prerequisite: concurrent plain
  `git status` calls may each try to refresh and write the index.
- Failure isolation already exists from 004: a `GitException` becomes that
  repo's `error`. `InterruptedException` → `shutdownNow()`, re-set interrupt,
  exit 130.
- Test: `ScannerTest` with a `FakeGit` that sleeps a few ms per repo, asserting
  identical `ScanResult` for `-j 1` and `-j 8`, and a wall-clock upper bound
  for `-j 8` that `-j 1` cannot meet.

## Scope

**In:** `Scanner`, `App` option, README.
**Out:** streaming/progress output (would break deterministic order; revisit if
scans are still slow); parallelising the directory walk itself (not until
measured).

## Acceptance criteria

- Output is byte-identical for any `-j` value.
- Scanning `~/Projects` is measurably faster at the default `-j` than at
  `-j 1` (record before/after in the PR).
- Ctrl-C exits within ~1 s with no orphaned git processes.
- No threads remain after `scan()` returns.

## Open questions (maintainer)

- Cap on the default job count (e.g. 8) to avoid hammering a laptop disk?
- Fixed pool vs virtual threads if the Java level is 21?

## Decisions (2026-11-07)

- Java 21: `Executors.newVirtualThreadPerTaskExecutor()`; no default job cap.
  `-j N` remains available to throttle (bounded via a semaphore if set).
