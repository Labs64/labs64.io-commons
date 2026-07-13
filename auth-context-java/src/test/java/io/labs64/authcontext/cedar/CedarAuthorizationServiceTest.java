package io.labs64.authcontext.cedar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

import io.labs64.authcontext.core.AuthContext;

class CedarAuthorizationServiceTest {

    // Mirror of the payment-gateway pilot policy set (schema-validated in
    // auth-policy-cedar/policies/domain.cedar).
    private static final String POLICIES = """
            forbid(principal, action, resource is Labs64IO::Payment)
            unless { principal in resource.tenant };

            permit(principal, action == Labs64IO::Action::"payPayment", resource)
            when { resource.status == "READY" && context.scopes.contains("payment:pay") };
            """;

    private static CedarAuthorizationService service(final CedarProperties.Mode mode, final String policies) {
        CedarProperties properties = new CedarProperties();
        properties.setEnabled(true);
        properties.setMode(mode);
        return new CedarAuthorizationService(properties, new ByteArrayResource(policies.getBytes()));
    }

    private static CedarEntity payment(final String tenant, final String status) {
        CedarEntity tenantRef = CedarEntity.ref("Tenant", tenant);
        return CedarEntity.builder("Payment", "pay_1")
                .attribute("tenant", tenantRef)
                .attribute("status", status)
                .parent(tenantRef)
                .build();
    }

    private static AuthContext user(final String tenant, final String... scopes) {
        return new AuthContext("alice", tenant, Set.of(scopes), "r-1");
    }

    @Test
    void allowsSameTenantReadyPaymentWithScope() {
        AuthorizationDecision decision = service(CedarProperties.Mode.SHADOW, POLICIES)
                .decide(user("t_100", "payment:pay"), "payPayment", payment("t_100", "READY"));
        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasons()).isNotEmpty();
        assertThat(decision.enforced()).isFalse();
    }

    @Test
    void deniesCrossTenantEvenWithScope() {
        AuthorizationDecision decision = service(CedarProperties.Mode.ENFORCE, POLICIES)
                .decide(user("t_200", "payment:pay"), "payPayment", payment("t_100", "READY"));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.enforced()).isTrue();
    }

    @Test
    void deniesNonReadyPayment() {
        AuthorizationDecision decision = service(CedarProperties.Mode.SHADOW, POLICIES)
                .decide(user("t_100", "payment:pay"), "payPayment", payment("t_100", "CLOSED"));
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void deniesMissingScope() {
        AuthorizationDecision decision = service(CedarProperties.Mode.SHADOW, POLICIES)
                .decide(user("t_100", "payment:read"), "payPayment", payment("t_100", "READY"));
        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void servicePrincipalGoesThroughSamePath() {
        AuthorizationDecision decision = service(CedarProperties.Mode.SHADOW, POLICIES)
                .decide(new AuthContext("svc:checkout-be", "t_100", Set.of("payment:pay"), "r-2"),
                        "payPayment", payment("t_100", "READY"));
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void shadowModeSurvivesBadPolicyAndReportsErrorDecisions() {
        CedarAuthorizationService broken = service(CedarProperties.Mode.SHADOW, "not cedar at all ;;;");
        AuthorizationDecision decision = broken.decide(user("t_100", "payment:pay"),
                "payPayment", payment("t_100", "READY"));
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.error()).contains("load failed");
    }

    @Test
    void enforceModeRefusesToStartOnBadPolicy() {
        assertThatThrownBy(() -> service(CedarProperties.Mode.ENFORCE, "not cedar at all ;;;"))
                .isInstanceOf(CedarAuthorizationException.class);
    }
}
