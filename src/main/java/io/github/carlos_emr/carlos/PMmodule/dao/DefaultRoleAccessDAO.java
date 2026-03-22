/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;

/**
 * Data access interface for managing {@link DefaultRoleAccess} entities that define
 * default access permissions for program roles.
 *
 * @since 2005-01-18
 * @see DefaultRoleAccess
 * @see DefaultRoleAccessDAOImpl
 */
public interface DefaultRoleAccessDAO {

    /**
     * Deletes a default role access record by its ID.
     *
     * @param id Long the record ID to delete
     */
    public void deleteDefaultRoleAccess(Long id);

    /**
     * Retrieves a default role access record by its ID.
     *
     * @param id Long the record ID
     * @return DefaultRoleAccess the record, or {@code null} if not found
     */
    public DefaultRoleAccess getDefaultRoleAccess(Long id);

    /**
     * Retrieves all default role access records ordered by role ID.
     *
     * @return List&lt;DefaultRoleAccess&gt; all records ordered by role_id
     */
    public List<DefaultRoleAccess> getDefaultRoleAccesses();

    /**
     * Retrieves all default role access records.
     *
     * @return List&lt;DefaultRoleAccess&gt; all records
     */
    public List<DefaultRoleAccess> findAll();

    /**
     * Saves or updates a default role access record.
     *
     * @param dra DefaultRoleAccess the record to save
     */
    public void saveDefaultRoleAccess(DefaultRoleAccess dra);

    /**
     * Finds a default role access record by role ID and access type ID.
     *
     * @param roleId Long the role ID
     * @param accessTypeId Long the access type ID
     * @return DefaultRoleAccess the matching record, or {@code null} if not found
     */
    public DefaultRoleAccess find(Long roleId, Long accessTypeId);

    /**
     * Retrieves all default role access records joined with their access types.
     *
     * @return List&lt;Object[]&gt; pairs of DefaultRoleAccess and AccessType objects
     */
    public List<Object[]> findAllRolesAndAccessTypes();
}
 