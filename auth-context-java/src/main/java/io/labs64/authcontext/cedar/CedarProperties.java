package io.labs64.authcontext.cedar;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the Cedar domain PEP (RFC-05 P3).
 *
 * <p>{@code labs64.auth.cedar.enabled=true} plus the optional
 * {@code com.cedarpolicy:cedar-java} dependency activate the
 * {@code @Authorize} interceptor. {@code mode=SHADOW} (default) evaluates and
 * publishes decisions without blocking — the mandatory pre-enforcement diff;
 * {@code mode=ENFORCE} denies with 403 and refuses to start on an unloadable
 * policy set (fail closed).
 */
@ConfigurationProperties(prefix = "labs64.auth.cedar")
public class CedarProperties {

    public enum Mode {
        SHADOW, ENFORCE
    }

    private boolean enabled = false;

    private Mode mode = Mode.SHADOW;

    /**
     * Spring resource location of the module's domain Cedar policy set,
     * validated in CI against the shared schema in
     * {@code labs64.io-commons/auth-policy-cedar/schema.cedarschema}.
     */
    private String policyLocation = "classpath:cedar/domain.cedar";

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

    public String getPolicyLocation() {
        return policyLocation;
    }

    public void setPolicyLocation(final String policyLocation) {
        this.policyLocation = policyLocation;
    }
}
