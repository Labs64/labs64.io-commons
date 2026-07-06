package io.labs64.authcontext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Parses the trusted gateway headers into a {@link UserContext} and binds it
 * for the request. On non-public paths a missing or invalid user identity is
 * rejected with 401 (fail closed). On public paths the context is populated
 * when present and valid, anonymous otherwise.
 */
public class AuthContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthContextFilter.class);

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_TENANT_ID = "tenantId";

    private final AuthContextProperties properties;

    public AuthContextFilter(AuthContextProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean publicPath = isPublicPath(request.getRequestURI());
        UserContext context = parse(request);

        if (context == null && !publicPath) {
            reject(response);
            return;
        }

        if (context == null) {
            filterChain.doFilter(request, response);
            return;
        }

        UserContextHolder.set(context);
        MDC.put(MDC_REQUEST_ID, context.requestId());
        if (context.tenantId() != null) {
            MDC.put(MDC_TENANT_ID, context.tenantId());
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_TENANT_ID);
        }
    }

    /** @return the parsed context, or {@code null} when the identity is missing/invalid */
    private UserContext parse(HttpServletRequest request) {
        String user = request.getHeader(AuthHeaders.USER);
        if (!AuthHeaders.isValidValue(user)) {
            if (user != null && !user.isEmpty()) {
                log.warn("Rejecting request with malformed {} header", AuthHeaders.USER);
            }
            return null;
        }

        List<String> roles = AuthHeaders.parseRoles(request.getHeader(AuthHeaders.ROLES));
        if (roles == null) {
            log.warn("Rejecting request with malformed {} header", AuthHeaders.ROLES);
            return null;
        }

        String tenant = request.getHeader(AuthHeaders.TENANT);
        if (tenant == null || tenant.isEmpty() || AuthHeaders.TENANT_NONE.equals(tenant)) {
            tenant = null;
        } else if (!AuthHeaders.isValidValue(tenant)) {
            log.warn("Rejecting request with malformed {} header", AuthHeaders.TENANT);
            return null;
        }

        String requestId = request.getHeader(AuthHeaders.REQUEST_ID);
        if (!AuthHeaders.isValidValue(requestId)) {
            requestId = UUID.randomUUID().toString();
        }

        return new UserContext(user, tenant, Set.copyOf(roles), requestId);
    }

    private boolean isPublicPath(String uri) {
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
