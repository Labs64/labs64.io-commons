package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHolder;
import io.labs64.authcontext.core.AuthContextParser;
import io.labs64.authcontext.web.AuthContextFilter;
import io.labs64.authcontext.web.AuthContextProperties;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Executes the shared cross-language behavior vectors
 * (test-vectors/auth-context-vectors.json). The Python library runs the same
 * file; a change in behavior must update the vectors, not just one
 * implementation.
 */
class AuthContextFilterVectorsTest {

    private static final String PROTECTED_PATH = "/customers";
    private static final String PUBLIC_PATH = "/v3/api-docs";

    @TestFactory
    Stream<DynamicTest> vectors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/test-vectors/auth-context-vectors.json")) {
            root = mapper.readTree(in);
        }
        List<JsonNode> cases = root.get("cases").findParents("name");
        return cases.stream().map(vector -> DynamicTest.dynamicTest(vector.get("name").asText(), () -> run(vector)));
    }

    private void run(JsonNode vector) throws Exception {
        boolean publicPath = vector.get("public").asBoolean();
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                publicPath ? PUBLIC_PATH : PROTECTED_PATH);
        vector.get("headers").properties()
                .forEach(entry -> request.addHeader(entry.getKey(), entry.getValue().asText()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<AuthContext> captured = new AtomicReference<>();
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                    jakarta.servlet.http.HttpServletResponse res) {
                chainCalled.set(true);
                AuthContextHolder.get().ifPresent(captured::set);
            }
        });

        new AuthContextFilter(new AuthContextProperties(), new AuthContextParser()).doFilter(request, response, chain);

        JsonNode expect = vector.get("expect");
        String outcome = expect.get("outcome").asText();
        switch (outcome) {
        case "reject" -> {
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chainCalled.get()).isFalse();
        }
        case "anonymous" -> {
            assertThat(response.getStatus()).isNotEqualTo(401);
            assertThat(chainCalled.get()).isTrue();
            assertThat(captured.get()).isNull();
        }
        case "accept" -> {
            assertThat(response.getStatus()).isNotEqualTo(401);
            assertThat(chainCalled.get()).isTrue();
            AuthContext context = captured.get();
            assertThat(context).as("context must be bound").isNotNull();
            assertThat(context.userId()).isEqualTo(expect.get("user").asText());
            if (expect.get("tenant").isNull()) {
                assertThat(context.tenantId()).isNull();
            } else {
                assertThat(context.tenantId()).isEqualTo(expect.get("tenant").asText());
            }
            var expectedScopes = new HashSet<String>();
            expect.get("scopes").forEach(scope -> expectedScopes.add(scope.asText()));
            assertThat(context.scopes()).isEqualTo(expectedScopes);
            String expectedRequestId = expect.get("requestId").asText();
            if ("GENERATED".equals(expectedRequestId)) {
                assertThat(context.requestId()).isNotBlank()
                        .isNotEqualTo(Map.of().toString());
            } else {
                assertThat(context.requestId()).isEqualTo(expectedRequestId);
            }
            if (expect.has("servicePrincipal")) {
                assertThat(context.isServicePrincipal()).isEqualTo(expect.get("servicePrincipal").asBoolean());
            }
        }
        default -> throw new IllegalStateException("Unknown outcome: " + outcome);
        }
        // The holder must never leak past the request
        assertThat(AuthContextHolder.get()).isEmpty();
    }
}

