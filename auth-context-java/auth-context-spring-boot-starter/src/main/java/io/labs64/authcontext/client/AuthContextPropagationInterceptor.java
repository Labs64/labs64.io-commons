package io.labs64.authcontext.client;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.labs64.authcontext.core.AuthContextHeaders;
import io.labs64.authcontext.core.AuthContextHolder;

/**
 * Propagates the bound auth context on in-cluster, on-behalf-of calls
 * (service-to-service): the callee authorizes against the original
 * user. Register on the service's RestClient/RestTemplate builder.
 */
public class AuthContextPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        AuthContextHolder.get().ifPresent(context -> {
            AuthContextHeaders.encode(context).forEach(request.getHeaders()::set);
        });
        return execution.execute(request, body);
    }
}

