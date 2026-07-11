package io.labs64.authcontext.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Parses the trusted gateway headers into an {@link AuthContext}.
 */
public class AuthContextParser {

    /**
     * Parses the context from a header lookup function.
     *
     * @param header resolves a header value by name
     * @return parsed context, or empty when the identity is missing
     * @throws AuthContextParseException when any present trusted auth header is malformed
     */
    public Optional<AuthContext> parse(final Function<String, String> header) {
        String user = header.apply(AuthHeaders.USER);
        if (user == null || user.isEmpty()) {
            return Optional.empty();
        }
        if (AuthHeaders.isNotValidValue(user)) {
            throw new AuthContextParseException(AuthHeaders.USER);
        }

        List<String> scopes = parseScopes(header.apply(AuthHeaders.SCOPES));
        if (scopes == null) {
            throw new AuthContextParseException(AuthHeaders.SCOPES);
        }

        TenantParseResult tenant = parseTenant(header.apply(AuthHeaders.TENANT));
        if (!tenant.valid()) {
            throw new AuthContextParseException(AuthHeaders.TENANT);
        }

        String requestId = header.apply(AuthHeaders.REQUEST_ID);
        if (AuthHeaders.isNotValidValue(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        return Optional.of(new AuthContext(user, tenant.value(), Set.copyOf(scopes), requestId));
    }

    private TenantParseResult parseTenant(final String tenant) {
        if (tenant == null || tenant.isEmpty() || AuthHeaders.TENANT_NONE.equals(tenant)) {
            return TenantParseResult.valid(null);
        }
        if (AuthHeaders.isNotValidValue(tenant)) {
            return TenantParseResult.invalid();
        }
        return TenantParseResult.valid(tenant);
    }

    /**
     * Parses the scopes CSV. Items are trimmed; empty items are dropped.
     *
     * @return the scope list, or {@code null} if any non-empty item violates the
     *         value pattern
     */
    private List<String> parseScopes(final String csv) {
        List<String> scopes = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return scopes;
        }
        for (String item : csv.split(",")) {
            String scope = item.trim();
            if (scope.isEmpty()) {
                continue;
            }
            if (AuthHeaders.isNotValidValue(scope)) {
                return null;
            }
            scopes.add(scope);
        }
        return scopes;
    }

    private record TenantParseResult(String value, boolean valid) {

        private static TenantParseResult valid(final String value) {
            return new TenantParseResult(value, true);
        }

        private static TenantParseResult invalid() {
            return new TenantParseResult(null, false);
        }
    }
}
