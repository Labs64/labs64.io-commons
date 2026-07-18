package io.labs64.authcontext.authorization;

import java.util.List;

/**
 * Engine-neutral query-plan expression tree — the abstract syntax the PDP's
 * conditional plan is mapped into, so downstream translators (e.g. JPA
 * {@code Specification}) never depend on a vendor proto (RFC-07).
 */
public sealed interface PlanExpr permits PlanExpr.Op, PlanExpr.Var, PlanExpr.Val {

    /** Operator node: {@code and|or|not|eq|in} over its operands. */
    record Op(String operator, List<PlanExpr> operands) implements PlanExpr {
    }

    /** Attribute reference, e.g. {@code request.resource.attr.tenant}. */
    record Var(String name) implements PlanExpr {
    }

    /** Literal value (String, Boolean, Number, or List thereof). */
    record Val(Object value) implements PlanExpr {
    }
}
