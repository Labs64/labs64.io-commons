package io.labs64.authcontext.authorization;

import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import io.labs64.authcontext.web.AuthErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enforces {@link RequireScopes} on handler methods and controller classes.
 */
public class RequireScopesInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        final RequireScopes annotation = AuthAnnotationSupport.find(handlerMethod, RequireScopes.class);
        if (annotation == null) {
            return true;
        }

        Optional<AuthContext> context = AuthContextHolder.get();
        if (context.isEmpty()) {
            AuthErrorResponseWriter.unauthorized(response);
            return false;
        }
        if (!context.get().hasAnyScope(annotation.value())) {
            AuthErrorResponseWriter.forbidden(response);
            return false;
        }
        return true;
    }
}

