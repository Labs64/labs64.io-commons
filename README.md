<p align="center"><img src="https://raw.githubusercontent.com/Labs64/.github/master/assets/labs64-io-ecosystem.png" alt="Labs64.IO Ecosystem"></p>

# Labs64.IO :: Commons

[![CI](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml/badge.svg)](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml)
[![Maven Central (auth-context)](https://img.shields.io/maven-central/v/io.labs64/auth-context-spring-boot-starter)](https://central.sonatype.com/artifact/io.labs64/auth-context-spring-boot-starter)
[![Maven Central (openapi-starter)](https://img.shields.io/maven-central/v/io.labs64/openapi-spring-boot-starter)](https://central.sonatype.com/artifact/io.labs64/openapi-spring-boot-starter)
[![Maven Central (authz-queryplan-jpa)](https://img.shields.io/maven-central/v/io.labs64/authz-queryplan-jpa)](https://central.sonatype.com/artifact/io.labs64/authz-queryplan-jpa)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![📖 Documentation](https://img.shields.io/badge/📖-Documentation-AB6543.svg)](https://labs64.io/docs/index.html)

Shared, cross-service libraries for the [Labs64.IO Ecosystem](https://labs64.io).

## Libraries

| Library | Language | Purpose |
|---|---|---|
| [`labs64io-parent`](labs64io-parent/) | Maven POM | Build parent of every Labs64.IO Java library and service: the Spring Boot line, BOM security overrides, shared dependency/plugin versions, commons library versions and the release rules |
| [`auth-context-core`](auth-context-java/auth-context-core/) | Java 17+ | Dependency-free auth-context model, holder and trusted-header parser |
| [`auth-context-spring-boot-starter`](auth-context-java/auth-context-spring-boot-starter/) | Java 17+ / Spring Boot 4 | Trusted gateway auth-context (`X-Auth-*`) parsing, fail-closed enforcement, `@RequireScopes`, outbound propagation, `@WithAuthContext` test support |
| [`openapi-spring-boot-starter`](openapi-spring-boot-starter/) | Java 17+ / Spring Boot 4 | Shared springdoc runtime servers, bearer security and canonical OpenAPI metadata configuration |
| [`openapi-schema-generator`](openapi-schema-generator/) | Java 17+ | Extracts versioned JSON Schema documents from explicit OpenAPI 3.1 `$schema` fields |
| [`authz-queryplan-jpa`](authz-queryplan-jpa/) | Java 17+ / Spring Boot 4 | Translates a Cerbos `PlanResources` query plan into a Spring Data JPA `Specification` (Data PEP) |
| [`auth-context-python`](auth-context-python/) | Python 3.13+ | Mirrored `AuthContext`, ASGI middleware, FastAPI dependencies, httpx propagation hook, pytest fixture |

Alongside the libraries, [`auth-policy-cerbos/`](auth-policy-cerbos/) holds the Cerbos policy-generation toolkit (`generate.sh` / `validate.sh` + reference OpenAPI) used to derive the central PDP's policies.

Both implementations obey the trusted header contract (`X-Auth-User`, `X-Auth-Scopes`, `X-Auth-Tenant`, `X-Request-ID`) and are pinned to identical behavior by the shared vectors in [`test-vectors/`](test-vectors/).

## Consuming

**Java:**

Published to Labs64 Nexus. A service inherits `labs64io-parent`; that one version pins the Spring
Boot line, the shared third-party versions **and** every commons library, so the libraries
themselves are declared without a version:

```xml
<parent>
    <groupId>io.labs64</groupId>
    <artifactId>labs64io-parent</artifactId>
    <version>X.Y.Z</version>
    <relativePath />
</parent>

<dependencies>
    <dependency>
        <groupId>io.labs64</groupId>
        <artifactId>auth-context-spring-boot-starter</artifactId>
    </dependency>
</dependencies>

<!-- Needed in the consumer too: Maven must reach Labs64 Nexus to find the parent itself. -->
<repositories>
    <repository>
        <id>labs64-nexus</id>
        <url>https://nexus.labs64.com/repository/labs64.io-releases/</url>
    </repository>
</repositories>
```

Pin a **released** version. `0.0.0-SNAPSHOT` (what `master` builds as) is a moving target: use it
only while developing against unreleased commons, and move back to a release before releasing the
service — a release build refuses `-SNAPSHOT` inputs (`requireReleaseDeps` in `labs64io-parent`).

**Python:**

```bash
pip install "auth-context-python @ git+https://github.com/Labs64/labs64.io-commons.git@COMMIT_OR_TAG#subdirectory=auth-context-python"
```

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
- **Maven Central** is opt-in: set the repository variable `PUBLISH_MAVEN_CENTRAL=true`
  (needs `OSS_USER` / `OSS_PASS`).

After a release, each consumer moves its `labs64io-parent` version to it (Renovate opens that PR;
`payment-gateway-api` additionally carries `openapi-schema-generator.version`).

Requires repository secrets `L64_PUB_CI_USERNAME` / `L64_PUB_CI_PASSWORD` (Nexus) and `GPG_KEY` /
`GPG_KEY_PASS` (release signing).

## OpenAPI Auth Policy Generation

Java services declare scopes, tenant requirements, and domain-resource metadata
with `x-labs64.auth`. The preprocessor generates derived artifacts from that
same source:

- an OpenAPI file enriched with `x-operation-extra-annotation` for OpenAPI Generator templates
- Cerbos policies for the central PDP (`--cerbos-output`)
- a routes manifest (`<module>.routes.yaml`) consumed by the Traefik auth-proxy for path matching (`--routes-output`)

Example:

```yaml
paths:
  /payments:
    get:
      operationId: listPayments
      x-labs64:
        auth:
          tenant: true
          scopes:
            - payment:read
  /health:
    get:
      operationId: health
```

`x-labs64.auth.scopes` generates `@RequireScopes`; `x-labs64.auth.tenant` generates
`@RequireTenant`; and `x-labs64.auth.resourceType` generates `@Authorize`. An optional
`x-labs64.auth.resource` supplies its SpEL resource reference, for example
`#paymentId`. An operation is public when it has no Labs64 auth requirements;
public operations generate `@PublicEndpoint`. Standard OpenAPI `security` is not
interpreted by
this custom header-based authorization pipeline.

CLI:

```bash
just install-java
cd auth-context-java/auth-context-spring-boot-starter
mvn -q exec:java \
  -Dexec.mainClass=io.labs64.authcontext.openapi.OpenApiAuthPreprocessorCli \
  -Dexec.args="--input openapi.yaml --openapi-output target/generated/openapi.yaml --cerbos-output target/cerbos --routes-output target/routes.yaml --module commons"
```

## Related

- Centralized authentication & authorization gateway (`labs64.io-authproxy/traefik-authproxy`)
- [`labs64.io-authproxy`](https://github.com/Labs64/labs64.io-authproxy) — traefik-authproxy, the header contract's producer

## License

The core of the *Labs64.IO Ecosystem* is entirely open source and free forever. Community modules are licensed under [Apache License 2.0](LICENSE).
