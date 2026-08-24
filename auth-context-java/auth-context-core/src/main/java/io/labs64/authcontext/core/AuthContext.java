package io.labs64.authcontext.core;

import java.util.Set;

/**
 * Immutable per-request identity derived from the trusted gateway headers.
 *
 * @param userId    authenticated user (or {@code svc:<name>} service principal); never null
 * @param tenantId  tenant identifier, or {@code null} for tenant-less calls
 * @param scopes    effective scopes; never null, possibly empty
 * @param requestId request correlation id; never null
 */
public record AuthContext(String userId, String tenantId, Set<String> scopes, String requestId) {

    public AuthContext {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId must not be empty");
        }
        if (requestId == null || requestId.isEmpty()) {
            throw new IllegalArgumentException("requestId must not be empty");
        }
        scopes = scopes == null ? Set.of() : Set.copyOf(scopes);
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    public boolean hasAnyScope(String... candidates) {
        for (String scope : candidates) {
            if (scopes.contains(scope)) {
                return true;
            }
        }
        return false;
    }

    public boolean isServicePrincipal() {
        return userId.startsWith(AuthHeaders.SERVICE_PRINCIPAL_PREFIX);
    }
}

