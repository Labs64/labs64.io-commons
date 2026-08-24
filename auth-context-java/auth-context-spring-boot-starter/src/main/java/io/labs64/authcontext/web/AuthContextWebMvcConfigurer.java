package io.labs64.authcontext.web;

import java.util.List;

import io.labs64.authcontext.authorization.RequireScopesInterceptor;
import io.labs64.authcontext.authorization.RequireTenantInterceptor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the MVC integration points used by the auth-context starter.
 */
public class AuthContextWebMvcConfigurer implements WebMvcConfigurer {

    private final AuthContextArgumentResolver authContextArgumentResolver;
    private final RequireTenantInterceptor requireTenantInterceptor;
    private final RequireScopesInterceptor requireScopesInterceptor;

    public AuthContextWebMvcConfigurer(final AuthContextArgumentResolver authContextArgumentResolver,
            final RequireTenantInterceptor requireTenantInterceptor,
            final RequireScopesInterceptor requireScopesInterceptor) {
        this.authContextArgumentResolver = authContextArgumentResolver;
        this.requireTenantInterceptor = requireTenantInterceptor;
        this.requireScopesInterceptor = requireScopesInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authContextArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requireTenantInterceptor);
        registry.addInterceptor(requireScopesInterceptor);
    }
}
