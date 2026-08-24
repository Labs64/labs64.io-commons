package io.labs64.authcontext.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Encodes an {@link AuthContext} as trusted internal-call headers.
 *
 * <p>
 * The header names and wire-format rules remain owned by auth-context-core;
 * API clients only need to accept the resulting map.
 * </p>
 */
public final class AuthContextHeaders {

    private AuthContextHeaders() {
    }

    /** Creates an empty builder for a new outbound auth context. */
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a header builder initialized from the supplied context. */
    public static Builder from(AuthContext context) {
        return new Builder(Objects.requireNonNull(context, "context must not be null"));
    }

    /** Encodes a context without overriding any values. */
    public static Map<String, String> encode(AuthContext context) {
        return from(context).build();
    }

    /**
     * Mutable builder that produces an immutable header map. A new builder
     * should be created for every request.
     */
    public static final class Builder {

        private String userId;
        private String tenantId;
        private Set<String> scopes;
        private String requestId;

        private Builder() {
            this.scopes = new LinkedHashSet<>();
        }

        private Builder(AuthContext context) {
            this.userId = context.userId();
            this.tenantId = context.tenantId();
            this.scopes = new LinkedHashSet<>(context.scopes());
            this.requestId = context.requestId();
        }

        /** Replaces the original user identity. */
        public Builder user(String userId) {
            this.userId = requireValue(userId, "userId");
            return this;
        }

        /**
         * Replaces the original user with a service principal using the
         * canonical prefix defined by the auth header contract.
         */
        public Builder servicePrincipal(String serviceName) {
            String name = requireValue(serviceName, "serviceName");
            return user(AuthHeaders.SERVICE_PRINCIPAL_PREFIX + name);
        }

        /**
         * Replaces the original tenant. Use {@link #withoutTenant()} for tenant-less
         * calls.
         */
        public Builder tenant(String tenantId) {
            this.tenantId = requireValue(tenantId, "tenantId");
            return this;
        }

        /** Marks the internal call as tenant-less. */
        public Builder withoutTenant() {
            this.tenantId = null;
            return this;
        }

        /** Replaces, rather than appends to, the original scopes. */
        public Builder scopes(String... scopes) {
            Objects.requireNonNull(scopes, "scopes must not be null");
            return scopes(Arrays.asList(scopes));
        }

        /** Replaces, rather than appends to, the original scopes. */
        public Builder scopes(Collection<String> scopes) {
            Objects.requireNonNull(scopes, "scopes must not be null");
            LinkedHashSet<String> replacement = new LinkedHashSet<>();
            for (String scope : scopes) {
                replacement.add(requireValue(scope, "scope"));
            }
            this.scopes = replacement;
            return this;
        }

        /** Replaces the request correlation id. */
        public Builder requestId(String requestId) {
            this.requestId = requireValue(requestId, "requestId");
            return this;
        }

        /** Returns an immutable map containing the complete trusted header context. */
        public Map<String, String> build() {
            if (userId == null) {
                throw new IllegalStateException("user or servicePrincipal is required");
            }
            if (requestId == null) {
                throw new IllegalStateException("requestId is required");
            }
            LinkedHashMap<String, String> headers = new LinkedHashMap<>();
            headers.put(AuthHeaders.USER, userId);
            headers.put(AuthHeaders.SCOPES, String.join(",", scopes));
            headers.put(AuthHeaders.TENANT, tenantId == null ? AuthHeaders.TENANT_NONE : tenantId);
            headers.put(AuthHeaders.REQUEST_ID, requestId);
            return Collections.unmodifiableMap(headers);
        }

        private static String requireValue(String value, String name) {
            if (AuthHeaders.isNotValidValue(value)) {
                throw new IllegalArgumentException(name + " must be a valid auth header value");
            }
            return value;
        }
    }
}
