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
package io.github.carlos_emr.carlos.services.security;

import java.util.List;

import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secrole;

/**
 * Service interface for managing security roles and their associated
 * object-level privileges in the CARLOS EMR system.
 *
 * <p>Roles define a named set of permissions that control access to
 * healthcare data objects and EMR functions. Each role has associated
 * privilege entries ({@link Secobjprivilege}) that specify read, write,
 * update, delete, or full access to specific security objects.</p>
 *
 * @see RolesManagerImpl
 * @see io.github.carlos_emr.carlos.model.security.Secrole
 * @see io.github.carlos_emr.carlos.model.security.Secobjprivilege
 * @since 2026-03-17
 */
public interface RolesManager {

    /**
     * Retrieves all security roles defined in the system.
     *
     * @return List of all Secrole records
     */
    List<Secrole> getRoles();

    /**
     * Retrieves a security role by its string identifier.
     *
     * @param id String the role identifier (parsed to int internally)
     * @return Secrole the matching role, or null if not found
     */
    Secrole getRole(String id);

    /**
     * Retrieves a security role by its integer identifier.
     *
     * @param id int the role identifier
     * @return Secrole the matching role, or null if not found
     */
    Secrole getRole(int id);

    /**
     * Retrieves a security role by its unique role name.
     *
     * @param roleName String the name of the role to retrieve
     * @return Secrole the matching role, or null if not found
     */
    Secrole getRoleByRolename(String roleName);

    /**
     * Persists a new or updated security role.
     *
     * @param secrole Secrole the role to save
     */
    void save(Secrole secrole);

    /**
     * Persists a single object privilege assignment.
     *
     * @param secobjprivilege Secobjprivilege the privilege entry to save
     */
    void saveFunction(Secobjprivilege secobjprivilege);

    /**
     * Synchronizes the privilege assignments for a role by comparing new
     * privileges against existing ones, deleting removed entries and
     * saving new or updated entries.
     *
     * @param secrole Secrole the role to update (saved if non-null)
     * @param newLst List the new list of Secobjprivilege entries to apply
     * @param roleName String the role name used to look up existing privileges
     */
    void saveFunctions(Secrole secrole, List newLst, String roleName);

    /**
     * Retrieves all object privilege entries for a given role name.
     *
     * @param roleName String the role name to query
     * @return List of Secobjprivilege entries assigned to the role
     */
    List<Secobjprivilege> getFunctions(String roleName);

    /**
     * Retrieves the human-readable description for a function code.
     *
     * @param function_code String the function code to describe
     * @return String the description of the function
     */
    String getFunctionDesc(String function_code);

    /**
     * Retrieves the human-readable description for an access type code.
     *
     * @param accessType_code String the access type code (e.g., "r", "w", "x")
     * @return String the description of the access type
     */
    String getAccessDesc(String accessType_code);
}
