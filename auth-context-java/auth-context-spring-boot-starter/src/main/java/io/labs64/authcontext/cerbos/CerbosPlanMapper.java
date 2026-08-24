package io.labs64.authcontext.cerbos;

import java.util.ArrayList;
import java.util.List;

import com.google.protobuf.Value;

import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression;
import dev.cerbos.api.v1.engine.Engine.PlanResourcesFilter.Expression.Operand;

import io.labs64.authcontext.authorization.PlanExpr;

/**
 * Maps a Cerbos {@code PlanResources} filter (a proto {@link Operand} oneof
 * tree) into the engine-neutral {@link PlanExpr} AST — the single point that
 * knows the Cerbos proto shape, keeping the query-plan translators
 * vendor-agnostic.
 */
final class CerbosPlanMapper {

    private CerbosPlanMapper() {
    }

    static PlanExpr toPlanExpr(final Operand operand) {
        return switch (operand.getNodeCase()) {
            case EXPRESSION -> {
                Expression expression = operand.getExpression();
                List<PlanExpr> operands = new ArrayList<>();
                for (Operand child : expression.getOperandsList()) {
                    operands.add(toPlanExpr(child));
                }
                yield new PlanExpr.Op(expression.getOperator(), operands);
            }
            case VARIABLE -> new PlanExpr.Var(operand.getVariable());
            case VALUE -> new PlanExpr.Val(unwrap(operand.getValue()));
            default -> throw new IllegalArgumentException("empty Cerbos plan operand");
        };
    }

    private static Object unwrap(final Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case NUMBER_VALUE -> value.getNumberValue();
            case NULL_VALUE -> null;
            case LIST_VALUE -> {
                List<Object> list = new ArrayList<>();
                for (Value element : value.getListValue().getValuesList()) {
                    list.add(unwrap(element));
                }
                yield list;
            }
            default -> throw new IllegalArgumentException("unsupported Cerbos plan value kind: " + value.getKindCase());
        };
    }
}
