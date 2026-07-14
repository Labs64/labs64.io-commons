package io.labs64.authcontext.cedar;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.core.io.ByteArrayResource;

import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.Value;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.labs64.authcontext.core.AuthContext;

/**
 * Pins the AuthContext → Cedar request serialization to the cross-language
 * contract in {@code test-vectors/cedar-request-vectors.json}:
 * the Java and Python PEPs must build identical Cedar requests for the same
 * trusted headers.
 */
class CedarRequestVectorsTest {

    private static final String TRIVIAL_POLICY = "permit(principal, action, resource) when { false };";

    private final CedarAuthorizationService service = new CedarAuthorizationService(
            new CedarProperties(), new ByteArrayResource(TRIVIAL_POLICY.getBytes()));

    @TestFactory
    List<DynamicTest> vectors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream("/test-vectors/cedar-request-vectors.json")) {
            root = mapper.readTree(in);
        }
        return java.util.stream.StreamSupport.stream(root.get("cases").spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.get("name").asText(), () -> assertCase(c)))
                .toList();
    }

    private void assertCase(final JsonNode c) {
        JsonNode in = c.get("authContext");
        Set<String> scopes = new LinkedHashSet<>();
        in.get("scopes").forEach(s -> scopes.add(s.asText()));
        AuthContext context = new AuthContext(
                in.get("user").asText(),
                in.get("tenant").isNull() ? null : in.get("tenant").asText(),
                scopes,
                in.get("requestId").asText());

        JsonNode expected = c.get("cedarRequest");

        // principal
        Entity principal = service.buildPrincipal(context);
        JsonNode expectedPrincipal = expected.get("principal");
        assertThat(principal.getEUID().getType().toString()).isEqualTo(expectedPrincipal.get("type").asText());
        assertThat(principal.getEUID().getId().getRepr()).isEqualTo(expectedPrincipal.get("id").asText());

        // context
        Map<String, Value> requestContext = service.buildContext(context);
        JsonNode expectedContext = expected.get("context");
        assertThat(requestContext.get("requestId").toString())
                .isEqualTo(expectedContext.get("requestId").asText());
        Set<String> actualScopes = new LinkedHashSet<>();
        ((Iterable<Value>) requestContext.get("scopes")).forEach(v -> actualScopes.add(v.toString()));
        Set<String> expectedScopes = new LinkedHashSet<>();
        expectedContext.get("scopes").forEach(s -> expectedScopes.add(s.asText()));
        assertThat(actualScopes).isEqualTo(expectedScopes);

        if (expectedContext.has("tenant")) {
            EntityUID tenant = (EntityUID) requestContext.get("tenant");
            assertThat(tenant.getType().toString()).isEqualTo(expectedContext.get("tenant").get("type").asText());
            assertThat(tenant.getId().getRepr()).isEqualTo(expectedContext.get("tenant").get("id").asText());
            // principal must also be a member of the tenant (structural guard input)
            assertThat(principal.parentsEUIDs).contains(tenant);
        } else {
            assertThat(requestContext).doesNotContainKey("tenant");
            assertThat(principal.parentsEUIDs).isEmpty();
        }
    }
}
