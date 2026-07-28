# Labs64.IO Commons development container

This configuration follows the open Dev Container Specification and opens the
complete Commons repository with Java 25, Maven, Python 3.13, `just`, and an
isolated Docker daemon.

Java libraries compile to Java 17 bytecode as configured by their Maven projects;
using JDK 25 here also matches current ecosystem service development.

## Shared ecosystem caches

The following volumes are shared with other Labs64.IO dev containers:

- `labs64io-maven-repository` at `/home/l64user/.m2/repository`
- `labs64io-vscode-extensions` at `/home/l64user/.vscode-server/extensions`

Running `just install-java`, `just install-openapi`, or
`just install-queryplan` makes the local SNAPSHOT immediately available to
consumer projects such as `labs64.io-payment-gateway`.

VS Code server data and AI CLI data remain project-scoped.

## Docker

The Cerbos validation scripts use nested `docker run` commands with paths from
inside the development workspace. Docker-in-Docker is used so those paths resolve
consistently on Linux, macOS, and Windows hosts. The development service is
therefore privileged and its Docker data is kept in a project-scoped volume.

## Common commands

```shell
just build
just test
just install-java
just install-openapi
just install-queryplan
just python
just generate-cerbos
just cerbos
```
