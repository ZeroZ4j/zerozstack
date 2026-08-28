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

## License
By contributing, you agree that your contributions will be licensed under its Apache 2.0 License.
