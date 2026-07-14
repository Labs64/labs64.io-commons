package io.labs64.authcontext.web;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the auth-context filter.
 *
 * <p>{@code public-paths} are request-path prefixes served without a mandatory
 * auth context — reserved for NON-OpenAPI surfaces (actuator, API docs, error).
 * Public OpenAPI operations are NOT listed here: they come from the
 * build-generated {@code public-paths-location} resource ({@link PublicPathMatcher}),
 * derived from {@code x-labs64-auth.public}, so OpenAPI stays the single source of
 * truth. All other paths fail closed with 401 when the gateway headers are missing,
 * so an accidentally exposed service rejects unauthenticated traffic.
 */
@ConfigurationProperties(prefix = "labs64.auth-context")
public class AuthContextProperties {

    private boolean enabled = true;

    private List<String> publicPaths = List.of("/actuator", "/v3/api-docs", "/swagger-ui", "/error");

    private String publicPathsLocation = "classpath:auth-public-paths";

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

    public String getPublicPathsLocation() {
        return publicPathsLocation;
    }

    public void setPublicPathsLocation(String publicPathsLocation) {
        this.publicPathsLocation = publicPathsLocation;
    }
}

