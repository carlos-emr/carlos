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

package io.github.carlos_emr.carlos.model;

import java.io.Serializable;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;



/**
 * Legacy model base. New entities should use the current model hierarchy.
 * Equality uses an explicitly supplied persistent key, with reference identity for
 * unsaved objects. Neither diagnostics nor equality traverses patient records or associations.
 * Entities opting into this contract must expose final forwarding overrides for these
 * object methods: otherwise Hibernate intercepts the call and loads the target before
 * this implementation can inspect the proxy. Legacy subclass contracts remain separate.
 */
public class BaseObject implements Serializable {
    /** Returns a type-only diagnostic without reading fields or associations. */
    @Override
    public String toString() {
        return persistentType(this).getSimpleName() + "[protected]";
    }

    /** Subclasses with persistent keys opt into identifier equality. */
    protected Object equalityId() {
        return null;
    }

    /** Compares persistent types and non-null keys; unsaved instances use reference identity. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BaseObject other) || persistentType(this) != persistentType(other)) return false;
        Object id = persistentId(this);
        return id != null && id.equals(persistentId(other));
    }

    /** Uses the persistent type so assigning an ID does not change collection membership. */
    @Override
    public int hashCode() {
        // Stable when an unsaved entity receives its ID; never touches a lazy association.
        return persistentType(this).hashCode();
    }

    private static Class<?> persistentType(BaseObject value) {
        LazyInitializer proxy = HibernateProxy.extractLazyInitializer(value);
        return proxy == null ? value.getClass() : proxy.getPersistentClass();
    }

    private static Object persistentId(BaseObject value) {
        LazyInitializer proxy = HibernateProxy.extractLazyInitializer(value);
        return proxy == null ? value.equalityId() : proxy.getInternalIdentifier();
    }
}
