package io.labs64.authcontext.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.labs64.authcontext.openapi.OpenApiAuthPreprocessor;

/**
 * Proves that every operation a module's OpenAPI spec declares protected is
 * actually rejected at runtime without credentials.
 *
 * <p>"Phantom auth" — advertised in the contract, not enforced at runtime — is
 * the failure this closes. The test list is derived from the effective
 * {@code x-labs64.auth} metadata that produces the Cerbos policies, gateway
 * routes and public-path list, so contract and enforcement cannot drift apart
 * silently: a new protected operation becomes a new test case with no test file
 * to write and none to forget.
 *
 * <p>Deliberately free of any test framework beyond the JUnit API and of any
 * HTTP client. The caller supplies an {@link AnonymousRequest} — MockMvc,
 * WebTestClient or a live HTTP call against the gateway all satisfy it — so the
 * same contract runs at the module layer and at the gateway edge.
 *
 * <p>Typical use in a module:
 *
 * <pre>{@code
 * @TestFactory
 * Stream<DynamicTest> everyProtectedOperationRejectsAnonymousCallers() {
 *     return AuthEnforcementContract.rejectsAnonymousAccess(SPEC,
 *             (method, path) -> mockMvc.perform(request(HttpMethod.valueOf(method), path))
 *                     .andReturn().getResponse().getStatus());
 * }
 * }</pre>
 */
public final class AuthEnforcementContract {

    /** Statuses that count as "the request was refused before it did anything". */
    private static final List<Integer> REJECTED = List.of(401, 403);

    private static final Pattern TEMPLATE_PARAM = Pattern.compile("\\{([^/{}]+)\\}");
    private static final String SAMPLE_UUID = "00000000-0000-4000-8000-000000000000";

    private static final ObjectMapper YAML = new ObjectMapper(YAMLFactory.builder().build());

    private AuthEnforcementContract() {
    }

    /**
     * One protected operation, with a concrete path ready to call.
     *
     * @param samplePath {@code pathTemplate} with every {@code {param}} replaced
     *                   by a syntactically valid value for its declared type.
     *                   Authentication is decided before path-variable binding,
     *                   so the value only has to parse — never to exist.
     */
    public record ProtectedOperation(String operationId, String method, String pathTemplate, String samplePath,
            boolean tenantRequired, List<String> scopes) {

        @Override
        public String toString() {
            return method + " " + pathTemplate + " (" + operationId + ")";
        }
    }

    /** Performs an unauthenticated call and returns the HTTP status code. */
    @FunctionalInterface
    public interface AnonymousRequest {
        int execute(String method, String path) throws Exception;
    }

    /**
     * Every operation the spec declares non-public.
     *
     * <p>Reads the canonical spec — not a build artifact — so the test cannot be
     * fooled by a stale or missing generated file. This reads the same
 * enrichment as the build-time preprocessor, including path-level
 * {@code x-labs64.auth} inheritance and inferred public operations.
     */
    public static List<ProtectedOperation> protectedOperations(final Path spec) throws IOException {
        List<ProtectedOperation> operations = new ArrayList<>();
        for (Map<String, Object> route : routes(spec)) {
            if (Boolean.TRUE.equals(route.get("public"))) {
                continue;
            }
            String template = String.valueOf(route.get("path"));
            operations.add(new ProtectedOperation(
                    String.valueOf(route.get("operationId")),
                    String.valueOf(route.get("method")),
                    template,
                    samplePath(spec, template, String.valueOf(route.get("method"))),
                    Boolean.TRUE.equals(route.get("tenantRequired")),
                    scopes(route.get("scopes"))));
        }
        return List.copyOf(operations);
    }

