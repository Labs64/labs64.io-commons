package io.labs64.authcontext.authorization;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fine-grained domain authorization on a handler method.
 *
 * <p>Additive to the coarse {@code @RequireScopes}/{@code @RequireTenant}
 * pre-filters: those stay as fast gates, the external PDP is the authoritative
 * resource-level decision. Active only when {@code labs64.auth.authz.enabled}
 * is set; the decision is delegated to the central Cerbos PDP. In
 * {@code SHADOW} mode decisions are evaluated, published to
 * {@link AuthorizationDecisionListener}s and logged, but never block.
 *
 * <pre>{@code
 * @PostMapping("/payments/{paymentId}/pay")
 * @RequireScopes("payment:pay")                                  // fast pre-filter
 * @Authorize(action = "payPayment", resource = "#paymentId",
 *            resourceType = "Payment")                           // PDP, authoritative
 * public ResponseEntity<Payment> payPayment(@PathVariable UUID paymentId) { ... }
 * }</pre>
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Authorize {

    /** Authorization action id (unqualified; matches the resource policy's action rule). */
    String action();

    /**
     * SpEL expression yielding the resource reference, evaluated against the
     * request's URI template variables (e.g. {@code #paymentId}). Empty means
     * the {@link ResourceResolver} receives a {@code null} reference and
     * builds the resource from the {@code AuthContext} alone.
     */
    String resource() default "";

    /**
     * Resource entity type (unqualified, e.g. {@code Payment}); passed to the
     * matching {@link ResourceResolver}.
     */
    String resourceType();
}
