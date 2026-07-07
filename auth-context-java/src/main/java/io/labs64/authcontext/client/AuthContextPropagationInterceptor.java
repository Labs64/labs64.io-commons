package io.labs64.authcontext.client;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.labs64.authcontext.AuthHeaders;

/**
 * Propagates the bound auth context on in-cluster, on-behalf-of calls
 * (service-to-service): the callee authorizes against the original
 * user. Register on the service's RestClient/RestTemplate builder.
 */
public class AuthContextPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        io.labs64.authcontext.UserContextHolder.get().ifPresent(context -> {
            request.getHeaders().set(AuthHeaders.USER, context.userId());
            request.getHeaders().set(AuthHeaders.ROLES, String.join(",", context.roles()));
            request.getHeaders().set(AuthHeaders.TENANT,
                    context.tenantId() == null ? AuthHeaders.TENANT_NONE : context.tenantId());
            request.getHeaders().set(AuthHeaders.REQUEST_ID, context.requestId());
        });
        return execution.execute(request, body);
    }
}
