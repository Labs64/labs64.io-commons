package io.labs64.authcontext.authorization;

import java.util.Optional;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Enforces {@link RequireTenant} on handler methods and controller classes. */
public class RequireTenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response, final Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        final RequireTenant annotation = AuthAnnotationSupport.find(handlerMethod, RequireTenant.class);
        if (annotation == null) {
            return true;
        }

        final Optional<AuthContext> context = AuthContextHolder.get();
        if (context.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (context.get().tenantId() == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
