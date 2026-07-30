package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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

    private static final String PUBLIC_ENDPOINT =
            "@io.labs64.authcontext.authorization.PublicEndpoint";
    private static final String REQUIRE_TENANT =
            "@io.labs64.authcontext.authorization.RequireTenant";
    private static final String REQUIRE_SCOPES =
            "@io.labs64.authcontext.authorization.RequireScopes";
    private static final String AUTHORIZE =
            "@io.labs64.authcontext.authorization.Authorize";

    @TempDir
    Path tempDir;

    /**
     * Three-pattern fixture: one public operation, one
     * tenant + OAuth scope + resource operation, and one scopes-only operation.
     */
    private Map<String, Object> openApiFixture() {
        return map(
                "security", List.of(map("oauth", List.of())),
                "paths", map(
                        "/payment-definitions", map(
                                "get", map(
                                        "operationId", "listPaymentDefinitions",
                                        "security", List.of())),
                        "/payments/{id}/pay", map(
                                "post", map(
                                        "operationId", "payPayment",
                                        "security", List.of(map("oauth", List.of("payment:pay"))),
                                        "x-labs64", labs64Auth(true, "Payment"))),
                        "/events", map(
                                "post", map(
                                        "operationId", "publishEvent",
                                        "security", List.of(map("oauth", List.of("audit-event:write")))))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addsOpenApiAnnotationsAndBuildsPolicyRoutes() {
        Map<String, Object> openApi = map(
                "security", List.of(map("oauth", List.of())),
                "paths", map(
                        "/payments", map(
                                "get", map(
                                        "operationId", "listPayments",
                                        "security", List.of(map("oauth", List.of("payment:read"))),
                                        "x-operation-extra-annotation", "@org.example.Existing",
                                        "x-labs64", labs64Auth(true, "Payment")),
                                "post", map(
                                        "operationId", "createPayment",
                                        "security", List.of())),
                        "/profile", map(
                                "get", map("operationId", "getProfile"))));

        Map<String, Object> policy = new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
        Map<String, Object> payments = (Map<String, Object>) paths.get("/payments");
        Map<String, Object> listPayments = (Map<String, Object>) payments.get("get");
        Map<String, Object> createPayment = (Map<String, Object>) payments.get("post");
        Map<String, Object> profile = (Map<String, Object>) paths.get("/profile");
        Map<String, Object> getProfile = (Map<String, Object>) profile.get("get");

        assertThat((List<String>) listPayments.get("x-operation-extra-annotation"))
                .containsExactly(
                        "@org.example.Existing",
                        REQUIRE_TENANT,
                        REQUIRE_SCOPES + "({\"payment:read\"})",
                        AUTHORIZE + "(action = \"listPayments\", resourceType = \"Payment\")");
        assertThat((List<String>) createPayment.get("x-operation-extra-annotation"))
                .containsExactly(PUBLIC_ENDPOINT);
        // oauth: [] requires authentication even though it generates no coarse
        // authorization annotation.
        assertThat((List<String>) getProfile.get("x-operation-extra-annotation")).isEmpty();
        assertThat((List<Map<String, Object>>) policy.get("routes"))
                .extracting(route -> route.get("operationId"))
                .containsExactlyInAnyOrder("listPayments", "createPayment", "getProfile");
        assertThat((List<Map<String, Object>>) policy.get("routes"))
                .filteredOn(route -> "getProfile".equals(route.get("operationId")))
                .singleElement()
                .extracting(route -> route.get("public"))
                .isEqualTo(false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unrelatedExtraAnnotationDoesNotPreventPublicInference() {
        Map<String, Object> openApi = map("paths", map(
                "/health", map("get", map(
                        "operationId", "health",
                        "x-operation-extra-annotation", "@org.example.Existing"))));

        new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
        Map<String, Object> health = (Map<String, Object>) paths.get("/health");
        Map<String, Object> getHealth = (Map<String, Object>) health.get("get");
        assertThat((List<String>) getHealth.get("x-operation-extra-annotation"))
                .containsExactly("@org.example.Existing", PUBLIC_ENDPOINT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unrelatedSecuritySchemeDoesNotCountAsRequiredOauth() {
        Map<String, Object> openApi = map("paths", map(
                "/health", map("get", map(
                        "operationId", "health",
                        "security", List.of(map("apiKey", List.of()))))));

        Map<String, Object> policy = new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) openApi.get("paths")).get("/health")).get("get");
        assertThat((List<String>) operation.get("x-operation-extra-annotation"))
                .containsExactly(PUBLIC_ENDPOINT);
        assertThat((List<Map<String, Object>>) policy.get("routes"))
                .singleElement()
                .extracting(route -> route.get("public"))
                .isEqualTo(true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void operationSecurityOverridesGlobalSecurity() {
        Map<String, Object> openApi = map(
                "security", List.of(map("oauth", List.of("global:read"))),
                "paths", map(
                        "/inherited", map("get", map("operationId", "inherited")),
                        "/public", map("get", map("operationId", "publicOperation", "security", List.of()))));

        new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> paths = (Map<String, Object>) openApi.get("paths");
        Map<String, Object> inherited = (Map<String, Object>) ((Map<String, Object>) paths.get("/inherited"))
                .get("get");
        Map<String, Object> publicOperation = (Map<String, Object>) ((Map<String, Object>) paths.get("/public"))
                .get("get");
        assertThat((List<String>) inherited.get("x-operation-extra-annotation"))
                .containsExactly(REQUIRE_SCOPES + "({\"global:read\"})");
        assertThat((List<String>) publicOperation.get("x-operation-extra-annotation"))
                .containsExactly(PUBLIC_ENDPOINT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void tenantRequirementProtectsOperationWithEmptySecurity() {
        Map<String, Object> openApi = map("paths", map(
                "/payments", map("get", map(
                        "operationId", "listPayments",
                        "security", List.of(),
                        "x-labs64", labs64Auth(true, null)))));

        new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) openApi.get("paths")).get("/payments")).get("get");
        assertThat((List<String>) operation.get("x-operation-extra-annotation"))
                .containsExactly(REQUIRE_TENANT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resourceDoesNotImplicitlyRequireTenant() {
        Map<String, Object> openApi = map("paths", map(
                "/catalog/{id}", map("get", map(
                        "operationId", "getCatalogItem",
                        "x-labs64", labs64Auth(false, "CatalogItem")))));

        new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) openApi.get("paths")).get("/catalog/{id}")).get("get");
        assertThat((List<String>) operation.get("x-operation-extra-annotation"))
                .containsExactly("@io.labs64.authcontext.authorization.Authorize(action = \"getCatalogItem\", "
                        + "resourceType = \"CatalogItem\")");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anonymousSecurityAlternativeMakesOperationPublic() {
        Map<String, Object> openApi = map("paths", map(
                "/catalog", map("get", map(
                        "operationId", "catalog",
                        "security", List.of(map(), map("oauth", List.of("catalog:read")))))));

        new OpenApiAuthPreprocessor().enrich(openApi);

        Map<String, Object> operation = (Map<String, Object>) ((Map<String, Object>)
                ((Map<String, Object>) openApi.get("paths")).get("/catalog")).get("get");
        assertThat((List<String>) operation.get("x-operation-extra-annotation"))
                .containsExactly(PUBLIC_ENDPOINT);
    }

    @Test
    void rejectsMalformedSecurityRequirement() {
        Map<String, Object> openApi = map("paths", map(
                "/payments", map("get", map(
                        "operationId", "listPayments",
                        "security", map("oauth", List.of("payment:read"))))));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new OpenApiAuthPreprocessor().enrich(openApi))
                .withMessageContaining("security must be an array");
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
        assertThat(edge).contains("- listPaymentDefinitions");
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
                security:
                  - oauth: []
                paths:
                  /payments/{id}/pay:
                    post:
                      operationId: payPayment
                      security:
                        - oauth:
                            - payment:pay
                      x-labs64:
                        auth:
                          tenant: true
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
    void publicPathsListsOnlyPublicOperationsWithMethod() {
        Map<String, Object> openApi = map(
                "security", List.of(map("oauth", List.of())),
                "paths", map(
                        "/payment-definitions", map(
                                "get", map("operationId", "listPaymentDefinitions", "security", List.of())),
                        "/providers/{provider}/webhooks", map(
                                "post", map("operationId", "handleProviderWebhook", "security", List.of())),
                        "/payments", map(
                                "get", map(
                                        "operationId", "listPayments",
                                        "security", List.of(map("oauth", List.of("payment:read"))),
                                        "x-labs64", labs64Auth(true, null)))));

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
                security:
                  - oauth: []
                paths:
                  /payment-definitions:
                    get:
                      operationId: listPaymentDefinitions
                      security: []
                  /payments:
                    get:
                      operationId: listPayments
                      security:
                        - oauth:
                            - payment:read
                      x-labs64:
                        auth:
                          tenant: true
                """);
        Path publicPathsOutput = tempDir.resolve("auth-public-paths");

        new OpenApiAuthPreprocessor().process(input, tempDir.resolve("out.yaml"),
                null, null, null, null, publicPathsOutput);

        String content = Files.readString(publicPathsOutput);
        assertThat(content).contains("GET /payment-definitions");
        assertThat(content).doesNotContain("/payments");
        assertThat(content).startsWith("# GENERATED from OpenAPI security and x-labs64.auth");
    }

    @Test
    void operationWithoutAuthRequirementsIsPublic() {
        Map<String, Object> openApi = map("paths", map(
                "/health", map("get", map("operationId", "health"))));

        Map<String, Object> policy = new OpenApiAuthPreprocessor().enrich(openApi);

        assertThat(new OpenApiAuthPreprocessor().publicPaths(policy)).containsExactly("GET /health");
    }

    private static Map<String, Object> labs64Auth(final boolean tenant, final String resource) {
        Map<String, Object> auth = new LinkedHashMap<>();
        if (tenant) {
            auth.put("tenant", true);
        }
        if (resource != null) {
            auth.put("resource", resource);
        }
        return map("auth", auth);
    }

    private static Map<String, Object> map(final Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
