// Tier 2 — DOMAIN policy set (RFC-05 §5.3). Fine-grained resource/owner/tenant/
// workflow rules, enforced in-process by each module's @Authorize PEP.

// ── Tenant isolation baseline (F4/F8) ──
// Structural guard: no principal may act on a resource outside its tenant. The
// cross-tenant isolation gate (tests/) proves this holds against the whole set;
// `forbid` overrides any `permit`, so no policy combination can grant across
// tenants. `principal in resource` holds because both are `in [Tenant]` and the
// adversarial fixtures put them in different tenants.
forbid(principal, action, resource)
unless { principal in resource.tenant };

// ── Owner-only refund (replaces payment-gateway's hard-coded if — F6) ──
permit(
  principal,
  action == Labs64IO::Action::"refundPayment",
  resource
) when {
  principal == resource.owner
  && resource.status == "captured"
  && principal.scopes.contains("payment:refund")
  && principal.mfaVerified
};

// ── AuditFlow PII gate ──
permit(
  principal == Labs64IO::Service::"auditflow-publisher",
  action == Labs64IO::Action::"publishAudit",
  resource
) when { !resource.pii || (context has redacted && context.redacted) };

// ── Service-to-service confused-deputy guard ──
// checkout-be may capture a payment only on behalf of the payment's owner.
permit(
  principal == Labs64IO::Service::"checkout-be",
  action == Labs64IO::Action::"capturePayment",
  resource
) when {
  context has onBehalfOf
  && context.onBehalfOf == resource.owner
  && principal.scopes.contains("payment:capture")
};

// ── Customer-portal tier gating (FeBAC foundation) ──
permit(
  principal,
  action == Labs64IO::Action::"manageAccount",
  resource
) when { resource.tier == "free" || principal.scopes.contains("portal:premium") };
