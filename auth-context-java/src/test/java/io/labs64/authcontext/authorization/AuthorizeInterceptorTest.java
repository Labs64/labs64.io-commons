package io.labs64.authcontext.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;

class AuthorizeInterceptorTest {

    static class PaymentController {
        @Authorize(action = "payPayment", resource = "#paymentId", resourceType = "Payment")
        public void payPayment(UUID paymentId) {
        }

        public void unannotated() {
        }
    }

    /** Fake repository: payment pay_1 lives in t_100 and is READY. */
    static class PaymentResolver implements ResourceResolver {
        @Override
        public boolean supports(final String resourceType) {
            return "Payment".equals(resourceType);
        }

        @Override
        public ResourceEntity resolve(final String resourceType, final Object resourceRef, final AuthContext ctx) {
            return ResourceEntity.builder("Payment", String.valueOf(resourceRef))
                    .attribute("tenant", "t_100")
                    .attribute("status", "READY")
                    .build();
        }
    }

    /**
     * Stub PDP standing in for the external {@link AuthorizationService} (the
     * real Cerbos client is exercised in {@code CerbosAuthorizationServiceTest}).
     * Mirrors the tenant-guard + scope semantics the interceptor relies on:
     * allow only when the principal's tenant matches the resource tenant and it
     * carries the {@code payment:pay} scope.
     */
    static class StubAuthorizationService implements AuthorizationService {
        private final AuthorizationProperties.Mode mode;

        StubAuthorizationService(final AuthorizationProperties.Mode mode) {
            this.mode = mode;
        }

        @Override
        public boolean isEnforcing() {
            return mode == AuthorizationProperties.Mode.ENFORCE;
        }

        @Override
        public AuthorizationDecision decide(final AuthContext ctx, final String action, final ResourceEntity resource) {
            Object tenant = resource.attributes().get("tenant");
            boolean sameTenant = tenant != null && tenant.equals(ctx.tenantId());
            boolean allowed = sameTenant && ctx.hasScope("payment:pay");
            return new AuthorizationDecision(action, resource.type(), resource.id(),
                    allowed, isEnforcing(), allowed ? List.of("policy0") : List.of(), null,
                    ctx.userId(), ctx.tenantId(), ctx.requestId());
        }
    }

    private final List<AuthorizationDecision> decisions = new ArrayList<>();

    @AfterEach
    void cleanup() {
        AuthContextHolder.clear();
    }

    private AuthorizeInterceptor interceptor(final AuthorizationProperties.Mode mode) {
        return new AuthorizeInterceptor(new StubAuthorizationService(mode),
                List.of(new PaymentResolver()), List.of(decisions::add));
    }

    private MockHttpServletResponse response = new MockHttpServletResponse();

    private boolean invoke(final AuthorizeInterceptor interceptor, final String methodName) throws Exception {
        Method method = PaymentController.class.getDeclaredMethod(methodName,
                methodName.equals("payPayment") ? new Class<?>[] { UUID.class } : new Class<?>[0]);
        HandlerMethod handler = new HandlerMethod(new PaymentController(), method);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("paymentId", "pay_1"));
        response = new MockHttpServletResponse();
        return interceptor.preHandle(request, response, handler);
    }

    @Test
    void shadowModeNeverBlocksButPublishesDecision() throws Exception {
        AuthContextHolder.set(new AuthContext("mallory", "t_200", Set.of("payment:pay"), "r-1"));
        boolean proceed = invoke(interceptor(AuthorizationProperties.Mode.SHADOW), "payPayment");
        assertThat(proceed).isTrue(); // cross-tenant would deny — shadow lets it pass
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).allowed()).isFalse();
        assertThat(decisions.get(0).enforced()).isFalse();
    }

    @Test
    void enforceModeBlocksCrossTenantWith403() throws Exception {
        AuthContextHolder.set(new AuthContext("mallory", "t_200", Set.of("payment:pay"), "r-1"));
        boolean proceed = invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment");
        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(decisions.get(0).enforced()).isTrue();
    }

    @Test
    void enforceModeAllowsSameTenantReadyPayment() throws Exception {
        AuthContextHolder.set(new AuthContext("alice", "t_100", Set.of("payment:pay"), "r-1"));
        boolean proceed = invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment");
        assertThat(proceed).isTrue();
        assertThat(decisions.get(0).allowed()).isTrue();
        assertThat(decisions.get(0).reasons()).isNotEmpty();
    }

    @Test
    void unannotatedMethodIsIgnored() throws Exception {
        AuthContextHolder.set(new AuthContext("alice", "t_100", Set.of(), "r-1"));
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "unannotated")).isTrue();
        assertThat(decisions).isEmpty();
    }

    @Test
    void missingAuthContextIs401InEnforce() throws Exception {
        boolean proceed = invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment");
        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingAuthContextPassesInShadow() throws Exception {
        assertThat(invoke(interceptor(AuthorizationProperties.Mode.SHADOW), "payPayment")).isTrue();
    }

    @Test
    void enforce401NoContextEmitsWarnSummary() throws Exception {
        Logger log = (Logger) LoggerFactory.getLogger(AuthorizeInterceptor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        log.addAppender(appender);
        try {
            boolean proceed = invoke(interceptor(AuthorizationProperties.Mode.ENFORCE), "payPayment");
            assertThat(proceed).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(appender.list).anySatisfy(e -> {
                assertThat(e.getLevel()).isEqualTo(Level.WARN);
                assertThat(e.getFormattedMessage())
                        .contains("outcome=enforced-deny", "reason=no-auth-context", "action=payPayment");
            });
        } finally {
            log.detachAppender(appender);
        }
    }

    @Test
    void resolverExceptionsPropagateWithModuleSemantics() throws Exception {
        // The module's own exceptions (NotFound etc.) must reach its handlers
        // untouched — an erroring request is fail-closed by nature, and a 404
        // must never be converted into an existence-leaking 403.
        ResourceResolver broken = new ResourceResolver() {
            @Override
            public boolean supports(final String resourceType) {
                return true;
            }

            @Override
            public ResourceEntity resolve(final String type, final Object ref, final AuthContext ctx) {
                throw new IllegalStateException("payment not found");
            }
        };
        AuthorizeInterceptor interceptor = new AuthorizeInterceptor(
                new StubAuthorizationService(AuthorizationProperties.Mode.ENFORCE), List.of(broken),
                List.of(decisions::add));

        AuthContextHolder.set(new AuthContext("alice", "t_100", Set.of("payment:pay"), "r-1"));
        Method method = PaymentController.class.getDeclaredMethod("payPayment", UUID.class);
        HandlerMethod handler = new HandlerMethod(new PaymentController(), method);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("paymentId", "pay_1"));
        response = new MockHttpServletResponse();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> interceptor.preHandle(request, response, handler))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("payment not found");
        assertThat(decisions).isEmpty();
    }
}
