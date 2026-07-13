package io.labs64.authcontext.cedar;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;

class AuthorizeInterceptorTest {

    private static final String POLICIES = """
            forbid(principal, action, resource is Labs64IO::Payment)
            unless { principal in resource.tenant };

            permit(principal, action == Labs64IO::Action::"payPayment", resource)
            when { resource.status == "READY" && context.scopes.contains("payment:pay") };
            """;

    static class PaymentController {
        @Authorize(action = "payPayment", resource = "#paymentId", resourceType = "Payment")
        public void payPayment(UUID paymentId) {
        }

        public void unannotated() {
        }
    }

    /** Fake repository: payment pay_1 lives in t_100 and is READY. */
    static class PaymentResolver implements CedarEntityResolver {
        @Override
        public boolean supports(final String resourceType) {
            return "Payment".equals(resourceType);
        }

        @Override
        public CedarEntity resolve(final String resourceType, final Object resourceRef, final AuthContext ctx) {
            CedarEntity tenant = CedarEntity.ref("Tenant", "t_100");
            return CedarEntity.builder("Payment", String.valueOf(resourceRef))
                    .attribute("tenant", tenant)
                    .attribute("status", "READY")
                    .parent(tenant)
                    .build();
        }
    }

    private final List<AuthorizationDecision> decisions = new ArrayList<>();

    @AfterEach
    void cleanup() {
        AuthContextHolder.clear();
    }

    private AuthorizeInterceptor interceptor(final CedarProperties.Mode mode) {
        CedarProperties properties = new CedarProperties();
        properties.setEnabled(true);
        properties.setMode(mode);
        CedarAuthorizationService service = new CedarAuthorizationService(
                properties, new ByteArrayResource(POLICIES.getBytes()));
        return new AuthorizeInterceptor(service, List.of(new PaymentResolver()), List.of(decisions::add));
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
        boolean proceed = invoke(interceptor(CedarProperties.Mode.SHADOW), "payPayment");
        assertThat(proceed).isTrue(); // cross-tenant would deny — shadow lets it pass
        assertThat(decisions).hasSize(1);
        assertThat(decisions.get(0).allowed()).isFalse();
        assertThat(decisions.get(0).enforced()).isFalse();
    }

    @Test
    void enforceModeBlocksCrossTenantWith403() throws Exception {
        AuthContextHolder.set(new AuthContext("mallory", "t_200", Set.of("payment:pay"), "r-1"));
        boolean proceed = invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment");
        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(decisions.get(0).enforced()).isTrue();
    }

    @Test
    void enforceModeAllowsSameTenantReadyPayment() throws Exception {
        AuthContextHolder.set(new AuthContext("alice", "t_100", Set.of("payment:pay"), "r-1"));
        boolean proceed = invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment");
        assertThat(proceed).isTrue();
        assertThat(decisions.get(0).allowed()).isTrue();
        assertThat(decisions.get(0).reasons()).isNotEmpty();
    }

    @Test
    void unannotatedMethodIsIgnored() throws Exception {
        AuthContextHolder.set(new AuthContext("alice", "t_100", Set.of(), "r-1"));
        assertThat(invoke(interceptor(CedarProperties.Mode.ENFORCE), "unannotated")).isTrue();
        assertThat(decisions).isEmpty();
    }

    @Test
    void missingAuthContextIs401InEnforce() throws Exception {
        boolean proceed = invoke(interceptor(CedarProperties.Mode.ENFORCE), "payPayment");
        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingAuthContextPassesInShadow() throws Exception {
        assertThat(invoke(interceptor(CedarProperties.Mode.SHADOW), "payPayment")).isTrue();
    }

    @Test
    void resolverExceptionsPropagateWithModuleSemantics() throws Exception {
        // The module's own exceptions (NotFound etc.) must reach its handlers
        // untouched — an erroring request is fail-closed by nature, and a 404
        // must never be converted into an existence-leaking 403.
        CedarProperties properties = new CedarProperties();
        properties.setEnabled(true);
        properties.setMode(CedarProperties.Mode.ENFORCE);
        CedarAuthorizationService service = new CedarAuthorizationService(
                properties, new ByteArrayResource(POLICIES.getBytes()));
        CedarEntityResolver broken = new CedarEntityResolver() {
            @Override
            public boolean supports(final String resourceType) {
                return true;
            }

            @Override
            public CedarEntity resolve(final String type, final Object ref, final AuthContext ctx) {
                throw new IllegalStateException("payment not found");
            }
        };
        AuthorizeInterceptor interceptor = new AuthorizeInterceptor(service, List.of(broken),
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
