package io.labs64.authcontext.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.labs64.authcontext.core.AuthContextParser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The AuthContextFilter's fail-closed gate must honor the build-generated public
 * operations (from x-labs64-auth.public) without a token, while still rejecting
 * protected requests — and it must respect the operation's HTTP method.
 */
class AuthContextFilterPublicPathMatcherTest {

    private final PublicPathMatcher matcher = PublicPathMatcher.fromLines(List.of(
            "GET /payment-definitions",
            "POST /providers/{provider}/webhooks"));

    private AuthContextFilter filter() {
        return new AuthContextFilter(new AuthContextProperties(), new AuthContextParser(), matcher);
    }

    private boolean chainReached(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean(false);
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                    jakarta.servlet.http.HttpServletResponse res) {
                reached.set(true);
            }
        });
        filter().doFilter(request, response, chain);
        if (!reached.get()) {
            assertThat(response.getStatus()).isEqualTo(401);
        }
        return reached.get();
    }

    @Test
    void publicOperationServedWithoutToken() throws Exception {
        assertThat(chainReached("GET", "/payment-definitions")).isTrue();
    }

    @Test
    void publicTemplatedOperationServedWithoutToken() throws Exception {
        assertThat(chainReached("POST", "/providers/stripe/webhooks")).isTrue();
    }

    @Test
    void protectedOperationRejectedWithoutToken() throws Exception {
        assertThat(chainReached("GET", "/payments")).isFalse();
    }

    @Test
    void publicIsMethodSpecific() throws Exception {
        // GET /payment-definitions is public; a POST to the same path is not.
        assertThat(chainReached("POST", "/payment-definitions")).isFalse();
    }
}
