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

import io.github.carlos_emr.carlos.commn.model.LookupListItem;

/**
 * DAO interface for lookup list operations.
 *
 * @since 2001
 */

public interface LookupListItemDao extends AbstractDao<LookupListItem> {

    /**
     * Find Active By Lookup List Id.
     *
     * @param lookupListId int the lookupListId
     * @return List<LookupListItem>
     */
    public List<LookupListItem> findActiveByLookupListId(int lookupListId);

    /**
     * Find By Lookup List Id.
     *
     * @param lookupListId int the lookupListId
     * @param active boolean the active
     * @return List<LookupListItem>
     */
    public List<LookupListItem> findByLookupListId(int lookupListId, boolean active);

    /**
     * Find By Lookup List Id And Value.
     *
     * @param lookupListId int the lookupListId
     * @param value String the value
     * @return LookupListItem
     */
    public LookupListItem findByLookupListIdAndValue(int lookupListId, String value);

}
