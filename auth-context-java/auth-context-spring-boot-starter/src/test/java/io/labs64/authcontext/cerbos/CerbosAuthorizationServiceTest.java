package io.labs64.authcontext.cerbos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosException;
import dev.cerbos.sdk.CheckResult;

import io.labs64.authcontext.authorization.AuthorizationDecision;
import io.labs64.authcontext.authorization.AuthorizationProperties;
import io.labs64.authcontext.authorization.ResourceEntity;
import io.labs64.authcontext.core.AuthContext;

class CerbosAuthorizationServiceTest {

    private AuthorizationProperties props(final AuthorizationProperties.Mode mode) {
        AuthorizationProperties properties = new AuthorizationProperties();
        properties.setEnabled(true);
        properties.setMode(mode);
        return properties;
    }

    private AuthContext ctx(final String user, final String tenant) {
        return new AuthContext(user, tenant, Set.of("payment:pay"), "r-1");
    }

    private ResourceEntity payment(final String tenant) {
        return ResourceEntity.builder("Payment", "pay_1").attribute("tenant", tenant).build();
    }

    @Test
    void pdpFailureIsFailClosedErrorDecision() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        when(client.check(any(), any(), any())).thenThrow(new RuntimeException("UNAVAILABLE"));

        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.ENFORCE), client);
        AuthorizationDecision d = service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        assertThat(d.allowed()).isFalse();
        assertThat(d.decision()).isEqualTo("error");
        assertThat(d.enforced()).isTrue();
        assertThat(d.error()).contains("UNAVAILABLE");
    }

    @Test
    void allowedCheckMapsToAllowDecision() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        CheckResult result = mock(CheckResult.class);
        when(result.isAllowed("payPayment")).thenReturn(true);
        when(client.check(any(), any(), any())).thenReturn(result);

        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.ENFORCE), client);
        AuthorizationDecision d = service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        assertThat(d.allowed()).isTrue();
        assertThat(d.decision()).isEqualTo("allow");
        assertThat(d.enforced()).isTrue();
    }

    @Test
    void deniedCheckInShadowModeIsNotEnforced() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        CheckResult result = mock(CheckResult.class);
        when(result.isAllowed(any())).thenReturn(false);
        when(client.check(any(), any(), any())).thenReturn(result);

        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.SHADOW), client);
        assertThat(service.isEnforcing()).isFalse();
        AuthorizationDecision d = service.decide(ctx("mallory", "t_200"), "payPayment", payment("t_100"));

        assertThat(d.decision()).isEqualTo("deny");
        assertThat(d.enforced()).isFalse();
    }

    @Test
    void transientDeadlineIsRetriedOnce() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        CheckResult result = mock(CheckResult.class);
        when(result.isAllowed("payPayment")).thenReturn(true);
        when(client.check(any(), any(), any()))
                .thenThrow(grpcFailure("DEADLINE_EXCEEDED"))
                .thenReturn(result);

        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.ENFORCE), client);
        AuthorizationDecision d = service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        assertThat(d.decision()).isEqualTo("allow");
        verify(client, times(2)).check(any(), any(), any());
    }

    @Test
    void secondTransientFailureIsStillFailClosed() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        when(client.check(any(), any(), any())).thenThrow(grpcFailure("UNAVAILABLE"));

        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.ENFORCE), client);
        AuthorizationDecision d = service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        assertThat(d.decision()).isEqualTo("error");
        assertThat(d.allowed()).isFalse();
        verify(client, times(2)).check(any(), any(), any());
    }

    @Test
    void nonTransientFailureIsNotRetried() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        when(client.check(any(), any(), any())).thenThrow(grpcFailure("PERMISSION_DENIED"));

        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.ENFORCE), client);
        AuthorizationDecision d = service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        assertThat(d.decision()).isEqualTo("error");
        verify(client, times(1)).check(any(), any(), any());
    }

    /**
     * A real PDP RPC failure for a gRPC status (DEADLINE_EXCEEDED, UNAVAILABLE, PERMISSION_DENIED).
     * Built reflectively: io.grpc is on the runtime classpath through the Cerbos SDK, not the compile one.
     */
    private static CerbosException grpcFailure(final String statusName) {
        try {
            Class<?> status = Class.forName("io.grpc.Status");
            Object value = status.getField(statusName).get(null);
            return CerbosException.class.getConstructor(status, Throwable.class).newInstance(value, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
