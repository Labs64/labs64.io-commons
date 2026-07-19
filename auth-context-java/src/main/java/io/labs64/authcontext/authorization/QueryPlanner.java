package io.labs64.authcontext.authorization;

import io.labs64.authcontext.core.AuthContext;

/**
 * Data-PEP SPI: asks the PDP "which {@code <resourceType>} rows may
 * this principal {@code <action>}?" and returns a {@link QueryPlan} the caller
 * translates into a storage-level filter (e.g. a JPA {@code Specification}).
 *
 * <p>Fail closed: any PDP or mapping error must surface as
 * {@link QueryPlan.AlwaysDenied}, never a thrown exception that a caller might
 * treat as "no filter".
 */
public interface QueryPlanner {

    QueryPlan plan(AuthContext context, String action, String resourceType);
}
