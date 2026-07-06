package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;

import io.labs64.authcontext.client.AuthContextPropagationInterceptor;

class AuthContextPropagationInterceptorTest {

    private final AuthContextPropagationInterceptor interceptor = new AuthContextPropagationInterceptor();

    @AfterEach
    void cleanup() {
        UserContextHolder.clear();
    }

    @Test
    void propagatesAllContractHeaders() throws Exception {
        UserContextHolder.set(new UserContext("jdoe", "t_100", Set.of("admin-role"), "req-1"));
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://payment-gateway/payments");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(AuthHeaders.USER)).isEqualTo("jdoe");
        assertThat(request.getHeaders().getFirst(AuthHeaders.ROLES)).isEqualTo("admin-role");
        assertThat(request.getHeaders().getFirst(AuthHeaders.TENANT)).isEqualTo("t_100");
        assertThat(request.getHeaders().getFirst(AuthHeaders.REQUEST_ID)).isEqualTo("req-1");
    }

    @Test
    void tenantlessContextPropagatesDash() throws Exception {
        UserContextHolder.set(new UserContext("svc:checkout-be", null, Set.of(), "req-2"));
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://auditflow/audit");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(AuthHeaders.TENANT)).isEqualTo(AuthHeaders.TENANT_NONE);
        assertThat(request.getHeaders().getFirst(AuthHeaders.ROLES)).isEmpty();
    }

    @Test
    void noContextMeansNoHeaders() throws Exception {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, "http://auditflow/audit");
        ClientHttpRequestExecution execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(mock(ClientHttpResponse.class));

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().containsHeader(AuthHeaders.USER)).isFalse();
        assertThat(request.getHeaders().containsHeader(AuthHeaders.REQUEST_ID)).isFalse();
    }
}
