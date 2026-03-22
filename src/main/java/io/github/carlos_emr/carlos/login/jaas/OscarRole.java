/**
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.login.jaas;

import java.io.Serializable;
import java.security.Principal;

import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;

/**
 * JAAS Principal adapter that wraps a {@link SecUserRole} to represent a user's role
 * in the JAAS security framework.
 *
 * <p>Extends {@link SecUserRole} to inherit role data while implementing
 * {@link Principal} for JAAS Subject integration. The principal name is
 * the role name (e.g., "doctor", "admin").
 *
 * @see OscarGroup
 * @see BaseLoginModule
 * @since 2026-03-17
 */
public class OscarRole extends SecUserRole implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    /** Default constructor. */
    public OscarRole() {
    }

    /**
     * Constructs an OscarRole by copying fields from an existing SecUserRole.
     *
     * @param role SecUserRole the role to copy from
     */
    public OscarRole(SecUserRole role) {
        setRoleName(role.getRoleName());
        setProviderNo(role.getProviderNo());
        setActive(role.getActive());
        setOrgCd(role.getOrgCd());
    }

    /**
     * Constructs an OscarRole with the specified role name.
     *
     * @param name String the role name
     */
    public OscarRole(String name) {
        setRoleName(name);
    }

    @Override
    public String getName() {
        return getRoleName();
    }

}
