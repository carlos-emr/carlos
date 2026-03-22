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

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;

/**
 * Data access interface for managing {@link SecUserRole} entities that define
 * security role assignments for providers in the Program Management module.
 *
 * @since 2005-01-18
 * @see SecUserRole
 * @see SecUserRoleDaoImpl
 */
public interface SecUserRoleDao {

    /**
     * Retrieves all security roles assigned to a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;SecUserRole&gt; roles for the provider
     * @throws IllegalArgumentException if providerNo is {@code null}
     */
    public List<SecUserRole> getUserRoles(String providerNo);

    /**
     * Retrieves all user role assignments with a specific role name.
     *
     * @param roleName String the role name to filter by
     * @return List&lt;SecUserRole&gt; matching role assignments
     */
    public List<SecUserRole> getSecUserRolesByRoleName(String roleName);

    /**
     * Finds role assignments matching both a role name and provider number.
     *
     * @param roleName String the role name
     * @param providerNo String the provider number
     * @return List&lt;SecUserRole&gt; matching assignments
     */
    public List<SecUserRole> findByRoleNameAndProviderNo(String roleName, String providerNo);

    /**
     * Checks whether a provider has the 'admin' role.
     *
     * @param providerNo String the provider number
     * @return {@code true} if the provider has admin role
     * @throws IllegalArgumentException if providerNo is {@code null}
     */
    public boolean hasAdminRole(String providerNo);

    /**
     * Finds a security user role by its ID.
     *
     * @param id Long the role assignment ID
     * @return SecUserRole the role assignment, or {@code null} if not found
     */
    public SecUserRole find(Long id);

    /**
     * Saves a new security user role assignment, setting the update date.
     *
     * @param sur SecUserRole the role assignment to save
     */
    public void save(SecUserRole sur);

    /**
     * Retrieves provider numbers of roles updated after a given date.
     *
     * @param date Date the reference date
     * @return List&lt;String&gt; provider numbers with updated roles
     */
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date);

}
