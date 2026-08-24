package io.labs64.authcontext.core;

import java.util.Optional;

/**
 * Request-scoped access to the current {@link AuthContext}. Populated by
 * {@code AuthContextFilter} for the duration of the request; absent on public
 * paths without valid gateway headers.
 */
public final class AuthContextHolder {

    private static final ThreadLocal<AuthContext> CURRENT = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static Optional<AuthContext> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** @throws IllegalStateException when no context is bound (e.g. public path, anonymous caller) */
    public static AuthContext require() {
        AuthContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No AuthContext bound to the current request");
        }
        return context;
    }

    public static void set(AuthContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}

