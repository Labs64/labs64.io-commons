package io.labs64.authcontext.core;

/**
 * Signals that trusted auth headers were present but malformed.
 */
public class AuthContextParseException extends RuntimeException {

    private final String headerName;

    public AuthContextParseException(final String headerName) {
        super("Malformed auth header: " + headerName);
        this.headerName = headerName;
    }

    public String headerName() {
        return headerName;
    }
}
