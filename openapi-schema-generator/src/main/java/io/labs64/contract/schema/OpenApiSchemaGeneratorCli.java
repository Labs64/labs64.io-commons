package io.labs64.contract.schema;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Command-line entry point for {@link OpenApiSchemaGenerator}. */
public final class OpenApiSchemaGeneratorCli {

    private OpenApiSchemaGeneratorCli() {
    }

    public static void main(final String[] args) throws Exception {
        int exitCode = run(args, System.in, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(final String[] args, final InputStream input, final PrintStream output, final PrintStream error)
            throws Exception {
        Options options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException exception) {
            error.println(exception.getMessage());
            return 2;
        }
        if (options.help()) {
            output.println(usage());
            return 0;
        }

        OpenApiSchemaGenerator generator = new OpenApiSchemaGenerator();
        OpenApiSchemaGenerator.Plan plan = generator.plan(options.input(), options.outputRoot());
        printPlan(plan, output);

        if (plan.schemas().isEmpty()) {
            output.println("No components with properties.$schema.const were found.");
            return 0;
        }
        if (options.dryRun()) {
            output.println("Dry run: no files written.");
            return plan.schemas().stream().anyMatch(schema -> schema.status() == OpenApiSchemaGenerator.Status.CONFLICT)
                    ? 1 : 0;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        Set<Path> overwrite = authorizeConflicts(plan, options, reader, output);
        long conflicts = plan.schemas().stream()
                .filter(schema -> schema.status() == OpenApiSchemaGenerator.Status.CONFLICT)
                .count();
        if (overwrite.size() != conflicts) {
            output.println("Aborted. No files written.");
            return 1;
        }

        long creates = plan.schemas().stream()
                .filter(schema -> schema.status() == OpenApiSchemaGenerator.Status.CREATE)
                .count();
        if ((creates > 0 || conflicts > 0) && !options.yes()
                && !confirm(reader, output, "Write " + (creates + conflicts) + " schema file(s)? [Y/n] ", true)) {
            output.println("Aborted. No files written.");
            return 1;
        }

        OpenApiSchemaGenerator.Result result = generator.write(plan, overwrite);
        result.created().forEach(path -> output.println("CREATED   " + relative(plan.outputRoot(), path)));
        result.overwritten().forEach(path -> output.println("OVERWROTE " + relative(plan.outputRoot(), path)));
        result.unchanged().forEach(path -> output.println("UNCHANGED " + relative(plan.outputRoot(), path)));
        output.println("Review the generated schemas in " + plan.outputRoot().resolve("schemas"));
        return 0;
    }

    private static Set<Path> authorizeConflicts(final OpenApiSchemaGenerator.Plan plan,
            final Options options,
            final BufferedReader reader,
            final PrintStream output) throws IOException {
        Set<Path> overwrite = new LinkedHashSet<>();
        for (OpenApiSchemaGenerator.PlannedSchema schema : plan.schemas()) {
            if (schema.status() != OpenApiSchemaGenerator.Status.CONFLICT) {
                continue;
            }
            if (options.forceOverwrite()) {
                overwrite.add(schema.output());
                continue;
            }
            if (options.yes()) {
                output.println("CONFLICT requires --force-overwrite: " + relative(plan.outputRoot(), schema.output()));
                continue;
            }

            output.println();
            output.println("Existing schema revision has different content:");
            output.println("  " + relative(plan.outputRoot(), schema.output()));
            printDifferenceSummary(schema, output);
            while (true) {
                output.print("[d] Show diff, [a] abort, [o] overwrite unpublished revision: ");
                output.flush();
                String answer = reader.readLine();
                if (answer == null || answer.isBlank() || "a".equalsIgnoreCase(answer)) {
                    return overwrite;
                }
                if ("d".equalsIgnoreCase(answer)) {
                    printDifference(schema, output);
                    continue;
                }
                if ("o".equalsIgnoreCase(answer)) {
                    String confirmation = "overwrite " + schema.module() + "/" + schema.object() + "/" + schema.revision();
                    output.print("Type \"" + confirmation + "\" to confirm: ");
                    output.flush();
                    if (confirmation.equals(reader.readLine())) {
                        overwrite.add(schema.output());
                        break;
                    }
                    output.println("Confirmation did not match.");
                    return overwrite;
                }
            }
        }
        return overwrite;
    }

    private static void printPlan(final OpenApiSchemaGenerator.Plan plan, final PrintStream output) {
        output.println("OpenAPI is the source of truth. Schema revisions are read from properties.$schema.const.");
        output.println("Output root: " + plan.outputRoot());
        for (OpenApiSchemaGenerator.PlannedSchema schema : plan.schemas()) {
            output.printf("%-9s %s  [component=%s, revision=%s]%n",
                    schema.status(),
                    relative(plan.outputRoot(), schema.output()),
                    schema.component(),
                    schema.revision());
        }
    }

    private static void printDifferenceSummary(final OpenApiSchemaGenerator.PlannedSchema schema,
            final PrintStream output) throws IOException {
        List<String> existing = schema.existingContents().lines().toList();
        List<String> generated = schema.contents().lines().toList();
        int maximum = Math.max(existing.size(), generated.size());
        for (int index = 0; index < maximum; index++) {
            String oldLine = index < existing.size() ? existing.get(index) : null;
            String newLine = index < generated.size() ? generated.get(index) : null;
            if (!java.util.Objects.equals(oldLine, newLine)) {
                output.println("  First difference at line " + (index + 1)
                        + " (existing=" + existing.size() + " lines, generated=" + generated.size() + " lines)");
                return;
            }
        }
    }

    private static void printDifference(final OpenApiSchemaGenerator.PlannedSchema schema,
            final PrintStream output) throws IOException {
        List<String> existing = schema.existingContents().lines().toList();
        List<String> generated = schema.contents().lines().toList();
        output.println("--- existing/" + schema.output().getFileName());
        output.println("+++ generated/" + schema.output().getFileName());
        int maximum = Math.max(existing.size(), generated.size());
        for (int index = 0; index < maximum; index++) {
            String oldLine = index < existing.size() ? existing.get(index) : null;
            String newLine = index < generated.size() ? generated.get(index) : null;
            if (!java.util.Objects.equals(oldLine, newLine)) {
                output.println("@@ line " + (index + 1) + " @@");
                if (oldLine != null) {
                    output.println("-" + oldLine);
                }
                if (newLine != null) {
                    output.println("+" + newLine);
                }
            }
        }
    }

    private static boolean confirm(final BufferedReader reader,
            final PrintStream output,
            final String prompt,
            final boolean defaultValue) throws IOException {
        output.print(prompt);
        output.flush();
        String answer = reader.readLine();
        if (answer == null) {
            return false;
        }
        if (answer.isBlank()) {
            return defaultValue;
        }
        return "y".equalsIgnoreCase(answer) || "yes".equalsIgnoreCase(answer);
    }

    private static Path relative(final Path root, final Path path) {
        return root.relativize(path);
    }

    private static Options parseArgs(final String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> flags = new LinkedHashSet<>();
        List<String> supportedValues = List.of("--input", "--output-root");
        List<String> supportedFlags = List.of("--dry-run", "--yes", "--force-overwrite", "--help");
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (supportedFlags.contains(argument)) {
                flags.add(argument);
            } else if (supportedValues.contains(argument)) {
                if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Missing value for " + argument + "\n" + usage());
                }
                values.put(argument, args[++index]);
            } else {
                throw new IllegalArgumentException("Unknown argument: " + argument + "\n" + usage());
            }
        }
        if (flags.contains("--help")) {
            return new Options(null, null, false, false, false, true);
        }
        Path input = requiredPath(values, "--input");
        Path outputRoot = requiredPath(values, "--output-root");
        return new Options(input, outputRoot,
                flags.contains("--dry-run"),
                flags.contains("--yes"),
                flags.contains("--force-overwrite"),
                false);
    }

    private static Path requiredPath(final Map<String, String> options, final String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument " + name + "\n" + usage());
        }
        return Path.of(value);
    }

    private static String usage() {
        return "Usage: OpenApiSchemaGeneratorCli --input <openapi.yaml> --output-root <labs64.io-dir> "
                + "[--dry-run] [--yes] [--force-overwrite]";
    }

    private record Options(
            Path input,
            Path outputRoot,
            boolean dryRun,
            boolean yes,
            boolean forceOverwrite,
            boolean help) {
    }
}
