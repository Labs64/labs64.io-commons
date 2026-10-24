# Labs64.IO :: Commons

**The shared libraries every Labs64.IO service is built on.**
*Part of the [Labs64.IO Ecosystem](https://labs64.io): the Java and Python libraries and the build parent behind every module.*

[![CI](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml/badge.svg)](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml)
[![Maven Central (auth-context)](https://img.shields.io/maven-central/v/io.labs64/auth-context-spring-boot-starter?label=Maven%20Central)](https://central.sonatype.com/artifact/io.labs64/auth-context-spring-boot-starter)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![📖 Documentation](https://img.shields.io/badge/📖-Documentation-AB6543.svg)](https://labs64.io/docs/development/shared-libraries.html)

<p align="center"><img src="docs/assets/commons-hero.svg" alt="Commons: The same payments:read rule written three ways, as a Java annotation, a Python decorator and a generated Cerbos policy, each returning the same ALLOW from one set of shared test vectors." width="880"></p>

[Why Commons?](#why-commons) · [Documentation](https://labs64.io/docs/development/shared-libraries.html) · [Build and test](#build-and-test) · [Ecosystem](#the-labs64io-ecosystem) · [labs64.io](https://labs64.io)

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

## Documentation

Install, configure and operate Commons in the **[Commons documentation](https://labs64.io/docs/development/shared-libraries.html)**.

[What is in the box](https://labs64.io/docs/development/shared-libraries.html#what-is-in-the-box) · [The trusted header contract](https://labs64.io/docs/development/shared-libraries.html#the-trusted-header-contract) · [Use the libraries in a Java service](https://labs64.io/docs/development/shared-libraries.html#use-the-libraries-in-a-java-service)

New to Labs64.IO? The [Quickstart](https://labs64.io/docs/getting-started/quickstart.html) puts the platform on any Kubernetes cluster in about five minutes.

## Build and test

```bash
just build                                        # build and test everything
just java                                         # Java tests (mvn verify)
just python-venv && cd auth-context-python && .venv/bin/pytest   # the Python library
just cerbos                                       # policy and equivalence gate (via Docker)
```

Needs JDK 25, Maven 3.6.3+ and Python 3.13. [`docs/reference.md`](docs/reference.md) covers the release process. Working on the code with an AI agent? [`AGENTS.md`](AGENTS.md) describes the layout, release rules and checks.

## The Labs64.IO Ecosystem

This repository is part of the [Labs64.IO Ecosystem](https://labs64.io): open-source building blocks for secure commerce platforms. Every module is documented at [labs64.io/docs](https://labs64.io/docs/).

| Module | What it does |
|---|---|
| [**AuditFlow**](https://github.com/Labs64/labs64.io-auditflow) | Publish once, route anywhere, lose nothing |
| [**Auth Gateway**](https://github.com/Labs64/labs64.io-authproxy) | One authenticated edge for every module |
| [**Payment Gateway**](https://github.com/Labs64/labs64.io-payment-gateway) | One API across multiple payment providers |
| [**Checkout**](https://github.com/Labs64/labs64.io-checkout) | Whitelabel checkout and order workflow |
| [**Customer Portal**](https://github.com/Labs64/labs64.io-customer-portal) | Self-service portal for your customers |

Built on [Commons](https://github.com/Labs64/labs64.io-commons), deployed with [Helm Charts](https://github.com/Labs64/labs64.io-helm-charts), developed in the [Workspace](https://github.com/Labs64/labs64.io-workspace), verified by [Tests](https://github.com/Labs64/labs64.io-tests) and documented in [Docs](https://github.com/Labs64/labs64.io-docs). Also from Labs64: [NetLicensing](https://netlicensing.io) for licensing-as-a-service and [GuideChimp](https://www.labs64.com/guidechimp/) for guided product tours.

## Contributing

We would love your help. A change to a shared library reaches every service, so tests (and, for the header contract, the shared test vectors) are not optional. Fork the repository, make your change on a branch, run the checks described in [`AGENTS.md`](AGENTS.md) and open a pull request. Not sure whether an idea fits? Open an issue first.

## License

Open source under the [Apache License 2.0](LICENSE): use it, change it and ship it commercially.

---

<p align="center">
  <a href="https://labs64.io"><img alt="Labs64.IO" src="https://labs64.io/docs/assets/images/labs64-logo.png" width="110"></a><br>
  <sub>Made by the <a href="https://labs64.io">Labs64</a> team and community.</sub>
</p>
