package io.labs64.authcontext.authorization;

/**
 * Raised by the authorization PEP in ENFORCE mode when the policy set could not be
 * loaded at startup (fail closed at boot). Request-time denials use the
 * servlet error path (403) directly, mirroring the coarse interceptors.
 */
public class AuthorizationException extends RuntimeException {

    public AuthorizationException(final String message) {
        super(message);
    }

    public AuthorizationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
