# 002 – Build and dependency modernization

**Size:** S mechanical + M decisions  **Depends on:** 001  **Unblocks:** 003 (code is written against the chosen Java level)

## Problem / Motivation

`build.gradle` is a 2019-era configuration with three different Java levels in
play and several latent traps:

- `compileJava` targets Java 8 (lines 20-24) while CI uses JDK 11 and local
  dev uses Temurin 21 (javac warns `source/target value 8 is obsolete`). The
  `adding-tests` branch pins 17 via `.java-version`, which breaks `./gradlew`
  for jenv users without 17 installed.
- `picocli` / `picocli-codegen` 4.1.0 (Nov 2019). Current is 4.7.x. Newer
  codegen emits reflection config in the format current GraalVM expects.
- `mockito-core:2.+` (line 15) is a floating version that resolves to 2.28.2
  with Byte Buddy 1.9.10, which **throws on Java 17 and 21 class files**. It is
  unused today, so it is a trap rather than a failure. It also makes builds
  non-reproducible.
- JUnit Jupiter 5.5.2 relies on Gradle auto-loading the test engine, which
  Gradle 9 removes (warning already printed by 8.6).
- `com.palantir.graal` 0.12.0 (line 5) is unmaintained (last release 2021),
  downloads its own GraalVM 20.2.0 for Java 8, and has no arm64 macOS build.
- `-H:IncludeResources=.*/projects.build.gradle$ -H:Log=registerResource:`
  (line 37) matches nothing in this project. Tutorial leftover.
- `jacoco` is applied (line 3) but nothing runs `jacocoTestReport`.
- No `distributionSha256Sum` in `gradle-wrapper.properties`.

## Proposed approach

### Mechanical (safe, do regardless of decisions)

- Delete `sourceCompatibility`/`targetCompatibility`; add
  `java { toolchain { languageVersion = JavaLanguageVersion.of(N) } }` and the
  `org.gradle.toolchains.foojay-resolver-convention` plugin in
  `settings.gradle` so the JDK is provisioned automatically.
- picocli and picocli-codegen to the current 4.7.x (keep codegen as
  `annotationProcessor` only; keep `-Aproject=${project.name}`).
- JUnit via `testImplementation platform('org.junit:junit-bom:5.x')` +
  `junit-jupiter`, plus `testRuntimeOnly 'org.junit.platform:junit-platform-launcher'`.
- Remove Mockito (spec 003 uses hand-written fakes). If the maintainer wants
  it, pin 5.x.
- Remove the dead `-H:IncludeResources` option.
- Wire `test { finalizedBy jacocoTestReport }` with XML output, no threshold
  gate.
- Add `distributionSha256Sum` to the wrapper properties.
- Add or remove `.java-version` to match N (redundant with toolchains).
- Update the commands in `README.md` and `CLAUDE.md`.

### Decision 1: Java level N

Recommend **21**, skipping 17:

- LTS, already the local JDK, and the maintainer's stated end state.
- GraalVM for JDK 21 is current with mature native-image; free Oracle GraalVM
  updates for 17 ended late 2024, so going via 17 means migrating twice.
- Records, `readAllBytes`, `Redirect.DISCARD`, virtual threads all simplify
  specs 003/004/006.
- The bytecode target is irrelevant to users: only the native binary ships.
  Nothing about a native binary benefits from a Java 8 target.
- Java 25 (next LTS) needs Gradle 9.1+; a later option.

### Decision 2: native-image plugin

Recommend replacing `com.palantir.graal` with the official
`org.graalvm.buildtools.native` (latest 0.10.x/0.11.x):

```groovy
graalvmNative {
    binaries.main {
        imageName = 'dirty'
        mainClass = 'net.sonofgarr.dirty.App'
        buildArgs.add('--no-fallback')
    }
}
```

Consequences the maintainer must accept: task renamed `nativeImage` →
`nativeCompile`; output moves `build/graal/dirty` → `build/native/nativeCompile/dirty`
(`.exe` on Windows), so README:31,33, CLAUDE.md, `gradle.yml`, `release.yml`
paths change; a GraalVM JDK must be installed locally (or via
`graalvm/setup-graalvm@v1` in CI) instead of being downloaded by the plugin.
Keep plain `./gradlew build` free of the native step so contributors do not
need GraalVM.

Staying on palantir is zero-setup but frozen at GraalVM 20.x with no arm64
macOS, which blocks spec 007.

## Scope

**In:** `build.gradle`, `settings.gradle`, `gradle-wrapper.properties`,
`.java-version`, README/CLAUDE.md build sections, CI JDK version from 001.
**Out:** Gradle 8.6 → 9.x upgrade (separate low-risk chore; needed for JDK
25); source changes beyond what the compiler bump forces; Dependabot config
(nice-to-have, can ride along).

## Acceptance criteria

- `./gradlew build` passes locally and in CI with the chosen JDK; no
  "obsolete" or "auto-loading deprecated" warnings.
- `nativeImage`/`nativeCompile` produces a binary on the CI runner, and that
  binary answers `--help`, `--version`, and `-d <dir>` correctly (proves
  picocli reflection config is still generated and bundled).
- No dynamic dependency versions (`2.+`) remain; `gradlew dependencies` shows
  picocli 4.7.x and JUnit 5.x only.
- `jacocoTestReport` runs after `test` and emits XML.
- Wrapper checksum verified by `setup-gradle` in CI.

## Open questions (maintainer)

- Java 17 or 21?
- Switch to `org.graalvm.buildtools.native` (requires installing GraalVM
  locally) or stay on palantir?
- Keep Mockito at 5.x "just in case", or drop it?
- Add Dependabot for Gradle and GitHub Actions in this PR?
