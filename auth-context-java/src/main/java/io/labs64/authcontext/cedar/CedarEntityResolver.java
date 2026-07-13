package io.labs64.authcontext.cedar;

import org.jspecify.annotations.Nullable;

import io.labs64.authcontext.core.AuthContext;

/**
 * Module SPI: turns an {@code @Authorize} resource reference into the Cedar
 * resource entity, supplying the domain attributes (tenant, status, owner, …)
 * the policies decide on. The module owns the resource context — RFC-05
 * Principle 3.
 *
 * <p>Implementations are regular beans; the interceptor picks the first one
 * whose {@link #supports(String)} matches the annotation's
 * {@code resourceType}. Throwing (e.g. resource not found) fails the request
 * with the module's normal error semantics — preferred over a 403 that would
 * leak existence.
 */
public interface CedarEntityResolver {

    boolean supports(String resourceType);

    /**
     * @param resourceType unqualified Cedar type from the annotation (e.g. {@code Payment})
     * @param resourceRef  SpEL result (e.g. a path-variable value), or null when
     *                     the annotation declares no resource expression
     * @param context      the trusted per-request identity
     */
    CedarEntity resolve(String resourceType, @Nullable Object resourceRef, AuthContext context);
}
