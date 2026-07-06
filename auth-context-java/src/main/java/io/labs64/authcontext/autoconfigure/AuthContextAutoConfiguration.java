package io.labs64.authcontext.autoconfigure;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.labs64.authcontext.AuthContextFilter;
import io.labs64.authcontext.AuthContextProperties;
import io.labs64.authcontext.client.AuthContextPropagationInterceptor;
import io.labs64.authcontext.web.RequireRoleInterceptor;
import io.labs64.authcontext.web.UserContextArgumentResolver;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "labs64.auth-context", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AuthContextProperties.class)
public class AuthContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<AuthContextFilter> authContextFilter(AuthContextProperties properties) {
        FilterRegistrationBean<AuthContextFilter> registration = new FilterRegistrationBean<>(
                new AuthContextFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthContextPropagationInterceptor authContextPropagationInterceptor() {
        return new AuthContextPropagationInterceptor();
    }

    @Bean
    public WebMvcConfigurer authContextWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new UserContextArgumentResolver());
            }

            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new RequireRoleInterceptor());
            }
        };
    }
}
