# Commons reference

Everything else about the shared libraries is on the docs page [Shared libraries](https://labs64.io/docs/development/shared-libraries.html).

Long-form reference that used to live in the repository README. The [README](../README.md) is the
short entry point; the published documentation is at [labs64.io/docs](https://labs64.io/docs/).

## Development

```bash
just build        # build + test all libraries
just java         # Java only (one reactor: labs64io-parent, then every library)
just java-module authz-queryplan-jpa   # one library and what it depends on
just python       # Python only
```

Local Java consumption: `just install-java` installs `labs64io-parent` and every library as
`0.0.0-SNAPSHOT` into the local Maven repository.

## Release process

All Java artifacts here — `labs64io-parent` and every library — share **one version line** and are
always released together.

No pom carries a version: each declares `<version>${revision}</version>`, and `revision` defaults
to `0.0.0-SNAPSHOT` ("built from source, unreleased"). The release version is the git tag.

- **Snapshot — automatic.** Every green push to `master` deploys `0.0.0-SNAPSHOT` of the whole
  reactor to the Nexus snapshot repository (`labs64io-ci.yml`).
- **Release — publish a GitHub Release** whose tag is the version (`X.Y.Z`).
  `labs64io-release.yml` builds the tagged commit with `-Drevision=<tag>` and deploys it,
  GPG-signed, to the Nexus release repository through the shared `maven-publish.yml` reusable
  workflow (`labs64.io-workspace`). Nothing is committed back and no pom is edited.
  To replay a release, run the workflow manually **from the tag**.
- **Maven Central — promoted by hand, never by a release.** Every release goes to Nexus only; Central
  has a deployment quota, so a release is published there only when someone decides it should be.
  Run *Commons - Promote to Maven Central* (`labs64io-promote-central.yml`) on `master` with the
  version `X.Y.Z`. It rebuilds that tag, uploads the signed bundle to the Central Portal and stops: the
  deployment waits as *validated* until you press *Publish* (or *Drop*) at
  <https://central.sonatype.com/publishing/deployments>. Only a version that is tagged and already on
  Nexus can be promoted (needs `OSS_USER` / `OSS_PASS`, a Portal user token).

After a release, each consumer moves its `labs64io-parent` version to it (Renovate opens that PR;
`payment-gateway-api` additionally carries `openapi-schema-generator.version`).

Requires repository secrets `L64_PUB_CI_USERNAME` / `L64_PUB_CI_PASSWORD` (Nexus) and `GPG_KEY` /
`GPG_KEY_PASS` (release signing).

## License

The core of the *Labs64.IO Ecosystem* is entirely open source and free forever. Community modules are licensed under [Apache License 2.0](../LICENSE).
