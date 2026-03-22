/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.services.security;

import java.util.List;

import io.github.carlos_emr.carlos.daos.security.UserAccessDao;
import io.github.carlos_emr.carlos.services.LookupManager;

/**
 * Service interface for building a user's security context by resolving
 * their function-level and organization-level access permissions.
 *
 * <p>This manager constructs a {@link SecurityManager} instance populated
 * with the authenticated provider's privileges, which is then used throughout
 * the session to evaluate access control decisions.</p>
 *
 * @see UserAccessManagerImpl
 * @see SecurityManager
 * @see io.github.carlos_emr.carlos.daos.security.UserAccessDao
 * @since 2026-03-17
 */
public interface UserAccessManager {

    /**
     * Builds a fully resolved {@link SecurityManager} for the specified provider,
     * loading their function access list and organization access list from the database.
     *
     * @param providerNo String the provider number identifying the logged-in user
     * @param shelterId Integer the shelter/facility context identifier
     * @param lkManager LookupManager the lookup manager for resolving reference data
     * @return SecurityManager a populated security context for the provider
     */
    SecurityManager getUserSecurityManager(String providerNo, Integer shelterId, LookupManager lkManager);

    /**
     * Extracts a contiguous group of access entries sharing the same function code,
     * starting from the specified index in the sorted access list.
     *
     * @param list List the sorted list of UserAccessValue entries
     * @param startIdx int the starting index in the list
     * @return List of UserAccessValue entries for the current function, or null if startIdx is out of bounds
     */
    List getAccessListForFunction(List list, int startIdx);

    /**
     * Sets the user access DAO via Spring dependency injection.
     *
     * @param dao UserAccessDao the data access object for user access queries
     */
    void setUserAccessDao(UserAccessDao dao);
}
