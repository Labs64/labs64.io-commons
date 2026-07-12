#!/usr/bin/env bash
# Unified Cedar CI gate (RFC-05 P1). Two checks, both must pass:
#
#   1. VALIDATE   every edge + domain policy against the ONE shared schema
#                 (§5.1). This is the single-schema, no-drift gate (P5/F3): a
#                 policy that references an entity/attr/action the schema does
#                 not define fails here, before merge.
#   2. ISOLATION  prove the tenant guard holds: a principal with a valid scope
#                 in tenant A must be DENIED on a resource in tenant B, even
#                 though the coarse (scope-only) edge permit would grant it.
#                 This is the cross-tenant non-access invariant (P6/F4/F8).
#
# Requires the `cedar` CLI (cedar-policy-cli). The isolation check here is
# fixture-based (adversarial concrete requests). The stronger *symbolic* proof —
# `cedar symcc` / "cedar analyze" over ALL inputs — needs a cedar CLI built with
# the `analyze` feature (+ SMT solver); wiring that is the documented upgrade
# (see README). The fixtures below are the enforced floor until then.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA="$DIR/schema.cedarschema"
ALL_POLICIES="$DIR/policies/edge.cedar $DIR/policies/domain.cedar"
COMBINED="$(mktemp)"
trap 'rm -f "$COMBINED"' EXIT
cat $ALL_POLICIES > "$COMBINED"

fail() { echo "FAIL: $*" >&2; exit 1; }

echo "== 1. validate edge + domain against the shared schema"
for p in "$DIR/policies/edge.cedar" "$DIR/policies/domain.cedar"; do
  cedar validate --schema "$SCHEMA" --schema-format cedar --policies "$p" \
    >/dev/null 2>"$COMBINED.err" || { cat "$COMBINED.err"; fail "validation: $p"; }
  echo "   ok: $(basename "$p")"
done

# Assert a request's decision. $1=label $2=expected(Allow|Deny) rest=cedar args
assert_decision() {
  local label="$1" expected="$2"; shift 2
  local out
  out="$(cedar authorize --schema "$SCHEMA" --schema-format cedar \
          --policies "$COMBINED" --entities "$DIR/tests/entities.json" \
          "$@" 2>/dev/null || true)"
  case "$out" in
    *"$expected"*) echo "   ok: $label -> $expected";;
    *) fail "$label: expected $expected, got: $out";;
  esac
}

echo "== 2. cross-tenant isolation gate (combined edge + domain policy set)"
# Positive control: same tenant, READY payment, payment:pay scope -> ALLOW.
assert_decision "alice pays same-tenant READY payment (t_100)" "ALLOW" \
  --principal 'Labs64IO::User::"alice"' \
  --action 'Labs64IO::Action::"payPayment"' \
  --resource 'Labs64IO::Payment::"pay_1"' \
  --context "$DIR/tests/ctx_t100.json"

# The invariant: mallory has payment:pay (the scope permit would fire) but is in
# a DIFFERENT tenant than the payment -> the domain forbid guard MUST deny.
assert_decision "mallory pays cross-tenant payment (t_200 -> t_100)" "DENY" \
  --principal 'Labs64IO::User::"mallory"' \
  --action 'Labs64IO::Action::"payPayment"' \
  --resource 'Labs64IO::Payment::"pay_1"' \
  --context "$DIR/tests/ctx_t200.json"

echo "== cedar gate: PASS"
