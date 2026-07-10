package io.labs64.authcontext.test;

import java.util.Set;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import io.labs64.authcontext.core.AuthHeaders;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;

/** JUnit 5 extension backing {@link WithAuthContext}. */
public class WithAuthContextExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        WithAuthContext annotation = AnnotationSupport
                .findAnnotation(context.getElement(), WithAuthContext.class)
                .or(() -> AnnotationSupport.findAnnotation(context.getTestClass(), WithAuthContext.class))
                .orElse(null);
        if (annotation == null) {
            return;
        }
        String tenant = annotation.tenant();
        if (tenant.isEmpty() || AuthHeaders.TENANT_NONE.equals(tenant)) {
            tenant = null;
        }
        AuthContextHolder.set(
                new AuthContext(annotation.user(), tenant, Set.of(annotation.scopes()), annotation.requestId()));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        AuthContextHolder.clear();
    }
}

