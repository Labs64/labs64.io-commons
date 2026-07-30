package io.labs64.authcontext.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.labs64.authcontext.test.AuthEnforcementContract.ProtectedOperation;

/**
 * The auth-enforcement contract is the thing that decides whether "every
 * protected endpoint is protected" is a fact or a slogan, so it gets its own
 * tests: it must select the right operations, build callable paths, fail on a
 * permissive runtime, and refuse to pass silently on an empty spec.
 */
class AuthEnforcementContractTest {

    @TempDir
    Path tmp;

    private Path spec(final String yaml) throws IOException {
        Path file = tmp.resolve("openapi-" + System.nanoTime() + ".yaml");
        Files.writeString(file, yaml);
        return file;
    }

    private static final String MIXED_SPEC = """
            openapi: 3.0.3
            info:
              title: Test
              version: 1.0.0
            security:
              - oauth: []
            paths:
              /audit/publish:
                post:
                  operationId: publishEvent
                  security:
                    - oauth:
                        - audit-event:write
                  x-labs64:
                    auth:
                      tenant: true
                  responses:
                    '200':
                      description: ok
              /health:
                get:
                  operationId: health
                  security: []
                  responses:
                    '200':
                      description: ok
            """;

    @Test
    void selectsOnlyTheProtectedOperations() throws IOException {
        List<ProtectedOperation> operations = AuthEnforcementContract.protectedOperations(spec(MIXED_SPEC));

        assertThat(operations).hasSize(1);
        ProtectedOperation publish = operations.get(0);
        assertThat(publish.operationId()).isEqualTo("publishEvent");
        assertThat(publish.method()).isEqualTo("POST");
        assertThat(publish.tenantRequired()).isTrue();
        assertThat(publish.scopes()).containsExactly("audit-event:write");
    }

    @Test
    void publicOperationsAreNotTested() throws IOException {
        assertThat(AuthEnforcementContract.protectedOperations(spec(MIXED_SPEC)))
                .extracting(ProtectedOperation::operationId)
                .doesNotContain("health");
    }

    // --- path templating ------------------------------------------------------

    private static final String PARAM_SPEC = """
            openapi: 3.0.3
            info:
              title: Test
              version: 1.0.0
            paths:
              /payments/{paymentId}/refunds/{index}:
                get:
                  operationId: getRefund
                  x-labs64:
                    auth:
                      tenant: true
                  parameters:
                    - name: paymentId
                      in: path
                      required: true
                      schema:
                        type: string
                        format: uuid
                    - name: index
                      in: path
                      required: true
                      schema:
                        type: integer
                  responses:
                    '200':
                      description: ok
            """;

    @Test
    void substitutesPathParametersWithTypeValidSamples() throws IOException {
        ProtectedOperation operation = AuthEnforcementContract.protectedOperations(spec(PARAM_SPEC)).get(0);

        assertThat(operation.pathTemplate()).isEqualTo("/payments/{paymentId}/refunds/{index}");
        assertThat(operation.samplePath())
                .isEqualTo("/payments/00000000-0000-4000-8000-000000000000/refunds/1")
                .doesNotContain("{", "}");
    }

