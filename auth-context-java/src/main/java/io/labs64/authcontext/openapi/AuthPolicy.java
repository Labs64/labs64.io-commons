package io.labs64.authcontext.openapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Authentication and authorization requirements extracted from
 * {@code x-labs64-auth}.
 *
 * <p>{@code resourceType} (optional, {@code x-labs64-auth.resource}) declares
 * the Cedar domain resource type this operation authorizes against — e.g.
 * {@code Payment}. It is the OpenAPI-native source for the generated Tier-2
 * domain policies (RFC-05 P3/P4): only operations that declare it get a domain
 * {@code permit} and contribute their type's tenant guard. Absent ⇒ the
 * operation is edge-only (coarse reachability).
 */
record AuthPolicy(Object raw, boolean isPublic, boolean tenantRequired, List<String> scopes, String resourceType) {

    static AuthPolicy from(final Object value, boolean isRequired) {
        if (value == null) {
            if (isRequired) {
                throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                        + " must declare either 'public: true' or specify 'tenant'/'scopes'");
            }
            return new AuthPolicy(null, false, false, List.of(), null);
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION + " must be an object");
        }

        boolean publicEndpoint = bool(map.get("public"));
        boolean tenantRequired = bool(map.get("tenant"));
        List<String> scopes = scopes(map.get("scopes"));
        String resourceType = resourceType(map.get("resource"));

        if (publicEndpoint && (tenantRequired || !scopes.isEmpty())) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                    + " cannot be public and require tenant/scopes at the same time");
        }
        if (publicEndpoint && resourceType != null) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                    + " cannot be public and declare a domain resource at the same time");
        }
        if (!publicEndpoint && !tenantRequired && scopes.isEmpty()) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                    + " must declare either 'public: true' or specify 'tenant'/'scopes'");
        }
        if (resourceType != null && !tenantRequired) {
            throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                    + " must require a tenant when declaring a domain resource");
        }
        return new AuthPolicy(value, publicEndpoint, tenantRequired, scopes, resourceType);
    }

    private static AuthPolicy publicEndpoint(final Object raw) {
        return new AuthPolicy(raw, true, false, List.of(), null);
    }

    private static String resourceType(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String type && !type.isBlank()) {
            return type;
        }
        throw new IllegalArgumentException(OpenApiAuthPreprocessor.AUTH_EXTENSION
                + ".resource must be a non-blank string");
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
