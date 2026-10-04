#!/usr/bin/env bash
# CI gate: cerbos compile (schema/syntax) + truth-table tests
# (decision equivalence incl. the cross-tenant isolation invariant).
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Keep on the Cerbos version the platform deploys (labs64.io-helm-charts charts/authz-pdp
# appVersion); `just check-pins` in labs64.io-workspace verifies it.
# renovate: datasource=docker depName=ghcr.io/cerbos/cerbos
CERBOS_VERSION="0.56.0"
docker run --rm -v "$DIR:/work" "ghcr.io/cerbos/cerbos:${CERBOS_VERSION}" \
  compile --tests=/work/tests /work/policies
echo "== cerbos gate: PASS"
