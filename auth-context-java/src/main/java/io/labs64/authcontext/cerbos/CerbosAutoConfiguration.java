package io.labs64.authcontext.cerbos;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CerbosClientBuilder;

import io.labs64.authcontext.authorization.AuthorizationDecisionListener;
import io.labs64.authcontext.authorization.AuthorizationProperties;
import io.labs64.authcontext.authorization.AuthorizationService;
import io.labs64.authcontext.authorization.AuthorizeInterceptor;
import io.labs64.authcontext.authorization.LoggingDecisionListener;
import io.labs64.authcontext.authorization.ResourceResolver;

/**
 * {@code @Authorize} domain PEP wiring (RFC-07). Opt-in via
 * {@code labs64.auth.authz.enabled=true}; decisions are delegated to the
 * central Cerbos PDP at {@code labs64.auth.authz.pdp-address}. Registered after
 * the coarse interceptors so {@code @RequireScopes}/{@code @RequireTenant} stay
 * the fast pre-filter.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "labs64.auth.authz", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AuthorizationProperties.class)
public class CerbosAutoConfiguration {

    /** Coarse interceptors register at order 0 — the domain PEP runs after them. */
    private static final int AUTHZ_INTERCEPTOR_ORDER = 100;

    @Bean
    @ConditionalOnMissingBean
    public CerbosBlockingClient cerbosBlockingClient(final AuthorizationProperties properties) throws Exception {
        return new CerbosClientBuilder(properties.getPdpAddress()).withPlaintext().buildBlockingClient();
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationService.class)
    public CerbosAuthorizationService authorizationService(final AuthorizationProperties properties,
            final CerbosBlockingClient client) {
        return new CerbosAuthorizationService(properties, client);
    }

    @Bean
    @ConditionalOnMissingBean(AuthorizationDecisionListener.class)
    public LoggingDecisionListener loggingDecisionListener() {
        return new LoggingDecisionListener();
    }

    @Bean
    @ConditionalOnMissingBean
    public AuthorizeInterceptor authorizeInterceptor(final AuthorizationService service,
            final List<ResourceResolver> resolvers, final List<AuthorizationDecisionListener> listeners) {
        return new AuthorizeInterceptor(service, resolvers, listeners);
    }

    @Bean
    public WebMvcConfigurer authzWebMvcConfigurer(final AuthorizeInterceptor authorizeInterceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(final InterceptorRegistry registry) {
                registry.addInterceptor(authorizeInterceptor).order(AUTHZ_INTERCEPTOR_ORDER);
            }
        };
    }
}
