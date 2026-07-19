package io.labs64.authcontext.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import io.labs64.authcontext.core.AuthContextParseException;
import io.labs64.authcontext.core.AuthContextParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Parses the trusted gateway headers into a {@link AuthContext} and binds it
 * for the request. On non-public paths a missing or invalid user identity is
 * rejected with 401 (fail closed). On public paths the context is populated
 * when present and valid, anonymous otherwise.
 */
public class AuthContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthContextFilter.class);

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_TENANT_ID = "tenantId";

    private final AuthContextProperties properties;
    private final AuthContextParser parser;
    private final PublicPathMatcher publicPathMatcher;

    public AuthContextFilter(AuthContextProperties properties, AuthContextParser parser) {
        this(properties, parser, PublicPathMatcher.empty());
    }

    public AuthContextFilter(AuthContextProperties properties, AuthContextParser parser,
            PublicPathMatcher publicPathMatcher) {
        this.properties = properties;
        this.parser = parser;
        this.publicPathMatcher = publicPathMatcher;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean publicPath = isPublicPath(request.getMethod(), request.getRequestURI());
        AuthContext context;
        try {
            context = parser.parse(request::getHeader).orElse(null);
        } catch (AuthContextParseException ex) {
            if (!publicPath) {
                log.warn("Rejecting protected request with malformed {} header", ex.headerName());
                reject(response);
                return;
            }
            log.warn("Ignoring malformed {} header on public request", ex.headerName());
            filterChain.doFilter(request, response);
            return;
        }

        if (context == null && !publicPath) {
            reject(response);
            return;
        }

        if (context == null) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthContextHolder.set(context);
        MDC.put(MDC_REQUEST_ID, context.requestId());
        if (context.tenantId() != null) {
            MDC.put(MDC_TENANT_ID, context.tenantId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthContextHolder.clear();
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TENANT_ID);
        }
    }

    private boolean isPublicPath(String method, String uri) {
        // OpenAPI operations: matched per method+template from the build-generated
        // list (derived from x-labs64-auth.public) — OpenAPI is the source of truth.
        if (publicPathMatcher.matches(method, uri)) {
            return true;
        }
        // Non-OpenAPI surfaces (actuator, docs, error): configured prefixes.
        for (String prefix : properties.getPublicPaths()) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\"}");
    }
}

