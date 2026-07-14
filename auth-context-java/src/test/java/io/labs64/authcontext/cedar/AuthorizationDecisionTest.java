package io.labs64.authcontext.cedar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AuthorizationDecisionTest {

    private AuthorizationDecision d(final boolean allowed, final boolean enforced, final String error) {
        return new AuthorizationDecision("payPayment", "Payment", "pay_1", allowed, enforced,
                List.of("policy0"), error, "alice", "t_100", "r-1");
    }

    @Test
    void decisionReflectsAllowDenyError() {
        assertThat(d(true, true, null).decision()).isEqualTo("allow");
        assertThat(d(false, true, null).decision()).isEqualTo("deny");
        assertThat(d(false, true, "boom").decision()).isEqualTo("error");
    }

    @Test
    void outcomeCombinesPhaseAndEffectiveResult() {
        assertThat(d(true, true, null).outcome()).isEqualTo("enforced-allow");
        assertThat(d(false, true, null).outcome()).isEqualTo("enforced-deny");
        assertThat(d(true, false, null).outcome()).isEqualTo("shadow-allow");
        assertThat(d(false, false, null).outcome()).isEqualTo("shadow-deny");
    }

    @Test
    void errorInEnforceIsADeny() {
        // fail-closed: an errored enforce decision blocked the request
        assertThat(d(false, true, "boom").outcome()).isEqualTo("enforced-deny");
    }
}
