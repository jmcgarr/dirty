# Dirty - Find those dirty repos

Small command line utility to find all the dirty git repositories on your machine.

![Build](https://github.com/jmcgarr/dirty/workflows/Build/badge.svg) ![Release](https://github.com/jmcgarr/dirty/workflows/Release/badge.svg)

## Installation

With [Homebrew](https://brew.sh) on macOS (Apple silicon) or Linux (x86_64):

```sh
brew install jmcgarr/tap/dirty
```

Homebrew asks once before running a third-party tap's formulae; answer for
every formula in the tap with `brew trust jmcgarr/tap`.

Prebuilt binaries and a `SHA256SUMS` file are attached to each
[GitHub release](https://github.com/jmcgarr/dirty/releases). Download the one
for your platform, `chmod +x` it, and put it on your `PATH`. Either way `dirty`
needs `git` on your `PATH`.

## Usage

`dirty` scans a directory of git checkouts and prints one line per repository
that needs attention: uncommitted changes, untracked files, or no remote.

```
dirty                         # scan ~/Projects (the git-grab default)
dirty -d Code                 # scan ~/Code
dirty -d /srv/repos           # scan an absolute path
dirty --max-depth 2           # only look two directory levels down (default 4)
dirty --exit-code             # exit 1 if anything needs attention (for scripts and prompts)
dirty -v                      # show every git command and a summary on stderr
dirty -f table                # aligned columns instead of one line per repo
dirty -f json | jq '.repos[] | select(.dirty)'
dirty -f csv > repos.csv
dirty --all                   # include clean repositories (text and table)
dirty --no-color              # plain text even on a terminal; --color forces colors on
```

Example output:

```
github/jmcgarr/budget: No remote repository
github/jmcgarr/dirty: 6 modified, 11 untracked
github/jmcgarr/since: 1 untracked
```

Hidden directories and `node_modules`, `build`, `target`, `.gradle` are
skipped; symbolic links are not followed; a repository's contents are not
searched for nested repositories. The "Finding dirty git repos in …" banner and
any warnings go to stderr, so stdout is safe to pipe. Colors are disabled
automatically when output is not a terminal or `NO_COLOR` is set; `--color`
and `--no-color` override that either way.

### Output formats

`text` (the default) and `table` show only repositories that need attention
unless `--all` is given. `json` and `csv` always list every repository found,
never use colors, and use `\n` line endings on every platform. The JSON
document looks like this:

```json
{
  "version": 1,
  "root": "/Users/me/Projects",
  "repos": [
    {
      "path": "github/jmcgarr/dirty",
      "dirty": true,
      "modified": 6,
      "untracked": 11,
      "hasRemote": true,
      "error": null
    },
    {
      "path": "github/jmcgarr/broken",
      "dirty": null,
      "modified": null,
      "untracked": null,
      "hasRemote": null,
      "error": "fatal: bad object HEAD"
    }
  ]
}
```

`dirty` is true when there are changed files or no remote. A repository that
could not be inspected has `error` set and the other fields `null`. The shape
is a contract: `version` is `1`; keys are never removed or renamed within a
version, new keys may be added, and a breaking change bumps `version`. The CSV
header is `path,dirty,modified,untracked,hasRemote,error` with the same values
(empty cells on error rows) and RFC 4180 quoting.

Exit status: `0` all repositories inspected, `1` one or more could not be
inspected (`error:` lines in text output, an `error` column or field in the others), `2` usage error or git not on PATH, `130`
interrupted. With `--exit-code`, `1` also means at least one repository needs
attention, so `dirty --exit-code -d Code && echo all clean` works as a check.
Each git call is given 30 seconds; a wedged repository is reported as an
error instead of hanging the scan.

## License

Apache License 2.0; see [LICENSE](LICENSE).

## Um, Why?

Glad you asked. So many reasons:

- I have wanted this program so many times when switching laptops
- I wanted to play with GraalVM (I ❤️ Java)
- I never finish work and forget it's there

Also, I don't need to justify myself to you! 😉

## Building

Requirements: Gradle needs **JDK 17 or newer** to run. Compilation uses JDK 25
through a Gradle toolchain, which Gradle downloads if none is installed. The
native binary additionally needs a **GraalVM JDK 25** (Community Edition 25.5 or newer).

With [sdkman](https://sdkman.io) the repo's `.sdkmanrc` selects GraalVM for you
once it is installed under the id `25.5-graalce`:

```
# first time only: sdkman lists the GraalVM 25.x Community builds but cannot
# download them (its broker returns 404 as of 2026-11-09), so register a
# manual download as a local install
curl -LO https://github.com/graalvm/graalvm-ce-builds/releases/download/graal-25.5.5.0.3/graalvm-community-jdk-25i5-25.0.5.0.3_macos-aarch64_bin.tar.gz
tar xzf graalvm-community-jdk-25i5-25.0.5.0.3_macos-aarch64_bin.tar.gz
sdk install java 25.5-graalce "$PWD/graalvm-community-25.5.5.0.3+1.1/Contents/Home"   # Linux: omit Contents/Home

# every session
sdk env
./gradlew build nativeCompile
```

Without sdkman, point `GRAALVM_HOME` (or `JAVA_HOME`) at any GraalVM 25
installation; the native plugin looks there first.

The binary lands at `build/native/nativeCompile/dirty`; copy it onto your `PATH`.

## What's next

This project is very nascent and lots of work is needed before it should taken seriously. 

**Features**

- [X] Add tests
- [X] Make output tabular (optional?)   
- [X] Option to output machine readable formats (csv, json)  
- [ ] Add better stdout/stderr (logging perhaps)
- [ ] Parallelize the scanning of git repos  
- [ ] Check to see how far behind each repository is (this should be an option)

**Build and publishing**

- [X] Build each commit via CI
- [X] Publish from CI to Github packages
- [X] Produce binaries for Mac and Linux
- [ ] Produce a Windows binary
- [ ] Test/support Windows
- [ ] Add signing to binaries for Mac and Windows
- [X] Allow `brew install` (likely requires me creating a tap)