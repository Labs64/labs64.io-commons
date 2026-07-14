package io.labs64.authcontext.cedar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import com.cedarpolicy.BasicAuthorizationEngine;
import com.cedarpolicy.model.AuthorizationRequest;
import com.cedarpolicy.model.AuthorizationResponse;
import com.cedarpolicy.model.AuthorizationSuccessResponse;
import com.cedarpolicy.model.entity.Entity;
import com.cedarpolicy.model.policy.PolicySet;
import com.cedarpolicy.value.CedarList;
import com.cedarpolicy.value.EntityTypeName;
import com.cedarpolicy.value.EntityUID;
import com.cedarpolicy.value.PrimBool;
import com.cedarpolicy.value.PrimLong;
import com.cedarpolicy.value.PrimString;
import com.cedarpolicy.value.Value;

import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthHeaders;

/**
 * In-process Cedar domain PDP.
 *
 * <p>Loads the module's domain policy set once at startup and evaluates
 * {@code @Authorize} checks against it. Request construction follows the
 * cross-language contract in
 * {@code labs64.io-commons/test-vectors/cedar-request-vectors.json}: the
 * principal maps to {@code Labs64IO::User} or {@code Labs64IO::Service}
 * ({@code svc:} prefix), the context carries {@code scopes}/{@code requestId}
 * and — only when present — the {@code tenant} entity reference.
 *
 * <p>Fail closed: in ENFORCE an unloadable policy set aborts startup and any
 * evaluation error is a deny; in SHADOW both degrade to logged error
 * decisions.
 */
