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

import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

/**
 * Hibernate-based implementation of {@link RoleProgramAccessDAO}. Queries default
 * role-based access rights using HQL with null-safe parameter handling.
 *
 * @since 2026-03-17
 */
@Transactional
public class RoleProgramAccessDAOImpl extends AbstractHibernateDao implements RoleProgramAccessDAO {

    @SuppressWarnings("unchecked")
    @Override
    public List<DefaultRoleAccess> getDefaultAccessRightByRole(Long roleId) {
        if (roleId == null) return Collections.emptyList();
        String q = "from DefaultRoleAccess da where da.caisi_role.id=?1";
        return (List<DefaultRoleAccess>) HqlQueryHelper.find(currentSession(), q, roleId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DefaultRoleAccess> getDefaultSpecificAccessRightByRole(Long roleId, String accessType) {
        if (roleId == null || accessType == null) return Collections.emptyList();
        String q = "from DefaultRoleAccess da where da.caisi_role.id=?1 and da.access_type.Name like ?2";
        return (List<DefaultRoleAccess>) HqlQueryHelper.find(currentSession(), q, roleId, accessType);
    }

    @Override
    public boolean hasAccess(String accessName, Long roleId) {
        if (accessName == null || roleId == null) return false;
        String q = "from DefaultRoleAccess da where da.caisi_role.id=?1 and da.access_type.Name=?2";
        return !HqlQueryHelper.find(currentSession(), q, roleId, accessName).isEmpty();
    }
}
