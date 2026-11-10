# CLAUDE.md

Guidance for Claude Code (and other AI agents) working in this repository.

## What this project is

`dirty` is a small Java command-line tool that walks a directory of git
checkouts (default `$HOME/Projects`) and reports repos that are "dirty":
repos with untracked/modified files (via `git status -s`) or repos with no
remote configured (via `git remote`). It is compiled to a native binary with
GraalVM and distributed via GitHub Releases.

- **Language / build:** Java 25 (Gradle toolchain), Gradle 9.8 (wrapper checked
  in), picocli 4.7 for CLI parsing, `org.graalvm.buildtools.native` for
  native-image builds.
- **Entry point:** `src/main/java/net/sonofgarr/dirty/App.java`, a picocli
  `@Command`. It only parses options, wires the real implementations, and
  maps the result to an exit code. Everything else is data in, data out:
  - `RepoFinder` walks the tree (`Files.walkFileTree`, no symlinks, depth cap,
    skip list). The only class that touches the filesystem walk.
  - `process/CommandRunner` + `ProcessCommandRunner`: the only class that
    touches `ProcessBuilder`. Tests use `FakeCommandRunner`.
  - `Git` + `ProcessGit`: runs `git status --porcelain` / `git remote` and
    parses them into `RepoStatus(modified, untracked, hasRemote)`. Non-zero
    exit or I/O failure → `GitException`.
  - `Scanner` → `ScanResult(root, List<RepoReport>)`; a `RepoReport` carries
    either a status or an error string.
  - `Reporter` + `TextReporter(Ansi)`: renders to an injected `PrintWriter`.
- **Tests:** `src/test` has unit tests for every component (hand-written fakes,
  no Mockito) plus `ProcessGitIT`, a real-git integration test that is skipped
  when git is not on PATH. `./gradlew test` runs all of them.
- **CI:** `.github/workflows/gradle.yml` (Build on push/PR to `master`),
  `release.yml` (on `v*` tags), `codesign.yml` (reusable `workflow_call`).

## Common commands

```bash
./gradlew build                 # compile + jar + tests (run Gradle on JDK 17+; it provisions JDK 25 for compiling)
./gradlew test                  # run JUnit tests
./gradlew run --args="-d Code"  # run the CLI; -d is a dir name under $HOME or an absolute path
sdk env && ./gradlew nativeCompile   # native binary -> build/native/nativeCompile/dirty (needs GraalVM 25)
./gradlew nativeTest            # run the JUnit suite inside a native image (catches reflection gaps)
```

Notes:
- The native plugin finds `native-image` via `GRAALVM_HOME`, then `JAVA_HOME`,
  then the JVM running Gradle. `.sdkmanrc` pins the local id `25.5-graalce`;
  run `sdk env` before native tasks. CI uses `graalvm/setup-graalvm`. Do not
  try to make `./gradlew build` depend on GraalVM; contributors without it
  must still be able to build and test.
- `sdk env install` does not work for GraalVM 25.x Community builds (as of
  2026-11-09, sdkman 5.18): the catalog lists `25.5.5.0+3-graalce` but its
  download broker returns 404, and the catalog id is too long for
  `sdk install java <id> <path>`. The README shows the manual download plus
  local-install route under the short id `25.5-graalce`.
- GraalVM Community Edition ships monthly "innovation" builds on the JDK 25
  baseline (see spec 008). The pin lives in two places Dependabot does not
  watch: `.sdkmanrc` and the `version:` input of `graalvm/setup-graalvm` in
  `gradle.yml` and `release.yml`. Bump both together, roughly quarterly.
  Upstream tag `graal-A.B.C.D.E` maps to setup-graalvm `version: 'A.B'` and
  to the local sdkman id `A.B-graalce`.
- Running `nativeCompile` on a plain JDK fails with a "native-image not found"
  style error. That is a toolchain problem, not a code bug.
- `build/`, `.gradle/`, and `.idea/` are git-ignored build/IDE output. Never
  commit them.

## Known rough edges (as of 2026-11-09)

A full architecture and test review lives in `specs/000-architecture-and-test-review.md`;
the fixes are sequenced in `specs/001` onward. Headline items:

- `codesign.yml` has the actual `codesign` invocation commented out and
  hard-codes a throwaway keychain password; nothing calls it any more. It is
  rewritten in spec 007 together with `release.yml`, which still uses the
  archived `create-release`/`upload-release-asset` actions.
- Not yet done from spec 004: per-call git timeout, `git --version` preflight,
  `--verbose`, `--exit-code`. Errors already surface as `error:` rows and exit 1.
- No `--format`/`--color` options yet (spec 005); `TextReporter` uses picocli
  `Ansi.AUTO`, so colors are off when piped or `NO_COLOR` is set.
- The `adding-tests` branch is superseded by the spec 003 refactor and can be
  deleted.
- README TODOs: tabular/JSON/CSV output, logging, parallel scanning,
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
