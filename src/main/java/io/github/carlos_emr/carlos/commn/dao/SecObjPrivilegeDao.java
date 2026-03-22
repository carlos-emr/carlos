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

import java.util.Collection;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.SecObjPrivilege;

/**
 * DAO interface for security operations.
 *
 * @since 2001
 */

public interface SecObjPrivilegeDao extends AbstractDao<SecObjPrivilege> {
    /**
     * Find By Role User Group And Object Name.
     *
     * @param roleUserGroup String the roleUserGroup
     * @param objectName String the objectName
     * @return List<SecObjPrivilege>
     */
    List<SecObjPrivilege> findByRoleUserGroupAndObjectName(String roleUserGroup, String objectName);

    /**
     * Find By Object Names.
     *
     * @param objectNames Collection<String> the objectNames
     * @return List<SecObjPrivilege>
     */
    List<SecObjPrivilege> findByObjectNames(Collection<String> objectNames);

    /**
     * Find By Role User Group.
     *
     * @param roleUserGroup String the roleUserGroup
     * @return List<SecObjPrivilege>
     */
    List<SecObjPrivilege> findByRoleUserGroup(String roleUserGroup);

    /**
     * Find By Object Name.
     *
     * @param objectName String the objectName
     * @return List<SecObjPrivilege>
     */
    List<SecObjPrivilege> findByObjectName(String objectName);

    /**
     * Count Objects By Name.
     *
     * @param objName String the objName
     * @return int
     */
    int countObjectsByName(String objName);

    /**
     * Find By Form Name Privilege And Provider No.
     *
     * @param formName String the formName
     * @param privilege String the privilege
     * @param providerNo String the providerNo
     * @return List<Object[]>
     */
    List<Object[]> findByFormNamePrivilegeAndProviderNo(String formName, String privilege, String providerNo);
}
