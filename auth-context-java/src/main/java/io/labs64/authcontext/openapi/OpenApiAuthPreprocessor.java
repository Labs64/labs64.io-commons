package io.labs64.authcontext.openapi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * Generates OpenAPI generator annotations and gateway policy from
 * {@code x-labs64-auth}.
 */
public class OpenApiAuthPreprocessor {

    public static final String AUTH_EXTENSION = "x-labs64-auth";
    public static final String EXTRA_ANNOTATION_EXTENSION = "x-operation-extra-annotation";

    private static final String PUBLIC_ENDPOINT = "@io.labs64.authcontext.authorization.PublicEndpoint";
    private static final String REQUIRE_TENANT = "@io.labs64.authcontext.authorization.RequireTenant";
    private static final String REQUIRE_SCOPES = "@io.labs64.authcontext.authorization.RequireScopes";
    private static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "options", "head",
            "patch", "trace");

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    public OpenApiAuthPreprocessor() {
        this(new ObjectMapper(YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build()), new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    OpenApiAuthPreprocessor(final ObjectMapper yamlMapper, final ObjectMapper jsonMapper) {
        this.yamlMapper = yamlMapper;
        this.jsonMapper = jsonMapper;
    }

    public void process(final Path input, final Path openApiOutput, final Path policyOutput) throws IOException {
        process(input, openApiOutput, policyOutput, null, null);
    }

    /**
     * Full pipeline including the generated Tier-1 edge Cedar policy set
     * (RFC-05 P2). {@code cedarOutput}/{@code module} may be null to skip.
     */
    public void process(final Path input, final Path openApiOutput, final Path policyOutput, final Path cedarOutput,
            final String module) throws IOException {
        Map<String, Object> openApi = readYaml(input);
        Map<String, Object> policy = enrich(openApi);
        writeYaml(openApiOutput, openApi);
        writeJson(policyOutput, policy);
        if (cedarOutput != null) {
            if (module == null || module.isBlank()) {
                throw new IllegalArgumentException("module is required when a cedar output is requested");
            }
            writeText(cedarOutput, cedarPolicies(module, policy));
        }
    }

    /**
     * Renders the enriched policy document as per-operation edge Cedar policies
     * against the shared schema's {@code ApiOperation}/{@code invoke} model.
     * The three x-labs64-auth patterns translate 1:1 (RFC-05 §5.2): public →
     * unconditional permit; tenant → {@code context has tenant}; scopes →
     * any-overlap ({@code ||}) on {@code context.scopes}, matching the
     * authproxy's OR-scope semantics.
     */
    @SuppressWarnings("unchecked")
    public String cedarPolicies(final String module, final Map<String, Object> policy) {
        StringBuilder cedar = new StringBuilder();
        cedar.append("// GENERATED from x-labs64-auth by OpenApiAuthPreprocessor — do not edit.\n");
        cedar.append("// Tier 1 edge policies for module \"").append(module).append("\" (RFC-05 P2).\n");
        for (Map<String, Object> route : (List<Map<String, Object>>) policy.get("routes")) {
            String operationId = route.get("operationId") instanceof String id && !id.isBlank() ? id
                    : route.get("method") + ":" + route.get("path");
            String entityId = cedarString(module + "::" + operationId);
            cedar.append('\n');
            cedar.append("@id(").append(entityId).append(")\n");
            cedar.append("permit(\n");
            cedar.append("  principal,\n");
            cedar.append("  action == Labs64IO::Action::\"invoke\",\n");
            cedar.append("  resource == Labs64IO::ApiOperation::").append(entityId).append('\n');
            cedar.append(')');
            String condition = cedarCondition(route);
            if (!condition.isEmpty()) {
                cedar.append(" when { ").append(condition).append(" }");
            }
            cedar.append(";\n");
        }
        return cedar.toString();
    }

    @SuppressWarnings("unchecked")
    private String cedarCondition(final Map<String, Object> route) {
        if (Boolean.TRUE.equals(route.get("public"))) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        if (Boolean.TRUE.equals(route.get("tenantRequired"))) {
            conditions.add("(context has tenant)");
        }
        List<String> scopes = (List<String>) route.get("scopes");
        if (scopes != null && !scopes.isEmpty()) {
            List<String> checks = new ArrayList<>();
            for (String scope : scopes) {
                checks.add("context.scopes.contains(" + cedarString(scope) + ")");
            }
            conditions.add("(" + String.join(" || ", checks) + ")");
        }
        return String.join(" && ", conditions);
    }

    private String cedarString(final String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public Map<String, Object> enrich(final Map<String, Object> openApi) {
        Map<String, Object> paths = asMap(openApi.get("paths"), "paths");
        List<Map<String, Object>> routes = new ArrayList<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = asMap(pathEntry.getValue(), "paths." + path);
            AuthPolicy pathAuth = AuthPolicy.from(pathItem.get(AUTH_EXTENSION));

            for (Map.Entry<String, Object> operationEntry : pathItem.entrySet()) {
                String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }

                Map<String, Object> operation = asMap(operationEntry.getValue(), method.toUpperCase(Locale.ROOT)
                        + " " + path);
                AuthPolicy auth = AuthPolicy.from(operation.getOrDefault(AUTH_EXTENSION, pathAuth.raw()));

                List<String> extraAnnotations = extraAnnotations(operation.get(EXTRA_ANNOTATION_EXTENSION));
                extraAnnotations.addAll(annotations(auth));
                operation.put(EXTRA_ANNOTATION_EXTENSION, extraAnnotations);
                routes.add(route(path, method, operation, auth));
            }
        }

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("version", 1);
        policy.put("routes", routes);
        return policy;
    }

    private List<String> annotations(final AuthPolicy auth) {
        List<String> annotations = new ArrayList<>();
        if (auth.isPublic()) {
            annotations.add(PUBLIC_ENDPOINT);
            return annotations;
        }
        if (auth.tenantRequired()) {
            annotations.add(REQUIRE_TENANT);
        }
        if (!auth.scopes().isEmpty()) {
            annotations.add(REQUIRE_SCOPES + "({" + quotedCsv(auth.scopes()) + "})");
        }
        return annotations;
    }

    private List<String> extraAnnotations(final Object value) {
        List<String> annotations = new ArrayList<>();
        if (value == null) {
            return annotations;
        }
        if (value instanceof String annotation) {
            annotations.add(annotation);
            return annotations;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(EXTRA_ANNOTATION_EXTENSION + " must be a string or list");
        }
        for (Object item : list) {
            if (!(item instanceof String annotation) || annotation.isBlank()) {
                throw new IllegalArgumentException(EXTRA_ANNOTATION_EXTENSION + " must contain non-blank strings");
            }
            annotations.add(annotation);
        }
        return annotations;
    }

    private Map<String, Object> route(final String path, final String method, final Map<String, Object> operation,
            final AuthPolicy auth) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("operationId", operation.get("operationId"));
        route.put("method", method.toUpperCase(Locale.ROOT));
        route.put("path", path);
        route.put("public", auth.isPublic());
        route.put("tenantRequired", auth.tenantRequired());
        route.put("scopes", auth.scopes());
        return route;
    }

    private String quotedCsv(final List<String> values) {
        List<String> quoted = new ArrayList<>();
        for (String value : values) {
            quoted.add("\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
        }
        return String.join(", ", quoted);
    }

    private Map<String, Object> readYaml(final Path input) throws IOException {
        return yamlMapper.readValue(Files.readString(input), new TypeReference<>() {
        });
    }

    private void writeYaml(final Path output, final Object value) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        yamlMapper.writeValue(output.toFile(), value);
    }

    private void writeText(final Path output, final String value) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(output, value);
    }

    private void writeJson(final Path output, final Object value) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        jsonMapper.writeValue(output.toFile(), value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(final Object value, final String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (Map<String, Object>) map;
    }
}
