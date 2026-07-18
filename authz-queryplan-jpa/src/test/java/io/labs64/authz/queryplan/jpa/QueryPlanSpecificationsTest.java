package io.labs64.authz.queryplan.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import io.labs64.authcontext.authorization.AuthorizationException;
import io.labs64.authcontext.authorization.PlanExpr;
import io.labs64.authcontext.authorization.QueryPlan;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@SuppressWarnings("unchecked")
class QueryPlanSpecificationsTest {

    private static final Map<String, String> FIELDS = Map.of("tenant", "tenantId");

    private static PlanExpr.Op eq(final String var, final Object val) {
        return new PlanExpr.Op("eq", List.of(new PlanExpr.Var(var), new PlanExpr.Val(val)));
    }

    @Test
    void eqTranslatesToCriteriaEqualOnMappedField() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        when(root.get("tenantId")).thenReturn(path);
        when(cb.equal(path, "t_100")).thenReturn(predicate);

        Specification<Object> spec = QueryPlanSpecifications.toSpecification(
                new QueryPlan.Conditional(eq("request.resource.attr.tenant", "t_100")), FIELDS);

        assertThat(spec.toPredicate(root, null, cb)).isSameAs(predicate);
    }

    @Test
    void inTranslatesToCriteriaIn() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate predicate = mock(Predicate.class);
        List<String> tenants = List.of("t_1", "t_2");
        when(root.get("tenantId")).thenReturn(path);
        when(path.in(tenants)).thenReturn(predicate);

        PlanExpr.Op in = new PlanExpr.Op("in",
                List.of(new PlanExpr.Var("request.resource.attr.tenant"), new PlanExpr.Val(tenants)));
        Specification<Object> spec = QueryPlanSpecifications.toSpecification(new QueryPlan.Conditional(in), FIELDS);

        assertThat(spec.toPredicate(root, null, cb)).isSameAs(predicate);
    }

    @Test
    void nestedAndCombinesWithCriteriaAnd() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> path = mock(Path.class);
        Predicate leaf = mock(Predicate.class);
        Predicate combined = mock(Predicate.class);
        when(root.get("tenantId")).thenReturn(path);
        when(cb.equal(path, "t_100")).thenReturn(leaf);
        when(cb.and(any(Predicate[].class))).thenReturn(combined);

        PlanExpr.Op and = new PlanExpr.Op("and", List.of(eq("request.resource.attr.tenant", "t_100")));
        Specification<Object> spec = QueryPlanSpecifications.toSpecification(new QueryPlan.Conditional(and), FIELDS);

        assertThat(spec.toPredicate(root, null, cb)).isSameAs(combined);
    }

    @Test
    void alwaysDeniedMatchesNothing() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate none = mock(Predicate.class);
        when(cb.disjunction()).thenReturn(none);

        Specification<Object> spec = QueryPlanSpecifications.toSpecification(new QueryPlan.AlwaysDenied(), FIELDS);

        assertThat(spec.toPredicate(root, null, cb)).isSameAs(none);
    }

    @Test
    void alwaysAllowedIsNoOpFilter() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate all = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(all);

        Specification<Object> spec = QueryPlanSpecifications.toSpecification(new QueryPlan.AlwaysAllowed(), FIELDS);

        assertThat(spec.toPredicate(root, null, cb)).isSameAs(all);
    }

    @Test
    void unsupportedOperatorFailsClosed() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        PlanExpr.Op gt = new PlanExpr.Op("gt",
                List.of(new PlanExpr.Var("request.resource.attr.amount"), new PlanExpr.Val(10)));
        Specification<Object> spec = QueryPlanSpecifications.toSpecification(new QueryPlan.Conditional(gt), FIELDS);

        assertThatThrownBy(() -> spec.toPredicate(root, null, cb))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("unsupported operator gt");
    }

    @Test
    void missingFieldMappingFailsClosed() {
        Root<Object> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Specification<Object> spec = QueryPlanSpecifications.toSpecification(
                new QueryPlan.Conditional(eq("request.resource.attr.owner", "alice")), FIELDS);

        assertThatThrownBy(() -> spec.toPredicate(root, null, cb))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("no field mapping");
    }
}
