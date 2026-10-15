package io.labs64.authcontext.cerbos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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

    // --- decision cache (labs64.auth.authz.decision-cache-ttl) ---

    private final AtomicLong now = new AtomicLong();

    private CerbosAuthorizationService cached(final CerbosBlockingClient client, final Duration ttl, final int max) {
        AuthorizationProperties properties = props(AuthorizationProperties.Mode.ENFORCE);
        properties.setDecisionCacheTtl(ttl);
        properties.setDecisionCacheMaxEntries(max);
        return new CerbosAuthorizationService(properties, client, now::get);
    }

    private static CerbosBlockingClient answering(final boolean allowed) {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        CheckResult result = mock(CheckResult.class);
        when(result.isAllowed(any())).thenReturn(allowed);
        when(client.check(any(), any(), any())).thenReturn(result);
        return client;
    }

    @Test
    void decisionCacheIsOffByDefault() {
        CerbosBlockingClient client = answering(true);
        CerbosAuthorizationService service =
                new CerbosAuthorizationService(props(AuthorizationProperties.Mode.ENFORCE), client);

        service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));
        service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        verify(client, times(2)).check(any(), any(), any());
    }

    @Test
    void repeatedDecisionWithinTtlIsAnsweredFromCache() {
        CerbosBlockingClient client = answering(true);
        CerbosAuthorizationService service = cached(client, Duration.ofSeconds(5), 100);

        AuthorizationDecision first = service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));
        AuthorizationDecision second = service.decide(
                new AuthContext("alice", "t_100", Set.of("payment:pay"), "r-2"), "payPayment", payment("t_100"));

        assertThat(first.decision()).isEqualTo("allow");
        assertThat(second.decision()).isEqualTo("allow");
        assertThat(second.requestId()).isEqualTo("r-2");
        verify(client, times(1)).check(any(), any(), any());
    }

    @Test
    void denyIsCachedToo() {
        CerbosBlockingClient client = answering(false);
        CerbosAuthorizationService service = cached(client, Duration.ofSeconds(5), 100);

        service.decide(ctx("mallory", "t_200"), "payPayment", payment("t_100"));
        AuthorizationDecision again = service.decide(ctx("mallory", "t_200"), "payPayment", payment("t_100"));

        assertThat(again.decision()).isEqualTo("deny");
        verify(client, times(1)).check(any(), any(), any());
    }

    @Test
    void anythingThePdpSeesIsPartOfTheKey() {
        CerbosBlockingClient client = answering(true);
        CerbosAuthorizationService service = cached(client, Duration.ofSeconds(5), 100);

        service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));
        service.decide(ctx("bob", "t_100"), "payPayment", payment("t_100"));                       // user
        service.decide(ctx("alice", "t_200"), "payPayment", payment("t_100"));                     // tenant
        service.decide(new AuthContext("alice", "t_100", Set.of("payment:read"), "r-1"),
                "payPayment", payment("t_100"));                                                     // scopes
        service.decide(ctx("alice", "t_100"), "refundPayment", payment("t_100"));                  // action
        service.decide(ctx("alice", "t_100"), "payPayment", payment("t_200"));                     // resource attribute
        service.decide(ctx("alice", "t_100"), "payPayment",
                ResourceEntity.builder("Payment", "pay_2").attribute("tenant", "t_100").build());   // resource id

        verify(client, times(7)).check(any(), any(), any());
    }

    @Test
    void decisionIsAskedAgainOnceTheTtlHasPassed() {
        CerbosBlockingClient client = answering(true);
        CerbosAuthorizationService service = cached(client, Duration.ofSeconds(5), 100);

        service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));
        now.addAndGet(Duration.ofSeconds(5).toNanos());
        service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100"));

        verify(client, times(2)).check(any(), any(), any());
    }

    @Test
    void pdpErrorsAreNotCached() {
        CerbosBlockingClient client = mock(CerbosBlockingClient.class);
        CheckResult result = mock(CheckResult.class);
        when(result.isAllowed(any())).thenReturn(true);
        when(client.check(any(), any(), any()))
                .thenThrow(grpcFailure("PERMISSION_DENIED"))
                .thenReturn(result);
        CerbosAuthorizationService service = cached(client, Duration.ofSeconds(5), 100);

        assertThat(service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100")).decision()).isEqualTo("error");
        assertThat(service.decide(ctx("alice", "t_100"), "payPayment", payment("t_100")).decision()).isEqualTo("allow");
        verify(client, times(2)).check(any(), any(), any());
    }

    @Test
    void cacheStaysWithinItsBound() {
        CerbosBlockingClient client = answering(true);
        CerbosAuthorizationService service = cached(client, Duration.ofSeconds(5), 2);

        service.decide(ctx("u1", "t_100"), "payPayment", payment("t_100"));
        service.decide(ctx("u2", "t_100"), "payPayment", payment("t_100"));
        service.decide(ctx("u3", "t_100"), "payPayment", payment("t_100"));   // full: cleared, then u3 stored
        service.decide(ctx("u3", "t_100"), "payPayment", payment("t_100"));   // hit
        service.decide(ctx("u1", "t_100"), "payPayment", payment("t_100"));   // was dropped: asked again

        verify(client, times(4)).check(any(), any(), any());
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
