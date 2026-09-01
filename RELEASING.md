# Releasing ZeroZ Stack

How a maintainer cuts a release. Not needed to *use* the framework — see
[README.md](README.md) and [docs/](docs/) for that.

## Before a release

1. `mvn clean install` from the root — the whole build, tests included. Since 0.4.1 each server
   module prunes its own `target/libs` before refilling it, so a plain `install` no longer leaves
   stale jars behind and dies with "WELD-001409: Ambiguous dependencies"; `clean` is still the
   honest choice before a release.
2. **Run the archetype smoke test** — [`zerozstack-archetype/smoke/README.md`](zerozstack-archetype/smoke/README.md).
   It generates a project from the archetype, builds it, starts it and drives it with a headless
   browser. The three blockers fixed in 0.4.1 all produced a project that compiled, started and
   served pages while not working; only an end-to-end run catches that class of defect.

   Install the browser driver with `npm install` **in `zerozstack-archetype/smoke`**, which is where
   the version is pinned and where both scripts look for it. Do not borrow an installation from
   another project on the machine: everything a run leaves in that folder is ignored by git, so
   there is no reason left to.

   Run **both** scripts. `smoke-test.mjs` proves a generated project works; `drop-recovery-test.mjs`
   proves it survives losing its server and getting it back, and since 0.8.0 also proves that the
   bar it puts on the screen says the right sentence rather than a piece of browser jargon — which
   is what every release before 0.8.0 shipped, unnoticed, because nothing had ever read it.
