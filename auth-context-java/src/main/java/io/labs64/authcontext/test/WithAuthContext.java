package io.labs64.authcontext.test;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.labs64.authcontext.core.AuthContext;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Binds a {@link AuthContext} for the duration of a
 * test, replacing the JWT/header plumbing that production requests go
 * through.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ExtendWith(WithAuthContextExtension.class)
public @interface WithAuthContext {

    String user() default "test-user";

    String tenant() default "t_test";

    String[] scopes() default {};

    String requestId() default "test-request-id";
}

