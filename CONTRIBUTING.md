# Contributing to zeroz4j

First off, thank you for considering contributing to zeroz4j! It's people like you that make zeroz4j such a great tool.

## Code of Conduct

By participating in this project, you are expected to uphold standard professional conduct. Please be welcoming and respectful to all members of our community.

## How Can I Contribute?

### Reporting Bugs
If you find a bug in the source code or a mistake in the documentation, you can help us by submitting an issue to our GitHub Repository. Even better, you can submit a Pull Request with a fix.

### Suggesting Enhancements
If you have an idea for an enhancement, please submit an issue to our GitHub Repository.

### Pull Requests
1. Fork the repo and create your branch from `main`.
2. If you've added code that should be tested, add tests.
3. If you've changed APIs, update the documentation.
4. Ensure the test suite passes (`mvn clean test`).
5. Make sure your code follows the existing formatting. All new source files should include the Apache 2.0 license header.
6. Issue that pull request!

## Every control works from the keyboard, and has a name

If your change makes something in `zerozstack-ui-components` clickable, it has to be reachable with
Tab and pressed with Enter, and it has to have words that say what it does.

`KeyboardAndNamingContractTest` enforces that on every build. It reads the source of every
component, works out which element each listener was put on and what tag that element is, and fails
the build when something can only be used with a mouse or is announced as nothing. It also requires
every control to appear on the browser proof page in `tools/ui-proof`, where real key presses are
sent at it.

The whole rule, and how to satisfy it, is on one page:
[Keyboard and naming](docs/guides/ui-keyboard-and-naming.md). The short version is that almost
every failure is a click listener on a `Div`, and almost every fix is a `Button`.

## A test gives the same answer wherever it lands in the run

The build runs test classes in whatever order it likes, and that order is not the same on every
machine. A test that passes only in one position is not a passing test; it is a test that will fail
on somebody else's computer, and the suite it belongs to stops meaning anything until it does.

Two ways to write one have already cost this project a red build for weeks at a time, and both look
completely reasonable on the page.

**Never wait for "a frame". Wait for a number of frames.** Every connection is sent an AUTH frame
the moment it opens, and frames are put on the wire by a writer thread rather than by the thread
that asked for them. A test that opens a connection, arms a latch and waits for the latch to count
down is therefore waiting for whichever frame arrives first — and on a busy machine that is the
AUTH frame, which was already on its way before the call under test was made. The assertion then
runs before the answer exists, and reports whatever the code had not done yet. Use
`FakeBasic.awaitFrames(count, timeoutMillis)`, which cannot be satisfied by the wrong frame, and
count the AUTH frame in the number you ask for.

**Never let an assertion depend on how long something took.** A whole Java process is slow the
first time it does anything and fast afterwards, so the same two lines of code can take fifty
milliseconds early in a run and none at all later. An assertion that only holds while a step is
slow passes at the top of the suite and fails further down. Fix the code so the answer does not
depend on the clock — a boundary that is checked with "later than" usually wanted "at or later
than" — rather than sleeping until the test agrees with you.

If you have to check that a change did not reintroduce either, run the module three times over:

```bash
mvn -pl <module> test -Dsurefire.runOrder=reversealphabetical
mvn -pl <module> test -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=202
mvn -pl <module> test -DargLine="-XX:ActiveProcessorCount=2"
```

The last one is the one that finds races: continuous integration runs on two processors, and this
machine almost certainly has more.

## Save every file as UTF-8

Every file in this repository is UTF-8. That includes source files, resource files and anything
you paste into them.

Two tests enforce it and both are worth knowing about before you hit them:

- `SourceTextEncodingTest` reads every Java file in the checkout.
- `PublishedArtifactTextTest` reads everything the build publishes: every jar it produced, the
  compiled classes and generated sources behind them, every module's resources, and the project
  template the archetype hands to new applications.

  This one also checks itself. It reads the list of published modules out of the POM files rather
  than having it written down, and it fails if any of those modules turned out to have nothing for
  it to read — naming the module. So it cannot quietly end up inspecting less than it says it does.
  The practical consequence: build the whole project once (`mvn install` from the root) before
  running it. If you have only built part of the project, it will tell you which module it could
  not see, and that is the message, not a bug.

