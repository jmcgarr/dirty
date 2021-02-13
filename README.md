# Dirty - Find those dirty repos

Small command line utility to find all the dirty git repositories on your machine.

## Installation

Coming soon,...

## Usage

`dirty` assumes that you have all your git repos checked out in a single directory. `dirty` builds off the defaults established by `git-grab`, which checks out code to `$HOME/Projects`. If this works, just run

Given this default, you can run `dirty` from any directory in your shell and get the same result 

## Um, Why?

Glad you asked. So many reasons:

- I have wanted this program so many times when switching laptops
- I wanted to play with GraalVM (I ❤️ Java)
- I never finish work and forget it's there

Also, I don't need to justify myself to you! 😉

## Building

You can build the binary using Gradle:

`./gradlew clean build nativeImage`

Once built, you can just need to add the `dirty/build/graal/dirty` binary to your path.

## What's next

This project is very nascent and lots of work is needed before it should taken seriously. 

- [ ] Add tests
- [ ] Add better stdout/stderr (logging perhaps)  
- [ ] Build each commit via CI
- [ ] Publish from CI to Github packages
- [ ] Allow `brew install` (likely requires me creating a tap)
- [ ] Check to see how far behind each repository is (this should be an option)
- [ ] Test/support Windows