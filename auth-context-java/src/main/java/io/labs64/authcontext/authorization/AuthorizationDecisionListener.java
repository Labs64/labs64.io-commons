package io.labs64.authcontext.authorization;

/**
 * Module SPI: receives every domain authorization decision (allow and deny, shadow and
 * enforce). Modules route these to AuditFlow / metrics; the default is a
 * structured log line ({@link LoggingDecisionListener}). Listener failures are
 * logged and never affect the request.
 */
@FunctionalInterface
public interface AuthorizationDecisionListener {

    void onDecision(AuthorizationDecision decision);
}
