package io.labs64.authcontext.cedar;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.cedarpolicy.BasicAuthorizationEngine;

/**
 * Cedar domain PEP wiring. Doubly opt-in: the module must add the
 * optional {@code com.cedarpolicy:cedar-java} (uber) dependency AND set
 * {@code labs64.auth.cedar.enabled=true}. Registered after the coarse
 * interceptors so {@code @RequireScopes}/{@code @RequireTenant} stay the fast
 * pre-filter.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(BasicAuthorizationEngine.class)
@ConditionalOnProperty(prefix = "labs64.auth.cedar", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CedarProperties.class)
public class CedarAutoConfiguration {

    /** Coarse interceptors register at order 0 — Cedar runs after them. */
    private static final int CEDAR_INTERCEPTOR_ORDER = 100;

    @Bean
    @ConditionalOnMissingBean
    public CedarAuthorizationService cedarAuthorizationService(final CedarProperties properties,
            final ResourceLoader resourceLoader) {
        return new CedarAuthorizationService(properties, resourceLoader.getResource(properties.getPolicyLocation()));
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationDecisionListener.class)
    public LoggingDecisionListener loggingDecisionListener() {
        return new LoggingDecisionListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizeInterceptor authorizeInterceptor(final CedarAuthorizationService service,
            final List<CedarEntityResolver> resolvers, final List<AuthorizationDecisionListener> listeners) {
        return new AuthorizeInterceptor(service, resolvers, listeners);
    }

    @Bean
    public WebMvcConfigurer cedarWebMvcConfigurer(final AuthorizeInterceptor authorizeInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry registry) {
                registry.addInterceptor(authorizeInterceptor).order(CEDAR_INTERCEPTOR_ORDER);
            }
        };
    }
}
