# Releasing ZeroZ Stack

How a maintainer cuts a release. Not needed to *use* the framework — see
[README.md](README.md) and [docs/](docs/) for that.

## Before a release

1. `mvn clean install` from the root — the whole build, tests included. Use `clean`: each example
   server copies dependencies into `target/libs`, which is never pruned, so stale jars otherwise
   accumulate and cause duplicate-bean warnings at startup.
2. Update [CHANGELOG.md](CHANGELOG.md). Read the **Breaking** section as a user would; that is what
   people rely on when upgrading.
3. Set the version. This build uses CI-friendly versioning: change `<revision>` in the root
   `pom.xml` and every module follows, with `flatten-maven-plugin` resolving it in the installed
   POMs.
4. Check the version in the archetype's generated `pom.xml` template still matches, since it is not
   covered by `${revision}`.

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

**Nine modules publish:** `zerozstack-parent`, `-shared-api`, `-apt`, `-client`, `-server-core`,
`-server-helidon`, `-ui-components`, `-store-eclipsestore`, `-bom` and `-archetype`.

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
git tag -a v0.4.0 -m "ZeroZ Stack 0.4.0"
git push origin v0.4.0
```

Tag after the release is published, not before — a tag that points at something never released is
worse than no tag.
