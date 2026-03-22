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

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Base abstract model class for all persistent domain entities in the CARLOS EMR system.
 *
 * <p>Provides common identity, equality, and utility operations for JPA/Hibernate-managed
 * entities. All model classes should extend this class, parameterized with their primary
 * key type (typically {@link Integer}).</p>
 *
 * <p>Equality and hash code are based on the persistent identifier ({@link #getId()}).
 * Calling these methods on transient (not yet persisted) entities will log a warning
 * and fall back to {@link Object} identity semantics.</p>
 *
 * @param <T> the type of the primary key (e.g., {@link Integer}, {@link Long})
 * @since 2012-01-11
 */
public abstract class AbstractModel<T> implements java.io.Serializable {
    protected static final String OBJECT_NOT_YET_PERISTED = "The object is not persisted yet, this operation requires the object to already be persisted.";

    /**
     * Returns the persistent identifier for this entity.
     *
     * @return T the primary key value, or {@code null} if the entity has not been persisted
     */
    public abstract T getId();

    /**
     * {@inheritDoc}
     *
     * <p>Uses Apache Commons {@link ReflectionToStringBuilder} to generate a
     * string representation of all fields for debugging purposes.</p>
     */
    @Override
    public String toString() {
        return (ReflectionToStringBuilder.toString(this));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the hash code of the persistent identifier. If the entity has not
     * been persisted yet, logs a warning and delegates to {@link Object#hashCode()}.
     * Special handling exists for calls originating from {@link #toString()} to
     * avoid spurious warnings.</p>
     */
    @Override
    public int hashCode() {
        if (getId() == null) {
            StackTraceElement[] stack = new Throwable().getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                if (stack[i].getClassName().equals("io.github.carlos_emr.carlos.commn.model.AbstractModel")
                        && stack[i].getMethodName().equals("toString")) {
                    return super.hashCode();
                }
            }
            MiscUtils.getLogger().warn(OBJECT_NOT_YET_PERISTED, new Exception());
            return (super.hashCode());
        }

        return (getId().hashCode());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two entities are considered equal if they are of the same class and share
     * the same persistent identifier. Logs a warning if either entity has not been persisted.</p>
     */
    @Override
    public boolean equals(Object o) {
        if (getClass() != o.getClass()) return (false);

        @SuppressWarnings("unchecked")
        AbstractModel<T> abstractModel = (AbstractModel<T>) o;
        if (getId() == null) {
            MiscUtils.getLogger().warn(OBJECT_NOT_YET_PERISTED, new Exception());
        }

        return (getId().equals(abstractModel.getId()));
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
