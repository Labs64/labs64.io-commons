package io.labs64.authcontext.cedar;

/**
 * Raised by the Cedar PEP in ENFORCE mode when the policy set could not be
 * loaded at startup (fail closed at boot). Request-time denials use the
 * servlet error path (403) directly, mirroring the coarse interceptors.
 */
public class CedarAuthorizationException extends RuntimeException {

    public CedarAuthorizationException(final String message) {
        super(message);
    }

    public CedarAuthorizationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