They look for text that was saved as UTF-8 once and then read back as if it were Windows-1252 or
one of the console code pages. That accident turns a dash into three or four pieces of nonsense,
and it is invisible: the code still compiles, nothing warns, and the damage only shows up on
somebody's screen. This is not hypothetical — it happened, and the broken text was published in
0.7.0.

If a test fails it prints the file, the characters that are there now, and what you originally
typed. Put that text back into the source file, save the file as UTF-8, and rebuild. There is
nothing to change in the build: an artifact is only ever a copy of the source.

Two habits prevent it entirely:

- Set your editor to UTF-8 and leave it there.
- Never create a file in this repository with a PowerShell redirect (`echo ... > file`). PowerShell
  writes UTF-16, which is how six logging settings ended up in a form Java silently ignored.

## The documentation is written in American English

`color`, not `colour`. `behavior`, not `behaviour`. Also `catalog`, `gray`, `canceled`, `organize`,
`license`, `center`.

`DocumentationSpellingTest` enforces it on every build. It reads every Markdown file in the checkout
plus `llms.txt` and `context7.json`, and fails naming the file, the line, the word you wrote and the
word to use. The rule was in the style guide from the beginning and every page ever written broke it,
which is what a rule with nothing behind it is worth.

Three things it deliberately does not touch:

- **Anything inside backticks**, and every fenced code block. API names, file names, command lines
  and settings keys are code, and the documentation must spell them the way the code spells them.
- **`Flavour` with a capital F.** TeaVM Flavour is a product and `FlavourWrapper` is a class here.
- **The source code.** Comments, javadoc and user-facing strings in `.java` files are still British in
  places. Converting them is a separate decision with test-assertion risk, so the check does not look
  at them and neither should you as a side errand.

The word list is explicit, not a pattern, and that is the point: "-ise becomes -ize" would fire on
*advertise*, *exercise*, *surprise* and *promise*, which are spelled that way everywhere. If you hit
a British word the list does not know, add the word — do not reach for a rule.

## Write the changelog entry for the person upgrading

[`CHANGELOG.md`](CHANGELOG.md) is the most-read page in this repository, and it is read by exactly
one kind of person: somebody who has an application on the previous version and is deciding whether
to move it. Write for them. It is also, in practice, how an AI coding agent with no training data on
this framework learns what changed — several of the features in 0.8.0 were built by people and
agents who had read nothing else.

Four rules. They are not style preferences; each one exists because dropping it made an entry
useless.

1. **Prose, not commit subjects.** A bullet is a short paragraph in plain English, not a
   line copied off a branch. "Fix Dialog modality" tells a reader nothing. Say what was wrong, what
   it cost somebody, and what happens now.

2. **Every breaking change names the fix.** A **Breaking** bullet is not finished until it says what
   to write instead, in a sentence beginning "If you…". Show the two or three lines of code where
   code is the answer. An upgrader who reads a breaking change and still does not know what to type
   has been told nothing useful.

3. **Say the concrete thing.** Numbers, real names, real symptoms. "One long word pushed the whole
   page sideways — 2,773 pixels of it, in a window the width of a telephone" is worth ten lines of
   "improved responsive behavior". Where a fault had no symptom at all, say that too: silence is the
   most expensive property a bug can have and the reader needs warning.

4. **Ordinary words.** Write for somebody who does not already know this codebase. Not "payload
   exceeds configured maximum" but "that file is bigger than the 25 MB limit". Not "focus
   management" but "where the keyboard goes".

**Sections, in this order:** `Breaking`, `Added`, `Changed`, `Fixed`, then `Documentation` if there
is anything to say. Skip any that are empty. Open the release with two short paragraphs: what this
release is about, and which breaking changes will catch the most applications.

**One release, one entry.** When several branches land in the same release, the entries get merged
into a single set of sections before the release is cut — never left as one block per branch. Merge
duplicates; never drop a change because it reads like another one; and never flatten a breaking
change's "what to do instead" out of existence while tidying.

## License
By contributing, you agree that your contributions will be licensed under its Apache 2.0 License.
