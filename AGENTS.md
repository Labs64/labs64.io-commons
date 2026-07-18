# AGENTS.md — Labs64.IO Commons

Guidance for AI agents working in this repository. Read this before making changes.

## What this is

Shared cross-service libraries for the Labs64.IO Ecosystem. Includes: the auth-context libraries (Java + Python), which parse, enforce and propagate the trusted gateway header contract, shared Spring Boot starters, plus the RFC-07 authorization assets: the engine-neutral `@Authorize` domain PEP in the Java starter (`io.labs64.authcontext.authorization`) with a Cerbos PDP client (`io.labs64.authcontext.cerbos`), feature-flagged via `labs64.auth.authz.*` (decisions delegated to the central Cerbos PDP over gRPC), the Cerbos-policy generator backend in `OpenApiAuthPreprocessor` (`--cerbos-output`/`--routes-output`), the decision-equivalence gate in `auth-policy-cerbos/`, and the `authz-queryplan-jpa` Data-PEP translator.

## Critical guardrails

1. **The two implementations must stay behaviorally identical.** Any behavior change MUST update `test-vectors/auth-context-vectors.json` and BOTH implementations in the same commit. The vectors are the contract; code follows.
2. **The header contract is owned by the auth gateway** (`labs64.io-authproxy/traefik-authproxy/`). Do not add, rename or reinterpret `X-Auth-*` headers here without an RFC change.
3. **No Spring Security dependency in the Java starter.** The whole point is header-trust without an in-app security stack.
4. **No mandatory runtime dependencies in the Python package.** FastAPI/httpx integrations import lazily; the core must stay dependency-free.
5. **Fail closed.** Non-public paths without a valid user identity return 401. Never weaken this default.
6. **Value sanitization pattern `^[a-zA-Z0-9_.:-]+$`** must match the ACS (traefik-authproxy) exactly.
7. **OpenAPI is the single source of policy.** Cerbos policies are GENERATED from each module's `x-labs64-auth` by `OpenApiAuthPreprocessor` — never hand-edit generated policy YAML. Decision equivalence (edge + domain, incl. the cross-tenant isolation invariant) is proven by `auth-policy-cerbos/validate.sh` (`just cerbos`); keep it green.

## Layout

| Path | What |
|---|---|
| `auth-context-java/` | `io.labs64:auth-context-spring-boot-starter` (auth-context + engine-neutral `@Authorize` PEP + Cerbos client) |
| `auth-context-python/` | `auth-context-python` (package `auth_context`) |
| `authz-queryplan-jpa/` | `io.labs64:authz-queryplan-jpa` — Cerbos query-plan → JPA Specification (Data PEP) |
| `auth-policy-cerbos/` | Reference OpenAPI → generated Cerbos policies + truth-table `validate.sh` CI gate |
| `test-vectors/` | Canonical cross-language behavior vectors (header contract) |
| `openapi-spring-boot-starter/` | `io.labs64:openapi-spring-boot-starter` |

## Commands

| Task | Command |
|---|---|
| Build + test everything | `just build` |
| Java tests | `cd auth-context-java && mvn test` |
| OpenAPI starter tests | `cd openapi-spring-boot-starter && mvn test` |
| Python tests | `cd auth-context-python && .venv/bin/pytest` (after `just python-venv`) |
| Install Java lib locally | `just install-java` |
| Cerbos policy + equivalence gate | `just cerbos` (runs `auth-policy-cerbos/validate.sh` via Docker) |

## Conventions

- Java 17 bytecode target (consumers run 17+), Spring Boot 4.x parent.
- Python ≥ 3.13, no runtime deps, `pyproject.toml` packaging.
- Versioning: bump both libraries together; consumers consume from Labs64 Nexus and git+https.
