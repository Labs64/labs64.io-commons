#!/usr/bin/env bash
# RFC-07: regenerate the reference Cerbos policy set from reference-openapi.yaml
# via the commons OpenApiAuthPreprocessorCli. The committed output
# (policies/*.yaml + policies/_schemas/*.json) is what validate.sh compiles and
# truth-tests — OpenAPI stays the single source of truth (F3, no hand-editing).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMONS="$DIR/../auth-context-java"

cd "$COMMONS"
mvn -q package -DskipTests
CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout | tail -1)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
# Clean stale generated policies so removed operations don't linger.
rm -rf "$DIR/policies"
java -cp "$CP" io.labs64.authcontext.openapi.OpenApiAuthPreprocessorCli \
  --input "$DIR/reference-openapi.yaml" \
  --openapi-output "$TMP/openapi.yaml" \
  --cerbos-output "$DIR" \
  --module payment-gateway
echo "== generated Cerbos policies under $DIR/policies"
