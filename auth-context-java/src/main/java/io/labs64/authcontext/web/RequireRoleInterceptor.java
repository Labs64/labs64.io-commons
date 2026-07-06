package io.labs64.authcontext.web;

import java.util.Optional;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import io.labs64.authcontext.UserContext;
import io.labs64.authcontext.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Enforces {@link RequireRole} on handler methods and controller classes. */
public class RequireRoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                RequireRole.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireRole.class);
        }
        if (annotation == null) {
            return true;
        }

        Optional<UserContext> context = UserContextHolder.get();
        if (context.isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        if (!context.get().hasAnyRole(annotation.value())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}
