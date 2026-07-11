package io.labs64.authcontext.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Operation-level scope check (authorization split: path-level RBAC is
 * enforced at the gateway; finer-grained checks live in the module). The
 * caller must hold at least one of the listed scopes; otherwise 403 (401 when
 * no context is bound at all).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireScopes {

    String[] value();
}

