// Tier 1 — EDGE policy set. Coarse "can this identity reach this
// operation at all?" — replaces the authproxy's auth-policy.json scope logic.
//
// In production these are GENERATED from each module's x-labs64-auth by the
// commons OpenApiAuthPreprocessor (--cedar-output) and shipped inside the
// signed P0 bundle; this file is the reference set the shared schema is
// validated against. One policy per operation; the three x-labs64-auth
// patterns translate 1:1:

// public — x-labs64-auth: public: true
@id("payment-gateway::listPaymentDefinitions")
permit(
  principal,
  action == Labs64IO::Action::"invoke",
  resource == Labs64IO::ApiOperation::"payment-gateway::listPaymentDefinitions"
);

// tenant-required + scope-gated — x-labs64-auth: tenant: true, scopes: [payment:pay]
@id("payment-gateway::payPayment")
permit(
  principal,
  action == Labs64IO::Action::"invoke",
  resource == Labs64IO::ApiOperation::"payment-gateway::payPayment"
) when { (context has tenant) && (context.scopes.contains("payment:pay")) };

// scope-gated only (OR semantics across listed scopes) —
// x-labs64-auth: scopes: [audit-event:write]
@id("auditflow::publishEvent")
permit(
  principal,
  action == Labs64IO::Action::"invoke",
  resource == Labs64IO::ApiOperation::"auditflow::publishEvent"
) when { (context.scopes.contains("audit-event:write")) };
