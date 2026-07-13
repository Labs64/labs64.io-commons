package io.labs64.authcontext.cedar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default decision sink: one structured log line per decision. In SHADOW mode
 * a deny (or error) is the signal to investigate before flipping ENFORCE, so
 * those log at WARN; enforced results log at INFO.
 */
public class LoggingDecisionListener implements AuthorizationDecisionListener {

    private static final Logger logger = LoggerFactory.getLogger(LoggingDecisionListener.class);

    @Override
    public void onDecision(final AuthorizationDecision d) {
        String line = String.format(
                "cedar-domain action=%s resource=%s::%s decision=%s mode=%s reasons=%s user=%s tenant=%s requestId=%s%s",
                d.action(), d.resourceType(), d.resourceId(), d.allowed() ? "allow" : "deny",
                d.enforced() ? "enforce" : "shadow", String.join(",", d.reasons()),
                d.userId(), d.tenantId() == null ? "-" : d.tenantId(), d.requestId(),
                d.error() == null ? "" : " error=" + d.error());
        if (!d.enforced() && (!d.allowed() || d.error() != null)) {
            logger.warn(line);
        } else {
            logger.info(line);
        }
    }
}
