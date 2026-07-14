package io.labs64.openapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Runtime OpenAPI settings shared by Labs64.IO services.
 */
@ConfigurationProperties(prefix = "labs64.openapi")
public class Labs64OpenApiProperties {

    private boolean enabled = true;
    private String specLocation;
    private String gatewayPath;
    private String gatewayDescription = "Via API Gateway (Traefik)";
    private String localPath = "/";
    private String localDescription = "Local Development Server (direct, root-mapped)";
    private final Security security = new Security();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSpecLocation() {
        return specLocation;
    }

    public void setSpecLocation(String specLocation) {
        this.specLocation = specLocation;
    }

    public String getGatewayPath() {
        return gatewayPath;
    }

    public void setGatewayPath(String gatewayPath) {
        this.gatewayPath = gatewayPath;
    }

    public String getGatewayDescription() {
        return gatewayDescription;
    }

    public void setGatewayDescription(String gatewayDescription) {
        this.gatewayDescription = gatewayDescription;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getLocalDescription() {
        return localDescription;
    }

    public void setLocalDescription(String localDescription) {
        this.localDescription = localDescription;
    }

    public Security getSecurity() {
        return security;
    }

    /**
     * OpenAPI bearer authentication scheme settings.
     */
    public static class Security {

        private boolean enabled = true;
        private String schemeName = "bearerAuth";
        private String bearerFormat = "JWT";
        private String description = "JWT authentication token";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSchemeName() {
            return schemeName;
        }

        public void setSchemeName(String schemeName) {
            this.schemeName = schemeName;
        }

        public String getBearerFormat() {
            return bearerFormat;
        }

        public void setBearerFormat(String bearerFormat) {
            this.bearerFormat = bearerFormat;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
