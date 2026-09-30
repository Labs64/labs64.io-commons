package io.labs64.contract.schema;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

/**
 * Extracts portable JSON Schema contracts from OpenAPI schemas that explicitly
 * declare {@code properties.$schema.const}.
 */
public final class OpenApiSchemaGenerator {

    public enum Status {
        CREATE,
        UNCHANGED,
        CONFLICT
    }

    public record PlannedSchema(
            String component,
            String module,
            String object,
            String revision,
            URI uri,
            Path output,
            String existingContents,
            String contents,
            Status status) {
    }

    public record Plan(Path outputRoot, List<PlannedSchema> schemas) {
        public int contractCount() {
            return schemas.size();
        }
    }

    public record Result(List<Path> created, List<Path> overwritten, List<Path> unchanged) {
    }

    private record Contract(String component, String module, String object, String revision, URI uri) {
    }

    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final String REGISTRY_HOST = "labs64.io";
    private static final Pattern COMPONENT_REF = Pattern.compile("^#/components/schemas/([^/]+)$");
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final ObjectMapper yamlMapper = new ObjectMapper(YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build());
    private final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Builds and validates the complete generation plan without writing files. */
    public Plan plan(final Path input, final Path outputRoot) throws IOException {
        Path normalizedOutputRoot = outputRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedOutputRoot)) {
            throw new IllegalArgumentException("Output root must be an existing directory: " + normalizedOutputRoot);
        }

        Map<String, Object> openApi = readYaml(input);
        requireOpenApi31(openApi);
        Map<String, Map<String, Object>> components = componentSchemas(openApi);
        Map<String, Contract> contracts = discoverContracts(components);
        return new Plan(normalizedOutputRoot, renderContracts(normalizedOutputRoot, components, contracts));
    }

    /** Writes a previously validated plan. Conflicting files require explicit authorization. */
    public Result write(final Plan plan, final Set<Path> overwrite) throws IOException {
        Set<Path> normalizedOverwrite = new LinkedHashSet<>();
        for (Path path : overwrite) {
            normalizedOverwrite.add(path.toAbsolutePath().normalize());
        }

        for (PlannedSchema schema : plan.schemas()) {
            String currentContents = Files.exists(schema.output()) ? Files.readString(schema.output()) : null;
            if (!java.util.Objects.equals(currentContents, schema.existingContents())) {
                throw new IllegalStateException("Schema output changed after confirmation; rerun the generator: "
                        + schema.output());
            }
        }

        List<Path> unauthorized = plan.schemas().stream()
                .filter(schema -> schema.status() == Status.CONFLICT)
                .map(PlannedSchema::output)
                .filter(path -> !normalizedOverwrite.contains(path.toAbsolutePath().normalize()))
                .toList();
        if (!unauthorized.isEmpty()) {
            throw new IllegalStateException("Refusing to overwrite existing schema revisions without confirmation:\n  "
                    + String.join("\n  ", unauthorized.stream().map(Path::toString).toList()));
        }

        List<Path> created = new ArrayList<>();
        List<Path> overwritten = new ArrayList<>();
        List<Path> unchanged = new ArrayList<>();
        for (PlannedSchema schema : plan.schemas()) {
            switch (schema.status()) {
                case CREATE -> {
                    atomicWrite(schema.output(), schema.contents());
                    created.add(schema.output());
                }
                case CONFLICT -> {
                    atomicWrite(schema.output(), schema.contents());
                    overwritten.add(schema.output());
                }
                case UNCHANGED -> unchanged.add(schema.output());
            }
        }
        return new Result(List.copyOf(created), List.copyOf(overwritten), List.copyOf(unchanged));
    }

    private List<PlannedSchema> renderContracts(final Path outputRoot,
            final Map<String, Map<String, Object>> components,
            final Map<String, Contract> contracts) throws IOException {
        List<PlannedSchema> generatedContracts = new ArrayList<>();
        Set<Path> outputs = new LinkedHashSet<>();
        for (Contract contract : contracts.values()) {
            Map<String, Object> schema = standaloneSchema(contract, components, contracts);
            Path relativeOutput = Path.of("schemas", contract.module(), contract.object(), contract.revision() + ".json");
            Path output = outputRoot.resolve(relativeOutput).normalize();
            if (!output.startsWith(outputRoot)) {
                throw new IllegalArgumentException("Contract output escapes the selected output root: " + output);
            }
            if (!outputs.add(output)) {
                throw new IllegalArgumentException("Multiple contracts map to " + output);
            }
            String contents = jsonMapper.writeValueAsString(schema) + System.lineSeparator();
            String existingContents = Files.exists(output) ? Files.readString(output) : null;
            Status status = existingContents == null
                    ? Status.CREATE
                    : contents.equals(existingContents) ? Status.UNCHANGED : Status.CONFLICT;
            generatedContracts.add(new PlannedSchema(
                    contract.component(),
                    contract.module(),
                    contract.object(),
                    contract.revision(),
                    contract.uri(),
                    output,
                    existingContents,
                    contents,
                    status));
        }
        return List.copyOf(generatedContracts);
    }

    private void atomicWrite(final Path output, final String contents) throws IOException {
        Path parent = output.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Schema output has no parent directory: " + output);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "." + output.getFileName(), ".tmp");
        try {
            Files.writeString(temporary, contents);
            Files.move(temporary, output,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private Map<String, Object> standaloneSchema(final Contract root,
            final Map<String, Map<String, Object>> components,
            final Map<String, Contract> contracts) {
        Map<String, Object> result = deepCopyMap(components.get(root.component()));
        result.put("$schema", DIALECT);
        result.put("$id", root.uri().toString());

        Deque<String> pendingDefinitions = new ArrayDeque<>();
        Set<String> includedDefinitions = new LinkedHashSet<>();
        rewriteReferences(result, root.component(), contracts, pendingDefinitions);

        Map<String, Object> definitions = new LinkedHashMap<>();
        while (!pendingDefinitions.isEmpty()) {
            String name = pendingDefinitions.removeFirst();
            if (!includedDefinitions.add(name)) {
                continue;
            }
            Map<String, Object> component = components.get(name);
            if (component == null) {
                throw new IllegalArgumentException("Schema " + root.component()
                        + " references missing component schema " + name);
            }
            Map<String, Object> definition = deepCopyMap(component);
            rewriteReferences(definition, root.component(), contracts, pendingDefinitions);
            definitions.put(name, definition);
        }
        if (!definitions.isEmpty()) {
            result.put("$defs", definitions);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void rewriteReferences(final Object node, final String rootComponent,
            final Map<String, Contract> contracts, final Deque<String> pendingDefinitions) {
        if (node instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = (Map<String, Object>) rawMap;
            Object refValue = map.get("$ref");
            if (refValue instanceof String ref) {
                Matcher matcher = COMPONENT_REF.matcher(ref);
                if (matcher.matches()) {
                    String target = matcher.group(1);
                    if (target.equals(rootComponent)) {
                        map.put("$ref", "#");
                    } else if (contracts.containsKey(target)) {
                        map.put("$ref", contracts.get(target).uri().toString());
                    } else {
                        map.put("$ref", "#/$defs/" + target);
                        pendingDefinitions.add(target);
                    }
                }
            }
            for (Object value : map.values()) {
                rewriteReferences(value, rootComponent, contracts, pendingDefinitions);
            }
        } else if (node instanceof List<?> list) {
            for (Object value : list) {
                rewriteReferences(value, rootComponent, contracts, pendingDefinitions);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Contract> discoverContracts(final Map<String, Map<String, Object>> components) {
        Map<String, Contract> contracts = new LinkedHashMap<>();
        Set<URI> uris = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> entry : components.entrySet()) {
            Object propertiesValue = entry.getValue().get("properties");
            if (!(propertiesValue instanceof Map<?, ?> properties)) {
                continue;
            }
            Object schemaPropertyValue = properties.get("$schema");
            if (!(schemaPropertyValue instanceof Map<?, ?> schemaProperty)) {
                continue;
            }
            Object constValue = schemaProperty.get("const");
            if (constValue == null) {
                continue;
            }
            String location = "components.schemas." + entry.getKey() + ".properties.$schema";
            if (!(constValue instanceof String uriText) || uriText.isBlank()) {
                throw new IllegalArgumentException(location + ".const must be a non-empty URI string");
            }
            requireContractProperty(entry.getKey(), entry.getValue(), schemaProperty, uriText);
            Contract contract = contractFromUri(entry.getKey(), uriText);
            requireMetadataObjectMatches(entry.getKey(), entry.getValue(), contract.object());
            if (!uris.add(contract.uri())) {
                throw new IllegalArgumentException("Duplicate contract URI " + contract.uri());
            }
            contracts.put(entry.getKey(), contract);
        }
        return contracts;
    }

    private void requireContractProperty(final String component,
            final Map<String, Object> componentSchema,
            final Map<?, ?> schemaProperty,
            final String uriText) {
        String location = "components.schemas." + component + ".properties.$schema";
        if (!"string".equals(schemaProperty.get("type"))) {
            throw new IllegalArgumentException(location + ".type must be string");
        }
        if (!"uri".equals(schemaProperty.get("format"))) {
            throw new IllegalArgumentException(location + ".format must be uri");
        }
        if (!Boolean.TRUE.equals(schemaProperty.get("readOnly"))) {
            throw new IllegalArgumentException(location + ".readOnly must be true");
        }
        if (!uriText.equals(schemaProperty.get("default"))) {
            throw new IllegalArgumentException(location + ".default must equal .const");
        }
        Object requiredValue = componentSchema.get("required");
        if (!(requiredValue instanceof List<?> required) || !required.contains("$schema")) {
            throw new IllegalArgumentException("components.schemas." + component + ".required must contain $schema");
        }
    }

    private void requireMetadataObjectMatches(final String component,
            final Map<String, Object> componentSchema,
            final String uriObject) {
        Object labs64Value = componentSchema.get("x-labs64");
        if (!(labs64Value instanceof Map<?, ?> labs64)) {
            return;
        }
        Object schemaValue = labs64.get("schema");
        if (!(schemaValue instanceof Map<?, ?> metadata)) {
            return;
        }
        Object objectValue = metadata.get("object");
        if (objectValue != null && !uriObject.equals(String.valueOf(objectValue))) {
            throw new IllegalArgumentException("components.schemas." + component
                    + ".x-labs64.schema.object must match the object in $schema.const: " + uriObject);
        }
    }

    private Contract contractFromUri(final String component, final String uriText) {
        final URI uri;
        try {
            uri = URI.create(uriText);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Contract " + component + " has an invalid $schema.const URI: "
                    + uriText, exception);
        }
        if (!"https".equals(uri.getScheme())
                || !REGISTRY_HOST.equals(uri.getHost())
                || uri.getPort() != -1
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Contract " + component
                    + " $schema.const must use https://labs64.io/schemas without port, query, or fragment: " + uriText);
        }

        String[] segments = uri.getRawPath().split("/");
        List<String> path = new ArrayList<>();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                path.add(segment);
            }
        }
        String fileName = path.isEmpty() ? "" : path.get(path.size() - 1);
        if (path.size() != 4 || !"schemas".equals(path.get(0)) || !fileName.endsWith(".json")) {
            throw new IllegalArgumentException("Contract " + component
                    + " $schema.const must match https://labs64.io/schemas/{module}/{object}/{schemaRevision}.json: "
                    + uriText);
        }
        String module = path.get(1);
        String object = path.get(2);
        String revision = fileName.substring(0, fileName.length() - ".json".length());
        requireSafeSegment("module", module);
        requireSafeSegment("contract object", object);
        requireSafeSegment("schema revision", revision);
        return new Contract(component, module, object, revision, uri);
    }

    private Map<String, Object> readYaml(final Path input) throws IOException {
        return yamlMapper.readValue(input.toFile(), new TypeReference<>() { });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> componentSchemas(final Map<String, Object> openApi) {
        Object componentsValue = openApi.get("components");
        if (!(componentsValue instanceof Map<?, ?> components)) {
            throw new IllegalArgumentException("OpenAPI document has no components");
        }
        Object schemasValue = components.get("schemas");
        if (!(schemasValue instanceof Map<?, ?> schemas)) {
            throw new IllegalArgumentException("OpenAPI document has no components.schemas");
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : schemas.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> schema) {
                result.put(String.valueOf(entry.getKey()), (Map<String, Object>) schema);
            }
        }
        return result;
    }

    private void requireOpenApi31(final Map<String, Object> openApi) {
        String version = String.valueOf(openApi.getOrDefault("openapi", ""));
        if (!version.startsWith("3.1.")) {
            throw new IllegalArgumentException("OpenAPI 3.1 is required, found: " + version);
        }
    }

    private void requireSafeSegment(final String name, final String value) {
        if (!SAFE_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + SAFE_SEGMENT.pattern() + ": " + value);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopyMap(final Map<String, Object> source) {
        return jsonMapper.convertValue(source, LinkedHashMap.class);
    }
}
