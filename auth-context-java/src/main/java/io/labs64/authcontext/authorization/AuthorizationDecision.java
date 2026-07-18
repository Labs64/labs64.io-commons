package io.labs64.authcontext.authorization;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * One domain authorization decision — published to every
 * {@link AuthorizationDecisionListener} for auditability.
 *
 * @param action       action id (unqualified)
 * @param resourceType resource type (unqualified)
 * @param resourceId   resource entity id ("-" when resolution failed)
 * @param allowed      the decision
 * @param enforced     true when the decision blocked/passed the request
 *                     (ENFORCE), false in SHADOW
 * @param reasons      matched policy ids from PDP diagnostics
 * @param error        engine/resolution error, null on a clean decision;
 *                     a non-null error always comes with {@code allowed=false}
 *                     (fail closed)
 * @param userId       principal (from the trusted AuthContext)
 * @param tenantId     tenant, null for tenant-less calls
 * @param requestId    correlation id
 */
public record AuthorizationDecision(String action, String resourceType, String resourceId,
        boolean allowed, boolean enforced, List<String> reasons, @Nullable String error,
        String userId, @Nullable String tenantId, String requestId) {

    public AuthorizationDecision {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    /** Three-valued decision for logs: {@code allow} / {@code deny} / {@code error}. */
    public String decision() {
        return error != null ? "error" : allowed ? "allow" : "deny";
    }

    /**
     * Enforcement outcome verb: {@code enforced-allow} / {@code enforced-deny} /
     * {@code shadow-allow} / {@code shadow-deny}. An errored decision is a deny
     * (fail-closed), so its outcome carries the blocking action, while
     * {@link #decision()} still reports {@code error} as the reason.
     */
    public String outcome() {
        String phase = enforced ? "enforced" : "shadow";
        String result = (allowed && error == null) ? "allow" : "deny";
        return phase + "-" + result;
    }
}
