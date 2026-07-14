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
                        "post", map("operationId", "createPayment",
                                "x-labs64-auth", map("public", true))),
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
    void emitsEdgeCedarPoliciesPerOperation() {
        Map<String, Object> openApi = map("paths", map(
                "/payments", map(
                        "get", map(
                                "operationId", "listPayments",
                                "x-labs64-auth", map(
                                        "tenant", true,
                                        "scopes", List.of("payment:read", "payment:admin")))),
                "/health", map(
                        "get", map(
                                "operationId", "health",
                                "x-labs64-auth", map("public", true)))));

        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        String cedar = preprocessor.cedarPolicies("payment-gateway", preprocessor.enrich(openApi));

        assertThat(cedar).contains("""
                @id("payment-gateway::listPayments")
                @path("/payments")
                @method("GET")
                @public("false")
                @tenantRequired("true")
                @scopes("payment:read,payment:admin")
                permit(
                  principal,
                  action == Labs64IO::Action::"invoke",
                  resource == Labs64IO::ApiOperation::"payment-gateway::listPayments"
                ) when { (context has tenant) && (context.scopes.contains("payment:read") || context.scopes.contains("payment:admin")) };
                """);
        assertThat(cedar).contains("""
                @id("payment-gateway::health")
                @path("/health")
                @method("GET")
                @public("true")
                @tenantRequired("false")
                @scopes("")
                permit(
                  principal,
                  action == Labs64IO::Action::"invoke",
                  resource == Labs64IO::ApiOperation::"payment-gateway::health"
                );
                """);
    }

    @Test
    void routingAnnotationsPreservePathTemplateBraces() {
        // Path-templated routes are the routing-table's hard case: the
        // traefik-authproxy's cedarpy-based parser reads @path back out of the
        // generated policy JSON verbatim, so the {param} braces must not get
        // mangled by Cedar string-literal escaping.
        Map<String, Object> openApi = map("paths", map(
                "/payments/{id}", map(
                        "get", map(
                                "operationId", "getPayment",
                                "x-labs64-auth", map(
                                        "tenant", true,
                                        "scopes", List.of("payment:write"))))));

        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        String cedar = preprocessor.cedarPolicies("payment-gateway", preprocessor.enrich(openApi));

        assertThat(cedar).contains("@path(\"/payments/{id}\")");
        assertThat(cedar).contains("@method(\"GET\")");
        assertThat(cedar).contains("@public(\"false\")");
        assertThat(cedar).contains("@tenantRequired(\"true\")");
        assertThat(cedar).contains("@scopes(\"payment:write\")");
    }

    @Test
    void emitsDomainCedarOnlyForOperationsDeclaringAResource() {
        Map<String, Object> openApi = map("paths", map(
                "/payments/{id}/pay", map(
                        "post", map(
                                "operationId", "payPayment",
                                "x-labs64-auth", map(
                                        "tenant", true,
                                        "scopes", List.of("payment:pay"),
                                        "resource", "Payment"))),
                "/providers", map(
                        "get", map(
                                "operationId", "listPaymentProviders",
                                "x-labs64-auth", map(
                                        "tenant", true,
                                        "scopes", List.of("payment-provider:read"))))));

        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        String cedar = preprocessor.cedarDomainPolicies("payment-gateway", preprocessor.enrich(openApi));

        // domain permit keyed on the operationId action against the typed resource
        assertThat(cedar).contains("""
                @id("payment-gateway::payPayment::domain")
                permit(
                  principal,
                  action == Labs64IO::Action::"payPayment",
                  resource
                ) when { (context has tenant) && (context.scopes.contains("payment:pay")) };
                """);
        // one structural tenant guard for the resource type
        assertThat(cedar).contains("""
                @id("payment-gateway::tenant-guard::Payment")
                forbid(
                  principal,
                  action,
                  resource is Labs64IO::Payment
                ) when { resource has tenant && !(principal in resource.tenant) };
                """);
        // operation without a resource declaration gets NO domain policy
        assertThat(cedar).doesNotContain("listPaymentProviders");
    }

    @Test
    void writesCedarOutputWhenRequested() throws IOException {
        Path input = tempDir.resolve("openapi.yaml");
        Files.writeString(input, """
                openapi: 3.0.3
                paths:
                  /health:
                    get:
                      operationId: health
                      x-labs64-auth:
                        public: true
                """);
        Path cedarOutput = tempDir.resolve("module.cedar");

        new OpenApiAuthPreprocessor().process(input, tempDir.resolve("out.yaml"), tempDir.resolve("policy.json"),
                cedarOutput, "auditflow");

        assertThat(Files.readString(cedarOutput)).contains("Labs64IO::ApiOperation::\"auditflow::health\"");
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

    @Test
    void throwsExceptionWhenNoAuthDetailsProvided() {
        Map<String, Object> openApi = map("paths", map(
                "/health", map(
                        "get", map(
                                "operationId", "health"))));

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenApiAuthPreprocessor().enrich(openApi))
                .withMessageContaining("must declare either 'public: true' or specify 'tenant'/'scopes'");
    }

    @Test
    void throwsExceptionWhenDomainResourceDeclaredWithoutTenant() {
        Map<String, Object> openApi = map("paths", map(
                "/payments", map(
                        "post", map(
                                "operationId", "createPayment",
                                "x-labs64-auth", map(
                                        "scopes", List.of("payment:write"),
                                        "resource", "Payment")))));

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenApiAuthPreprocessor().enrich(openApi))
                .withMessageContaining("must require a tenant when declaring a domain resource");
    }

    private static Map<String, Object> map(final Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
