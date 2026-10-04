# Labs64.IO :: Commons

# print available recipes
default:
    @just --list

# build + test all libraries
build: java install-java python

# test all libraries
test: java python

# build + test every Java library (one reactor: labs64io-parent, then the libraries in dependency order)
java:
    mvn -B -ntp clean verify

# install labs64io-parent and every Java library into the local Maven repository (as 0.0.0-SNAPSHOT)
install-java:
    mvn -B -ntp -DskipTests clean install

# build + test one Java library and whatever it depends on, e.g. `just java-module authz-queryplan-jpa`
java-module module:
    mvn -B -ntp -pl {{module}} -am clean verify

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
