package io.labs64.authcontext.test;

import java.util.Set;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;

import io.labs64.authcontext.AuthHeaders;
import io.labs64.authcontext.UserContext;
import io.labs64.authcontext.UserContextHolder;

/** JUnit 5 extension backing {@link WithUserContext}. */
public class WithUserContextExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        WithUserContext annotation = AnnotationSupport
                .findAnnotation(context.getElement(), WithUserContext.class)
                .or(() -> AnnotationSupport.findAnnotation(context.getTestClass(), WithUserContext.class))
                .orElse(null);
        if (annotation == null) {
            return;
        }
        String tenant = annotation.tenant();
        if (tenant.isEmpty() || AuthHeaders.TENANT_NONE.equals(tenant)) {
            tenant = null;
        }
        UserContextHolder.set(
                new UserContext(annotation.user(), tenant, Set.of(annotation.roles()), annotation.requestId()));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        UserContextHolder.clear();
    }
}
