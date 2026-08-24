package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import org.junit.jupiter.api.Test;

import io.labs64.authcontext.test.WithAuthContext;

class WithAuthContextTest {

    @Test
    @WithAuthContext(user = "jdoe", tenant = "t_9", scopes = { "account:read" }, requestId = "req-9")
    void bindsAnnotatedContext() {
        AuthContext context = AuthContextHolder.require();
        assertThat(context.userId()).isEqualTo("jdoe");
        assertThat(context.tenantId()).isEqualTo("t_9");
        assertThat(context.hasScope("account:read")).isTrue();
        assertThat(context.requestId()).isEqualTo("req-9");
    }

    @Test
    @WithAuthContext(user = "svc:batch", tenant = "-")
    void dashTenantBindsTenantless() {
        AuthContext context = AuthContextHolder.require();
        assertThat(context.tenantId()).isNull();
        assertThat(context.isServicePrincipal()).isTrue();
    }

    @Test
    void noAnnotationMeansNoContext() {
        assertThat(AuthContextHolder.get()).isEmpty();
    }
}

