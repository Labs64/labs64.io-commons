package io.labs64.authcontext;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the auth-context filter.
 *
 * <p>{@code public-paths} are request-path prefixes served without a mandatory
 * user context (matching the gateway's public routes: API docs, health). All
 * other paths fail closed with 401 when the gateway headers are missing, so an
 * accidentally exposed service rejects unauthenticated traffic.
 */
@ConfigurationProperties(prefix = "labs64.auth-context")
public class AuthContextProperties {

    private boolean enabled = true;

    private List<String> publicPaths = List.of("/actuator", "/v3/api-docs", "/swagger-ui", "/error");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }
}
