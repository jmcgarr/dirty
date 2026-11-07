# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository.

## What this project is

`dirty` is a small Java command-line tool that walks a directory of git
checkouts (default `$HOME/Projects`) and reports repos that are "dirty":
repos with untracked/modified files (via `git status -s`) or repos with no
remote configured (via `git remote`). It is compiled to a native binary with
GraalVM and distributed via GitHub Releases.

- **Language / build:** Java 21 (Gradle toolchain), Gradle 8.6 (wrapper checked
  in), picocli 4.7 for CLI parsing, `org.graalvm.buildtools.native` for
  native-image builds.
- **Entry point:** `src/main/java/net/sonofgarr/dirty/App.java`
  (`net.sonofgarr.dirty.App`, a picocli `@Command` returning an exit code).
- **Helper:** `StreamGobbler.java` drains a subprocess stdout into a consumer.
- **Tests:** none on `master` yet. JUnit 5 + Mockito are already declared in
  `build.gradle`. The `adding-tests` branch contains an in-progress refactor
  (`GitRepo`, `PathInspector`, `util/ANSICode`) plus a first test; check it
  before starting any test or refactor work so you do not duplicate it.
- **CI:** `.github/workflows/gradle.yml` (Build on push/PR to `master`),
  `release.yml` (on `v*` tags), `codesign.yml` (reusable `workflow_call`).

## Common commands

```bash
./gradlew build                 # compile + jar + tests (run Gradle on JDK 17+; it provisions JDK 21 for compiling)
./gradlew test                  # run JUnit tests
./gradlew run --args="-d Code"  # run the CLI; -d is a dir name under $HOME (currently a no-op, spec 003)
sdk env && ./gradlew nativeCompile   # native binary -> build/native/nativeCompile/dirty (needs GraalVM 21)
./gradlew nativeTest            # run the JUnit suite inside a native image (catches reflection gaps)
```

Notes:
- The native plugin finds `native-image` via `GRAALVM_HOME`, then `JAVA_HOME`,
  then the JVM running Gradle. `.sdkmanrc` pins `21.0.2-graalce`; run `sdk env`
  (or `sdk env install` once) before native tasks. CI uses
  `graalvm/setup-graalvm`. Do not try to make `./gradlew build` depend on
  GraalVM; contributors without it must still be able to build and test.
- Running `nativeCompile` on a plain JDK fails with a "native-image not found"
  style error. That is a toolchain problem, not a code bug.
- `build/`, `.gradle/`, and `.idea/` are git-ignored build/IDE output. Never
  commit them.

## Known rough edges (as of 2026-11-06)

A full architecture and test review lives in `specs/000-architecture-and-test-review.md`;
the fixes are sequenced in `specs/001` through `specs/007`. Headline items:

- `codesign.yml` has the actual `codesign` invocation commented out and
  hard-codes a throwaway keychain password; nothing calls it any more. It is
  rewritten in spec 007 together with `release.yml`, which still uses the
  archived `create-release`/`upload-release-asset` actions.
- **`-d` is ignored.** `path` in `App` is a field initializer computed before
  picocli injects the option, so the tool always scans `$HOME/Projects` (spec 003).
- **Git output is lost.** The stdout gobbler is never joined before counts are
  read; in a cold JVM a 2-line `git status` reports 0 changes. stderr is never
  drained (hang risk), executors leak per call, `assert` is dead at runtime, and
  every git failure is reported as "No remote repository" with exit 0 (spec 004).
- `App.inspect` recurses with no depth limit, follows symlinks (a `loop -> .`
  link reports the same repo dozens of times), and NPEs on an unreadable
  directory because `listFiles()` returns null (spec 003).
- ANSI codes are written unconditionally, even when piped (spec 005).
- The `adding-tests` branch is **not mergeable**: it carries every bug above,
  regresses path display, inverts `hasRemote()`, and its only test is
  `assertEquals(1, 1)`. Spec 003 supersedes it.
- README TODOs: tests, tabular/JSON/CSV output, logging, parallel scanning,
  "how far behind remote" check, Windows support, cross-platform binaries,
  Homebrew tap.

## How to work in this repo

### Branches, commits, and pull requests
- **Never commit directly to `master`.** Always create a branch first
  (`git checkout -b <type>/<short-description>`, e.g. `feat/json-output`,
  `fix/ci-codesign-call`, `chore/update-gradle`).
- Commit in small, logical units with clear messages. Co-author lines as
  provided by the environment are expected.
- When the work on a branch is complete and verified (build passes, tests
  pass where they exist), **push the branch and open a pull request** against
  `master` using `gh pr create`. Do not merge PRs yourself; the maintainer
  reviews and merges.
- The PR description should say what changed, why, how it was tested, and
  link to the spec in `specs/` if one exists.

### Specs and plans
- For any non-trivial improvement (new feature, refactor, CI change, anything
  more than a quick fix), **write a plan/spec first** in `specs/` as
  `specs/NNN-short-title.md`. Follow the template in `specs/README.md`.
- Get the spec reviewed (ask the maintainer) before implementing if the
  approach involves a real design choice.
- When the work has shipped, **move the spec to `specs/completed/`** (same
  file name) and add a short "Shipped in" note pointing at the PR.

### Ask questions
- When requirements are ambiguous, a decision has real trade-offs, or an
  action is hard to reverse (changing the Java target, altering release
  workflows, touching signing secrets, force-pushing), **stop and ask** the
  maintainer rather than guessing. Make routine judgment calls yourself.
- State any assumptions you had to make in the PR description.

### Use subagents
- Lean on the available subagents and try to use all of them where they fit,
  rather than doing everything in the main thread:
  - `architect` / `Plan` – draft specs and implementation plans in `specs/`.
  - `Explore` – broad read-only searches across the codebase.
  - `java-architect` – Java design, Gradle, and GraalVM questions.
  - `code-reviewer` – review every diff before opening a PR.
  - `test-analyst` – diagnose failing or flaky tests.
  - `devops-engineer` – GitHub Actions, release, and codesigning workflows.
  - `security-review` skill – run before PRs that touch workflows or secrets.
  - `claude-code-guide` – questions about Claude Code itself.
- Give each subagent a narrow, explicit brief and relay its findings; do not
  run the same work yourself in parallel.

## Style
- Keep the tool small and dependency-light; it is meant to compile to a
  fast-starting native binary.
- Prefer plain Java and the standard library over new dependencies.
- Preserve the existing ANSI-colored, one-line-per-repo output unless a spec
  says otherwise.
