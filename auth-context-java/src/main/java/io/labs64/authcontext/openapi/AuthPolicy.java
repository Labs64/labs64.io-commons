package io.labs64.authcontext.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authentication and authorization requirements extracted from
 * {@code x-labs64-auth}.
 */
record AuthPolicy(Object raw, boolean isPublic, boolean tenantRequired, List<String> scopes) {

    static AuthPolicy from(final Object value) {
        if (value == null) {
            return publicEndpoint(null);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION + " must be an object");
        }

        boolean publicEndpoint = bool(map.get("public"));
        boolean tenantRequired = bool(map.get("tenant"));
        List<String> scopes = scopes(map.get("scopes"));

        if (publicEndpoint && (tenantRequired || !scopes.isEmpty())) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                    + " cannot be public and require tenant/scopes at the same time");
        }
        if (!publicEndpoint && !tenantRequired && scopes.isEmpty()) {
            return publicEndpoint(value);
        }
        return new AuthPolicy(value, false, tenantRequired, scopes);
    }

    private static AuthPolicy publicEndpoint(final Object raw) {
        return new AuthPolicy(raw, true, false, List.of());
    }

    private static boolean bool(final Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException("auth boolean value expected");
    }

    private static List<String> scopes(final Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof String scope) {
            return List.of(scope);
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("auth scopes must be a string or list");
        }
        List<String> scopes = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String scope) || scope.isBlank()) {
                throw new IllegalArgumentException("auth scopes must contain non-blank strings");
            }
            scopes.add(scope);
        }
        return List.copyOf(scopes);
    }
}
