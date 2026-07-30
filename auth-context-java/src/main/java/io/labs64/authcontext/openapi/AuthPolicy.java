package io.labs64.authcontext.openapi;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authentication and authorization requirements extracted from standard
 * OpenAPI {@code security} plus {@code x-labs64.auth}.
 *
 * <p>OAuth scopes come from the {@code oauth} Security Requirement. Labs64
 * metadata is limited to the concerns OpenAPI cannot express: tenant and
 * domain-resource requirements.
 */
record AuthPolicy(boolean isPublic, boolean tenantRequired,
        List<String> scopes, String resourceType) {

    /**
     * Builds a policy from the current contract.
     *
     * @param value effective operation/path-level {@code x-labs64}
     * @param security    effective operation/root-level {@code security}
     */
    static AuthPolicy from(final Object value, final Object security) {
        SecurityRequirements requirements = SecurityRequirements.from(security);
        Map<String, Object> auth = authMap(value);
        boolean tenantRequired = bool(auth.get("tenant"));
        String resourceType = resourceType(auth.get("resource"));
        boolean publicEndpoint = !requirements.oauthRequired()
                && !tenantRequired
                && resourceType == null;

        return new AuthPolicy(publicEndpoint, tenantRequired,
                requirements.scopes(), resourceType);
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

    /**
     * Effective OpenAPI security after operation-level override/root-level
     * inheritance has already been resolved.
     */
    private record SecurityRequirements(boolean oauthRequired, List<String> scopes) {

        @SuppressWarnings("unchecked")
        private static SecurityRequirements from(final Object value) {
            if (value == null) {
                return new SecurityRequirements(false, List.of());
            }
            if (!(value instanceof List<?> requirements)) {
                throw new IllegalArgumentException("security must be an array");
            }
            if (requirements.isEmpty()) {
                return new SecurityRequirements(false, List.of());
            }

            Set<String> scopes = new LinkedHashSet<>();
            boolean oauthRequired = true;

            for (Object requirementValue : requirements) {
                if (!(requirementValue instanceof Map<?, ?> rawRequirement)) {
                    throw new IllegalArgumentException("security requirements must be objects");
                }
             
                Map<String, Object> requirement = (Map<String, Object>) rawRequirement;
             
                // An empty requirement is the OpenAPI anonymous alternative.
                if (requirement.isEmpty()) {
                    return new SecurityRequirements(false, List.of());
                }
             
                if (!requirement.containsKey(OpenApiAuthPreprocessor.OAUTH_SCHEME)) {
                    // Security Requirement Objects are alternatives. OAuth is
                    // required only when every alternative contains it.
                    oauthRequired = false;
                    continue;
                }
                Object oauthScopes = requirement.get(OpenApiAuthPreprocessor.OAUTH_SCHEME);
                if (!(oauthScopes instanceof List<?> list)) {
                    throw new IllegalArgumentException("security." + OpenApiAuthPreprocessor.OAUTH_SCHEME
                            + " must be an array of scopes");
                }
                scopes.addAll(stringList(list, "security." + OpenApiAuthPreprocessor.OAUTH_SCHEME));
            }
            return new SecurityRequirements(oauthRequired, List.copyOf(scopes));
        }
    }
}
