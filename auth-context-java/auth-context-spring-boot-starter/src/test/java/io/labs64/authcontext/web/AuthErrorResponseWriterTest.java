package io.labs64.authcontext.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.Set;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AuthErrorResponseWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void writesUnauthorizedUsingStandardErrorResponseContract() throws Exception {
        assertResponse(AuthErrorResponseWriter::unauthorized, 401, "UNAUTHORIZED", "Unauthorized");
    }

    @Test
    void writesForbiddenUsingStandardErrorResponseContract() throws Exception {
        assertResponse(AuthErrorResponseWriter::forbidden, 403, "FORBIDDEN", "Forbidden");
    }

    @Test
    void usesAuthContextRequestIdAsTraceId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthContextHolder.set(new AuthContext("jdoe", "t_100", Set.of(), "request-123"));
        try {
            AuthErrorResponseWriter.forbidden(response);
        } finally {
            AuthContextHolder.clear();
        }

        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.get("traceId").asText()).isEqualTo("request-123");
    }

    private static void assertResponse(final ResponseWriter writer, final int status,
            final String code, final String message) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response);

        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        JsonNode body = MAPPER.readTree(response.getContentAsString());
        assertThat(body.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("code", "message", "timestamp");
        assertThat(body.get("code").asText()).isEqualTo(code);
        assertThat(body.get("message").asText()).isEqualTo(message);
        assertThatCode(() -> Instant.parse(body.get("timestamp").asText()))
                .doesNotThrowAnyException();
    }

    @FunctionalInterface
    private interface ResponseWriter {
        void write(MockHttpServletResponse response) throws Exception;
    }
}
