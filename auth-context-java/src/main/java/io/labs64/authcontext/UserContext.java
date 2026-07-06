package io.labs64.authcontext;

import java.util.Set;

/**
 * Immutable per-request identity derived from the trusted gateway headers.
 *
 * @param userId    authenticated user (or {@code svc:<name>} service principal); never null
 * @param tenantId  tenant identifier, or {@code null} for tenant-less calls
 * @param roles     effective roles; never null, possibly empty
 * @param requestId request correlation id; never null
 */
public record UserContext(String userId, String tenantId, Set<String> roles, String requestId) {

    public UserContext {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId must not be empty");
        }
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId must not be empty");
        }
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(String... candidates) {
        for (String role : candidates) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

    public boolean isServicePrincipal() {
        return userId.startsWith(AuthHeaders.SERVICE_PRINCIPAL_PREFIX);
    }
}