    /**
     * A dynamic test per protected operation, plus a guard that the spec
     * declares any at all.
     *
     * <p>The guard matters as much as the tests: a spec that stopped declaring
     * protection, or a path this helper failed to parse, would otherwise produce
     * an empty, permanently green suite. Silence must not read as success.
     */
    public static Stream<DynamicTest> rejectsAnonymousAccess(final Path spec, final AnonymousRequest request)
            throws IOException {
        List<ProtectedOperation> operations = protectedOperations(spec);

        DynamicTest completeness = DynamicTest.dynamicTest(
                "spec declares at least one protected operation", () -> {
                    if (operations.isEmpty()) {
                        throw new AssertionError("No protected operations found in " + spec
                                + ". Either every operation is public — which needs a deliberate review — or the "
                                + "spec failed to parse. An empty auth-enforcement suite is not a passing one.");
                    }
                });

        Stream<DynamicTest> perOperation = operations.stream().map(operation ->
                DynamicTest.dynamicTest(operation.toString(), () -> {
                    int status = request.execute(operation.method(), operation.samplePath());
                    if (!REJECTED.contains(status)) {
                        throw new AssertionError(String.format(
                                "%s %s declares authentication requirements (tenant=%s, scopes=%s) but an "
                                        + "unauthenticated call "
                                        + "returned %d. Expected 401 or 403. The contract advertises protection the "
                                        + "runtime does not enforce.",
                                operation.method(), operation.samplePath(), operation.tenantRequired(),
                                operation.scopes(), status));
                    }
                }));

        return Stream.concat(Stream.of(completeness), perOperation);
    }

    // --- spec reading ---------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> routes(final Path spec) throws IOException {
        Map<String, Object> openApi = readSpec(spec);
        Map<String, Object> policy = new OpenApiAuthPreprocessor().enrich(openApi);
        return (List<Map<String, Object>>) policy.get("routes");
    }

    private static Map<String, Object> readSpec(final Path spec) throws IOException {
        return YAML.readValue(Files.readString(spec), new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static List<String> scopes(final Object value) {
        return value instanceof List<?> list ? List.copyOf((List<String>) list) : List.of();
    }

    // --- path templating ------------------------------------------------------

    /**
     * Substitutes each {@code {param}} with a value valid for its declared
     * schema type, so a protected operation cannot be reported as "enforced"
     * merely because the URL failed to route.
     */
    private static String samplePath(final Path spec, final String template, final String method) throws IOException {
        Matcher matcher = TEMPLATE_PARAM.matcher(template);
        if (!matcher.find()) {
            return template;
        }
        Map<String, String> samples = parameterSamples(spec, template, method);
        matcher.reset();
        StringBuilder path = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(path,
                    Matcher.quoteReplacement(samples.getOrDefault(matcher.group(1), "sample")));
        }
        matcher.appendTail(path);
        return path.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> parameterSamples(final Path spec, final String template, final String method)
            throws IOException {
        Map<String, String> samples = new LinkedHashMap<>();
        Map<String, Object> paths = (Map<String, Object>) readSpec(spec).get("paths");
        Object pathItemValue = paths == null ? null : paths.get(template);
        if (!(pathItemValue instanceof Map<?, ?> pathItem)) {
            return samples;
        }
        // Path-level parameters first; the operation may override them.
        collectSamples(((Map<String, Object>) pathItem).get("parameters"), samples);
        Object operation = ((Map<String, Object>) pathItem).get(method.toLowerCase(Locale.ROOT));
        if (operation instanceof Map<?, ?> op) {
            collectSamples(((Map<String, Object>) op).get("parameters"), samples);
        }
        return samples;
    }

    @SuppressWarnings("unchecked")
    private static void collectSamples(final Object parameters, final Map<String, String> samples) {
        if (!(parameters instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> parameter = (Map<String, Object>) raw;
            if (!"path".equals(parameter.get("in"))) {
                continue;
            }
            Object name = parameter.get("name");
            if (name == null) {
                continue;
            }
            samples.put(String.valueOf(name), sampleFor(parameter.get("schema")));
        }
    }

    @SuppressWarnings("unchecked")
    private static String sampleFor(final Object schemaValue) {
        if (!(schemaValue instanceof Map<?, ?> raw)) {
            return "sample";
        }
        Map<String, Object> schema = (Map<String, Object>) raw;
        String format = String.valueOf(schema.getOrDefault("format", ""));
        if ("uuid".equals(format)) {
            return SAMPLE_UUID;
        }
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> values && !values.isEmpty()) {
            return String.valueOf(values.get(0));
        }
        return switch (String.valueOf(schema.getOrDefault("type", "string"))) {
            case "integer", "number" -> "1";
            case "boolean" -> "true";
            default -> "sample";
        };
    }
}
