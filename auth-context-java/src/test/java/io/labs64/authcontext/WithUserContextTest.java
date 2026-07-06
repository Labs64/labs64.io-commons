package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.labs64.authcontext.test.WithUserContext;

class WithUserContextTest {

    @Test
    @WithUserContext(user = "jdoe", tenant = "t_9", roles = { "admin-role" }, requestId = "req-9")
    void bindsAnnotatedContext() {
        UserContext context = UserContextHolder.require();
        assertThat(context.userId()).isEqualTo("jdoe");
        assertThat(context.tenantId()).isEqualTo("t_9");
        assertThat(context.hasRole("admin-role")).isTrue();
        assertThat(context.requestId()).isEqualTo("req-9");
    }

    @Test
    @WithUserContext(user = "svc:batch", tenant = "-")
    void dashTenantBindsTenantless() {
        UserContext context = UserContextHolder.require();
        assertThat(context.tenantId()).isNull();
        assertThat(context.isServicePrincipal()).isTrue();
    }

    @Test
    void noAnnotationMeansNoContext() {
        assertThat(UserContextHolder.get()).isEmpty();
    }
}
