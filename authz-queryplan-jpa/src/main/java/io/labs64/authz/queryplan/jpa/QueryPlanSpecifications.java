package io.labs64.authz.queryplan.jpa;

import java.util.Collection;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;

import io.labs64.authcontext.authorization.AuthorizationException;
import io.labs64.authcontext.authorization.PlanExpr;
import io.labs64.authcontext.authorization.QueryPlan;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

/**
 * Translates a {@link QueryPlan} into a Spring Data JPA {@link Specification}
 * (RFC-07 Data PEP). Bounded on purpose: only {@code and|or|not|eq|in} are
 * supported — anything else fails closed with {@link AuthorizationException}
 * rather than silently widening the query.
 *
 * <p>{@code attributeToField} maps the plan's
 * {@code request.resource.attr.<name>} tails to JPA entity field names.
 * {@link QueryPlan.AlwaysAllowed} yields a no-op conjunction (no filter);
 * {@link QueryPlan.AlwaysDenied} yields a disjunction that matches nothing.
 */
public final class QueryPlanSpecifications {

    private static final String ATTR_PREFIX = "request.resource.attr.";

    private QueryPlanSpecifications() {
    }

    public static <T> Specification<T> toSpecification(final QueryPlan plan,
            final Map<String, String> attributeToField) {
        if (plan instanceof QueryPlan.AlwaysAllowed) {
            return (root, query, cb) -> cb.conjunction();
        }
        if (plan instanceof QueryPlan.AlwaysDenied) {
            return (root, query, cb) -> cb.disjunction();
        }
        PlanExpr condition = ((QueryPlan.Conditional) plan).condition();
        return (root, query, cb) -> toPredicate(condition, root, cb, attributeToField);
    }

    private static <T> Predicate toPredicate(final PlanExpr expr, final Root<T> root,
            final CriteriaBuilder cb, final Map<String, String> fields) {
        if (!(expr instanceof PlanExpr.Op op)) {
            throw new AuthorizationException("query plan: bare operand outside operator: " + expr);
        }
        return switch (op.operator()) {
            case "and" -> cb.and(op.operands().stream()
                    .map(o -> toPredicate(o, root, cb, fields)).toArray(Predicate[]::new));
            case "or" -> cb.or(op.operands().stream()
                    .map(o -> toPredicate(o, root, cb, fields)).toArray(Predicate[]::new));
            case "not" -> cb.not(toPredicate(op.operands().get(0), root, cb, fields));
            case "eq" -> cb.equal(root.get(field(op, fields)), value(op));
            case "in" -> root.get(field(op, fields)).in((Collection<?>) value(op));
            default -> throw new AuthorizationException("query plan: unsupported operator " + op.operator());
        };
    }

    /** The entity field name behind this operator's {@code Var} operand. */
    private static String field(final PlanExpr.Op op, final Map<String, String> fields) {
        for (PlanExpr operand : op.operands()) {
            if (operand instanceof PlanExpr.Var var) {
                String name = var.name();
                String tail = name.startsWith(ATTR_PREFIX) ? name.substring(ATTR_PREFIX.length()) : name;
                String mapped = fields.get(tail);
                if (mapped == null) {
                    throw new AuthorizationException("query plan: no field mapping for attribute " + tail);
                }
                return mapped;
            }
        }
        throw new AuthorizationException("query plan: operator " + op.operator() + " has no variable operand");
    }

    /** The literal behind this operator's {@code Val} operand. */
    private static Object value(final PlanExpr.Op op) {
        for (PlanExpr operand : op.operands()) {
            if (operand instanceof PlanExpr.Val val) {
                return val.value();
            }
        }
        throw new AuthorizationException("query plan: operator " + op.operator() + " has no value operand");
    }
}
