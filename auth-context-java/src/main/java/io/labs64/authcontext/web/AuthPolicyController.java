package io.labs64.authcontext.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the build-generated {@code auth-policy.json} (emitted by
 * {@code OpenApiAuthPreprocessorCli}) at the standardized well-known location.
 *
 * <p>The endpoint is intentionally unauthenticated: the gateway's ACS fetches
 * it in-cluster before it can authorize anything, and the data is derived from
 * the OpenAPI spec whose {@code /v3/api-docs} is public anyway. It is not
 * routed through the external gateway (module IngressRoutes only expose API
 * prefixes), so exposure is cluster-internal by construction.
 */
@RestController
public class AuthPolicyController {

    public static final String AUTH_POLICY_PATH = "/.well-known/auth-policy";

    static final String POLICY_RESOURCE = "auth-policy.json";

    @GetMapping(value = AUTH_POLICY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> authPolicy() {
        ClassPathResource resource = new ClassPathResource(POLICY_RESOURCE);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(resource);
    }
}
