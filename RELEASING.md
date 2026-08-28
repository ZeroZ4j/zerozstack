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
3. Update [CHANGELOG.md](CHANGELOG.md). If several branches landed in this release, merge their
   entries into **one** set of sections first — a release entry is never one block per branch. The
   rules for what an entry has to do, and why, are in
   [CONTRIBUTING.md](CONTRIBUTING.md#write-the-changelog-entry-for-the-person-upgrading). Then read
   the **Breaking** section as a user would; that is what people rely on when upgrading.
4. Set the version. This build uses CI-friendly versioning: change `<revision>` in the root
   `pom.xml` and every module follows, with `flatten-maven-plugin` resolving it in the installed
   POMs.
5. Check the version in the archetype's generated `pom.xml` template still matches, since it is not
   covered by `${revision}`.
6. **The text check runs itself** — `PublishedArtifactTextTest` in `zerozstack-store-eclipsestore`
   reads the text of everything this build publishes: the strings baked into compiled classes, the
   resource files, the generated sources, and the project template inside the archetype. It fails
   the build if it finds text that was saved as UTF-8 and then read back through a single-byte code
   page, which is what put nonsense in place of a dash, a play triangle and a block cursor in the
   0.7.0 component library.

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
`-store-eclipsestore`, `-server-test` (new in 0.8.0), `-bom` and `-archetype`. The count said nine
until 0.7.0, when it was checked against `target/central-staging` rather than against this sentence;
the three bindings and the OIDC provider had been publishing for some time. Read the staging
directory before believing this list.

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
git tag -a v0.8.0 -m "ZeroZ Stack 0.8.0"
git push origin v0.8.0
```

Tag after the release is published, not before — a tag that points at something never released is
worse than no tag.
