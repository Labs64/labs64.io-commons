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

import io.labs64.authcontext.openapi.OpenApiAuthPreprocessor;

class OpenApiAuthPreprocessorTest {

    @TempDir
    Path tempDir;

    /**
     * Three-pattern fixture: one {@code public} op, one
     * {@code tenant + scopes + resource} op, one {@code scopes}-only op — the
     * shapes the Cerbos generator must translate.
     */
    private Map<String, Object> openApiFixture() {
        return map("paths", map(
                "/payment-definitions", map(
                        "get", map("operationId", "listPaymentDefinitions",
                                "x-labs64-auth", map("public", true))),
                "/payments/{id}/pay", map(
                        "post", map("operationId", "payPayment",
                                "x-labs64-auth", map(
                                        "tenant", true,
                                        "scopes", List.of("payment:pay"),
                                        "resource", "Payment"))),
                "/events", map(
                        "post", map("operationId", "publishEvent",
                                "x-labs64-auth", map(
                                        "scopes", List.of("audit-event:write"))))));
    }

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
    void cerbosResourceKindNormalisesModuleName() {
        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        assertThat(preprocessor.cerbosResourceKind("payment-gateway")).isEqualTo("payment_gateway_api");
        assertThat(preprocessor.cerbosResourceKind("auditflow")).isEqualTo("auditflow_api");
    }

    @Test
    void cerbosEdgePolicyTranslatesThreePatterns() throws IOException {
        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        Map<String, Object> policy = preprocessor.enrich(openApiFixture());
        Map<String, String> files = preprocessor.cerbosPolicies("payment-gateway", policy);
        String edge = files.get("payment_gateway_api.yaml");

        assertThat(edge).contains("resource: payment_gateway_api");
        // public op: an ALLOW action listed by operationId
        assertThat(edge).contains("- listPaymentDefinitions");
        // scope OR-check + tenant/service exemption for the protected op (fragments
        // asserted without embedded quotes so they survive YAML scalar escaping)
        assertThat(edge).contains("request.principal.attr.scopes.exists(s, s ==");
        assertThat(edge).contains("has(request.principal.attr.tenant)");
        assertThat(edge).contains("in request.principal.roles)");
        assertThat(edge).contains("cerbos:///principal.json");
    }

    @Test
    void cerbosDomainPolicyCarriesTenantGuardDeny() throws IOException {
        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        Map<String, Object> policy = preprocessor.enrich(openApiFixture());
        Map<String, String> files = preprocessor.cerbosPolicies("payment-gateway", policy);
        String domain = files.get("payment-gateway_Payment.yaml");

        assertThat(domain).contains("resource: Payment");
        assertThat(domain).contains("EFFECT_DENY");
        assertThat(domain).contains(
                "has(request.resource.attr.tenant) && (!has(request.principal.attr.tenant) "
                        + "|| request.resource.attr.tenant != request.principal.attr.tenant)");
        assertThat(domain).contains("cerbos:///principal.json");
        // op without a resource declaration gets no domain policy
        assertThat(files).doesNotContainKey("payment-gateway_.yaml");
    }

    @Test
    void cerbosSchemasCoverPrincipalAndEachResourceType() throws IOException {
        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        Map<String, String> schemas = preprocessor.cerbosSchemas(preprocessor.enrich(openApiFixture()));
        assertThat(schemas).containsKey("principal.json");
        assertThat(schemas).containsKey("Payment.json");
        assertThat(schemas.get("principal.json")).contains("scopes").contains("tenant");
    }

    @Test
    void routesManifestListsEveryOperationWithBasePath() throws IOException {
        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        Map<String, Object> policy = preprocessor.enrich(openApiFixture());
        String routes = preprocessor.routesManifest("payment-gateway", "/payment-gateway/api/v1", policy);
        assertThat(routes).contains("module: payment-gateway");
        assertThat(routes).contains("basePath: /payment-gateway/api/v1");
        assertThat(routes).contains("operationId: payPayment");
        assertThat(routes).contains("operationId: listPaymentDefinitions");
    }

    @Test
    void writesCerbosOutputAndRoutesWhenRequested() throws IOException {
        Path input = tempDir.resolve("openapi.yaml");
        Files.writeString(input, """
                openapi: 3.0.3
                paths:
                  /payments/{id}/pay:
                    post:
                      operationId: payPayment
                      x-labs64-auth:
                        tenant: true
                        scopes:
                          - payment:pay
                        resource: Payment
                """);
        Path cerbosDir = tempDir.resolve("cerbos");
        Path routes = tempDir.resolve("payment-gateway.routes.yaml");

        new OpenApiAuthPreprocessor().process(input, tempDir.resolve("out.yaml"),
                cerbosDir, "payment-gateway", routes, "/payment-gateway/api/v1", null);

        assertThat(Files.readString(cerbosDir.resolve("policies/payment_gateway_api.yaml")))
                .contains("resource: payment_gateway_api");
        assertThat(Files.readString(cerbosDir.resolve("policies/payment-gateway_Payment.yaml")))
                .contains("resource: Payment");
        assertThat(Files.readString(cerbosDir.resolve("policies/_schemas/principal.json")))
                .contains("scopes");
        assertThat(Files.readString(routes)).contains("operationId: payPayment");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicPathsListsOnlyPublicOperationsWithMethod() {
        Map<String, Object> openApi = map("paths", map(
                "/payment-definitions", map(
                        "get", map("operationId", "listPaymentDefinitions",
                                "x-labs64-auth", map("public", true))),
                "/providers/{provider}/webhooks", map(
                        "post", map("operationId", "handleProviderWebhook",
                                "x-labs64-auth", map("public", true))),
                "/payments", map(
                        "get", map("operationId", "listPayments",
                                "x-labs64-auth", map("tenant", true,
                                        "scopes", List.of("payment:read"))))));

        OpenApiAuthPreprocessor preprocessor = new OpenApiAuthPreprocessor();
        List<String> publicPaths = preprocessor.publicPaths(preprocessor.enrich(openApi));

        assertThat(publicPaths).containsExactlyInAnyOrder(
                "GET /payment-definitions",
                "POST /providers/{provider}/webhooks");
    }

    @Test
    void writesPublicPathsOutputWhenRequested() throws IOException {
        Path input = tempDir.resolve("openapi.yaml");
        Files.writeString(input, """
                openapi: 3.0.3
                paths:
                  /payment-definitions:
                    get:
                      operationId: listPaymentDefinitions
                      x-labs64-auth:
                        public: true
                  /payments:
                    get:
                      operationId: listPayments
                      x-labs64-auth:
                        tenant: true
                        scopes:
                          - payment:read
                """);
        Path publicPathsOutput = tempDir.resolve("auth-public-paths");

        new OpenApiAuthPreprocessor().process(input, tempDir.resolve("out.yaml"),
                null, null, null, null, publicPathsOutput);

        String content = Files.readString(publicPathsOutput);
        assertThat(content).contains("GET /payment-definitions");
        assertThat(content).doesNotContain("/payments");
        assertThat(content).startsWith("#");
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
