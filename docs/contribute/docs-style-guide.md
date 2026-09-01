# Documentation style guide

These rules keep the documentation coherent as it grows, and keep it useful to the AI coding agents
that read it. They are short enough to actually follow.

## Structure

Documentation is organized on [Diátaxis](https://diataxis.fr): four kinds of page that serve different
needs and must not be mixed.

| Section | Serves | Voice |
|---|---|---|
| `start/` | learning — an ordered path that must work verbatim | "Do this, then this." |
| `guides/` | a task, for someone who already has the concepts | "To achieve X, do Y." |
| `decide/` | choosing between constructs | "Use X when…, not when…" |
| `reference/` | looking a fact up | dry, complete, no narrative |
| Explanation | understanding why | discursive; the only place for rationale |
| AI agents | AI coding agents | imperative, compressed |

The last two have no folder of their own yet. Explanation is `ARCHITECTURE.md`, `CONCEPTS.md` and
`CODE_WALKTHROUGH.md` at the top of `docs/`; the agent material is `AGENTS.md` in the repository root
and `docs/AGENT_PROMPTS.md`. `mkdocs.yml` groups them under those names. Several older pages also sit
at the top of `docs/` — `SIGNALS.md`, `ROUTING.md`, `UI_COMPONENTS.md` and the rest — and are filed
as guides in the navigation; new guides go in `guides/`.

If you cannot tell which section a page belongs in, it is probably two pages.

## Writing

1. **Second person, present tense, active voice.** "You declare the topic once." Not "The topic should
   be declared."
2. **Instructions carry no thesis.** Architectural justification lives only on the explanation pages
   (`ARCHITECTURE.md`, `CONCEPTS.md`). A how-to page never explains why the industry is wrong.
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
9. **American spelling**, consistent with the code identifiers. Color, behavior, catalog, gray,
   canceled, organize, license, center. `DocumentationSpellingTest` fails the build on a British
   spelling in prose, naming the file, the line and the word to use; anything inside backticks or a
   code fence is exempt, because a real name has to be spelled the way the code spells it.
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

Keep [`context7.json`](https://github.com/ZeroZ4j/zerozstack/blob/main/context7.json)'s `rules` array
and [`llms.txt`](https://github.com/ZeroZ4j/zerozstack/blob/main/llms.txt) in sync with
[`AGENTS.md`](https://github.com/ZeroZ4j/zerozstack/blob/main/AGENTS.md). Those two files are the
compressed projection of that page, and they are how guidance reaches agents working in other
repositories. Three things drift and have drifted: the version number, a rule that describes an old
API, and a new page that never gets a link. Check all three whenever you change `AGENTS.md`.

Only the first of the three is caught for you. `VersionStatementTest` fails the build on a version
number that no longer matches `<revision>` in the root `pom.xml`, naming the file and the line —
across every Markdown file in the checkout, not only those. It leaves a sentence about the past
alone, as long as the sentence says it is about the past: `(0.6.0+)`, `since 0.5.0`, `before 0.8.0`,
`added in 0.6.0`. Write one of those forms when you mean history, because a version with no such
marker is read as a claim about the version you are on, and has to be that one.

The `rules` array in `context7.json` is now also copied into `zerozstack-shared-api.jar` during the
build, so applications can read the rules for the exact version they depend on. Editing that array
changes what ships; nothing else has to be touched.

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
