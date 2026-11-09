/**
 * Modifications by CARLOS Contributors, 2026.
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

public interface SecurityInfoManager {
    public static final String READ = "r";
    public static final String WRITE = "w";
    public static final String UPDATE = "u";
    public static final String DELETE = "d";
    public static final String NORIGHTS = "o";

    public List<Secuserrole> getRoles(LoggedInInfo loggedInInfo);

    public List<Secobjprivilege> getSecurityObjects(LoggedInInfo loggedInInfo);

    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, int demographicNo);

    public boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo);

    /** Evaluate the same privilege policy without changing the session's account-lock state.
     * Use for discovery that may inspect records belonging to several patients. */
    public boolean hasPrivilegeReadOnly(LoggedInInfo loggedInInfo, String objectName, String privilege, String demographicNo);

    /**
     * Checks every distinct patient's demographic read policy before a bulk read.
     * Implementations may batch the role/privilege reads, but must retain patient overrides
     * and account-lock effects. The snapshot must not be reused across requests.
     * @param loggedInInfo current user and session
     * @param demographicNos non-null patient IDs in evaluation order
     * @return true only when every patient is readable
     */
    default boolean hasDemographicReadPrivileges(LoggedInInfo loggedInInfo, Collection<Integer> demographicNos) {
        for (Integer id : new LinkedHashSet<>(demographicNos)) {
            if (!hasPrivilege(loggedInInfo, "_demographic", READ, id)) return false;
        }
        return true;
    }

    /** Returns readable IDs for discovery without changing session lock state. */
    default java.util.Set<Integer> getReadableDemographicIds(LoggedInInfo info, Collection<Integer> ids) {
        java.util.Set<Integer> result = new java.util.LinkedHashSet<>();
        for (Integer id : ids) {
            if (hasPrivilegeReadOnly(info, "_demographic", READ, id.toString())) result.add(id);
        }
        return result;
    }

    public boolean isAllowedAccessToPatientRecord(LoggedInInfo loggedInInfo, Integer demographicNo);
}