    @Test
    void inheritsPathLevelParameters() throws IOException {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /tenants/{tenantId}:
                    parameters:
                      - name: tenantId
                        in: path
                        required: true
                        schema:
                          type: integer
                    delete:
                      operationId: deleteTenant
                      x-labs64:
                        auth:
                          tenant: true
                      responses:
                        '204':
                          description: ok
                """;
        assertThat(AuthEnforcementContract.protectedOperations(spec(yaml)).get(0).samplePath())
                .isEqualTo("/tenants/1");
    }

    // --- the actual contract --------------------------------------------------

    private static List<String> run(final Stream<DynamicTest> tests) {
        List<String> failures = new ArrayList<>();
        tests.forEach(test -> {
            try {
                test.getExecutable().execute();
            } catch (Throwable failure) {
                failures.add(test.getDisplayName() + ": " + failure.getMessage());
            }
        });
        return failures;
    }

    @Test
    void passesWhenEveryProtectedOperationIsRejected() throws IOException {
        Stream<DynamicTest> tests = AuthEnforcementContract.rejectsAnonymousAccess(
                spec(MIXED_SPEC), (method, path) -> 401);

        assertThat(run(tests)).isEmpty();
    }

    @Test
    void acceptsForbiddenAsWellAsUnauthorized() throws IOException {
        assertThat(run(AuthEnforcementContract.rejectsAnonymousAccess(spec(MIXED_SPEC), (m, p) -> 403))).isEmpty();
    }

    @Test
    void failsWhenAProtectedOperationServesAnonymousCallers() throws IOException {
        Stream<DynamicTest> tests = AuthEnforcementContract.rejectsAnonymousAccess(
                spec(MIXED_SPEC), (method, path) -> 200);

        assertThat(run(tests))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("publishEvent")
                .contains("returned 200");
    }

    @Test
    void failsWhenTheRouteIsNotEvenMapped() throws IOException {
        // A 404 means the call never reached an enforcement point — that is a gap,
        // not a pass. Only an explicit refusal counts.
        assertThat(run(AuthEnforcementContract.rejectsAnonymousAccess(spec(MIXED_SPEC), (m, p) -> 404)))
                .hasSize(1);
    }

    @Test
    void anEmptySuiteIsNotAPassingSuite() throws IOException {
        String allPublic = """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /health:
                    get:
                      operationId: health
                      responses:
                        '200':
                          description: ok
                """;

        assertThat(run(AuthEnforcementContract.rejectsAnonymousAccess(spec(allPublic), (m, p) -> 401)))
                .singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("No protected operations found");
    }

    @Test
    void anOperationWithoutAuthRequirementsIsPublic() throws IOException {
        String undeclared = """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /secrets:
                    get:
                      operationId: listSecrets
                      responses:
                        '200':
                          description: ok
                """;

        assertThat(AuthEnforcementContract.protectedOperations(spec(undeclared))).isEmpty();
    }

    @Test
    void reportsExceptionsFromTheRequestExecutorAsFailures() throws IOException {
        Stream<DynamicTest> tests = AuthEnforcementContract.rejectsAnonymousAccess(
                spec(MIXED_SPEC), (method, path) -> {
                    throw new IllegalStateException("context failed to start");
                });

        assertThat(run(tests)).hasSize(1).first(org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("context failed to start");
    }

    @Test
    void coversEveryProtectedOperationInAMultiOperationSpec() throws IOException {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                paths:
                  /a:
                    get:
                      operationId: a
                      x-labs64: { auth: { tenant: true } }
                      responses: { '200': { description: ok } }
                    post:
                      operationId: b
                      x-labs64: { auth: { tenant: true } }
                      responses: { '200': { description: ok } }
                  /c:
                    delete:
                      operationId: c
                      security:
                        - oauth: [ 'x:write' ]
                      responses: { '200': { description: ok } }
                """;
        List<String> called = new ArrayList<>();
        run(AuthEnforcementContract.rejectsAnonymousAccess(spec(yaml), (method, path) -> {
            called.add(method + " " + path);
            return 401;
        }));

        assertThat(called).containsExactlyInAnyOrder("GET /a", "POST /a", "DELETE /c");
    }

    @Test
    void honoursGlobalSecurityDeclaredAtSpecLevel() throws IOException {
        String yaml = """
                openapi: 3.0.3
                info:
                  title: Test
                  version: 1.0.0
                security:
                  - oauth:
                      - payment:read
                paths:
                  /inherited:
                    get:
                      operationId: inherited
                      responses: { '200': { description: ok } }
                """;
        List<ProtectedOperation> operations = AuthEnforcementContract.protectedOperations(spec(yaml));

        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).tenantRequired()).isFalse();
        assertThat(operations.get(0).scopes()).containsExactly("payment:read");
    }

    @Test
    void operationToStringIdentifiesTheContractLine() throws IOException {
        assertThat(AuthEnforcementContract.protectedOperations(spec(MIXED_SPEC)).get(0))
                .hasToString("POST /audit/publish (publishEvent)");
    }
}
