package io.labs64.authcontext.openapi;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Command line entry point for {@link OpenApiAuthPreprocessor}.
 */
public final class OpenApiAuthPreprocessorCli {

    private OpenApiAuthPreprocessorCli() {
    }

    public static void main(final String[] args) throws Exception {
        Map<String, String> options = parseArgs(args);
        Path input = requiredPath(options, "--input");
        Path openApiOutput = requiredPath(options, "--openapi-output");
        Path policyOutput = requiredPath(options, "--policy-output");
        String cedarOutput = options.get("--cedar-output");
        String module = options.get("--module");
        if (cedarOutput != null && (module == null || module.isBlank())) {
            throw usage();
        }

        new OpenApiAuthPreprocessor().process(input, openApiOutput, policyOutput,
                cedarOutput == null ? null : Path.of(cedarOutput), module);
    }

    private static Map<String, String> parseArgs(final String[] args) {
        if (args.length % 2 != 0) {
            throw usage();
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String name = args[i];
            if (!name.startsWith("--")) {
                throw usage();
            }
            options.put(name, args[i + 1]);
        }
        return options;
    }

    private static Path requiredPath(final Map<String, String> options, final String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw usage();
        }
        return Path.of(value);
    }

    private static IllegalArgumentException usage() {
        return new IllegalArgumentException("Usage: OpenApiAuthPreprocessorCli --input <openapi.yaml> "
                + "--openapi-output <generated-openapi.yaml> --policy-output <auth-policy.json> "
                + "[--cedar-output <module.cedar> --module <name>]");
    }
}
