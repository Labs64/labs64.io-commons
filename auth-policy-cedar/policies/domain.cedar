// Tier 2 — DOMAIN policy set (RFC-05 §5.3). Fine-grained resource/tenant/
// workflow rules, enforced in-process by each module's @Authorize PEP.
//
// This is the validated REFERENCE set; each module ships its own subset under
// src/main/resources/cedar/domain.cedar (git-reviewed, CI-validated against
// THIS schema — same type system, no drift).

// ── Tenant isolation baseline (F4/F8) ──
// Structural guard: no principal may act on a resource outside its tenant. The
// cross-tenant isolation gate (tests/) proves this holds against the whole set;
// `forbid` overrides any `permit`, so no policy combination can grant across
// tenants. One typed forbid per tenant-carrying resource type (a single
// untyped guard would not validate against ApiOperation, which has no tenant);
// AuditEvent (optional tenant) carries its own guard below.
forbid(principal, action, resource is Labs64IO::Payment)
unless { principal in resource.tenant };

forbid(principal, action, resource is Labs64IO::PurchaseOrder)
unless { principal in resource.tenant };

forbid(principal, action, resource is Labs64IO::PortalAccount)
unless { principal in resource.tenant };

// ── payment-gateway (pilot, P3) — replaces service-layer authz assumptions ──
permit(
  principal,
  action == Labs64IO::Action::"readPayment",
  resource
) when { context.scopes.contains("payment:read") };

// pay only a READY payment (workflow state — F6: scope AND state, not OR-scope)
permit(
  principal,
  action == Labs64IO::Action::"payPayment",
  resource
) when { resource.status == "READY" && context.scopes.contains("payment:pay") };

permit(
  principal,
  action == Labs64IO::Action::"closePayment",
  resource
) when { resource.status != "CLOSED" && context.scopes.contains("payment:write") };

// ── checkout (P4 fan-out) ──
permit(
  principal,
  action == Labs64IO::Action::"readPurchaseOrder",
  resource
) when { context.scopes.contains("purchase-order:read") };

permit(
  principal,
  action == Labs64IO::Action::"updatePurchaseOrder",
  resource
) when { context.scopes.contains("purchase-order:write") };

permit(
  principal,
  action == Labs64IO::Action::"checkoutPurchaseOrder",
  resource
) when { context.scopes.contains("purchase-order:checkout") };

// ── auditflow (P4 fan-out) ──
// publish requires the write scope; when the event is tenant-scoped the
// publisher must belong to that tenant (tenant guard for the optional-tenant
// resource type).
permit(
  principal,
  action == Labs64IO::Action::"publishAudit",
  resource
) when { context.scopes.contains("audit-event:write") };

forbid(
  principal,
  action == Labs64IO::Action::"publishAudit",
  resource
) when { resource has tenant && !(principal in resource.tenant) };

// ── Customer-portal tier gating (FeBAC foundation; BE is a stub today) ──
permit(
  principal,
  action == Labs64IO::Action::"manageAccount",
  resource
) when { resource.tier == "free" || principal.scopes.contains("portal:premium") };
