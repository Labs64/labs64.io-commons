package io.labs64.contract.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSchemaGeneratorCliTest {

    @TempDir
    Path tempDir;

    @Test
    void dryRunPrintsPlanWithoutWriting() throws Exception {
        Path site = Files.createDirectory(tempDir.resolve("site"));
        Invocation invocation = invoke("", "--input", writeOpenApi().toString(),
                "--output-root", site.toString(), "--dry-run");

        assertThat(invocation.exitCode()).isZero();
        assertThat(invocation.output())
                .contains("Schema revisions are read from properties.$schema.const")
                .contains("CREATE")
                .contains("schemas/checkout/Receipt/1.0.0.json")
                .contains("Dry run: no files written");
        assertThat(site.resolve("schemas/checkout/Receipt/1.0.0.json")).doesNotExist();
    }

    @Test
    void interactiveGenerationAcceptsDefaultConfirmation() throws Exception {
        Path site = Files.createDirectory(tempDir.resolve("site"));
        Invocation invocation = invoke("\n", "--input", writeOpenApi().toString(),
                "--output-root", site.toString());

        assertThat(invocation.exitCode()).isZero();
        assertThat(invocation.output()).contains("Write 1 schema file(s)? [Y/n]").contains("CREATED");
        assertThat(site.resolve("schemas/checkout/Receipt/1.0.0.json")).exists();
    }

    @Test
    void conflictingRevisionRequiresTypedOverwriteConfirmation() throws Exception {
        Path site = Files.createDirectory(tempDir.resolve("site"));
        Path schema = site.resolve("schemas/checkout/Receipt/1.0.0.json");
        Files.createDirectories(schema.getParent());
        Files.writeString(schema, "unpublished");

        Invocation aborted = invoke("a\n", "--input", writeOpenApi().toString(),
                "--output-root", site.toString());
        assertThat(aborted.exitCode()).isOne();
        assertThat(Files.readString(schema)).isEqualTo("unpublished");

        Invocation overwritten = invoke("o\noverwrite checkout/Receipt/1.0.0\n\n",
                "--input", writeOpenApi().toString(), "--output-root", site.toString());
        assertThat(overwritten.exitCode()).isZero();
        assertThat(overwritten.output()).contains("OVERWROTE schemas/checkout/Receipt/1.0.0.json");
        assertThat(Files.readString(schema)).contains("\"$id\"");
    }

    @Test
    void yesFlagDoesNotAuthorizeOverwrite() throws Exception {
        Path site = Files.createDirectory(tempDir.resolve("site"));
        Path schema = site.resolve("schemas/checkout/Receipt/1.0.0.json");
        Files.createDirectories(schema.getParent());
        Files.writeString(schema, "published");

        Invocation invocation = invoke("", "--input", writeOpenApi().toString(),
                "--output-root", site.toString(), "--yes");

        assertThat(invocation.exitCode()).isOne();
        assertThat(invocation.output()).contains("CONFLICT requires --force-overwrite");
        assertThat(Files.readString(schema)).isEqualTo("published");
    }

    private Invocation invoke(final String stdin, final String... args) throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = OpenApiSchemaGeneratorCli.run(
                args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Invocation(exitCode, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private Path writeOpenApi() throws Exception {
        Path input = tempDir.resolve("openapi-" + System.nanoTime() + ".yaml");
        Files.writeString(input, """
                openapi: 3.1.0
                info: { title: Checkout, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                    Receipt:
                      type: object
                      required: [$schema]
                      properties:
                        $schema:
                          type: string
                          format: uri
                          readOnly: true
                          const: https://labs64.io/schemas/checkout/Receipt/1.0.0.json
                          default: https://labs64.io/schemas/checkout/Receipt/1.0.0.json
                """);
        return input;
    }

    private record Invocation(int exitCode, String output, String error) {
    }
}
