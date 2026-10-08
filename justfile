# Labs64.IO :: Commons

# List available recipes
default:
    @just --list

# Build and test the Java libraries, install them locally, then test the Python library
build: java install-java python

# Test the Java and Python libraries
test: java python

# Build and test all Java libraries in dependency order
java:
    mvn -B -ntp clean verify

# Install the parent POM and all Java libraries into the local Maven repository as 0.0.0-SNAPSHOT
install-java:
    mvn -B -ntp -DskipTests clean install

# Build and test one Java library together with the modules it depends on
java-module module:
    mvn -B -ntp -pl {{module}} -am clean verify

# Create the Python virtualenv and install the dev dependencies
python-venv:
    cd auth-context-python && python3 -m venv .venv && .venv/bin/pip install -q -e ".[dev]"

# Test the Python library, creating the virtualenv first if it is missing
python:
    cd auth-context-python && test -d .venv || just python-venv
    cd auth-context-python && .venv/bin/pytest -q

# Regenerate the reference Cerbos policies from reference-openapi.yaml
generate-cerbos:
    ./auth-policy-cerbos/generate.sh

# Compile the Cerbos policies and check their decisions against the truth table
cerbos:
    ./auth-policy-cerbos/validate.sh
