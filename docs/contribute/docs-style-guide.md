# Documentation style guide

These rules keep the documentation coherent as it grows, and keep it useful to the AI coding agents
that read it. They are short enough to actually follow.

## Structure

Documentation is organised on [Diátaxis](https://diataxis.fr): four kinds of page that serve different
needs and must not be mixed.

| Section | Serves | Voice |
|---|---|---|
| `start/` | learning — an ordered path that must work verbatim | "Do this, then this." |
| `guides/` | a task, for someone who already has the concepts | "To achieve X, do Y." |
| `decide/` | choosing between constructs | "Use X when…, not when…" |
| `reference/` | looking a fact up | dry, complete, no narrative |
| `explain/` | understanding why | discursive; the only place for rationale |
| `agents/` | AI coding agents | imperative, compressed |

If you cannot tell which section a page belongs in, it is probably two pages.

## Writing

1. **Second person, present tense, active voice.** "You declare the topic once." Not "The topic should
   be declared."
2. **Instructions carry no thesis.** Architectural justification lives only in `explain/`. A how-to
   page never explains why the industry is wrong.
3. **Open every page with one sentence** naming who it is for and what they will end up with.
4. **State limits where the feature is taught**, not in a distant caveats file. A "Limits" section at
   the end of a guide is the habit that keeps this documentation trustworthy — keep it, and keep it
   blunt.
5. **No superlatives.** "Incredible", "blazing", "ultra-fast", "radically" are banned. Give a number
   or say nothing.
6. **No present tense for things that do not exist.** "Tracked collections are planned" is fine. A
   reserved protocol opcode described as though it works is not.
7. **Terminology is fixed by the [glossary](../reference/glossary.md).** Event, signal, push, message,
   sync and mutation never drift. If you need a new term, add it there first.
8. **Cross-link on first mention** of another construct, always to the same canonical page.
9. **American spelling**, consistent with the code identifiers.
10. **One sentence per line** where it makes review diffs readable. Wrap at 100 columns.

## Code samples

Samples are the part readers copy and agents learn from, so they get their own rules.

1. **Every sample must be true.** Copy it from a module under `zerozstack-examples/` — which CI builds on
   every push — or verify it compiles. A sample you have not run is a future bug report.
2. **Make samples self-contained.** Include the imports, or name the types in a comment. Do not depend
   on a variable declared three blocks earlier. Context7 and other retrieval tools return snippets
   *without their surrounding page*, so a snippet that only makes sense in context becomes broken
   generated code.
3. **Always tag the fence language** — ` ```java `, ` ```xml `, ` ```bash `. Untagged fences extract
   badly.
4. **Prefer one good sample to three variations.** Near-duplicates get deduplicated by retrieval
   tools anyway, and variations dilute which one is canonical.
5. **Never show wrong code without marking the wrong line itself.** A `// WRONG` heading above a block
   disappears when the block is extracted. Put the marker inside:

    ```java
    // WRONG — same list reference; the equality check swallows it, nothing re-renders.
    tasks.get().add(task);
    tasks.set(tasks.get());
    ```

6. **Show the correct version last**, so the final impression is the right one.

## Retrieval-friendliness

The documentation is indexed by Context7 and read directly by coding agents. Three habits cost
nothing and make retrieval much better:

- **One question per page**, stated in the `<h1>` as something a developer would actually type —
  "Broadcasting server events", not "Events, deeply".
- **A `## When to use this` section first** in every guide, three lines or so. This is what gets
  returned for "should I use X or Y" queries, which are the queries this framework most needs to
  answer well.
- **Stable headings.** They anchor retrieval and inbound links. Do not rename them casually.

Keep [`context7.json`](https://github.com/fschoning/zeroz4j/blob/main/context7.json)'s `rules` array in
sync with `agents/rules.md`. The array is the compressed projection of that page, and it is how
guidance reaches agents working in other repositories.

## Markdown

Documentation is plain Markdown, rendered by MkDocs Material. Keep the source readable on
github.com — no generator-specific syntax beyond admonitions and snippet includes.

```markdown
!!! warning "Experimental"
    Content indented four spaces.
```

Snippet includes are enabled with `check_paths: true`, so a line beginning `--8<--` must resolve to a
real file or the build fails. That is deliberate — it is what will let tutorial samples be pulled from
the CI-built example modules instead of being retyped. To *write about* the syntax without triggering
it, prefix the line with a semicolon.

## Building locally

```bash
python -m venv .venv
.venv/Scripts/activate          # or: source .venv/bin/activate
pip install -r docs/requirements.txt
mkdocs serve
```

`mkdocs build --strict` is what CI runs on every pull request touching `docs/`. It fails on broken
links, bad anchors and unresolved snippets, so run it before you push.

## Before you open a pull request

- Every command in the page has been run, on the OS it claims to support.
- Every API name, signature and annotation attribute matches the source.
- Every version number matches `<revision>` in the root `pom.xml`.
- Links resolve, including anchors.
- No feature is described in the present tense unless it works today.
