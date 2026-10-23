# Labs64.IO :: Commons

**The shared libraries every Labs64.IO service is built on.**
*Part of the [Labs64.IO Ecosystem](https://labs64.io): the Java and Python libraries and the build parent behind every module.*

[![CI](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml/badge.svg)](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml)
[![Maven Central (auth-context)](https://img.shields.io/maven-central/v/io.labs64/auth-context-spring-boot-starter?label=Maven%20Central)](https://central.sonatype.com/artifact/io.labs64/auth-context-spring-boot-starter)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![📖 Documentation](https://img.shields.io/badge/📖-Documentation-AB6543.svg)](https://labs64.io/docs/development/contributing.html)

<p align="center"><img src="docs/assets/commons-hero.svg" alt="Commons: The same payments:read rule written three ways, as a Java annotation, a Python decorator and a generated Cerbos policy, each returning the same ALLOW from one set of shared test vectors." width="880"></p>

[**Get started**](#get-started) · [Features](#features) · [Architecture](#architecture) · [AI agents](#using-commons-with-ai-agents) · [Ecosystem](#the-labs64io-ecosystem) · [Documentation](https://labs64.io/docs/development/contributing.html) · [labs64.io](https://labs64.io)

> [!NOTE]
> Consume Commons through the `labs64io-parent` POM: one version pins the Spring Boot line, the shared dependency versions **and** every library, so libraries are declared without a version.

## Why Commons?

Services that each re-implement tenant headers, OpenAPI metadata and authorization plumbing drift apart, and the differences are exactly where bugs and security holes live. Commons is that plumbing, written and tested once.

1. **One trust contract.** Every service parses the same `X-Auth-*` headers the same way, in Java and in Python, pinned to identical behaviour by shared test vectors.
2. **One build parent.** `labs64io-parent` carries the Spring Boot line, security overrides and release rules for every Java module.
3. **Authorization that follows the contract.** Policies and routes are generated from OpenAPI; the Data PEP turns a Cerbos query plan into a JPA `Specification`.

**A good fit if**

- You build Java (Spring Boot) or Python services that sit behind the Labs64.IO API Gateway.
- You want your service to parse the trusted identity headers exactly as the rest of the platform does.
- You generate authorization policies from your OpenAPI contract.

**Probably not what you need if**

- Your services are not behind the Labs64.IO gateway: the auth-context libraries assume its header contract.
- You need a general-purpose utility library: these are purpose-built platform libraries.

## Get started

Four ways in. Pick the one that matches what you have; each is complete on its own.

```
 1  install.sh             2  Maven · pip            3  Docker                 4  Source
┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
│ The platform built    │ │ Add the parent POM    │ │ Build and test all    │ │ Build, change a       │
│ on these libraries,   │ │ and a starter to      │ │ libraries in a        │ │ library, run the      │
│ running on a cluster  │ │ your Java service,    │ │ Maven container       │ │ tests, contribute     │
│ with a smoke test     │ │ or pip install        │ │                       │ │ back                  │
├───────────────────────┤ ├───────────────────────┤ ├───────────────────────┤ ├───────────────────────┤
│ needs  a cluster,     │ │ needs  JDK 17+, Maven │ │ needs  Docker         │ │ needs  JDK 25, Maven, │
│        kubectl, helm  │ │        or Python 3.13 │ │                       │ │        Python 3.13    │
│ time   ~5 minutes     │ │ time   ~5 minutes     │ │ time   a few minutes  │ │ time   first build    │
└───────────────────────┘ └───────────────────────┘ └───────────────────────┘ └───────────────────────┘
 see them at work          use it in a service       verify a build            work on the code
```

Not sure? Start with **2** to add a library to your service, or **1** to see the platform that uses all of them.

### 1. `install.sh`: the quick ecosystem start

Commons is consumed, not installed, so the quickest way to see it at work is the platform built on it. One script installs the gateway, AuditFlow and Payment Gateway on any Kubernetes cluster, then proves it with a real request.

```bash
# No cluster yet? A throwaway one needs only Docker and k3d:
k3d cluster create labs64io-demo -p "8000:80@loadbalancer" --k3s-arg "--disable=traefik@server:0"

curl -fsSL https://raw.githubusercontent.com/Labs64/labs64.io-helm-charts/master/install.sh -o install.sh
bash install.sh
```

Press Enter three times to take the defaults. A few minutes later you should see:

```text
  PASS  Obtained a demo access token from mock-oidc
  PASS  Published a demo audit event (HTTP 200) — first good request succeeded

 Labs64.IO is ready.
   Base URL:  http://localhost:8000
   API docs:  http://localhost:8000/swagger-ui/
```

Every service in that stack inherits `labs64io-parent` and parses the trusted headers with the auth-context starter. The same script later runs `status`, `stop`, `start` and `uninstall`. This is an evaluation install with a demo identity provider: keep it on a local or private cluster. Details: [Quickstart](https://labs64.io/docs/getting-started/quickstart.html) · [`install.sh`](https://github.com/Labs64/labs64.io-helm-charts/blob/master/install.sh).

### 2. Maven and pip: use it in your service

Stable releases are published to Maven Central under `io.labs64` (pick the version shown by the badge above); no repository configuration is needed. A service inherits `labs64io-parent`; that one version pins the Spring Boot line, the shared third-party versions **and** every commons library:

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
```

Pin a **released** version; `0.0.0-SNAPSHOT` is what `master` builds as, and a release build refuses it. For Python:

```bash
pip install "auth-context-python @ git+https://github.com/Labs64/labs64.io-commons.git@COMMIT_OR_TAG#subdirectory=auth-context-python"
```

### 3. Docker: on your machine

Only Docker is needed. Build every library inside a Maven container, with no JDK on your machine:

```bash
git clone https://github.com/Labs64/labs64.io-commons.git && cd labs64.io-commons
docker run --rm -v "$PWD":/src -w /src maven:3.9-eclipse-temurin-25 \
  mvn -B -DskipTests package
```

Drop `-DskipTests` to run the Java tests. The Python library has its own tests (`pytest` in `auth-context-python`).

### 4. Source: build and change it

Needs JDK 25 and Maven 3.6.3+ (Python 3.13 for the Python library):

```bash
git clone https://github.com/Labs64/labs64.io-commons.git && cd labs64.io-commons
mvn -B verify                                   # build and test every Java library
cd auth-context-python && pip install -e . pytest && pytest   # the Python library
```

[`AGENTS.md`](AGENTS.md) describes the layout, the release rules and where each library lives; the [Contributing guide](https://labs64.io/docs/development/contributing.html) covers the workflow.

### Where next?

Everything below is in the [Commons documentation](https://labs64.io/docs/development/contributing.html):

| I want to… | Read |
|---|---|
| Understand the trusted header contract | [Auth context integration](https://github.com/Labs64/labs64.io-commons/blob/master/auth-context-java/INTEGRATION.md) |
| See how a module uses the libraries | [Contributing guide](https://labs64.io/docs/development/contributing.html) |
| Release a library | [`AGENTS.md`](AGENTS.md) |
| Compare all the ways to run Labs64.IO | [Choose a deployment path](https://labs64.io/docs/getting-started/choose-a-deployment-path.html) |

Something not working? [Troubleshooting your first run](https://labs64.io/docs/getting-started/troubleshooting.html) covers the usual suspects, and you are always welcome to [open an issue](https://github.com/Labs64/labs64.io-commons/issues).

## Features

- **`labs64io-parent`**: the build parent of every Java library and service; Spring Boot line, BOM security overrides, shared versions and release rules.
- **`auth-context-*`**: dependency-free model and parser, a Spring Boot starter (fail-closed enforcement, `@RequireScopes`, outbound propagation, test support) and a Python mirror (ASGI middleware, FastAPI dependencies, httpx hook).
- **`openapi-spring-boot-starter`**: shared springdoc runtime servers, bearer security and canonical OpenAPI metadata.
- **`openapi-schema-generator`**: versioned JSON Schema documents from OpenAPI 3.1 `$schema` fields.
- **`authz-queryplan-jpa`**: translates a Cerbos `PlanResources` query plan into a Spring Data JPA `Specification` (Data PEP).
- **`auth-policy-cerbos`**: the policy-generation toolkit that derives the central PDP's policies from OpenAPI.
- **Shared test vectors**: Java and Python implementations are pinned to identical behaviour.

## Architecture

```
  ┌─────────────────────────────────────────────────────────────────────┐
  │ labs64io-parent           Spring Boot line · shared versions · rules │
  └───────┬─────────────────────────────┬───────────────────────────────┘
          │ inherited by                │ pins
  ┌───────▼────────────────────┐  ┌─────▼─────────────────────────────────┐
  │ Every Java service         │  │ auth-context-core                     │
  │ AuditFlow · Payment        │  │ auth-context-spring-boot-starter      │
  │ Gateway · Checkout         │  │ openapi-spring-boot-starter           │
  └───────┬────────────────────┘  │ openapi-schema-generator              │
          │                       │ authz-queryplan-jpa (Data PEP)        │
          │                       └───────────────────────────────────────┘
  ┌───────▼────────────────────┐  ┌───────────────────────────────────────┐
  │ Python services            │  │ auth-policy-cerbos (toolkit)          │
  │ auth-context-python        │  │ OpenAPI ──► Cerbos policies + routes  │
  └────────────────────────────┘  └───────────────────────────────────────┘
          both parse the same X-Auth-* contract, pinned by test-vectors/
```

## What is in the box

| Library | Language | What it gives you |
|---|---|---|
| [`labs64io-parent`](labs64io-parent/) | Maven POM | The build parent: Spring Boot line, security overrides, shared versions and release rules in one version |
| [`auth-context-core`](auth-context-java/auth-context-core/) | Java 17+ | The identity model, holder and trusted-header parser, with no dependencies |
| [`auth-context-spring-boot-starter`](auth-context-java/auth-context-spring-boot-starter/) | Java 17+ / Spring Boot 4 | Fail-closed header enforcement, `@RequireScopes`, outbound propagation and test support |
| [`auth-context-python`](auth-context-python/) | Python 3.13+ | The same contract for ASGI and FastAPI services, with an httpx hook and pytest fixture |
| [`openapi-spring-boot-starter`](openapi-spring-boot-starter/) | Java 17+ / Spring Boot 4 | Shared springdoc servers, bearer security and canonical OpenAPI metadata |
| [`openapi-schema-generator`](openapi-schema-generator/) | Java 17+ | Versioned JSON Schema documents from OpenAPI 3.1 |
| [`authz-queryplan-jpa`](authz-queryplan-jpa/) | Java 17+ / Spring Boot 4 | Turns a Cerbos query plan into a JPA `Specification` (Data PEP) |
| [`auth-policy-cerbos`](auth-policy-cerbos/) | Shell + OpenAPI | The toolkit that derives the central policy engine's policies from your contract |

The Java and Python implementations are pinned to identical behaviour by the shared vectors in [`test-vectors/`](test-vectors/).

## Using Commons with AI agents

Commons is what makes every Labs64.IO service behave the same, which is what lets an agent trust one mental model across the platform.

- **The contract is explicit.** The trusted headers are `X-Auth-User`, `X-Auth-Scopes`, `X-Auth-Tenant` and `X-Request-ID`; values are validated against `^[a-zA-Z0-9_.:-]+$` and scopes are comma-separated.
- **Test vectors are the spec.** [`test-vectors/`](test-vectors/) define expected behaviour for both languages; an agent changing a parser must keep them green.
- **Never re-pin versions in a service.** Change `labs64io-parent` once; every service inherits it.
- **Working on the code with an agent?** [`AGENTS.md`](AGENTS.md) describes the layout, release rules and test commands.

## The Labs64.IO Ecosystem

Commons is one module of the [Labs64.IO Ecosystem](https://labs64.io); [every module is documented here](https://labs64.io/docs/).

| Module | What it does | Link |
|---|---|---|
| **AuditFlow** | Audit-event routing pipeline | [labs64.io-auditflow](https://github.com/Labs64/labs64.io-auditflow) |
| **API Gateway** | Authenticating, policy-enforcing edge for all modules | [labs64.io-authproxy](https://github.com/Labs64/labs64.io-authproxy) |
| **Payment Gateway** | One API over multiple payment providers | [labs64.io-payment-gateway](https://github.com/Labs64/labs64.io-payment-gateway) |
| **Checkout** | Embeddable, brandable checkout | [labs64.io-checkout](https://github.com/Labs64/labs64.io-checkout) |
| **Customer Portal** | Customer self-service UI | [labs64.io-customer-portal](https://github.com/Labs64/labs64.io-customer-portal) |
| **Commons** | Shared Java and Python libraries | [labs64.io-commons](https://github.com/Labs64/labs64.io-commons) |
| **Helm Charts** | Kubernetes deployment for every module | [labs64.io-helm-charts](https://github.com/Labs64/labs64.io-helm-charts) |
| **Workspace** | Dev container and multi-repo tooling | [labs64.io-workspace](https://github.com/Labs64/labs64.io-workspace) |
| **Tests** | Cross-module regression suite | [labs64.io-tests](https://github.com/Labs64/labs64.io-tests) |
| **Docs** | Published documentation | [labs64.io-docs](https://github.com/Labs64/labs64.io-docs) |
| **NetLicensing** | Licensing-as-a-Service: license and entitlement management | [netlicensing.io](https://netlicensing.io) |
| **GuideChimp** | Interactive guided product tours | [labs64.com/guidechimp](https://www.labs64.com/guidechimp/) |

## Contributing

We would love your help. A change to a shared library reaches every service, so tests (and, for the header contract, shared test vectors) are not optional. Fork the repository, make your change on a branch, run the checks described in [`AGENTS.md`](AGENTS.md) and open a pull request. Not sure whether an idea fits? Open an issue first and we will talk it through; that is a perfectly good way to start.

## License

Everything in this repository is open source under the [Apache License 2.0](LICENSE): you can use it, change it and ship it commercially. The core of the *Labs64.IO Ecosystem* is free forever; community modules are Apache-2.0 licensed.

---

<p align="center">
  <a href="https://labs64.io"><img alt="Labs64.IO" src="https://labs64.io/docs/assets/images/labs64-logo.png" width="110"></a><br>
  <sub>Made by the <a href="https://labs64.io">Labs64</a> team and community.</sub>
</p>
