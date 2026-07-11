package io.labs64.authcontext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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
    void pathConstantIsWellKnown() {
        assertEquals("/.well-known/auth-policy", AuthPolicyController.AUTH_POLICY_PATH);
    }
}
