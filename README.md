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

## What's next

This project is very nascent and lots of work is needed before it should taken seriously. 

- [ ] Add tests
- [ ] Build each commit on CircleCI
- [ ] Publish from CircleCI to Github packages
- [ ] Allow `brew install` (likely requires me creating a tap)