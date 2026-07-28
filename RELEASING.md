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

Not yet configured for this repository. The process, the account steps and the signing key are
documented once for the whole family in
**[ZeroZ DB's RELEASING.md](https://github.com/ZeroZ4j/zerozdb/blob/main/RELEASING.md)** — the
namespace verification there covers all of `com.zeroz4j`, so this repository needs only the build
configuration, not the account setup again.

What this repository still needs before its first Central release:

- the POM metadata Central validates: `url`, `licenses`, `developers`, `scm` (already present in
  ZeroZ DB's POM, and worth copying from there);
- a `release` profile attaching source and javadoc jars, signing with `maven-gpg-plugin`
  (including `--pinentry-mode loopback`, without which a scripted release hangs), and uploading
  with `central-publishing-maven-plugin`;
- a decision on which modules publish. The examples should not; the archetype, BOM and the six
  library modules should.

Being a multi-module build, one `mvn deploy -Prelease` publishes every selected module together.

## Tagging

```bash
git tag -a v0.4.0 -m "ZeroZ Stack 0.4.0"
git push origin v0.4.0
```

Tag after the release is published, not before — a tag that points at something never released is
worse than no tag.
