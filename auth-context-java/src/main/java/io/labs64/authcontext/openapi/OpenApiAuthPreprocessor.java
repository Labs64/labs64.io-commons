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
    /** Clean, quote-minimised YAML for the Cerbos policies and routes manifest. */
    private final ObjectMapper cerbosYamlMapper = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .disable(YAMLGenerator.Feature.SPLIT_LINES)
            .build());

    public OpenApiAuthPreprocessor() {
        this(new ObjectMapper(YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .build()), new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    OpenApiAuthPreprocessor(final ObjectMapper yamlMapper, final ObjectMapper jsonMapper) {
        this.yamlMapper = yamlMapper;
        this.jsonMapper = jsonMapper;
    }

    public void process(final Path input, final Path openApiOutput) throws IOException {
        process(input, openApiOutput, null, null, null, null, null);
    }

    /**
     * Full pipeline. Writes the enriched OpenAPI and, when the
     * corresponding output is given, the Cerbos policy set
     * ({@code policies/*.yaml} resource policies plus
     * {@code policies/_schemas/*.json} schemas), the routes manifest, and the
     * flat public-path list. OpenAPI stays the single source of truth for
     * enforcement — the same {@code x-labs64-auth} feeds every output.
     *
     * @param cerbosOutputDir   base dir for {@code policies/} + {@code policies/_schemas/}; null to skip
     * @param module            module name; required when {@code cerbosOutputDir} or {@code routesOutput} is set
     * @param routesOutput      routes-manifest file; null to skip
     * @param basePath          gateway base path prefixed onto the routes manifest; may be null
     * @param publicPathsOutput flat public-path list for {@code AuthContextFilter}; null to skip
     */
    public void process(final Path input, final Path openApiOutput, final Path cerbosOutputDir,
            final String module, final Path routesOutput, final String basePath,
            final Path publicPathsOutput) throws IOException {
        Map<String, Object> openApi = readYaml(input);
        Map<String, Object> policy = enrich(openApi);
        writeYaml(openApiOutput, openApi);
        if ((cerbosOutputDir != null || routesOutput != null) && (module == null || module.isBlank())) {
            throw new IllegalArgumentException("module is required when a cerbos or routes output is requested");
        }
        if (cerbosOutputDir != null) {
            Path policiesDir = cerbosOutputDir.resolve("policies");
            for (Map.Entry<String, String> file : cerbosPolicies(module, policy).entrySet()) {
                writeText(policiesDir.resolve(file.getKey()), file.getValue());
            }
            Path schemasDir = policiesDir.resolve("_schemas");
            for (Map.Entry<String, String> file : cerbosSchemas(policy).entrySet()) {
                writeText(schemasDir.resolve(file.getKey()), file.getValue());
            }
        }
        if (routesOutput != null) {
            writeText(routesOutput, routesManifest(module, basePath, policy));
        }
        if (publicPathsOutput != null) {
            writeText(publicPathsOutput, publicPathsDocument(policy));
        }
    }

    /**
     * The public operations as {@code <METHOD> <path-template>} entries — the
     * backend {@code AuthContextFilter}'s public-path source, generated from the
     * SAME {@code x-labs64-auth.public} as the edge/domain Cerbos so no public
     * path is ever hand-maintained. Only OpenAPI operations appear here; non-API
     * surfaces (actuator, docs) stay configured prefixes on the filter.
     */
    @SuppressWarnings("unchecked")
    public List<String> publicPaths(final Map<String, Object> policy) {
        List<String> entries = new ArrayList<>();
        for (Map<String, Object> route : (List<Map<String, Object>>) policy.get("routes")) {
            if (Boolean.TRUE.equals(route.get("public"))) {
                entries.add(route.get("method") + " " + route.get("path"));
            }
        }
        return entries;
    }

    private String publicPathsDocument(final Map<String, Object> policy) {
        StringBuilder doc = new StringBuilder();
        doc.append("# GENERATED from x-labs64-auth by OpenApiAuthPreprocessor — do not edit.\n");
        doc.append("# One '<METHOD> <path-template>' per public operation; consumed by AuthContextFilter.\n");
        for (String entry : publicPaths(policy)) {
            doc.append(entry).append('\n');
        }
        return doc.toString();
    }

    /** Edge resource kind for a module: {@code payment-gateway} -> {@code payment_gateway_api}. */
    public String cerbosResourceKind(final String module) {
        return module.replace('-', '_') + "_api";
    }

    /** CEL condition mirroring the Cerbos semantics 1:1 (empty for public ops). */
    @SuppressWarnings("unchecked")
    private String celCondition(final Map<String, Object> route) {
        if (Boolean.TRUE.equals(route.get("public"))) {
            return "";
        }
        List<String> conditions = new ArrayList<>();
        if (Boolean.TRUE.equals(route.get("tenantRequired"))) {
            conditions.add("(has(request.principal.attr.tenant) || (\"service\" in request.principal.roles))");
        }
        List<String> scopes = (List<String>) route.get("scopes");
        if (scopes != null && !scopes.isEmpty()) {
            List<String> checks = new ArrayList<>();
            for (String scope : scopes) {
                checks.add("s == \"" + scope.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
            }
            conditions.add("request.principal.attr.scopes.exists(s, " + String.join(" || ", checks) + ")");
        }
        return String.join(" && ", conditions);
    }

    private Map<String, Object> cerbosRule(final List<String> actions, final String effect, final String condition) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("actions", actions);
        rule.put("effect", effect);
        rule.put("roles", List.of("*"));
        if (!condition.isEmpty()) {
            rule.put("condition", Map.of("match", Map.of("expr", condition)));
        }
        return rule;
    }

    private String cerbosPolicyYaml(final String resourceKind, final List<Map<String, Object>> rules,
            final String resourceSchema) throws IOException {
        Map<String, Object> rp = new LinkedHashMap<>();
        rp.put("version", "default");
        rp.put("resource", resourceKind);
        Map<String, Object> schemas = new LinkedHashMap<>();
        schemas.put("principalSchema", Map.of("ref", "cerbos:///principal.json"));
        if (resourceSchema != null) {
            schemas.put("resourceSchema", Map.of("ref", "cerbos:///" + resourceSchema));
        }
        rp.put("schemas", schemas);
        rp.put("rules", rules);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("apiVersion", "api.cerbos.dev/v1");
        doc.put("resourcePolicy", rp);
        return "# GENERATED from x-labs64-auth by OpenApiAuthPreprocessor — do not edit.\n"
                + cerbosYamlMapper.writeValueAsString(doc);
    }

    /**
     * Translates the enriched policy document into Cerbos resource policies —
     * the replacement for the two legacy tiers, from the SAME
     * {@code x-labs64-auth} so OpenAPI stays the single source of truth.
     *
     * <p>Emits one <b>edge</b> resource policy per module (kind
     * {@link #cerbosResourceKind}, one ALLOW rule per operationId, the three
     * {@code x-labs64-auth} patterns translated to CEL 1:1: public → no
     * condition; tenant → {@code has(principal.attr.tenant) || service role};
     * scopes → OR-overlap on {@code principal.attr.scopes}). Additionally, for
     * every operation declaring {@code x-labs64-auth.resource}, one <b>domain</b>
     * resource policy per type carrying the same ALLOW rules plus a structural
     * tenant-guard DENY (the cross-tenant isolation invariant). Returns a
     * filename → YAML map: {@code <kind>.yaml} and {@code <module>_<Type>.yaml}.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> cerbosPolicies(final String module, final Map<String, Object> policy)
            throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        List<Map<String, Object>> edgeRules = new ArrayList<>();
        Map<String, List<Map<String, Object>>> domainRules = new LinkedHashMap<>();
        for (Map<String, Object> route : (List<Map<String, Object>>) policy.get("routes")) {
            String operationId = route.get("operationId") instanceof String id && !id.isBlank() ? id
                    : route.get("method") + ":" + route.get("path");
            edgeRules.add(cerbosRule(List.of(operationId), "EFFECT_ALLOW", celCondition(route)));
            if (route.get("resource") instanceof String type && !type.isBlank()) {
                domainRules.computeIfAbsent(type, t -> new ArrayList<>())
                        .add(cerbosRule(List.of(operationId), "EFFECT_ALLOW", celCondition(route)));
            }
        }
        files.put(cerbosResourceKind(module) + ".yaml",
                cerbosPolicyYaml(cerbosResourceKind(module), edgeRules, null));
        for (Map.Entry<String, List<Map<String, Object>>> e : domainRules.entrySet()) {
            List<Map<String, Object>> rules = new ArrayList<>(e.getValue());
            rules.add(cerbosRule(List.of("*"), "EFFECT_DENY",
                    "has(request.resource.attr.tenant) && (!has(request.principal.attr.tenant) "
                    + "|| request.resource.attr.tenant != request.principal.attr.tenant)"));
            files.put(module + "_" + e.getKey() + ".yaml",
                    cerbosPolicyYaml(e.getKey(), rules, e.getKey() + ".json"));
        }
        return files;
    }

    /**
     * JSON schemas for the Cerbos {@code schema.enforcement: reject} mode: a
     * permissive {@code principal.json} (scopes/tenant attributes) plus one
     * {@code <Type>.json} per declared domain resource type (tenant attribute).
     * Filename → JSON body.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> cerbosSchemas(final Map<String, Object> policy) throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        // LinkedHashMap throughout (not Map.of()): schemas are committed, generated
        // artifacts — Map.of()'s iteration order is randomized per JVM launch, which
        // would make generate.sh non-reproducible (spurious diffs with zero policy change).
        Map<String, Object> scopesProperty = new LinkedHashMap<>();
        scopesProperty.put("type", "array");
        scopesProperty.put("items", Map.of("type", "string"));
        Map<String, Object> principalProperties = new LinkedHashMap<>();
        principalProperties.put("scopes", scopesProperty);
        principalProperties.put("tenant", Map.of("type", "string"));
        Map<String, Object> principalSchema = new LinkedHashMap<>();
        principalSchema.put("type", "object");
        principalSchema.put("properties", principalProperties);
        principalSchema.put("additionalProperties", true);
        files.put("principal.json", jsonMapper.writeValueAsString(principalSchema));
        for (Map<String, Object> route : (List<Map<String, Object>>) policy.get("routes")) {
            if (route.get("resource") instanceof String type && !type.isBlank()) {
                Map<String, Object> resourceProperties = new LinkedHashMap<>();
                resourceProperties.put("tenant", Map.of("type", "string"));
                Map<String, Object> resourceSchema = new LinkedHashMap<>();
                resourceSchema.put("type", "object");
                resourceSchema.put("properties", resourceProperties);
                resourceSchema.put("additionalProperties", true);
                files.putIfAbsent(type + ".json", jsonMapper.writeValueAsString(resourceSchema));
            }
        }
        return files;
    }

    /**
     * The routes manifest the traefik-authproxy loads to map incoming requests
     * to (module, operationId, edge-resource-kind). Carries the full enriched
     * route list plus the module and its gateway {@code basePath} (prefixed onto
     * each route path by the loader).
     */
    public String routesManifest(final String module, final String basePath, final Map<String, Object> policy)
            throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", 1);
        doc.put("module", module);
        doc.put("basePath", basePath == null ? "" : basePath);
        doc.put("routes", policy.get("routes"));
        return "# GENERATED from x-labs64-auth by OpenApiAuthPreprocessor — do not edit.\n"
                + cerbosYamlMapper.writeValueAsString(doc);
    }

    public Map<String, Object> enrich(final Map<String, Object> openApi) {
        Map<String, Object> paths = asMap(openApi.get("paths"), "paths");
        List<Map<String, Object>> routes = new ArrayList<>();

        for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Map<String, Object> pathItem = asMap(pathEntry.getValue(), "paths." + path);
            AuthPolicy pathAuth = AuthPolicy.from(pathItem.get(AUTH_EXTENSION), false);

            for (Map.Entry<String, Object> operationEntry : pathItem.entrySet()) {
                String method = operationEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }

                Map<String, Object> operation = asMap(operationEntry.getValue(), method.toUpperCase(Locale.ROOT)
                        + " " + path);
                AuthPolicy auth = AuthPolicy.from(operation.getOrDefault(AUTH_EXTENSION, pathAuth.raw()), true);

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
        if (auth.resourceType() != null) {
            route.put("resource", auth.resourceType());
        }
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



    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(final Object value, final String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return (Map<String, Object>) map;
    }
}
