package io.labs64.authcontext.authorization;

/**
 * Engine-neutral result of a {@link QueryPlanner#plan} call — the row-filter
 * decision for a list/query action.
 */
public sealed interface QueryPlan permits QueryPlan.AlwaysAllowed, QueryPlan.AlwaysDenied, QueryPlan.Conditional {

    /** Principal may see every row — no filter. */
    record AlwaysAllowed() implements QueryPlan {
    }

    /** Principal may see no rows — the query returns empty. */
    record AlwaysDenied() implements QueryPlan {
    }

    /** Principal may see rows matching {@code condition}. */
    record Conditional(PlanExpr condition) implements QueryPlan {
    }
}
