package io.labs64.authcontext.cedar;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fine-grained Cedar domain authorization on a handler method.
 *
 * <p>Additive to the coarse {@code @RequireScopes}/{@code @RequireTenant}
 * pre-filters: those stay as fast gates, Cedar is the authoritative
 * resource-level decision. Active only when {@code labs64.auth.cedar.enabled}
 * is set and the optional {@code com.cedarpolicy:cedar-java} dependency is on
 * the classpath; in {@code SHADOW} mode decisions are evaluated, published to
 * {@link AuthorizationDecisionListener}s and logged, but never block.
 *
 * <pre>{@code
 * @PostMapping("/payments/{paymentId}/pay")
 * @RequireScopes("payment:pay")                                  // fast pre-filter
 * @Authorize(action = "payPayment", resource = "#paymentId",
 *            resourceType = "Payment")                           // Cedar, authoritative
 * public ResponseEntity<Payment> payPayment(@PathVariable UUID paymentId) { ... }
 * }</pre>
 */
@Documented
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Authorize {

    /** Cedar action id (unqualified; namespaced as {@code Labs64IO::Action::"<action>"}). */
    String action();

    /**
     * SpEL expression yielding the resource reference, evaluated against the
     * request's URI template variables (e.g. {@code #paymentId}). Empty means
     * the {@link CedarEntityResolver} receives a {@code null} reference and
     * builds the resource from the {@code AuthContext} alone.
     */
    String resource() default "";

    /**
     * Cedar resource entity type (unqualified, e.g. {@code Payment}); passed
     * to the matching {@link CedarEntityResolver}.
     */
    String resourceType();
}
