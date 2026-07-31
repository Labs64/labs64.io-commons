package io.labs64.authcontext.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authentication and authorization requirements extracted from
 * {@code x-labs64.auth}.
 */
record AuthPolicy(boolean isPublic, boolean tenantRequired,
        List<String> scopes, String resourceType) {

    /**
     * Builds a policy from the current contract.
     *
     * @param value effective operation/path-level {@code x-labs64}
     */
    static AuthPolicy from(final Object value) {
        Map<String, Object> auth = authMap(value);
        boolean tenantRequired = bool(auth.get("tenant"));
        List<String> scopes = scopes(auth.get("scopes"));
        String resourceType = resourceType(auth.get("resource"));
        boolean publicEndpoint = !tenantRequired
                && scopes.isEmpty()
                && resourceType == null;

        return new AuthPolicy(publicEndpoint, tenantRequired,
                scopes, resourceType);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> authMap(final Object value) {
        if (value == null) {
            return Map.of();
        }

        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION + " must be an object");
        }

        Object authValue = ((Map<String, Object>) raw).get("auth");
        
        if (authValue == null) {
            return Map.of();
        }
        
        if (!(authValue instanceof Map<?, ?> rawAuth)) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION + ".auth must be an object");
        }

        return (Map<String, Object>) rawAuth;
    }

    private static String resourceType(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String type && !type.isBlank()) {
            return type;
        }
        throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                + ".auth.resource must be a non-blank string");
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

    private static List<String> stringList(final List<?> values, final String field) {
        List<String> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof String value) || value.isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static List<String> scopes(final Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                    + ".auth.scopes must be an array");
        }
        return stringList(list, OpenApiAuthPreprocessor.AUTH_EXTENSION + ".auth.scopes");
    }
}
