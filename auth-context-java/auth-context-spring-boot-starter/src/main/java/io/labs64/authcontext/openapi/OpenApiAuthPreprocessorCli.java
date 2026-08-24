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
        String cerbosOutput = options.get("--cerbos-output");
        String routesOutput = options.get("--routes-output");
        String basePath = options.get("--base-path");
        String publicPathsOutput = options.get("--public-paths-output");
        String module = options.get("--module");
        if ((cerbosOutput != null || routesOutput != null) && (module == null || module.isBlank())) {
            throw usage();
        }

        new OpenApiAuthPreprocessor().process(input, openApiOutput,
                cerbosOutput == null ? null : Path.of(cerbosOutput), module,
                routesOutput == null ? null : Path.of(routesOutput), basePath,
                publicPathsOutput == null ? null : Path.of(publicPathsOutput));
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
                + "--openapi-output <generated-openapi.yaml> "
                + "[--cerbos-output <dir>] [--routes-output <routes.yaml>] [--base-path <prefix>] "
                + "[--public-paths-output <auth-public-paths>] "
                + "[--module <name>] (module required when --cerbos-output or --routes-output is given)");
    }
}
