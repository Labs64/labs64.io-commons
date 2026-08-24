package io.labs64.authcontext.core;

import java.util.regex.Pattern;

/**
 * The trusted header contract emitted by the auth gateway (ACS).
 * Values are re-validated here as defense in depth; the ACS sanitizes with the
 * same pattern before emission.
 */
public final class AuthHeaders {

    public static final String USER = "X-Auth-User";
    public static final String SCOPES = "X-Auth-Scopes";
    public static final String TENANT = "X-Auth-Tenant";
    public static final String REQUEST_ID = "X-Request-ID";

    /** Tenant value the ACS emits for tenant-less calls. */
    public static final String TENANT_NONE = "-";

    /** Prefix identifying a service principal (client-credentials caller). */
    public static final String SERVICE_PRINCIPAL_PREFIX = "svc:";

    private static final Pattern VALUE_PATTERN = Pattern.compile("^[a-zA-Z0-9_.:-]+$");

    private AuthHeaders() {
    }

    /** Returns true when the value is missing, blank, or violates the contract's sanitization pattern. */
    public static boolean isNotValidValue(String value) {
        return value == null || value.isEmpty() || !VALUE_PATTERN.matcher(value).matches();
    }
}

