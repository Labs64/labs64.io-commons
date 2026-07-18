package io.labs64.authcontext.cerbos;

import java.util.ArrayList;
import java.util.List;

import dev.cerbos.sdk.CerbosBlockingClient;
import dev.cerbos.sdk.CheckResult;
import dev.cerbos.sdk.PlanResourcesResult;
import dev.cerbos.sdk.builders.AttributeValue;
import dev.cerbos.sdk.builders.Principal;
import dev.cerbos.sdk.builders.Resource;

import io.labs64.authcontext.authorization.AuthorizationDecision;
import io.labs64.authcontext.authorization.AuthorizationProperties;
import io.labs64.authcontext.authorization.AuthorizationService;
import io.labs64.authcontext.authorization.PlanExpr;
import io.labs64.authcontext.authorization.QueryPlan;
import io.labs64.authcontext.authorization.QueryPlanner;
import io.labs64.authcontext.authorization.ResourceEntity;
import io.labs64.authcontext.core.AuthContext;
import io.labs64.authcontext.core.AuthHeaders;

/**
 * Cerbos PDP client — the RFC-07 {@link AuthorizationService} implementation.
 * One {@code CheckResources} gRPC call per {@code @Authorize} decision; fail
 * closed on any client or transport error. Roles: {@code "service"} for
 * {@code svc:}-prefixed principals, else {@code "user"} (rules match
 * {@code roles: ["*"]}; the role only feeds the tenant-condition's service
 * exemption).
 *
 * <p>Also implements {@link QueryPlanner} (Data PEP): a {@code PlanResources}
 * call yields a row-filter plan, likewise fail-closed to
 * {@link QueryPlan.AlwaysDenied}.
 */
public class CerbosAuthorizationService implements AuthorizationService, QueryPlanner {

    private final AuthorizationProperties properties;
    private final CerbosBlockingClient client;

    public CerbosAuthorizationService(final AuthorizationProperties properties, final CerbosBlockingClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public boolean isEnforcing() {
        return properties.getMode() == AuthorizationProperties.Mode.ENFORCE;
    }

    @Override
    public AuthorizationDecision decide(final AuthContext context, final String action,
            final ResourceEntity resource) {
        boolean enforced = isEnforcing();
        try {
            CheckResult result = client.check(principal(context), toCerbosResource(resource), action);
            boolean allowed = result.isAllowed(action);
            // Cerbos surfaces matched-policy detail in its response meta / audit
            // log; reasons stay best-effort empty here — the summary log still
            // carries the action/resource/decision triple, joinable to PDP logs
            // by requestId.
            return new AuthorizationDecision(action, resource.type(), resource.id(),
                    allowed, enforced, List.of(), null,
                    context.userId(), context.tenantId(), context.requestId());
        } catch (RuntimeException e) { // fail closed on anything the client throws
            return new AuthorizationDecision(action, resource == null ? "-" : resource.type(),
                    resource == null ? "-" : resource.id(), false, enforced, List.of(), e.toString(),
                    context.userId(), context.tenantId(), context.requestId());
        }
    }

    @Override
    public QueryPlan plan(final AuthContext context, final String action, final String resourceType) {
        try {
            PlanResourcesResult result = client.plan(principal(context), Resource.newInstance(resourceType), action);
            if (result.isAlwaysAllowed()) {
                return new QueryPlan.AlwaysAllowed();
            }
            if (result.isAlwaysDenied()) {
                return new QueryPlan.AlwaysDenied();
            }
            PlanExpr condition = CerbosPlanMapper.toPlanExpr(result.getCondition().orElseThrow());
            return new QueryPlan.Conditional(condition);
        } catch (RuntimeException e) {
            return new QueryPlan.AlwaysDenied(); // fail closed
        }
    }

    private Principal principal(final AuthContext context) {
        boolean service = context.userId().startsWith(AuthHeaders.SERVICE_PRINCIPAL_PREFIX);
        Principal principal = Principal.newInstance(context.userId(), service ? "service" : "user");
        List<AttributeValue> scopes = new ArrayList<>();
        context.scopes().forEach(s -> scopes.add(AttributeValue.stringValue(s)));
        principal = principal.withAttribute("scopes", AttributeValue.listValue(scopes));
        if (context.tenantId() != null) {
            principal = principal.withAttribute("tenant", AttributeValue.stringValue(context.tenantId()));
        }
        return principal;
    }

    private Resource toCerbosResource(final ResourceEntity entity) {
        Resource resource = Resource.newInstance(entity.type(), entity.id());
        for (var attr : entity.attributes().entrySet()) {
            resource = resource.withAttribute(attr.getKey(), toAttributeValue(attr.getValue()));
        }
        return resource;
    }

    private AttributeValue toAttributeValue(final Object value) {
        if (value instanceof ResourceEntity nested) {
            return AttributeValue.stringValue(nested.id()); // entity refs flatten to ids (tenant)
        }
        if (value instanceof String s) {
            return AttributeValue.stringValue(s);
        }
        if (value instanceof Boolean b) {
            return AttributeValue.boolValue(b);
        }
        if (value instanceof Number n) {
            return AttributeValue.doubleValue(n.doubleValue());
        }
        if (value instanceof Iterable<?> iterable) {
            List<AttributeValue> values = new ArrayList<>();
            iterable.forEach(item -> values.add(toAttributeValue(item)));
            return AttributeValue.listValue(values);
        }
        throw new IllegalArgumentException("unsupported resource attribute value type: " + value.getClass());
    }
}
