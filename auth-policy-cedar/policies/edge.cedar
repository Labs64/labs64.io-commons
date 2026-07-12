// Tier 1 — EDGE policy set (RFC-05 §5.2). Coarse "can this identity reach this
// operation at all?" — replaces the authproxy's auth-policy.json scope logic.
// In production these are GENERATED from each module's x-labs64-auth (the three
// patterns below are the 1:1 translation targets); this file is the reference
// set the shared schema is validated against.

// public — x-labs64-auth: public: true (health/docs reachable without scopes)
permit(
  principal,
  action == Labs64IO::Action::"readAudit",
  resource
) when { context has path && context.path == "/auditflow/api/v1/audit/health" };

// scope-gated — x-labs64-auth: scopes: [audit-event:write]
permit(
  principal,
  action == Labs64IO::Action::"publishAudit",
  resource
) when { principal.scopes.contains("audit-event:write") };

// scope-gated — x-labs64-auth: scopes: [payment:refund]
permit(
  principal,
  action == Labs64IO::Action::"refundPayment",
  resource
) when { principal.scopes.contains("payment:refund") };
