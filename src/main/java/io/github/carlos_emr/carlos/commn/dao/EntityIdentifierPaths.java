/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.IdentifiableType;
import jakarta.persistence.metamodel.ManagedType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.Type;

/**
 * Resolves the JPQL attribute paths that make up an entity's identifier, so a query can order by a
 * key that is unique for every row (issue #5308).
 *
 * <p>The paths are read from the JPA metamodel rather than assumed to be {@code id}: the identifier
 * attribute is not always called {@code id} (for example {@code FaxConfig.Id}), and keys come in
 * three shapes, each of which is expanded into basic attributes:</p>
 * <ul>
 *   <li>a single basic identifier: {@code id};</li>
 *   <li>an {@code @EmbeddedId}: one path per basic attribute of the embeddable, for example
 *       {@code id.providerNo} and {@code id.siteId};</li>
 *   <li>an {@code @IdClass}: one path per {@code @Id} attribute. An {@code @Id} that is itself an
 *       association (derived identity, for example {@code EReferAttachmentData.eReferAttachment})
 *       expands to the target entity's identifier, {@code eReferAttachment.id}, which Hibernate reads
 *       from the foreign-key column without a join.</li>
 * </ul>
 *
 * <p>The components of a composite key are returned sorted by attribute name. The metamodel reports
 * them as an unordered set, so sorting is what keeps the generated {@code ORDER BY} identical from
 * one JVM start to the next. Any fixed order of all key components is a total order; it need not
 * match the primary key's column order to be correct.</p>
 *
 * @since 2026-10-29
 */
final class EntityIdentifierPaths {

    /**
     * Guards the recursion through embeddables and derived identities. Real mappings nest one or two
     * levels; a deeper chain means a mapping cycle, which would otherwise overflow the stack.
     */
    private static final int MAX_NESTING_DEPTH = 8;

    private static final Comparator<Attribute<?, ?>> BY_NAME = Comparator.comparing(Attribute::getName);

    private EntityIdentifierPaths() {
    }

    /**
     * Returns the identifier paths of {@code entityClass}, relative to an entity alias.
     *
     * @param metamodel   the metamodel of the persistence unit that maps the entity
     * @param entityClass the mapped entity class
     * @return the basic attribute paths that together identify one row, never empty
     * @throws IllegalArgumentException if {@code entityClass} is not a mapped entity
     * @throws IllegalStateException    if the identifier cannot be expanded into basic attributes
     */
    static List<String> resolve(Metamodel metamodel, Class<?> entityClass) {
        List<String> paths = new ArrayList<>();
        appendIdentifierPaths(metamodel.entity(entityClass), "", paths, 0);
        if (paths.isEmpty()) {
            throw new IllegalStateException("No identifier attributes found for " + entityClass.getName());
        }
        return List.copyOf(paths);
    }

    private static void appendIdentifierPaths(IdentifiableType<?> type, String prefix, List<String> paths, int depth) {
        requireDepth(type, depth);
        for (SingularAttribute<?, ?> attribute : identifierAttributes(type)) {
            appendAttributePaths(attribute, prefix + attribute.getName(), paths, depth);
        }
    }

    private static List<SingularAttribute<?, ?>> identifierAttributes(IdentifiableType<?> type) {
        if (type.hasSingleIdAttribute()) {
            // A basic id or an @EmbeddedId.
            return List.of(singleIdAttribute(type));
        }
        // @IdClass: every attribute annotated @Id is part of the key.
        return sortedByName(type.getIdClassAttributes());
    }

    private static SingularAttribute<?, ?> singleIdAttribute(IdentifiableType<?> type) {
        Type<?> idType = type.getIdType();
        if (idType != null) {
            try {
                return type.getId(idType.getJavaType());
            } catch (IllegalArgumentException typeMismatch) {
                // Some providers report a primitive id through its wrapper type and then reject that
                // wrapper in getId(Class); fall back to the attribute flagged as the id.
            }
        }
        SingularAttribute<?, ?> found = null;
        for (SingularAttribute<?, ?> attribute : type.getSingularAttributes()) {
            if (attribute.isId()) {
                if (found != null) {
                    throw new IllegalStateException("More than one identifier attribute on " + type.getJavaType().getName());
                }
                found = attribute;
            }
        }
        if (found == null) {
            throw new IllegalStateException("No identifier attribute found on " + type.getJavaType().getName());
        }
        return found;
    }

    private static void appendAttributePaths(SingularAttribute<?, ?> attribute, String path, List<String> paths, int depth) {
        Type<?> type = attribute.getType();
        switch (type.getPersistenceType()) {
            case BASIC -> paths.add(path);
            case EMBEDDABLE -> {
                requireDepth(type, depth + 1);
                for (SingularAttribute<?, ?> component : sortedByName(((ManagedType<?>) type).getSingularAttributes())) {
                    appendAttributePaths(component, path + "." + component.getName(), paths, depth + 1);
                }
            }
            // Derived identity: the key column is the foreign key, i.e. the target's own identifier.
            case ENTITY -> appendIdentifierPaths((IdentifiableType<?>) type, path + ".", paths, depth + 1);
            default -> throw new IllegalStateException("Unsupported identifier attribute " + path
                    + " of persistence type " + type.getPersistenceType());
        }
    }

    private static List<SingularAttribute<?, ?>> sortedByName(Collection<? extends SingularAttribute<?, ?>> attributes) {
        List<SingularAttribute<?, ?>> sorted = new ArrayList<>(attributes);
        sorted.sort(BY_NAME);
        return sorted;
    }

    private static void requireDepth(Type<?> type, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IllegalStateException("Identifier of " + type.getJavaType().getName()
                    + " nests deeper than " + MAX_NESTING_DEPTH + " levels; check the mapping for a cycle");
        }
    }
}
