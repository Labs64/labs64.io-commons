package io.labs64.authcontext.cedar;

/**
 * A {@link CedarEntityResolver} may throw this (or any module exception) when
 * the referenced resource does not exist; the interceptor rethrows it so the
 * module's normal 404 semantics apply instead of a existence-leaking 403.
 */
public class CedarResourceNotFoundException extends RuntimeException {

    public CedarResourceNotFoundException(final String message) {
        super(message);
    }
}
