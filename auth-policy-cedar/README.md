# Unified Cedar schema & policies (RFC-05 P1, extended in P2/P3)

The **single source of truth** for authorization types across both tiers. The
edge tier (authproxy PEP) and the domain tier (module SDK PEP) validate their
policies against the one `schema.cedarschema` here, in one CI step — which is
what makes split-authorization drift (RFC-05 **F3**) a validation failure rather
than a review miss.

```
schema.cedarschema          # the one shared schema (§5.1)
policies/edge.cedar         # Tier 1 — coarse "can this identity reach this op?" (§5.2)
policies/domain.cedar       # Tier 2 — owner/tenant/workflow rules (§5.3)
tests/entities.json         # entity fixtures (two tenants, cross-tenant payment)
tests/ctx_*.json            # request contexts
validate.sh                 # the CI gate (validate + cross-tenant isolation)
```

## Run the gate

```bash
cargo install cedar-policy-cli   # provides the `cedar` CLI
./validate.sh                    # or: just cedar   (from repo root)
```

It performs two checks, both blocking:

1. **Validate** — every edge + domain policy against the shared schema (F3/P5).
2. **Cross-tenant isolation** — an adversarial request where a principal *with a
   valid scope* in tenant B is DENIED on a resource in tenant A. The scope-only
   permit would grant it; the typed domain guards (`forbid(..., resource is
   <Type>) unless { principal in resource.tenant }`) override and deny
   (F4/F8/P6). The gate also keeps a positive control (same-tenant, READY
   payment → allow) so it can't pass vacuously.

## Schema v2 (P2/P3)

- **Edge tier model:** `entity ApiOperation` + `action "invoke"`. Edge policies
  are *generated* from each module's `x-labs64-auth` by the commons
  `OpenApiAuthPreprocessorCli --cedar-output` — one `permit` per operation,
  resource id `"<module>::<operationId>"` (`"static::<prefix>"` reserved),
  conditions built from `context.scopes` / `context has tenant`. They ship in
  the signed P0 bundle; the authproxy PEP evaluates them in-process (cedarpy).
- **Domain actions mirror real operationIds** (`payPayment`,
  `checkoutPurchaseOrder`, `publishAudit`, …) instead of the RFC's illustrative
  set; resource types match the modules' JPA entities (`Payment`,
  `PurchaseOrder`, `AuditEvent`, `PortalAccount`).
- `Ctx.tenant` is **optional** — tenant-less requests omit it (matches
  `cedar-request-vectors.json`).

## Cross-language request contract

`../test-vectors/cedar-request-vectors.json` pins how an `AuthContext` serializes
into a Cedar request (principal + context), so the Java and Python `@Authorize`
PEPs (RFC-05 P2/P3) build identical requests from the same trusted headers —
the Cedar-layer analogue of `auth-context-vectors.json`.

## Relationship to the running system

These policies are the **reference set the schema is validated against**. In P2
the edge set is *generated* from each module's OpenAPI `x-labs64-auth` (the three
patterns in `edge.cedar` are the 1:1 translation targets) and embedded in the
authproxy PEP; in P3/P4 the domain set is authored per module and enforced by the
SDK. Today (P0 done, P1 here) they gate the schema and prove the isolation
invariant in CI ahead of those enforcement phases.

## Upgrade path — symbolic proof

`validate.sh`'s isolation check is **fixture-based** (concrete adversarial
requests). The stronger *symbolic* proof — `cedar symcc` / "cedar analyze" over
**all** possible inputs, the true F8 machine-checked invariant — needs a cedar
CLI built with the `analyze` experimental feature and an SMT solver (cvc5).
Wiring that into CI is the documented next step; until then the enumerated
fixtures are the enforced floor.
