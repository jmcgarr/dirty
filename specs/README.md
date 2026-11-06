# Specs

Plans and specifications for improvements to `dirty` live here. Write a spec
**before** starting non-trivial work so the approach can be reviewed first.

## Conventions

- One Markdown file per improvement: `specs/NNN-short-title.md`, where `NNN`
  is the next zero-padded number (e.g. `001-add-unit-tests.md`).
- Each spec should contain, at minimum:
  - **Problem / Motivation** – what is wrong or missing and why it matters
  - **Proposed approach** – the design, with alternatives considered
  - **Scope** – what is in and out
  - **Acceptance criteria** – how we know it is done
  - **Open questions** – anything that needs a decision from the maintainer
- Keep the spec updated as the work evolves. The spec is the source of truth
  for *why*; the PR is the source of truth for *what changed*.
- When the work ships (PR merged), move the spec to `specs/completed/` in the
  same PR, or in an immediate follow-up. Keep the original file name.

See `specs/completed/README.md` for the archive.
