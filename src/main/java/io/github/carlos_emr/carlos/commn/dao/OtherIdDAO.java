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

import io.github.carlos_emr.carlos.commn.model.OtherId;

/**
 * DAO interface for other identifier operations.
 *
 * @since 2001
 */

public interface OtherIdDAO extends AbstractDao<OtherId> {
    /**
     * Get Other Id.
     *
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param otherKey String the otherKey
     * @return OtherId
     */
    OtherId getOtherId(Integer tableName, Integer tableId, String otherKey);

    /**
     * Get Other Id.
     *
     * @param tableName Integer the tableName
     * @param tableId String the tableId
     * @param otherKey String the otherKey
     * @return OtherId
     */
    OtherId getOtherId(Integer tableName, String tableId, String otherKey);

    /**
     * Search Table.
     *
     * @param tableName Integer the tableName
     * @param otherKey String the otherKey
     * @param otherValue String the otherValue
     * @return OtherId
     */
    OtherId searchTable(Integer tableName, String otherKey, String otherValue);

    /**
     * Save.
     *
     * @param otherId OtherId the otherId
     */
    void save(OtherId otherId);
}
