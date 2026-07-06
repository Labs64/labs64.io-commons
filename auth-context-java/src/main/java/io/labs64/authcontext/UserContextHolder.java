package io.labs64.authcontext;

import java.util.Optional;

/**
 * Request-scoped access to the current {@link UserContext}. Populated by
 * {@code AuthContextFilter} for the duration of the request; absent on public
 * paths without valid gateway headers.
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static Optional<UserContext> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** @throws IllegalStateException when no context is bound (e.g. public path, anonymous caller) */
    public static UserContext require() {
        UserContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException("No UserContext bound to the current request");
        }
        return context;
    }

    public static void set(UserContext context) {
        CURRENT.set(context);
    }

    public static void clear() {
        CURRENT.remove();
    }
}
