package io.labs64.authcontext.authorization;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine-agnostic authorization resource entity: what a module's {@link ResourceResolver}
 * returns for the resource under authorization.
 *
 * <p>Attribute values may be {@link String}, {@link Boolean}, {@link Number}
 * (mapped to a numeric value), a nested {@code ResourceEntity} (mapped to an entity
 * reference), or a {@code List} of those. Types are unqualified
 * (e.g. {@code Payment}) and namespaced by the engine.
 */
public record ResourceEntity(String type, String id, Map<String, Object> attributes, List<ResourceEntity> parents) {

    public ResourceEntity {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("entity type must not be empty");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("entity id must not be empty");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        parents = parents == null ? List.of() : List.copyOf(parents);
    }

    /** Bare entity reference (no attributes/parents), e.g. a tenant. */
    public static ResourceEntity ref(final String type, final String id) {
        return new ResourceEntity(type, id, Map.of(), List.of());
    }

    public static Builder builder(final String type, final String id) {
        return new Builder(type, id);
    }

    public static final class Builder {
        private final String type;
        private final String id;
        private final Map<String, Object> attributes = new LinkedHashMap<>();
        private final List<ResourceEntity> parents = new ArrayList<>();

        private Builder(final String type, final String id) {
            this.type = type;
            this.id = id;
        }

        public Builder attribute(final String name, final Object value) {
            if (value != null) {
                attributes.put(name, value);
            }
            return this;
        }

        public Builder parent(final ResourceEntity parent) {
            parents.add(parent);
            return this;
        }

        public ResourceEntity build() {
            return new ResourceEntity(type, id, attributes, parents);
        }
    }
}
