/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.model;

import java.util.List;

import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;

import io.github.carlos_emr.carlos.utility.LogSafe;

public abstract class AbstractModel<T> implements java.io.Serializable {
    protected static final String OBJECT_NOT_YET_PERISTED = "The object is not persisted yet, this operation requires the object to already be persisted.";

    public abstract T getId();

    /** Reports only the type and an Integer/Long ID; arbitrary keys and entity fields stay protected. */
    @Override
    public String toString() {
        // String and composite keys may themselves contain credentials or patient data.
        // Never traverse associations or call arbitrary key toString() implementations.
        T id = getId();
        String diagnosticId = id == null ? "null"
                : id instanceof Integer || id instanceof Long ? id.toString() : "protected";
        return getClass().getSimpleName() + "[id=" + LogSafe.sanitize(diagnosticId) + "]";
    }

    /**
     * Hash of the persistent type, never of the identifier.
     *
     * <p>An entity commonly enters a {@link java.util.Set} or {@link java.util.Map} before the
     * database assigns its identifier (for example a new child added to a cascading collection).
     * An identifier-based hash then moves the entity to a different bucket on save and the
     * collection can no longer find it (#5303). Hashing the type keeps membership stable across
     * persistence, stays consistent with {@link #equals(Object)} (equal entities always share a
     * persistent type) and, because it is read from the Hibernate proxy's metadata, never
     * initializes a lazy association. Entities that need a finer hash, or an equality over a
     * business key, override both methods.</p>
     */
    @Override
    public int hashCode() {
        return persistentType(this).hashCode();
    }

    /**
     * Entity equality by persistent type and identifier.
     *
     * <p>An instance always equals itself, never equals {@code null} or an instance of another
     * persistent type, and an instance without an identifier (not yet persisted) equals only
     * itself. Two distinct instances are equal when both carry the same non-null identifier. This
     * used to throw {@link NullPointerException} for {@code null} and for any unsaved instance,
     * including {@code x.equals(x)}, which broke collection lookups on new entities (#5303).</p>
     *
     * <p>The argument may be a Hibernate proxy: its persistent class and identifier are read from
     * the proxy's metadata, so comparing against an uninitialized or detached proxy neither runs
     * SQL nor throws {@code LazyInitializationException}. A proxy <em>receiver</em> is initialized
     * by Hibernate before this method runs, as for every entity that overrides {@code equals}.</p>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractModel<?> other) || persistentType(this) != persistentType(other)) {
            return false;
        }

        T id = getId();
        if (id == null) {
            return false;
        }
        return id.equals(persistentId(other));
    }

    /** The entity class behind {@code value}, looking through a Hibernate proxy without loading it. */
    private static Class<?> persistentType(AbstractModel<?> value) {
        LazyInitializer proxy = HibernateProxy.extractLazyInitializer(value);
        return proxy == null ? value.getClass() : proxy.getPersistentClass();
    }

    /** The identifier of {@code value}; a proxy answers from its metadata instead of loading. */
    private static Object persistentId(AbstractModel<?> value) {
        LazyInitializer proxy = HibernateProxy.extractLazyInitializer(value);
        return proxy == null ? value.getId() : proxy.getInternalIdentifier();
    }

    /**
     * This method checks to see if there is an entry in the list with the corresponding primary key, it does not check to see that the other values are the
     * same or not.
     */
    // TODO Move this to the base DAO instead ???
    public static <X extends AbstractModel<?>> boolean existsId(List<X> list, X searchModel) {
        Object searchPk = searchModel.getId();
        for (X tempModel : list) {
            Object tempPk = tempModel.getId();
            if (searchPk.equals(tempPk)) return (true);
        }

        return (false);
    }

    /**
     * THis method returns a comma separated list of the ids as a string, useful for logging or debugging.
     */
    public static <X extends AbstractModel<?>> String getIdsAsStringList(List<X> list) {
        StringBuilder sb = new StringBuilder();

        for (X model : list) {
            sb.append(model.getId().toString());
            sb.append(',');
        }

        return (sb.toString());
    }

    /**
     * Checks if the persistent id has been assigned to this instance.
     *
     * @return Returns true if the ID is not null and false otherwise.
     */
    public boolean isPersistent() {
        return getId() != null;
    }
}
