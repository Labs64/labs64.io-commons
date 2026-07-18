package io.labs64.authcontext.authorization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default decision sink. Emits two lines per decision:
 *
 * <ul>
 *   <li>a non-sensitive <b>summary</b> on this class's logger — INFO for a clean
 *       allow, WARN for any deny/error/mismatch so it is grep-able before
 *       flipping ENFORCE; and</li>
 *   <li>a <b>detail</b> line on the dedicated {@code io.labs64.authcontext.authorization.detail}
 *       logger at DEBUG carrying the sensitive fields (user, tenant, resolved
 *       resource id, raw error). It is off unless that logger is raised to DEBUG,
 *       so the default stream stays safe to read during the shadow phase.</li>
 * </ul>
 *
 * Both lines share {@code requestId} so they can be joined.
 */
public class LoggingDecisionListener implements AuthorizationDecisionListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDecisionListener.class);
    private static final Logger detail = LoggerFactory.getLogger("io.labs64.authcontext.authorization.detail");

    @Override
    public void onDecision(final AuthorizationDecision d) {
        String summary = String.format(
                "authz-domain outcome=%s decision=%s mode=%s action=%s resourceType=%s reasons=%s requestId=%s",
                d.outcome(), d.decision(), d.enforced() ? "enforce" : "shadow",
                d.action(), d.resourceType(),
                d.reasons().isEmpty() ? "-" : String.join(",", d.reasons()), d.requestId());
        if ("allow".equals(d.decision())) {
            logger.info(summary);
        } else {
            logger.warn(summary);
        }

        if (detail.isDebugEnabled()) {
            detail.debug("authz-detail requestId={} user={} tenant={} resource={}::{}{}",
                    d.requestId(), d.userId(), d.tenantId() == null ? "-" : d.tenantId(),
                    d.resourceType(), d.resourceId(),
                    d.error() == null ? "" : " error=" + d.error());
        }
    }
}
