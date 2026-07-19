package io.labs64.authcontext.authorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@code @Authorize} domain PEP.
 *
 * <p>{@code labs64.auth.authz.enabled=true} activates the {@code @Authorize}
 * interceptor, which delegates decisions to the central Cerbos PDP at
 * {@link #getPdpAddress()}. {@code mode=SHADOW} (default) evaluates and
 * publishes decisions without blocking — the mandatory pre-enforcement diff;
 * {@code mode=ENFORCE} denies with 403 (fail closed on any PDP error).
 */
@ConfigurationProperties(prefix = "labs64.auth.authz")
public class AuthorizationProperties {

    public enum Mode {
        SHADOW, ENFORCE
    }

    private boolean enabled = false;

    private Mode mode = Mode.SHADOW;

    /** Cerbos PDP gRPC address (host:port). In-cluster: cerbos.<ns>.svc.cluster.local:3593. */
    private String pdpAddress = "localhost:3593";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(final Mode mode) {
        this.mode = mode;
    }

    public String getPdpAddress() {
        return pdpAddress;
    }

    public void setPdpAddress(final String pdpAddress) {
        this.pdpAddress = pdpAddress;
    }
}
