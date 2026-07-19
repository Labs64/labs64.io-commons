package io.labs64.authcontext.authorization;

import io.labs64.authcontext.core.AuthContext;

/**
 * Engine-neutral PDP client SPI — .
 *
 * <p>Decouples the {@code @Authorize} PEP from any concrete policy engine. The
 * implementation ({@code CerbosAuthorizationService}) talks to the
 * central Cerbos PDP over gRPC; the contract is intentionally engine-agnostic
 * so the interceptor and the module resolvers never depend on a vendor SDK.
 *
 * <p>Fail closed: {@link #decide} never throws — any client, transport or
 * mapping error surfaces as an {@code allowed=false} decision with
 * {@link AuthorizationDecision#error()} set.
 */
public interface AuthorizationService {

    /**
     * Evaluates one domain authorization request. Never throws — errors come
     * back as {@code allowed=false} decisions with {@code error} set (fail
     * closed).
     */
    AuthorizationDecision decide(AuthContext context, String action, ResourceEntity resource);

    /** True in ENFORCE mode (decisions block), false in SHADOW (observe only). */
    boolean isEnforcing();
}
