# 007 – Release pipeline: multi-platform binaries, signing, Homebrew

**Size:** L (mostly maintainer decisions)  **Depends on:** 002 (native plugin, Java level)  **Independent of:** 003–006

## Problem / Motivation

- `release.yml` uses archived actions (`actions/create-release@v1`,
  `actions/upload-release-asset@v1`), the same JDK 11.0.2 setup that fails TLS
  (spec 001), runs no tests, has no `permissions` block, builds macOS only, and
  uploads a single asset named `dirty` with content type `application/x-sh`.
  Last release is v0.0.5 (2021).
- `codesign.yml` is half-finished: the `codesign` and `set-key-partition-list`
  lines are commented out, the keychain password is a literal (`12345pwd`),
  the identity hash is hard-coded, `$MACOS_CERTIFICATE` is unquoted, the signed
  binary is never re-uploaded, there is no notarization, and nothing guards it
  from running on `pull_request` where secrets are empty.
- README "Installation: Coming soon" and TODOs for Windows/Mac/Linux binaries,
  signing, and `brew install`.
- The version string is hard-coded in `App.java:19` (`dirty 0.1.0`) and does
  not match the tag.

## Proposed approach

### Build matrix (shared by Build and Release)

Consider a `build-native.yml` **reusable workflow called at job level** by both
`gradle.yml` and `release.yml`. That is the correct use of `workflow_call`.

| Runner | Asset |
|---|---|
| `ubuntu-latest` | `dirty-linux-x86_64` |
| `ubuntu-24.04-arm` (optional) | `dirty-linux-aarch64` |
| `macos-15` (arm64) | `dirty-macos-aarch64` |
| `macos-13` / `macos-15-intel` (optional; Intel runners are being retired) | `dirty-macos-x86_64` |
| `windows-latest` | `dirty-windows-x86_64.exe` |

Each leg: `graalvm/setup-graalvm@v1` (`java-version` per 002, `distribution:
graalvm-community`, `github-token`), `./gradlew nativeCompile`, smoke-test
`--version`, rename (`shell: bash`), `upload-artifact@v4` with a distinct name
per leg (v4 rejects duplicate names). Running the tests on Windows also serves
the README "Test/support Windows" TODO; expect to replace the `File.separator`
string hack with `Path.relativize`.

### `release.yml`

1. `build`: the matrix above.
2. `sign-macos` (`needs: build`, see below).
3. `publish` (`needs: [build, sign-macos]`, `permissions: contents: write`):
   `download-artifact@v4` with `merge-multiple: true` into `dist/`, generate
   `SHA256SUMS`, then `gh release create "$GITHUB_REF_NAME" dist/*
   --generate-notes --verify-tag` (or `softprops/action-gh-release@v2`).
   A failed leg blocks publish through `needs`.
4. Derive the `--version` string from the tag at build time (Gradle
   `-Pversion=`) instead of the literal in `App.java`.

### Codesigning (macOS)

Make it a job in `release.yml` with `needs`, or keep `codesign.yml` and call it
as `jobs.sign: { needs: build, uses: ./.github/workflows/codesign.yml, secrets: inherit }`,
guarded with `if: startsWith(github.ref, 'refs/tags/v')`. Fix: keychain
password from `uuidgen`/`openssl rand -hex 16`; `security delete-keychain` in
an `if: always()` step; `printf '%s' "$MACOS_CERTIFICATE" | base64 -d`;
identity from a secret; `codesign --force --options runtime --timestamp -s
"$IDENTITY"`; re-upload the signed artifact. Notarization needs
`xcrun notarytool submit --wait` on a zip plus API-key secrets; a bare binary
cannot be stapled. Arm64 binaries get an ad-hoc signature from the linker, so
"unsigned but runnable from a terminal" is a valid first step.

**This touches signing secrets. Per CLAUDE.md, get explicit maintainer
approval and run the `security-review` skill before opening the PR.**

### Distribution

A Homebrew tap (`jmcgarr/homebrew-tap`) with a formula pointing at the release
assets sidesteps Gatekeeper quarantine entirely (brew strips it) and is the
README's own stated install path. Recommend shipping the tap **before**
investing in Developer ID + notarization. Windows Authenticode is out of scope
for now.

## Scope

**In:** `release.yml`, `codesign.yml` (or its deletion), optional
`build-native.yml`, version-from-tag wiring, README install section, tap repo
(separate repository).
**Out:** auto-update; package managers beyond Homebrew; Windows signing.

## Acceptance criteria

- Pushing `vX.Y.Z` produces a GitHub release with one binary per supported
  platform plus `SHA256SUMS`, and `dirty --version` on each prints `X.Y.Z`.
- If signing is chosen: the macOS binary runs after browser download without a
  Gatekeeper block (`spctl -a -vv` passes). If tap is chosen:
  `brew install jmcgarr/tap/dirty && dirty --version` works on a clean Mac.
- Build and Release badges green; no archived actions remain.
- No secret value or keychain password appears as a literal in any workflow.

## Open questions (all maintainer)

- Is Apple Developer ID signing worth the yearly cost and secret handling, or
  is Homebrew-tap-only distribution enough?
- Which platforms are actually supported? Intel mac? Windows? Linux arm64?
- Tags only, or also nightly artifacts from `master`?
- Keep the reusable-workflow approach for codesign, or fold it into
  `release.yml`?

## Decisions (2026-11-07)

- **Both** Apple Developer ID signing + notarization **and** a Homebrew tap.
  Secrets required: Developer ID Application certificate (.p12, base64) and
  password, signing identity, App Store Connect API key id / issuer id / key.
  Run the `security-review` skill before opening the PR.
- Platforms: `ubuntu-latest` (linux-x86_64), `macos-15` (macos-aarch64),
  `windows-latest` (windows-x86_64). Intel mac and linux-aarch64 optional.
- Releases on tags only; no nightly artifacts. Per-push native build on Linux
  is already in `gradle.yml` (spec 001).
- Reusable `build-native.yml` called at job level by both workflows is
  preferred over duplicating the matrix.
