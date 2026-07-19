# Labs64.IO :: Commons

# print available recipes
default:
    @just --list

# build + test all libraries
build: java install-java openapi queryplan python

# build + test the Java starter
java:
    cd auth-context-java && mvn -B -ntp clean test

# install the Java starter into the local Maven repository
install-java:
    cd auth-context-java && mvn -B -ntp -DskipTests clean install

# build + test the OpenAPI starter
openapi:
    cd openapi-spring-boot-starter && mvn -B -ntp clean test

# install the OpenAPI starter into the local Maven repository
install-openapi:
    cd openapi-spring-boot-starter && mvn -B -ntp -DskipTests clean install

# build + test the authz query-plan → JPA translator
queryplan:
    cd authz-queryplan-jpa && mvn -B -ntp clean test

# install the query-plan translator into the local Maven repository
install-queryplan:
    cd authz-queryplan-jpa && mvn -B -ntp -DskipTests clean install

# create the Python venv with dev dependencies
python-venv:
    cd auth-context-python && python3 -m venv .venv && .venv/bin/pip install -q -e ".[dev]"

# test the Python library (creates the venv when missing)
python:
    cd auth-context-python && test -d .venv || just python-venv
    cd auth-context-python && .venv/bin/pytest -q

# regenerate the reference Cerbos policy set from reference-openapi.yaml
generate-cerbos:
    ./auth-policy-cerbos/generate.sh

# Cerbos compile + decision-equivalence truth-table gate (requires Docker)
cerbos:
    ./auth-policy-cerbos/validate.sh
