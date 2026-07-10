package io.labs64.authcontext.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint as intentionally public in the generated API contract.
 * <p>
 * The fail-closed filter still controls public path prefixes. This annotation is
 * primarily a source-of-truth marker for generated API interfaces and policy
 * validation.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
}
