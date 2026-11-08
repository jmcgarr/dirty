# Dirty - Find those dirty repos

Small command line utility to find all the dirty git repositories on your machine.

![Build](https://github.com/jmcgarr/dirty/workflows/Build/badge.svg) ![Release](https://github.com/jmcgarr/dirty/workflows/Release/badge.svg)

## Installation

Coming soon,...

## Usage

`dirty` scans a directory of git checkouts and prints one line per repository
that needs attention: uncommitted changes, untracked files, or no remote.

```
dirty                         # scan ~/Projects (the git-grab default)
dirty -d Code                 # scan ~/Code
dirty -d /srv/repos           # scan an absolute path
dirty --max-depth 2           # only look two directory levels down (default 4)
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
automatically when output is not a terminal or `NO_COLOR` is set.

Exit status: `0` all repositories inspected, `1` one or more could not be
inspected (shown as `error:` lines), `2` usage error.

## Um, Why?

Glad you asked. So many reasons:

- I have wanted this program so many times when switching laptops
- I wanted to play with GraalVM (I ❤️ Java)
- I never finish work and forget it's there

Also, I don't need to justify myself to you! 😉

## Building

Requirements: Gradle needs **JDK 17 or newer** to run. Compilation uses JDK 21
through a Gradle toolchain, which Gradle downloads if none is installed. The
native binary additionally needs a **GraalVM JDK 21**.

With [sdkman](https://sdkman.io) the repo's `.sdkmanrc` selects GraalVM for you:

```
sdk env install   # first time only
sdk env
./gradlew build nativeCompile
```

The binary lands at `build/native/nativeCompile/dirty`; copy it onto your `PATH`.

## What's next

This project is very nascent and lots of work is needed before it should taken seriously. 

**Features**

- [X] Add tests
- [ ] Make output tabular (optional?)   
- [ ] Option to output machine readable formats (csv, json)  
- [ ] Add better stdout/stderr (logging perhaps)
- [ ] Parallelize the scanning of git repos  
- [ ] Check to see how far behind each repository is (this should be an option)

**Build and publishing**

- [X] Build each commit via CI
- [X] Publish from CI to Github packages
- [ ] Produce binaries for Windows, Mac and Linux
- [ ] Test/support Windows
- [ ] Add signing to binaries for Mac and Windows
- [ ] Allow `brew install` (likely requires me creating a tap)