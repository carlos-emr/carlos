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
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;

public class RoleProgramAccessDAOImpl extends HibernateDaoSupport implements RoleProgramAccessDAO {

    @SuppressWarnings("unchecked")
    @Override
    public List<DefaultRoleAccess> getDefaultAccessRightByRole(Long roleId) {
        String q = "from DefaultRoleAccess da where da.caisi_role.id=?0";
        return (List<DefaultRoleAccess>) getHibernateTemplate().find(q, roleId);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<DefaultRoleAccess> getDefaultSpecificAccessRightByRole(Long roleId, String accessType) {
        String q = "from DefaultRoleAccess da where da.caisi_role.id=?0 and da.access_type.Name like ?1";
        return (List<DefaultRoleAccess>) getHibernateTemplate().find(q, new Object[]{roleId, accessType});
    }

    @Override
    public boolean hasAccess(String accessName, Long roleId) {
        String q = "from DefaultRoleAccess da where da.caisi_role.id=" + roleId + " and da.access_type.Name= ?0";
        return getHibernateTemplate().find(q, accessName).isEmpty() ? false : true;
    }
}
