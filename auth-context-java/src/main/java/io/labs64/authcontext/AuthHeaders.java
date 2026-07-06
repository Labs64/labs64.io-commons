package io.labs64.authcontext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The trusted header contract emitted by the auth gateway (ACS) per RFC-03.
 * Values are re-validated here as defense in depth; the ACS sanitizes with the
 * same pattern before emission.
 */
public final class AuthHeaders {

    public static final String USER = "X-Auth-User";
    public static final String ROLES = "X-Auth-Roles";
    public static final String TENANT = "X-Auth-Tenant";
    public static final String REQUEST_ID = "X-Request-ID";

    /** Tenant value the ACS emits for tenant-less calls. */
    public static final String TENANT_NONE = "-";

    /** Prefix identifying a service principal (client-credentials caller). */
    public static final String SERVICE_PRINCIPAL_PREFIX = "svc:";

    private static final Pattern VALUE_PATTERN = Pattern.compile("^[a-zA-Z0-9_.:-]+$");

    private AuthHeaders() {
    }

    /** A non-blank value matching the contract's sanitization pattern. */
    public static boolean isValidValue(String value) {
        return value != null && !value.isEmpty() && VALUE_PATTERN.matcher(value).matches();
    }

    /**
     * Parses the roles CSV. Items are trimmed; empty items are dropped.
     *
     * @return the role list, or {@code null} if any non-empty item violates the
     *         value pattern
     */
    public static List<String> parseRoles(String csv) {
        List<String> roles = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return roles;
        }
        for (String item : csv.split(",")) {
            String role = item.trim();
            if (role.isEmpty()) {
                continue;
            }
            if (!isValidValue(role)) {
                return null;
            }
            roles.add(role);
        }
        return roles;
    }
}
