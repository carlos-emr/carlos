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

package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;

/**
 * Data access interface for querying default role-based program access rights.
 * Used to determine global default access permissions when program-specific
 * access rules are not defined.
 *
 * @since 2026-03-17
 */
public interface RoleProgramAccessDAO {

    /**
     * Retrieves all default access rights for the specified role.
     *
     * @param roleId Long the security role identifier
     * @return List&lt;DefaultRoleAccess&gt; the default access rights for the role
     */
    public List<DefaultRoleAccess> getDefaultAccessRightByRole(Long roleId);

    /**
     * Retrieves default access rights for the specified role filtered by access type.
     *
     * @param roleId Long the security role identifier
     * @param accessType String the access type to filter by
     * @return List&lt;DefaultRoleAccess&gt; the filtered default access rights
     */
    public List<DefaultRoleAccess> getDefaultSpecificAccessRightByRole(Long roleId, String accessType);

    /**
     * Checks whether the specified role has the given access right by default.
     *
     * @param accessName String the access right name to check
     * @param roleId Long the security role identifier
     * @return boolean true if the role has the specified default access
     */
    public boolean hasAccess(String accessName, Long roleId);
}