public class CedarAuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(CedarAuthorizationService.class);

    static final String NAMESPACE = "Labs64IO";

    private final CedarProperties properties;
    private final BasicAuthorizationEngine engine = new BasicAuthorizationEngine();
    private final PolicySet policySet;
    private final String loadError;

    public CedarAuthorizationService(final CedarProperties properties, final Resource policyResource) {
        this.properties = properties;
        PolicySet loaded = null;
        String error = null;
        try (InputStream in = policyResource.getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            loaded = PolicySet.parsePolicies(text);
            logger.info("Cedar domain policy set loaded from {} ({} policies, mode={})",
                    policyResource.getDescription(), loaded.getNumPolicies(), properties.getMode());
        } catch (IOException | RuntimeException | com.cedarpolicy.model.exception.InternalException e) {
            error = "cedar domain policy load failed (" + policyResource.getDescription() + "): " + e.getMessage();
            if (properties.getMode() == CedarProperties.Mode.ENFORCE) {
                // Fail closed at boot: an enforcing module must not start
                // without its policy set.
                throw new CedarAuthorizationException(error, e);
            }
            logger.error("{} — SHADOW mode continues with error decisions", error);
        }
        this.policySet = loaded;
        this.loadError = error;
    }

    public boolean isEnforcing() {
        return properties.getMode() == CedarProperties.Mode.ENFORCE;
    }

    /**
     * Evaluates one domain authorization request. Never throws — errors come
     * back as {@code allowed=false} decisions with {@code error} set.
     */
    public AuthorizationDecision decide(final AuthContext context, final String action, final CedarEntity resource) {
        boolean enforced = isEnforcing();
        if (policySet == null) {
            return errorDecision(context, action, resource, enforced, loadError);
        }
        try {
            Entity principal = buildPrincipal(context);
            Entity resourceEntity = toEntity(resource);
            Map<String, Value> requestContext = buildContext(context);

            Set<Entity> entities = new HashSet<>();
            entities.add(principal);
            collectEntities(resource, entities);
            if (context.tenantId() != null) {
                entities.add(new Entity(tenantUid(context.tenantId())));
            }

            AuthorizationRequest request = new AuthorizationRequest(
                    principal.getEUID(), actionUid(action), resourceEntity.getEUID(),
                    java.util.Optional.of(requestContext), java.util.Optional.empty(), false);
            AuthorizationResponse response = engine.isAuthorized(request, policySet, entities);
            if (response.type != AuthorizationResponse.SuccessOrFailure.Success || response.success.isEmpty()) {
                // response.errors is guava-typed (runtime scope) — toString()
                // carries the detail without a compile-time guava dependency.
                return errorDecision(context, action, resource, enforced, response.toString());
            }
            AuthorizationSuccessResponse success = response.success.get();
            return new AuthorizationDecision(action, resource.type(), resource.id(),
                    success.isAllowed(), enforced, List.copyOf(success.getReason()), null,
                    context.userId(), context.tenantId(), context.requestId());
        } catch (Exception e) { // fail closed on anything the engine throws
            return errorDecision(context, action, resource, enforced, e.toString());
        }
    }

    private AuthorizationDecision errorDecision(final AuthContext context, final String action,
            final CedarEntity resource, final boolean enforced, final String error) {
        return new AuthorizationDecision(action, resource == null ? "-" : resource.type(),
                resource == null ? "-" : resource.id(), false, enforced, List.of(), error,
                context.userId(), context.tenantId(), context.requestId());
    }

    // -- request construction (pinned by cedar-request-vectors.json) ---------

    Entity buildPrincipal(final AuthContext context) {
        boolean service = context.userId().startsWith(AuthHeaders.SERVICE_PRINCIPAL_PREFIX);
        EntityUID uid = typeName(service ? "Service" : "User").of(context.userId());
        Map<String, Value> attrs = new HashMap<>();
        attrs.put("scopes", stringList(context.scopes()));
        Set<EntityUID> parents = new HashSet<>();
        if (context.tenantId() != null) {
            parents.add(tenantUid(context.tenantId()));
        }
        return new Entity(uid, attrs, parents);
    }

    Map<String, Value> buildContext(final AuthContext context) {
        Map<String, Value> requestContext = new HashMap<>();
        requestContext.put("scopes", stringList(context.scopes()));
        requestContext.put("requestId", new PrimString(context.requestId()));
        if (context.tenantId() != null) {
            requestContext.put("tenant", tenantUid(context.tenantId()));
        }
        return requestContext;
    }

    private void collectEntities(final CedarEntity entity, final Set<Entity> out) {
        out.add(toEntity(entity));
        for (CedarEntity parent : entity.parents()) {
            collectEntities(parent, out);
        }
        for (Object value : entity.attributes().values()) {
            if (value instanceof CedarEntity nested) {
                out.add(new Entity(uid(nested)));
            }
        }
    }

    private Entity toEntity(final CedarEntity entity) {
        Map<String, Value> attrs = new HashMap<>();
        entity.attributes().forEach((name, value) -> attrs.put(name, toValue(value)));
        Set<EntityUID> parents = new HashSet<>();
        for (CedarEntity parent : entity.parents()) {
            parents.add(uid(parent));
        }
        return new Entity(uid(entity), attrs, parents);
    }

    private Value toValue(final Object value) {
        if (value instanceof CedarEntity entity) {
            return uid(entity);
        }
        if (value instanceof String s) {
            return new PrimString(s);
        }
        if (value instanceof Boolean b) {
            return new PrimBool(b);
        }
        if (value instanceof Number n) {
            return new PrimLong(n.longValue());
        }
        if (value instanceof Iterable<?> iterable) {
            List<Value> values = new ArrayList<>();
            for (Object item : iterable) {
                values.add(toValue(item));
            }
            return new CedarList(values);
        }
        throw new IllegalArgumentException("unsupported cedar attribute value type: " + value.getClass());
    }

    private CedarList stringList(final Iterable<String> values) {
        List<Value> list = new ArrayList<>();
        for (String value : values) {
            list.add(new PrimString(value));
        }
        return new CedarList(list);
    }

    private EntityUID uid(final CedarEntity entity) {
        return typeName(entity.type()).of(entity.id());
    }

    private EntityUID tenantUid(final String tenantId) {
        return typeName("Tenant").of(tenantId);
    }

    private EntityUID actionUid(final String action) {
        return typeName("Action").of(action);
    }

    private EntityTypeName typeName(final String unqualified) {
        String qualified = unqualified.contains("::") ? unqualified : NAMESPACE + "::" + unqualified;
        return EntityTypeName.parse(qualified)
                .orElseThrow(() -> new IllegalArgumentException("invalid cedar type: " + qualified));
    }
}
