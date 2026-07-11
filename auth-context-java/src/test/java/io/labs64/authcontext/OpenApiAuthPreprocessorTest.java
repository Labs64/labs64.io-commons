package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.labs64.authcontext.openapi.OpenApiAuthPreprocessor;

class OpenApiAuthPreprocessorTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void addsOpenApiAnnotationsAndBuildsPolicyRoutes() {
        Map<String, Object> openApi = map("paths", map(
                "/payments", map(
                        "get", map(
                                "operationId", "listPayments",
                                "x-operation-extra-annotation", "@org.example.Existing",
                                "x-labs64-auth", map(
                                        "tenant", true,
                                        "scopes", List.of("payment:read"))),
                        "post", map("operationId", "createPayment")),
                "/health", map(
                        "get", map(
                                "operationId", "health",
                                "x-labs64-auth", map("public", true)))));

        Map<String, Object> policy = new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
        Map<String, Object> payments = (Map<String, Object>) paths.get("/payments");
        Map<String, Object> listPayments = (Map<String, Object>) payments.get("get");
        Map<String, Object> createPayment = (Map<String, Object>) payments.get("post");

        assertThat((List<String>) listPayments.get("x-operation-extra-annotation"))
                .containsExactly(
                        "@org.example.Existing",
                        "@io.labs64.authcontext.authorization.RequireTenant",
                        "@io.labs64.authcontext.authorization.RequireScopes({\"payment:read\"})");
        assertThat((List<String>) createPayment.get("x-operation-extra-annotation"))
                .containsExactly("@io.labs64.authcontext.authorization.PublicEndpoint");
        assertThat((List<Map<String, Object>>) policy.get("routes"))
                .extracting(route -> route.get("operationId"))
                .containsExactlyInAnyOrder("listPayments", "createPayment", "health");
    }

    @Test
    void writesPolicyAsJson() throws IOException {
        Path input = tempDir.resolve("openapi.yaml");
        Path openApiOutput = tempDir.resolve("generated-openapi.yaml");
        Path policyOutput = tempDir.resolve("auth-policy.json");
        Files.writeString(input, """
                openapi: 3.0.3
                paths:
                  /health:
                    get:
                      operationId: health
                      x-labs64-auth:
                        public: true
                """);

        new OpenApiAuthPreprocessor().process(input, openApiOutput, policyOutput);

        String policyJson = Files.readString(policyOutput);
        Map<String, Object> policy = new ObjectMapper().readValue(policyJson, new TypeReference<>() {
        });
        assertThat(policyJson).startsWith("{");
        assertThat(policy).containsEntry("version", 1);
        assertThat(openApiOutput).exists();
    }

    private static Map<String, Object> map(final Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