3. Update [CHANGELOG.md](CHANGELOG.md). If several branches landed in this release, merge their
   entries into **one** set of sections first — a release entry is never one block per branch. The
   rules for what an entry has to do, and why, are in
   [CONTRIBUTING.md](CONTRIBUTING.md#write-the-changelog-entry-for-the-person-upgrading). Then read
   the **Breaking** section as a user would; that is what people rely on when upgrading.
4. Set the version. This build uses CI-friendly versioning: change `<revision>` in the root
   `pom.xml` and every module follows, with `flatten-maven-plugin` resolving it in the installed
   POMs. The archetype needs nothing separate: its generated project's version comes from
   `@project.version@` in `archetype-metadata.xml`, which that module's POM filters at build time,
   so it follows `<revision>` too. (Until 0.8.0 there was a step here saying to check it by hand.)

   There is also nothing temporary to remove. Documentation always names the version in
   `<revision>`, whatever that is, so no page ever holds a number that has to be swapped back on
   the day of a release.
5. **Read the four documents an AI coding assistant reads, and correct the version in each.**
   `AGENTS.md`, `llms.txt`, `context7.json` and [`docs/AGENT_PROMPTS.md`](docs/AGENT_PROMPTS.md)
   state the version in prose, and nothing in `${revision}` reaches them. This step exists because
   `llms.txt` was once found two releases stale — naming a version long superseded and still calling
   the router unimplemented — and nobody had noticed.

   `VersionStatementTest` in `zerozstack-ui-components` does the finding for you: run it after
   step 4 and it names every file and line still saying the old number, across the whole checkout
   and not just those four. **Correct only the lines it names.** A sentence about what an earlier
   release did keeps its own number — a previous bump walked the documentation incrementing every
   version it saw, and left pages claiming that work released in 0.5.0 had arrived in the version
   being prepared. The check is built around that distinction and will not report a sentence that
   says when.

   `context7.json` and `llms.txt` are also where a rule can go quietly out of date, which no check
   can catch. Read the rules you changed this cycle. The rule list in `context7.json` is now
   copied into `zerozstack-shared-api.jar` at build time, so it reaches applications as well as
   the documentation index.
6. **The text check runs itself** — `PublishedArtifactTextTest` in `zerozstack-store-eclipsestore`
   reads the text of everything this build publishes: the strings baked into compiled classes, the
   resource files, the generated sources, and the project template inside the archetype. It fails
   the build if it finds text that was saved as UTF-8 and then read back through a single-byte code
   page, which is what put nonsense in place of a dash, a play triangle and a block cursor in the
   component library released in 0.7.0.

   It also refuses to pass on a partial job. It works out which modules the release publishes by
   reading the POM files — never from a list written down here, which would drift — and fails,
   naming the module, if one of them had nothing for it to read. So do not skip tests before a
   release, and if it says a module was not covered, that is a real gap: build the whole reactor
   and run it again rather than narrowing the check.

## Publishing to Maven Central

The build is configured. The account steps — Central Portal account, namespace verification and
the signing key — are documented once for the whole family in
**[ZeroZ DB's RELEASING.md](https://github.com/ZeroZ4j/zerozdb/blob/main/RELEASING.md)**, and the
verified `com.zeroz4j` namespace covers this repository too, so none of that is repeated here.

```bash
mvn clean deploy -Prelease
```

The `release` profile attaches source and javadoc jars, signs everything, and uploads to the
portal. `autoPublish` is false, so the deployment waits for your approval at
[central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments)
rather than going straight out — a published version can never be changed or removed.

**Fourteen modules publish:** `zerozstack-parent`, `-shared-api`, `-apt`, `-client`, `-server-core`,
`-server-jaxrs`, `-server-jakarta`, `-server-helidon`, `-auth-oidc`, `-ui-components`,
`-store-eclipsestore`, `-server-test` (new in 0.8.0), `-bom` and `-archetype`.

**This sentence has now been wrong twice.** It said nine until 0.7.0, when somebody checked
`target/central-staging` instead of believing it — the three server bindings and the OIDC provider
had been publishing for some time. It then said thirteen until 0.8.0, when `-server-test` was added
and the sentence was not. **Read `target/central-staging` after `mvn clean verify -Prelease` and
count what is actually there.** A sentence in a document is not a manifest, and this one has a
record.

**The examples do not.** They are demonstrations rather than libraries, and
`zerozstack-examples/pom.xml` sets `maven.deploy.skip`, `skipPublishing`, `gpg.skip`,
`maven.source.skip` and `maven.javadoc.skip`, all inherited by every example module. They are still
built and tested by the reactor; they are simply never published, and not signed either, since
signing artifacts nobody consumes only slows the build.

Verify before deploying, which signs everything without uploading:

```bash
mvn clean verify -Prelease -DskipTests
```

## Tagging

```bash
git tag -a v0.9.0 -m "ZeroZ Stack 0.9.0"
git push origin v0.9.0
```

Tag after the release is published, not before — a tag that points at something never released is
worse than no tag.

## Opening the next line — do this in the same sitting as the tag

**Bump `<revision>` to the next `-SNAPSHOT` immediately.** Not next week, not when the first feature
lands. The moment the tag is pushed, `main` is no longer the released version and must stop claiming
to be it.

1. Set `<revision>` in the root `pom.xml` to the next version with `-SNAPSHOT` on it —
   `0.9.0-SNAPSHOT` after 0.8.0. A protocol change or new public API makes it a minor bump, not a patch.
2. Run `VersionStatementTest` and correct every line it names, exactly as at step 5 above. The
   documentation always describes the code sitting next to it, which is the development version.
3. Add a `## [Unreleased]` heading to [CHANGELOG.md](CHANGELOG.md) with a compare link from the tag
   you just pushed to `HEAD`.

**Why this is a numbered step and not a habit.** It happened after 0.8.0. The tag was pushed,
`<revision>` was left at the released number, and ordinary work carried on. Every `mvn install` from
then on overwrote the genuine published artifacts in the local repository with post-release builds:
the released jar was stamped 11:33, the one on the machine 17:04. Anything resolving those
coordinates was compiling against code that is not what is on Maven Central — silently, with nothing
to notice. Repairing it meant deleting the release's directories under
`~/.m2/repository/com/zeroz4j` and letting Maven re-fetch the real ones.

A released version number must never again be the version an ordinary build produces.
