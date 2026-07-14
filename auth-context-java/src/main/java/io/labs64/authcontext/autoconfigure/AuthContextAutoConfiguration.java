package io.labs64.authcontext.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import io.labs64.authcontext.authorization.RequireScopesInterceptor;
import io.labs64.authcontext.authorization.RequireTenantInterceptor;
import io.labs64.authcontext.client.AuthContextPropagationInterceptor;
import io.labs64.authcontext.core.AuthContextParser;
import io.labs64.authcontext.web.AuthContextArgumentResolver;
import io.labs64.authcontext.web.AuthContextFilter;
import io.labs64.authcontext.web.AuthContextProperties;
import io.labs64.authcontext.web.AuthContextWebMvcConfigurer;
import io.labs64.authcontext.web.AuthPolicyController;
import io.labs64.authcontext.web.PublicPathMatcher;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "labs64.auth-context", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuthContextProperties.class)
public class AuthContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AuthContextParser authContextParser() {
        return new AuthContextParser();
    }

    @Bean
    @ConditionalOnMissingBean
    public PublicPathMatcher publicPathMatcher(AuthContextProperties properties, ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource(properties.getPublicPathsLocation());
        if (!resource.exists()) {
            // No generated list (module has no public OpenAPI operations, or the
            // build step is not wired): fall back to configured prefixes only.
            return PublicPathMatcher.empty();
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().toList();
            return PublicPathMatcher.fromLines(lines);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to read generated public paths from " + properties.getPublicPathsLocation(), e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<AuthContextFilter> authContextFilter(AuthContextProperties properties,
            AuthContextParser parser, PublicPathMatcher publicPathMatcher) {
        FilterRegistrationBean<AuthContextFilter> registration = new FilterRegistrationBean<>(
                new AuthContextFilter(properties, parser, publicPathMatcher));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthContextPropagationInterceptor authContextPropagationInterceptor() {
        return new AuthContextPropagationInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthContextArgumentResolver authContextArgumentResolver() {
        return new AuthContextArgumentResolver();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequireTenantInterceptor requireTenantInterceptor() {
        return new RequireTenantInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequireScopesInterceptor requireScopesInterceptor() {
        return new RequireScopesInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(AuthContextWebMvcConfigurer.class)
    public AuthContextWebMvcConfigurer authContextWebMvcConfigurer(
            AuthContextArgumentResolver authContextArgumentResolver,
            RequireTenantInterceptor requireTenantInterceptor,
            RequireScopesInterceptor requireScopesInterceptor) {
        return new AuthContextWebMvcConfigurer(authContextArgumentResolver, requireTenantInterceptor,
                requireScopesInterceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnResource(resources = "classpath:auth-policy.json")
    public AuthPolicyController authPolicyController() {
        return new AuthPolicyController();
    }
}
