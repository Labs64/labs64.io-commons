#!/usr/bin/env bash
# CI gate: cerbos compile (schema/syntax) + truth-table tests
# (decision equivalence incl. the cross-tenant isolation invariant).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
docker run --rm -v "$DIR:/work" ghcr.io/cerbos/cerbos:0.51.0 \
  compile --tests=/work/tests /work/policies
echo "== cerbos gate: PASS"
