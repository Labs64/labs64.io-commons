package io.labs64.openapi.autoconfigure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Configures the runtime OpenAPI document exposed by springdoc.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OpenAPI.class, OpenApiCustomizer.class})
@ConditionalOnProperty(prefix = "labs64.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(Labs64OpenApiProperties.class)
public class Labs64OpenApiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(Labs64OpenApiAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "labs64OpenApiCustomizer")
    public OpenApiCustomizer labs64OpenApiCustomizer(
            Labs64OpenApiProperties properties,
            ResourceLoader resourceLoader) {
        return openApi -> {
            configureServers(openApi, properties);
            configureSecurity(openApi, properties.getSecurity());
            configureInfo(openApi, properties.getSpecLocation(), resourceLoader);
        };
    }

    private static void configureServers(OpenAPI openApi, Labs64OpenApiProperties properties) {
        List<Server> servers = new ArrayList<>();
        addServer(servers, properties.getGatewayPath(), properties.getGatewayDescription());
        addServer(servers, properties.getLocalPath(), properties.getLocalDescription());
        if (!servers.isEmpty()) {
            openApi.setServers(servers);
        }
    }

    private static void addServer(List<Server> servers, String url, String description) {
        if (StringUtils.hasText(url)) {
            servers.add(new Server().url(url).description(description));
        }
    }

    private static void configureSecurity(OpenAPI openApi, Labs64OpenApiProperties.Security security) {
        if (!security.isEnabled()) {
            return;
        }

        SecurityScheme scheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat(security.getBearerFormat())
                .description(security.getDescription());

        Components components = openApi.getComponents() != null ? openApi.getComponents() : new Components();
        components.addSecuritySchemes(security.getSchemeName(), scheme);
        openApi.setComponents(components);
        openApi.setSecurity(List.of(new SecurityRequirement().addList(security.getSchemeName())));
    }

    private static void configureInfo(OpenAPI openApi, String specLocation, ResourceLoader resourceLoader) {
        if (!StringUtils.hasText(specLocation)) {
            return;
        }

        Resource resource = resourceLoader.getResource(specLocation);
        if (!resource.exists()) {
            log.warn("OpenAPI specification resource not found: {}", specLocation);
            return;
        }

        try {
            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
            yaml.setResources(resource);
            Properties values = yaml.getObject();
            if (values != null) {
                openApi.setInfo(toInfo(values));
            }
        } catch (RuntimeException exception) {
            log.warn("Unable to read OpenAPI info from {}", specLocation, exception);
        }
    }

    private static Info toInfo(Properties values) {
        Contact contact = new Contact()
                .name(values.getProperty("info.contact.name"))
                .url(values.getProperty("info.contact.url"))
                .email(values.getProperty("info.contact.email"));

        return new Info()
                .title(values.getProperty("info.title"))
                .version(values.getProperty("info.version"))
                .description(values.getProperty("info.description"))
                .contact(contact);
    }
}
