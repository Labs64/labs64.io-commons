<p align="center"><img src="https://raw.githubusercontent.com/Labs64/.github/master/assets/labs64-io-ecosystem.png" alt="Labs64.IO Ecosystem"></p>

# Labs64.IO :: Commons

Shared, cross-service libraries for the [Labs64.IO Ecosystem](https://labs64.io).

## Libraries

| Library | Language | Purpose |
|---|---|---|
| [`auth-context-spring-boot-starter`](auth-context-java/) | Java 17+ / Spring Boot 4 | Trusted gateway auth-context (`X-Auth-*`) parsing, fail-closed enforcement, `@RequireScopes`, outbound propagation, `@WithAuthContext` test support |
| [`auth-context-python`](auth-context-python/) | Python 3.13+ | Mirrored `AuthContext`, ASGI middleware, FastAPI dependencies, httpx propagation hook, pytest fixture |

Both implementations obey the trusted header contract (`X-Auth-User`, `X-Auth-Scopes`, `X-Auth-Tenant`, `X-Request-ID`) and are pinned to identical behavior by the shared vectors in [`test-vectors/`](test-vectors/).

## Consuming

**Java:**

Published to Labs64 Nexus. Ensure your `settings.xml` or CI environment is configured to resolve from the Labs64 Maven repositories.

```xml
<dependency>
    <groupId>io.labs64</groupId>
    <artifactId>auth-context-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Python:**

```bash
pip install "auth-context-python @ git+https://github.com/Labs64/labs64.io-commons.git@COMMIT_OR_TAG#subdirectory=auth-context-python"
```

## Development

```bash
just build   # build + test both libraries
just java    # Java only
just python  # Python only
```

Local Java consumption: `just install-java` installs `0.1.0` into the local Maven repository.

## OpenAPI Auth Policy Generation

Java services can mark OpenAPI operations with `x-labs64-auth` and generate two derived artifacts from the same source:

- an OpenAPI file enriched with `x-operation-extra-annotation` for OpenAPI Generator templates
- a route policy JSON for the Traefik auth gateway

Example:

```yaml
paths:
  /payments:
    get:
      operationId: listPayments
      x-labs64-auth:
        tenant: true
        scopes: [payment:read]
  /health:
    get:
      operationId: health
      x-labs64-auth:
        public: true
```

Endpoints without `tenant` or `scopes` are treated as public. Protected endpoints generate annotations such as `@RequireTenant` and `@RequireScopes`; public endpoints generate `@PublicEndpoint`.

CLI:

```bash
cd auth-context-java
mvn -q exec:java \
  -Dexec.mainClass=io.labs64.authcontext.openapi.OpenApiAuthPreprocessorCli \
  -Dexec.args="--input openapi.yaml --openapi-output target/generated/openapi.yaml --policy-output target/generated/auth-policy.json"
```

## Related

- Centralized authentication & authorization gateway (`labs64.io-authproxy/traefik-authproxy`)
- [`labs64.io-authproxy`](https://github.com/Labs64/labs64.io-authproxy) — traefik-authproxy, the header contract's producer
