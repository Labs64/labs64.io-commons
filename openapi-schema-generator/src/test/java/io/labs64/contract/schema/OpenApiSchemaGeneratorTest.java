package io.labs64.contract.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSchemaGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void plansGenericRegistryPathsFromOpenApi() throws Exception {
        Path input = writeOpenApi();
        String originalOpenApi = Files.readString(input);
        Path site = Files.createDirectory(tempDir.resolve("site"));

        OpenApiSchemaGenerator.Plan plan = new OpenApiSchemaGenerator().plan(input, site);

        assertThat(plan.contractCount()).isEqualTo(2);
        OpenApiSchemaGenerator.PlannedSchema payment = plan.schemas().get(0);
        assertThat(payment.module()).isEqualTo("payment-gateway");
        assertThat(payment.object()).isEqualTo("Payment");
        assertThat(payment.revision()).isEqualTo("1.0.1");
        assertThat(payment.output()).isEqualTo(
                site.toAbsolutePath().resolve("schemas/payment-gateway/Payment/1.0.1.json"));
        assertThat(payment.status()).isEqualTo(OpenApiSchemaGenerator.Status.CREATE);
        assertThat(payment.contents())
                .contains("\"$schema\" : \"https://json-schema.org/draft/2020-12/schema\"")
                .contains("\"$id\" : \"https://labs64.io/schemas/payment-gateway/Payment/1.0.1.json\"")
                .contains("\"version\" : \"2.0.0\"")
                .contains("\"$ref\" : \"#/$defs/Money\"")
                .contains("\"$ref\" : \"https://labs64.io/schemas/payment-gateway/Customer/3.0.2.json\"");
        assertThat(Files.readString(input)).isEqualTo(originalOpenApi);
    }

    @Test
    void writesAtomicallyAndThenPlansIdenticalFilesAsUnchanged() throws Exception {
        Path input = writeOpenApi();
        Path site = Files.createDirectory(tempDir.resolve("site"));
        OpenApiSchemaGenerator generator = new OpenApiSchemaGenerator();

        OpenApiSchemaGenerator.Result result = generator.write(generator.plan(input, site), Set.of());
        OpenApiSchemaGenerator.Plan secondPlan = generator.plan(input, site);

        assertThat(result.created()).hasSize(2);
        assertThat(result.overwritten()).isEmpty();
        assertThat(secondPlan.schemas()).allMatch(schema -> schema.status() == OpenApiSchemaGenerator.Status.UNCHANGED);
        assertThat(site.resolve("schemas/payment-gateway/Payment/1.0.1.json")).exists();
        assertThat(site.resolve("schemas/payment-gateway/Customer/3.0.2.json")).exists();
        assertThat(Files.list(site.resolve("schemas/payment-gateway/Payment")))
                .noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
    }

    @Test
    void conflictRequiresExplicitAuthorization() throws Exception {
        Path input = writeOpenApi();
        Path site = Files.createDirectory(tempDir.resolve("site"));
        Path payment = site.resolve("schemas/payment-gateway/Payment/1.0.1.json");
        Files.createDirectories(payment.getParent());
        Files.writeString(payment, "published-content");
        OpenApiSchemaGenerator generator = new OpenApiSchemaGenerator();
        OpenApiSchemaGenerator.Plan plan = generator.plan(input, site);

        assertThat(plan.schemas().get(0).status()).isEqualTo(OpenApiSchemaGenerator.Status.CONFLICT);
        assertThatThrownBy(() -> generator.write(plan, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without confirmation");
        assertThat(Files.readString(payment)).isEqualTo("published-content");

        OpenApiSchemaGenerator.Result result = generator.write(plan, Set.of(payment));
        assertThat(result.overwritten()).containsExactly(payment.toAbsolutePath());
        assertThat(Files.readString(payment)).contains("\"$id\"");
    }

    @Test
    void refusesToWriteWhenOutputChangedAfterPlanning() throws Exception {
        Path input = writeOpenApi();
        Path site = Files.createDirectory(tempDir.resolve("site"));
        OpenApiSchemaGenerator generator = new OpenApiSchemaGenerator();
        OpenApiSchemaGenerator.Plan plan = generator.plan(input, site);
        Path payment = plan.schemas().get(0).output();
        Files.createDirectories(payment.getParent());
        Files.writeString(payment, "appeared-after-plan");

        assertThatThrownBy(() -> generator.write(plan, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("changed after confirmation");
        assertThat(site.resolve("schemas/payment-gateway/Customer/3.0.2.json")).doesNotExist();
    }

    @Test
    void requiresAnExistingOutputRoot() throws Exception {
        assertThatThrownBy(() -> new OpenApiSchemaGenerator().plan(writeOpenApi(), tempDir.resolve("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("existing directory");
    }

    @Test
    void rejectsNonCanonicalRegistryUri() throws Exception {
        Path input = writeOpenApi(Files.readString(writeOpenApi())
                .replace("https://labs64.io/schemas/", "https://schemas.labs64.io/"));
        Path site = Files.createDirectory(tempDir.resolve("site"));

        assertThatThrownBy(() -> new OpenApiSchemaGenerator().plan(input, site))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https://labs64.io/schemas");
    }

    @Test
    void rejectsIncompleteSchemaPropertyContract() throws Exception {
        String yaml = Files.readString(writeOpenApi())
                .replace("readOnly: true", "readOnly: false");
        Path input = writeOpenApi(yaml);
        Path site = Files.createDirectory(tempDir.resolve("site"));

        assertThatThrownBy(() -> new OpenApiSchemaGenerator().plan(input, site))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readOnly must be true");
    }

    @Test
    void rejectsMetadataObjectThatDisagreesWithCanonicalUri() throws Exception {
        String yaml = Files.readString(writeOpenApi()).replace("object: Payment", "object: Invoice");
        Path input = writeOpenApi(yaml);
        Path site = Files.createDirectory(tempDir.resolve("site"));

        assertThatThrownBy(() -> new OpenApiSchemaGenerator().plan(input, site))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match the object in $schema.const");
    }

    @Test
    void ignoresComponentsWithoutSchemaConst() throws Exception {
        Path input = writeOpenApi("""
                openapi: 3.1.0
                info: { title: Test, version: 1.0.0 }
                paths: {}
                components:
                  schemas:
                    Money:
                      type: object
                    PartialMarker:
                      type: object
                      properties:
                        $schema:
                          type: string
                """);
        Path site = Files.createDirectory(tempDir.resolve("site"));

        assertThat(new OpenApiSchemaGenerator().plan(input, site).schemas()).isEmpty();
    }

    private Path writeOpenApi() throws Exception {
        return writeOpenApi("""
                openapi: 3.1.0
                info:
                  title: Payment Gateway
                  version: 1.0.0
                paths: {}
                components:
                  schemas:
                    Payment:
                      type: object
                      title: Payment
                      required: [amount, $schema]
                      properties:
                        $schema:
                          type: string
                          format: uri
                          readOnly: true
                          const: https://labs64.io/schemas/payment-gateway/Payment/1.0.1.json
                          default: https://labs64.io/schemas/payment-gateway/Payment/1.0.1.json
                        amount:
                          $ref: '#/components/schemas/Money'
                        customer:
                          $ref: '#/components/schemas/Customer'
                      x-labs64:
                        schema:
                          object: Payment
                          version: 2.0.0
                    Money:
                      type: object
                      properties:
                        amount: { type: number }
                    Customer:
                      type: object
                      required: [$schema]
                      properties:
                        $schema:
                          type: string
                          format: uri
                          readOnly: true
                          const: https://labs64.io/schemas/payment-gateway/Customer/3.0.2.json
                          default: https://labs64.io/schemas/payment-gateway/Customer/3.0.2.json
                        name: { type: string }
                      x-labs64:
                        schema:
                          object: Customer
                          version: 7.1.0
                """);
    }

    private Path writeOpenApi(final String contents) throws Exception {
        Path input = tempDir.resolve("openapi-" + System.nanoTime() + ".yaml");
        Files.writeString(input, contents);
        return input;
    }
}
