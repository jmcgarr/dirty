# 001 – Make the Build workflow green and valid

**Size:** S  **Depends on:** –  **Unblocks:** everything else (a trustworthy CI signal)

## Problem / Motivation

`.github/workflows/gradle.yml` fails on every push to `master` (latest: run
42237362152). Verified causes, in the order they bite:

1. `actions/setup-java@v1` with `java-version: 11.0.2` (lines 17-20). That
   JDK (Jan 2019) cannot complete TLS to today's Maven Central. The build dies
   in `compileJava` with `Could not GET .../picocli-codegen-4.1.0.pom > peer
   not authenticated`. **This is the actual red build.**
2. `uses: ./.github/workflows/codesign.yml` as a *step* (line 44). Reusable
   workflows can only be invoked at job level (`jobs.<id>.uses`). GitHub treats
   a `./path` step `uses:` as a local action and fails for lack of `action.yml`.
   Currently never reached.
3. `runs-on: macos-15` (line 13) is an Apple Silicon runner. The palantir
   plugin downloads GraalVM 20.2.0, which has no darwin-aarch64 build, so
   `nativeImage` will most likely fail once compile succeeds. Not yet verified
   in CI because compile fails first.
4. `actions/checkout@v2`, `actions/cache` + manual lock-file cleanup (lines
   21-28, 39-42), `chmod +x gradlew`: all deprecated or unnecessary.

The Build badge on the README has been red throughout, so it carries no
information.

## Proposed approach

- `actions/checkout@v4`; `actions/setup-java@v4` with `distribution: temurin`
  and a `java-version` matching the decision in spec 002 (11 keeps the Java 8
  target compiling today; 21 anticipates 002. If 002 has not been decided, use
  **17** as the minimum that resolves TLS and compiles a Java 8 target).
- `gradle/actions/setup-gradle@v4` replaces the manual cache and cleanup steps
  and validates the wrapper checksum.
- Commit `gradlew` with the exec bit set; drop `chmod`. Drop `clean`.
- Split into two jobs:
  - `test` on `ubuntu-latest`: `./gradlew build`; upload `build/reports` on
    failure.
  - `native` (`needs: test`) on the platform the maintainer actually ships for:
    runs `nativeImage`, smoke-tests the binary with `--version`, uploads the
    artifact.
- **Remove** the step-level codesign call. Signing belongs in the release
  pipeline (spec 007). If the maintainer wants it kept, the only valid form is
  `jobs.codesign: { needs: native, if: github.event_name == 'push', uses:
  ./.github/workflows/codesign.yml, secrets: inherit }`.
- Add `permissions: contents: read` and a `concurrency` group with
  `cancel-in-progress: true`.
- Apply the same `checkout` / `setup-java` bumps to `release.yml` so it stops
  rotting; its functional rewrite is spec 007.
- Run `actionlint` locally before opening the PR.

## Scope

**In:** `gradle.yml`; action version bumps in `release.yml`; README badge if
job names change; CLAUDE.md "Known rough edges" CI paragraph.
**Out:** codesign content, `build.gradle`, the Java target decision itself,
multi-platform matrix (007).

## Acceptance criteria

- Build workflow succeeds on the PR and on `master` after merge.
- `actionlint` reports no errors.
- If the `native` job is kept, its uploaded artifact is a runnable `dirty`
  binary that prints `dirty 0.1.0` for `--version`.
- No step-level `uses:` of a workflow file remains anywhere under
  `.github/workflows/`.

## Open questions (maintainer)

- JDK version for CI before spec 002 lands: 17 as a stopgap, or decide 002
  first and do both in one PR?
- Keep a native build on every commit, or only on tags (cheaper, slower
  feedback)?
- Primary native target OS/arch: `macos-15` (arm64) or `macos-13` (last x64
  image) or `ubuntu-latest`?

## Decisions (2026-11-07)

- JDK 21 everywhere; done together with spec 002 in one PR because the
  workflow had to reference the new `nativeCompile` task and output path.
- Native build on **every** push/PR, single `ubuntu-latest` runner. Full
  matrix is spec 007.
- Step-level codesign call removed; signing lives in release (spec 007).
- `release.yml` received only the action/JDK/path bumps needed to keep it
  from breaking; its rewrite remains spec 007.

**Shipped in:** https://github.com/jmcgarr/dirty/pull/3 (merged 2026-11-08). First green Build run: 42397080410.
