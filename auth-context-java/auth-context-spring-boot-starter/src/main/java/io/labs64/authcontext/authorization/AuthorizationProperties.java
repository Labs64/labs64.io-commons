package io.labs64.authcontext.authorization;

import java.time.Duration;

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

    /**
     * Deadline of one PDP call. The Cerbos SDK default is 500 ms, which the first call on a cold
     * JVM exceeds (it also opens the gRPC channel), and every miss is a fail-closed 403.
     */
    private Duration timeout = Duration.ofSeconds(2);

    /**
     * Make one PDP call at startup, before the application reports ready, so the first real
     * request finds an open channel. A failure is logged and does not stop the application.
     */
    private boolean warmUp = true;

    /**
     * How long an allow/deny from the PDP is reused for the same principal (user, tenant, scopes),
     * action and resource (type, id, attributes). Zero (the default) asks the PDP every time. A
     * policy change then takes effect within this time. PDP errors are never cached.
     */
    private Duration decisionCacheTtl = Duration.ZERO;

    /** Upper bound of cached decisions; a full cache drops its expired entries, then all of them. */
    private int decisionCacheMaxEntries = 2048;

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

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isWarmUp() {
        return warmUp;
    }

    public void setWarmUp(final boolean warmUp) {
        this.warmUp = warmUp;
    }

    public Duration getDecisionCacheTtl() {
        return decisionCacheTtl;
    }

    public void setDecisionCacheTtl(final Duration decisionCacheTtl) {
        this.decisionCacheTtl = decisionCacheTtl == null ? Duration.ZERO : decisionCacheTtl;
    }

    public int getDecisionCacheMaxEntries() {
        return decisionCacheMaxEntries;
    }

    public void setDecisionCacheMaxEntries(final int decisionCacheMaxEntries) {
        this.decisionCacheMaxEntries = decisionCacheMaxEntries;
    }
}
