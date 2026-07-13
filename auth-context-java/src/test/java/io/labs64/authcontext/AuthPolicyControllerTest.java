package io.labs64.authcontext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.labs64.authcontext.core.AuthContextParser;
import io.labs64.authcontext.web.AuthContextFilter;
import io.labs64.authcontext.web.AuthContextProperties;
import io.labs64.authcontext.web.AuthPolicyController;

class AuthPolicyControllerTest {

    @Test
    void servesClasspathPolicyAsJson() {
        // src/test/resources/auth-policy.json must exist for this test
        ResponseEntity<Resource> response = new AuthPolicyController().authPolicy();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertTrue(response.getBody().exists());
    }

    @Test
    void servesClasspathCedarPolicyAsText() {
        // src/test/resources/auth-policy.cedar must exist for this test
        ResponseEntity<Resource> response = new AuthPolicyController().authPolicyCedar();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(MediaType.TEXT_PLAIN, response.getHeaders().getContentType());
        assertTrue(response.getBody().exists());
    }

    @Test
    void pathConstantIsWellKnown() {
        assertEquals("/.well-known/auth-policy", AuthPolicyController.AUTH_POLICY_PATH);
        assertEquals("/.well-known/auth-policy.cedar", AuthPolicyController.AUTH_POLICY_CEDAR_PATH);
    }

    @Test
    void wellKnownAuthPolicyIsAlwaysPublic() throws Exception {
        assertPathIsPublic(AuthPolicyController.AUTH_POLICY_PATH);
    }

    @Test
    void wellKnownCedarPolicyIsAlwaysPublic() throws Exception {
        assertPathIsPublic(AuthPolicyController.AUTH_POLICY_CEDAR_PATH);
    }

    private void assertPathIsPublic(String path) throws Exception {
        AuthContextProperties props = new AuthContextProperties();
        props.setPublicPaths(java.util.List.of()); // module overrode defaults
        AuthContextFilter filter = new AuthContextFilter(props, new AuthContextParser());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).as("filter chain must be invoked").isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(401);
    }
}
