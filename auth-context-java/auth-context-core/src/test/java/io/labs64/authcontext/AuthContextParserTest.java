package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.labs64.authcontext.core.AuthContextParser;
import io.labs64.authcontext.core.AuthContextParseException;
import io.labs64.authcontext.core.AuthHeaders;

class AuthContextParserTest {

    private final AuthContextParser parser = new AuthContextParser();

    @Test
    void parsesTrustedHeaders() {
        Map<String, String> headers = Map.of(
                AuthHeaders.USER, "user-1",
                AuthHeaders.TENANT, "tenant-1",
                AuthHeaders.SCOPES, "payment:read, payment:write",
                AuthHeaders.REQUEST_ID, "request-1");

        var context = parser.parse(headers::get);

        assertThat(context).isPresent();
        var parsed = context.orElseThrow();
        assertThat(parsed.userId()).isEqualTo("user-1");
        assertThat(parsed.tenantId()).isEqualTo("tenant-1");
        assertThat(parsed.scopes()).containsExactlyInAnyOrder("payment:read", "payment:write");
        assertThat(parsed.requestId()).isEqualTo("request-1");
    }

    @Test
    void treatsTenantNoneAsTenantLessContext() {
        Map<String, String> headers = Map.of(
                AuthHeaders.USER, "svc:payment-gateway",
                AuthHeaders.TENANT, AuthHeaders.TENANT_NONE);

        var context = parser.parse(headers::get);

        assertThat(context).isPresent();
        var parsed = context.orElseThrow();
        assertThat(parsed.tenantId()).isNull();
        assertThat(parsed.requestId()).isNotBlank();
    }

    @Test
    void throwsWhenScopeIsMalformed() {
        Map<String, String> headers = Map.of(
                AuthHeaders.USER, "user-1",
                AuthHeaders.SCOPES, "payment:read,payment write");

        assertThatThrownBy(() -> parser.parse(headers::get))
                .isInstanceOf(AuthContextParseException.class)
                .hasMessageContaining(AuthHeaders.SCOPES);
    }

    @Test
    void returnsEmptyWhenUserIsMissing() {
        assertThat(parser.parse(Map.<String, String>of()::get)).isEmpty();
    }
}
