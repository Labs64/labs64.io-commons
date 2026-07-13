package io.labs64.authcontext.cedar;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import io.labs64.authcontext.authorization.AuthAnnotationSupport;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The {@code @Authorize} PEP (RFC-05 P3): runs after the coarse
 * {@code @RequireTenant}/{@code @RequireScopes} pre-filters, resolves the
 * domain resource through the module's {@link CedarEntityResolver}, asks the
 * Cedar PDP, publishes the decision to every
 * {@link AuthorizationDecisionListener}, and — in ENFORCE mode — blocks with
 * 403 (401 when no {@code AuthContext} is present). SHADOW mode never blocks.
 *
 * <p>The {@code resource} SpEL expression is evaluated against the request's
 * URI template variables (read-only data binding), e.g. {@code #paymentId}.
 */
public class AuthorizeInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizeInterceptor.class);

    private final CedarAuthorizationService service;
    private final List<CedarEntityResolver> resolvers;
    private final List<AuthorizationDecisionListener> listeners;
    private final ExpressionParser parser = new SpelExpressionParser();

    public AuthorizeInterceptor(final CedarAuthorizationService service,
            final List<CedarEntityResolver> resolvers,
            final List<AuthorizationDecisionListener> listeners) {
        this.service = service;
        this.resolvers = resolvers;
        this.listeners = listeners;
    }

    @Override
    public boolean preHandle(@NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response, @NonNull final Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        final Authorize annotation = AuthAnnotationSupport.find(handlerMethod, Authorize.class);
        if (annotation == null) {
            return true;
        }

        Optional<AuthContext> context = AuthContextHolder.get();
        if (context.isEmpty()) {
            // The AuthContextFilter fails closed before us on protected paths;
            // this is defense in depth for misconfigured public paths.
            if (service.isEnforcing()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
            logger.warn("cedar-domain action={} skipped: no AuthContext (shadow)", annotation.action());
            return true;
        }

        AuthorizationDecision decision = decide(annotation, request, context.get());
        publish(decision);
        if (service.isEnforcing() && !decision.allowed()) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }

    private AuthorizationDecision decide(final Authorize annotation, final HttpServletRequest request,
            final AuthContext context) {
        // Resolver/SpEL exceptions propagate untouched: the module's own error
        // semantics apply (e.g. its NotFoundException → 404, never a 403 that
        // leaks existence) and an erroring request is fail-closed by nature.
        Object resourceRef = evaluateResourceRef(annotation.resource(), request);
        CedarEntity resource = resolve(annotation.resourceType(), resourceRef, context);
        return service.decide(context, annotation.action(), resource);
    }

    @Nullable
    private Object evaluateResourceRef(final String expression, final HttpServletRequest request) {
        if (expression.isEmpty()) {
            return null;
        }
        SimpleEvaluationContext evaluationContext = SimpleEvaluationContext.forReadOnlyDataBinding().build();
        Object uriVars = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (uriVars instanceof Map<?, ?> map) {
            map.forEach((name, value) -> evaluationContext.setVariable(String.valueOf(name), value));
        }
        return parser.parseExpression(expression).getValue(evaluationContext);
    }

    private CedarEntity resolve(final String resourceType, @Nullable final Object resourceRef,
            final AuthContext context) {
        for (CedarEntityResolver resolver : resolvers) {
            if (resolver.supports(resourceType)) {
                return resolver.resolve(resourceType, resourceRef, context);
            }
        }
        throw new IllegalStateException("no CedarEntityResolver supports resource type " + resourceType);
    }

    private void publish(final AuthorizationDecision decision) {
        for (AuthorizationDecisionListener listener : listeners) {
            try {
                listener.onDecision(decision);
            } catch (RuntimeException e) {
                logger.warn("AuthorizationDecisionListener {} failed: {}", listener.getClass().getName(), e.toString());
            }
        }
    }
}
