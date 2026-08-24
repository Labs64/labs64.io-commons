package io.labs64.authcontext.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import io.labs64.authcontext.core.AuthContextHolder;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes authentication and authorization failures using the ecosystem-wide
 * {@code ErrorResponse} contract.
 */
public final class AuthErrorResponseWriter {

    private AuthErrorResponseWriter() {
    }

    public static void unauthorized(final HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Unauthorized");
    }

    public static void forbidden(final HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Forbidden");
    }

    private static void write(final HttpServletResponse response, final int status,
            final String code, final String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        final String traceId = AuthContextHolder.get()
                .map(context -> ",\"traceId\":\"" + context.requestId() + "\"")
                .orElse("");
        response.getWriter().write("""
                {"code":"%s","message":"%s","timestamp":"%s"%s}\
                """.formatted(code, message, Instant.now(), traceId));
    }
}
