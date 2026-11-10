# 008 – Move to Java 25 and the GraalVM 25 release line

**Size:** S  **Depends on:** 002, 003  **Unblocks:** 007 (release matrix builds on a maintained GraalVM)

## Problem / Motivation

Spec 002 chose Java 21 and pinned GraalVM Community Edition 21.0.2. That pin
has aged badly, and not because of Java 21 itself:

- GraalVM CE shipped its last JDK 21 build (21.0.2) in January 2024. Every
  later 21.0.x GraalVM is Oracle GraalVM under the GFTC license. Our native
  binaries are therefore built with a JDK that has had no security fixes for
  almost three years.
- GraalVM changed its release model in 2026. Per the
  [release calendar](https://www.graalvm.org/release-calendar/), Community
  Edition and Oracle GraalVM "provide innovation (feature) releases on a monthly
  cadence with quarterly CPUs incorporated when available", and "the most recent
  feature release supersedes all previous feature releases". The calendar lists
  CE builds only for those monthly feature releases (`graal-25.1.3` in June
  through `graal-25.5.5.0.3` on 2026-11-03; the tag grew from three to five
  parts as CPU levels were folded in); the quarterly 25.0.x / 21.0.x CPU builds
  are listed for Oracle GraalVM only. The
  [CE releases page](https://github.com/graalvm/graalvm-ce-builds/releases)
  confirms there has been no CE build on the 21 baseline since `jdk-21.0.2`.
- Two workarounds in `build.gradle` exist only because of CE 21.0.2: the
  shared reachability-metadata repository is disabled (its schema needs a newer
  GraalVM), and `org.junit.platform.engine.TestTag` is force-initialized at
  build time for `nativeTest`.

Java 25 is the current LTS. The codebase uses nothing that changed between 21
and 25, and the maintainer already has Temurin 25 installed.

## Approach

Move the toolchain to 25 and build native images with the current GraalVM CE
25 innovation release. Verified on 2026-11-09 with a temporary local edit:
`./gradlew build nativeCompile nativeTest` passes on GraalVM CE 25.5.5.0.3
with the metadata repository re-enabled and the TestTag workaround removed
(47 found / 44 pass / 3 aborted by assumption, same as on 21).

1. `build.gradle`: `JavaLanguageVersion.of(25)`; delete the
   `metadataRepository { enabled = false }` block. The repository is on by
   default in native-build-tools 1.1.x, so `nativeCompile` now fetches
   `graalvm-reachability-metadata-1.1.18-repository.zip` from Maven Central and
   applies its picocli entry (a few conditional reflection registrations on top
   of what picocli-codegen emits). Delete the `TestTag` `buildArgs` and the
   test-binary block.
2. `.sdkmanrc`: `java=25.5-graalce`, a local-install id. sdkman's catalog id
   for this build (`25.5.5.0+3-graalce`) cannot be used: the download broker
   returns 404 for every 25.x CE/Oracle id as of 2026-11-09 (`sdk env install`
   reports "archive was corrupt"), and the id exceeds sdkman's 15-character
   limit for `sdk install java <id> <path>`. README documents downloading the
   tarball from GitHub and registering it under the short id.
3. `.github/workflows/gradle.yml`: `setup-java` → Temurin 25;
   `setup-graalvm` → `java-version: '25'`, `version: '25.5'`,
   `distribution: graalvm-community`. Same in `release.yml`.
4. Docs: CLAUDE.md and README version mentions, the Java-level references in
   spec 006; note that Dependabot does not
   watch `.sdkmanrc` or the `setup-graalvm` `version` input, so the GraalVM
   pin is bumped by hand (monthly CE releases; quarterly is fine).
5. Record the decision in spec 000.

## Scope

In: the five items above. Out: any code change; the release matrix,
signing, and Homebrew tap (007); automating the GraalVM pin bump.

## Acceptance criteria

- `./gradlew build` passes on a plain JDK 17+ host (toolchain provisions 25).
- `./gradlew nativeCompile nativeTest` passes on GraalVM CE 25.5 locally and in
  CI; the binary answers `--version` and scans a directory.
- No `enabled = false` / `initialize-at-build-time` workarounds remain.
- `actionlint` is clean.

## Open questions

- Pin `version: '25.5'` (reproducible, bumped by hand) or `25i-latest`
  (always current, CI can change under us)? Decision: pin; a tool this small
  should never go red because upstream shipped a new monthly build.
- Oracle GraalVM instead of CE, to get the 25.0.x security-only line?
  Decision: no; CE keeps the project free of the GFTC license, and per the
  release calendar each monthly CE build incorporates the latest quarterly CPU
  (e.g. 25.5.5.0.3 is built on JDK 25.0.5).

## Decisions (2026-11-09)

- Maintainer approved the move ("Go ahead and upgrade to java 25").
- Pin GraalVM CE 25.5 explicitly in CI and `.sdkmanrc`.
- `.sdkmanrc` uses the short local id `25.5-graalce` because sdkman cannot
  install the catalog id (see step 2).

**Shipped in:** https://github.com/jmcgarr/dirty/pull/8 (merged 2026-11-09).
