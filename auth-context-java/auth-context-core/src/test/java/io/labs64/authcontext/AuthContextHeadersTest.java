package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthContextHeaders;
import io.labs64.authcontext.core.AuthHeaders;

class AuthContextHeadersTest {

    private final AuthContext context = new AuthContext(
            "user-1", "tenant-1", Set.of("payment:read"), "request-1");

    @Test
    void encodesCompleteContext() {
        assertThat(AuthContextHeaders.encode(context)).containsExactlyInAnyOrderEntriesOf(Map.of(
                AuthHeaders.USER, "user-1",
                AuthHeaders.SCOPES, "payment:read",
                AuthHeaders.TENANT, "tenant-1",
                AuthHeaders.REQUEST_ID, "request-1"));
    }

    @Test
    void replacesIdentityAndScopesForInternalCall() {
        Map<String, String> headers = AuthContextHeaders.from(context)
                .servicePrincipal("payment-gateway")
                .scopes("audit:publish")
                .build();

        assertThat(headers)
                .containsEntry(AuthHeaders.USER, "svc:payment-gateway")
                .containsEntry(AuthHeaders.SCOPES, "audit:publish")
                .containsEntry(AuthHeaders.TENANT, "tenant-1")
                .containsEntry(AuthHeaders.REQUEST_ID, "request-1");
    }

    @Test
    void buildsNewServiceContextForInternalCall() {
        Map<String, String> headers = AuthContextHeaders.builder()
                .servicePrincipal("payment-gateway")
                .tenant("tenant-1")
                .scopes("audit:publish")
                .requestId("request-1")
                .build();

        assertThat(headers)
                .containsEntry(AuthHeaders.USER, "svc:payment-gateway")
                .containsEntry(AuthHeaders.SCOPES, "audit:publish")
                .containsEntry(AuthHeaders.TENANT, "tenant-1")
                .containsEntry(AuthHeaders.REQUEST_ID, "request-1");
    }

    @Test
    void encodesTenantLessContextWithContractSentinel() {
        Map<String, String> headers = AuthContextHeaders.from(context)
                .withoutTenant()
                .build();

        assertThat(headers).containsEntry(AuthHeaders.TENANT, AuthHeaders.TENANT_NONE);
    }

    @Test
    void returnsImmutableMap() {
        Map<String, String> headers = AuthContextHeaders.encode(context);

        assertThatThrownBy(() -> headers.put(AuthHeaders.USER, "another-user"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidOverrides() {
        assertThatThrownBy(() -> AuthContextHeaders.from(context).scopes("audit publish"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scope");
    }
}
