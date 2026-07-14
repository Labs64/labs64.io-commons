package io.labs64.authcontext.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import io.labs64.authcontext.authorization.RequireScopesInterceptor;
import io.labs64.authcontext.authorization.RequireTenantInterceptor;
import io.labs64.authcontext.client.AuthContextPropagationInterceptor;
import io.labs64.authcontext.core.AuthContextParser;
import io.labs64.authcontext.web.AuthContextArgumentResolver;
import io.labs64.authcontext.web.AuthContextFilter;
import io.labs64.authcontext.web.AuthContextProperties;
import io.labs64.authcontext.web.AuthContextWebMvcConfigurer;
import io.labs64.authcontext.web.AuthPolicyController;

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
    public FilterRegistrationBean<AuthContextFilter> authContextFilter(AuthContextProperties properties,
            AuthContextParser parser) {
        FilterRegistrationBean<AuthContextFilter> registration = new FilterRegistrationBean<>(
                new AuthContextFilter(properties, parser));
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

