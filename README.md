# Labs64.IO :: Commons

**The shared libraries every Labs64.IO service is built on.** Commons is the shared plumbing for tenant headers, OpenAPI metadata and authorization, written and tested once so that services do not each re-implement it. It provides Java and Python libraries that parse the trusted `X-Auth-*` headers the same way, a build parent for every Java module, and tooling that generates authorization policies from OpenAPI.

[![CI](https://img.shields.io/github/actions/workflow/status/Labs64/labs64.io-commons/labs64io-ci.yml?label=CI&logo=github)](https://github.com/Labs64/labs64.io-commons/actions/workflows/labs64io-ci.yml)
[![Maven Central (auth-context)](https://img.shields.io/maven-central/v/io.labs64/auth-context-spring-boot-starter?label=Maven%20Central)](https://central.sonatype.com/artifact/io.labs64/auth-context-spring-boot-starter)
[![📖 Documentation](https://img.shields.io/badge/📖-Documentation-AB6543.svg)](https://labs64.io/docs/development/contributing.html)
[![Labs64.IO Ecosystem](https://img.shields.io/badge/Labs64.IO-Ecosystem-E14817.svg?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9Ii01IC01IDE1NSAxNTUiPjxwYXRoIGZpbGw9IiNmZmYiIGQ9Ik0zIDNoMTM5djEzOUgzWk02OSAzdjEzOWg3VjNaTTQzIDQzdjU5aDI2VjQzWk03NiA0M3Y1OWgyNlY0M1oiLz48cGF0aCBmaWxsPSIjYmJiIiBkPSJNNDkgNDloNDd2NDdINDlaIi8%2BPC9zdmc%2B)](https://labs64.io)

<p align="center"><img src="assets/commons-hero.svg" alt="Commons: The same payments:read rule written three ways, as a Java annotation, a Python decorator and a generated Cerbos policy, each returning the same ALLOW from one set of shared test vectors." width="880"></p>

## The Labs64.IO Ecosystem

Commons is part of the [Labs64.IO Ecosystem](https://labs64.io). Its documentation is at [labs64.io/docs/development/shared-libraries](https://labs64.io/docs/development/shared-libraries.html); [all modules are listed in the documentation](https://labs64.io/docs/services/).

---

<p align="center">
  <a href="https://labs64.io"><img alt="Labs64.IO" src="https://labs64.io/docs/assets/images/labs64-logo.png" width="110"></a><br>
  <sub>Made by the <a href="https://labs64.io">Labs64</a> team and community.</sub>
</p>
