// Tier 2 — DOMAIN reference policy set (RFC-05 §5.3, aligned 2026-07-13).
//
// In production these are GENERATED from each module's x-labs64-auth by the
// commons OpenApiAuthPreprocessor (--cedar-domain-output) and loaded by the
// module @Authorize PEP; this file is the reference set the shared schema is
// validated against and the cross-tenant isolation gate runs on (validate.sh).
//
// Two things per module, both derivable from OpenAPI (the single source of
// truth): one permit per domain-authorized operation (action = operationId,
// conditioned on tenant/scopes exactly like the edge tier), and one structural
// tenant guard per resource type. `forbid` overrides `permit`, so a principal
// acting across tenants is denied even with a valid scope (F4/F8/P6).
// Fine-grained status/ownership rules are NOT here — OpenAPI cannot express
// them; they stay in the service layer (and later Postgres RLS).

// ── payment-gateway (resource: Payment) ──
@id("payment-gateway::getPayment::domain")
permit(
  principal,
  action == Labs64IO::Action::"getPayment",
  resource
) when { (context has tenant) && (context.scopes.contains("payment:read")) };

@id("payment-gateway::payPayment::domain")
permit(
  principal,
  action == Labs64IO::Action::"payPayment",
  resource
) when { (context has tenant) && (context.scopes.contains("payment:pay")) };

@id("payment-gateway::closePayment::domain")
permit(
  principal,
  action == Labs64IO::Action::"closePayment",
  resource
) when { (context has tenant) && (context.scopes.contains("payment:write")) };

@id("payment-gateway::tenant-guard::Payment")
forbid(
  principal,
  action,
  resource is Labs64IO::Payment
) when { resource has tenant && !(principal in resource.tenant) };

// ── checkout (resource: PurchaseOrder) ──
@id("checkout::getPurchaseOrder::domain")
permit(
  principal,
  action == Labs64IO::Action::"getPurchaseOrder",
  resource
) when { (context has tenant) && (context.scopes.contains("purchase-order:read")) };

@id("checkout::updatePurchaseOrder::domain")
permit(
  principal,
  action == Labs64IO::Action::"updatePurchaseOrder",
  resource
) when { (context has tenant) && (context.scopes.contains("purchase-order:write")) };

@id("checkout::checkoutPurchaseOrder::domain")
permit(
  principal,
  action == Labs64IO::Action::"checkoutPurchaseOrder",
  resource
) when { (context has tenant) && (context.scopes.contains("purchase-order:checkout")) };

@id("checkout::tenant-guard::PurchaseOrder")
forbid(
  principal,
  action,
  resource is Labs64IO::PurchaseOrder
) when { resource has tenant && !(principal in resource.tenant) };

// ── auditflow (resource: AuditEvent; tenant optional — platform events) ──
@id("auditflow::publishEvent::domain")
permit(
  principal,
  action == Labs64IO::Action::"publishEvent",
  resource
) when { (context.scopes.contains("audit-event:write")) };

@id("auditflow::tenant-guard::AuditEvent")
forbid(
  principal,
  action,
  resource is Labs64IO::AuditEvent
) when { resource has tenant && !(principal in resource.tenant) };
