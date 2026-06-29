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

import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.model.DefaultRoleAccess;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;

/**
 * Hibernate implementation for DefaultRoleAccessDAO, persisting role access policies across programs.
 */
@Transactional
@SuppressWarnings("unchecked")
public class DefaultRoleAccessDAOImpl extends AbstractJpaDao implements DefaultRoleAccessDAO {
    // Handles core business logic and state for DefaultRoleAccessDAOImpl operations to ensure consistent behavior.

    public void deleteDefaultRoleAccess(Long id) {
        entityManager().remove(getDefaultRoleAccess(id));
    }

    public DefaultRoleAccess getDefaultRoleAccess(Long id) {
        return entityManager().find(DefaultRoleAccess.class, id);
    }

    public List<DefaultRoleAccess> getDefaultRoleAccesses() {
        return (List<DefaultRoleAccess>) JpqlQueryHelper.find(entityManager(), "from DefaultRoleAccess dra ORDER BY role_id");
    }

    public List<DefaultRoleAccess> findAll() {
        return (List<DefaultRoleAccess>) JpqlQueryHelper.find(entityManager(), "from DefaultRoleAccess dra");
    }

    public void saveDefaultRoleAccess(DefaultRoleAccess dra) {
        if (dra.getId() == null) {
            entityManager().persist(dra);
        } else {
            entityManager().merge(dra);
        }
    }

    public DefaultRoleAccess find(Long roleId, Long accessTypeId) {
        String sSQL = "from DefaultRoleAccess dra where dra.roleId=?1 and dra.accessTypeId=?2";
        List results = JpqlQueryHelper.find(entityManager(), sSQL, roleId, accessTypeId);

        if (!results.isEmpty()) {
            return (DefaultRoleAccess) results.get(0);
        }
        return null;
    }

    public List<Object[]> findAllRolesAndAccessTypes() {
        return (List<Object[]>) JpqlQueryHelper.find(entityManager(), "SELECT a, b FROM DefaultRoleAccess a, AccessType b WHERE a.accessTypeId = b.id");
    }

}
