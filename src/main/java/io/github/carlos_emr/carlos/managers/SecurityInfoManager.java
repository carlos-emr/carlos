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
package io.github.carlos_emr.carlos.managers;

import java.util.*;

import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.model.security.Secuserrole;

/**
 * Central authorization service for the CARLOS EMR system.
 *
 * <p>Evaluates whether the currently authenticated provider has the required
 * privilege on a named security object, optionally scoped to a specific patient
 * demographic. This interface is the primary entry point for access control checks
 * used across all 2Action classes and service managers.</p>
 *
 * <p>Privilege levels are defined as single-character codes:</p>
 * <ul>
 *   <li>{@code "r"} - Read access</li>
 *   <li>{@code "w"} - Write access</li>
 *   <li>{@code "u"} - Update access</li>
 *   <li>{@code "d"} - Delete access</li>
 *   <li>{@code "o"} - No rights</li>
 * </ul>
 *
 * @see SecurityInfoManagerImpl
 * @see io.github.carlos_emr.carlos.model.security.Secobjprivilege
 * @see io.github.carlos_emr.carlos.model.security.Secuserrole
 * @since 2026-03-17
 */
public interface SecurityInfoManager {
    /** Privilege code for read access. */
    public static final String READ = "r";
    /** Privilege code for write access. */
    public static final String WRITE = "w";
    /** Privilege code for update access. */
    public static final String UPDATE = "u";
    /** Privilege code for delete access. */
    public static final String DELETE = "d";
    /** Privilege code indicating no rights. */
    public static final String NORIGHTS = "o";

    /**
     * Retrieves the security roles assigned to the current user.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @return List of Secuserrole role assignments for the user
     */
    public List<Secuserrole> getRoles(LoggedInInfo loggedInInfo);

    /**
     * Retrieves all defined security objects and their privilege mappings.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @return List of Secobjprivilege security object definitions
     */
    public List<Secobjprivilege> getSecurityObjects(LoggedInInfo loggedInInfo);

    /**
     * Checks whether the user has the specified privilege on a security object,
     * optionally scoped to a patient demographic.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param objectName String the security object name (e.g., "_demographic", "_tickler")
     * @param privilege String the required privilege code ("r", "w", "u", "d")
     * @param demographicNo int the patient demographic number for scoped checks, or 0 for unscoped
     * @return boolean true if the user has the required privilege
     */
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo);

    /**
     * Checks whether the user has the specified privilege on a security object,
     * with the demographic number provided as a String.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param objectName String the security object name
     * @param privilege String the required privilege code
     * @param demographicNo String the patient demographic number, or null for unscoped
     * @return boolean true if the user has the required privilege
     */
    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo);

    /**
     * Determines whether the current provider is allowed to access the specified
     * patient's record based on consent and program enrollment rules.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographicNo Integer the patient demographic number
     * @return boolean true if access to the patient record is permitted
     */
    public boolean isAllowedAccessToPatientRecord(LoggedInInfo loggedInInfo, Integer demographicNo);
}
