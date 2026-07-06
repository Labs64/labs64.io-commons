# Labs64.IO :: Commons

# print available recipes
default:
    @just --list

# build + test both libraries
build: java python

# build + test the Java starter
java:
    cd auth-context-java && mvn -B -ntp test

# install the Java starter into the local Maven repository
install-java:
    cd auth-context-java && mvn -B -ntp -DskipTests install

# create the Python venv with dev dependencies
python-venv:
    cd auth-context-python && python3 -m venv .venv && .venv/bin/pip install -q -e ".[dev]"

# test the Python library (creates the venv when missing)
python:
    cd auth-context-python && test -d .venv || just python-venv
    cd auth-context-python && .venv/bin/pytest -q
