package io.labs64.authcontext.cedar;

/**
 * Module SPI: receives every Cedar domain decision (allow and deny, shadow and
 * enforce). Modules route these to AuditFlow / metrics; the default is a
 * structured log line ({@link LoggingDecisionListener}). Listener failures are
 * logged and never affect the request.
 */
@FunctionalInterface
public interface AuthorizationDecisionListener {

    void onDecision(AuthorizationDecision decision);
}
