package io.labs64.authcontext.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Operation-level role check (RFC-03 authorization split: path-level RBAC is
 * enforced at the gateway; finer-grained checks live in the module). The
 * caller must hold at least one of the listed roles; otherwise 403 (401 when
 * no context is bound at all).
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {

    String[] value();
}
