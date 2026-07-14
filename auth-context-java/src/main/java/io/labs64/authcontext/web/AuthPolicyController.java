package io.labs64.authcontext.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the build-generated {@code auth-policy.json} and {@code auth-policy.cedar}
 * (both emitted by {@code OpenApiAuthPreprocessorCli}) at the standardized
 * well-known locations.
 *
 * <p>The endpoints are intentionally unauthenticated: the gateway's ACS fetches
 * them in-cluster before it can authorize anything, and the data is derived from
 * the OpenAPI spec whose {@code /v3/api-docs} is public anyway. They are not
 * routed through the external gateway (module routes only expose API
 * prefixes), so exposure is cluster-internal by construction.
 */
@RestController
public class AuthPolicyController {

    public static final String AUTH_POLICY_PATH = "/.well-known/auth-policy";
    public static final String AUTH_POLICY_CEDAR_PATH = "/.well-known/auth-policy.cedar";

    static final String POLICY_RESOURCE = "auth-policy.json";
    static final String CEDAR_RESOURCE = "auth-policy.cedar";

    @GetMapping(value = AUTH_POLICY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> authPolicy() {
        return serve(POLICY_RESOURCE, MediaType.APPLICATION_JSON);
    }

    @GetMapping(value = AUTH_POLICY_CEDAR_PATH, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Resource> authPolicyCedar() {
        return serve(CEDAR_RESOURCE, MediaType.TEXT_PLAIN);
    }

    private ResponseEntity<Resource> serve(final String name, final MediaType mediaType) {
        ClassPathResource resource = new ClassPathResource(name);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(mediaType).body(resource);
    }
}
