# Unified Cedar schema & policies

The **single source of truth** for authorization types across both tiers. The
edge tier (authproxy PEP) and the domain tier (module SDK PEP) validate their
policies against the one `schema.cedarschema` here, in one CI step — which is
what makes split-authorization drift a validation failure rather
than a review miss.

```
schema.cedarschema          # the one shared schema (§5.1)
policies/edge.cedar         # Tier 1 — coarse "can this identity reach this op?" (§5.2)
policies/domain.cedar       # Tier 2 — per-op permits + cross-tenant guard (§5.3)
tests/entities.json         # entity fixtures (two tenants, cross-tenant payment)
tests/ctx_*.json            # request contexts
validate.sh                 # the CI gate (validate + cross-tenant isolation)
```

Both `edge.cedar` and `domain.cedar` here are the **reference** sets the schema
is validated against; in production each module *generates* its own from
`x-labs64-auth` (see below).

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

## Schema (aligned with auth-policy.json, 2026-07-13)

OpenAPI (`x-labs64-auth`) is the single source of truth for enforcement, and it
expresses only per-operation reachability — `public` / `tenant` / `scopes`
(+ an optional `resource` type). The schema carries exactly that and nothing
more; **both** policy tiers are generated from it.

- **Edge tier model:** `entity ApiOperation` + `action "invoke"`. Edge policies
  are *generated* from each module's `x-labs64-auth` by the commons
  `OpenApiAuthPreprocessorCli --cedar-output` — one `permit` per operation,
  resource id `"<module>::<operationId>"` (`"static::<prefix>"` reserved),
  conditions built from `context.scopes` / `context has tenant`. The authproxy
  PEP evaluates them in-process (cedarpy).
- **Domain tier model:** actions mirror the modules' real operationIds
  (`getPayment`, `payPayment`, `checkoutPurchaseOrder`, `publishEvent`, …)
  against tenant-only typed resources (`Payment`, `PurchaseOrder`,
  `AuditEvent`). Domain policies are *generated* from the operations that
  declare `x-labs64-auth.resource` (`--cedar-domain-output`): one `permit` per
  operation (same scope/tenant conditions) + one structural cross-tenant
  `forbid` guard per resource type. The module `@Authorize` PEP evaluates them
  in-process (cedar-java).
- **Deliberately not modelled:** workflow status, ownership, MFA — OpenAPI
  cannot express them, so they stay in the service layer (and later Postgres
  RLS), never in generated Cedar.
- `Ctx.tenant` is **optional** — tenant-less requests omit it (matches
  `cedar-request-vectors.json`).

## Cross-language request contract

`../test-vectors/cedar-request-vectors.json` pins how an `AuthContext` serializes
into a Cedar request (principal + context), so the Java and Python `@Authorize`
PEPs build identical requests from the same trusted headers —
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
