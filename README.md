<p align="center"><img src="https://raw.githubusercontent.com/Labs64/.github/master/assets/labs64-io-ecosystem.png" alt="Labs64.IO Ecosystem"></p>

# Labs64.IO :: Commons

Shared, cross-service libraries for the [Labs64.IO Ecosystem](https://labs64.io).

## Libraries

| Library | Language | Purpose |
|---|---|---|
| [`auth-context-spring-boot-starter`](auth-context-java/) | Java 17+ / Spring Boot 4 | Trusted gateway auth-context (`X-Auth-*`) parsing, fail-closed enforcement, `@RequireScopes`, outbound propagation, `@WithAuthContext` test support |
| [`openapi-spring-boot-starter`](openapi-spring-boot-starter/) | Java 17+ / Spring Boot 4 | Shared springdoc runtime servers, bearer security and canonical OpenAPI metadata configuration |
| [`authz-queryplan-jpa`](authz-queryplan-jpa/) | Java 17+ / Spring Boot 4 | Translates a Cerbos `PlanResources` query plan into a Spring Data JPA `Specification` (Data PEP) |
| [`auth-context-python`](auth-context-python/) | Python 3.13+ | Mirrored `AuthContext`, ASGI middleware, FastAPI dependencies, httpx propagation hook, pytest fixture |

Alongside the libraries, [`auth-policy-cerbos/`](auth-policy-cerbos/) holds the Cerbos policy-generation toolkit (`generate.sh` / `validate.sh` + reference OpenAPI) used to derive the central PDP's policies.

Both implementations obey the trusted header contract (`X-Auth-User`, `X-Auth-Scopes`, `X-Auth-Tenant`, `X-Request-ID`) and are pinned to identical behavior by the shared vectors in [`test-vectors/`](test-vectors/).

## Consuming

**Java:**

Published to Labs64 Nexus (snapshots and releases both). Ensure your `settings.xml` or CI
environment is configured to resolve from the Labs64 Maven repositories.

**Current status: pre-release.** Every Java library here is still on its first development line
(`0.1.0-SNAPSHOT`) — no `0.1.0` (or any other) stable version has been cut yet, and every current
ecosystem consumer (`labs64.io-auditflow`, `labs64.io-checkout`, `labs64.io-payment-gateway`)
depends on the snapshot for exactly that reason. A snapshot is a moving target — Nexus lets it be
overwritten at any time — so no downstream service should stay pinned to one longer than
necessary. See [Release process](#release-process) below for cutting the first stable release and
[Where each library actually is](#where-each-library-actually-is) for what changes once one exists.

```xml
<dependency>
    <groupId>io.labs64</groupId>
    <artifactId>auth-context-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**Python:**

```bash
pip install "auth-context-python @ git+https://github.com/Labs64/labs64.io-commons.git@COMMIT_OR_TAG#subdirectory=auth-context-python"
```

## Development

```bash
just build   # build + test all libraries
just java    # Java only
just openapi # OpenAPI starter only
just python  # Python only
```

Local Java consumption: `just install-java` installs `0.1.0-SNAPSHOT` into the local Maven repository.

## Release process

`.github/workflows/labs64io-ci.yml` publishes both automatically and on demand, via the shared
`maven-publish.yml` reusable workflow (`labs64.io-workspace`):

- **Snapshot — automatic.** Every push to `master` deploys the current `-SNAPSHOT` for
  `auth-context-java` and `openapi-spring-boot-starter` to the Nexus snapshot repository (silently
  skipped if the pom version is not a `-SNAPSHOT` — a release version is never accidentally
  re-pushed there). `authz-queryplan-jpa` follows once `auth-context`'s snapshot publish
  completes.
- **Release — manual, `workflow_dispatch` only.** Trigger `labs64io-ci.yml` from the Actions tab
  and fill in whichever of `release-auth-context-version` / `release-openapi-starter-version` /
  `release-authz-queryplan-version` you're cutting (`X.Y.Z`; leave the others blank to skip them).
  Each does, for that one library: `versions:set` to the given version, a GPG-signed deploy to the
  Nexus release repository, a commit + git tag, then a bump back to the next `-SNAPSHOT` — pushed
  straight to `master`.

  `authz-queryplan-jpa` pins its *own* dependency on auth-context at the **latest released**
  version (`<auth-context.version>` in its `pom.xml`), deliberately never the in-repo snapshot —
  see that property's comment. After releasing `auth-context-java`, bump that property to the new
  version in a follow-up commit before releasing `authz-queryplan-jpa`, so it isn't left building
  against a now-superseded release.

Requires repository secrets `L64_PUB_CI_USERNAME` / `L64_PUB_CI_PASSWORD` (Nexus) and `GPG_KEY` /
`GPG_KEY_PASS` (release signing) — already configured for this repository's snapshot publishing to
work at all.

### Where each library actually is

| Library | Released? | Consumers currently pin |
|---|---|---|
| `auth-context-spring-boot-starter` | Not yet | `0.1.0-SNAPSHOT` (auditflow-be, checkout-be, payment-gateway-be) |
| `openapi-spring-boot-starter` | Not yet | `0.1.0-SNAPSHOT` (via `${labs64-openapi.version}`) |
| `authz-queryplan-jpa` | Not yet (last real release: `0.0.3`, tagged) | `0.1.0-SNAPSHOT` (checkout-be) |
| `auth-context-python` | git-ref install only, no package index | — |

Once a Java library's first stable release is cut, update every consumer's pinned version away
from the snapshot in the same change — a released library with a downstream still pinned to its
snapshot is the exact state this section exists to avoid recreating.

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
cd auth-context-java
mvn -q exec:java \
  -Dexec.mainClass=io.labs64.authcontext.openapi.OpenApiAuthPreprocessorCli \
  -Dexec.args="--input openapi.yaml --openapi-output target/generated/openapi.yaml --cerbos-output target/cerbos --routes-output target/routes.yaml --module commons"
```

## Related

- Centralized authentication & authorization gateway (`labs64.io-authproxy/traefik-authproxy`)
- [`labs64.io-authproxy`](https://github.com/Labs64/labs64.io-authproxy) — traefik-authproxy, the header contract's producer

## License

The core of the *Labs64.IO Ecosystem* is entirely open source and free forever. Community modules are licensed under [Apache License 2.0](LICENSE).
