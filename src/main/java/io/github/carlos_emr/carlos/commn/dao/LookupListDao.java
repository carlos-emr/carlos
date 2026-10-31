/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.LookupList;

public interface LookupListDao extends AbstractDao<LookupList> {

    /**
     * Returns the active lookup lists ordered by name, each with its items.
     *
     * <p>Served from the shared {@code lookupLists} cache. Every call returns an unmodifiable
     * list of new detached deep copies (each list's items are copied too), so changing a list,
     * its item list or an item affects only the caller's copy, never another reader or the
     * cache, and writes nothing to the database.</p>
     *
     * @return the active lookup lists, as detached copies
     */
    public List<LookupList> findAllActive();

    /**
     * Finds a lookup list, with its items, by its unique name.
     *
     * <p>Served from the shared {@code lookupLists} cache (except for a null or empty name).
     * Every call returns a new detached deep copy, so changing the list, its item list or an
     * item affects only the caller's copy and writes nothing to the database.</p>
     *
     * @param name the list name
     * @return a detached copy of the list, or {@code null} when no list has that name
     */
    public LookupList findByName(String name);
}
