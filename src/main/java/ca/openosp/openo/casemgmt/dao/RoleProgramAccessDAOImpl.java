//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.casemgmt.dao;

import java.util.List;

import ca.openosp.openo.PMmodule.model.DefaultRoleAccess;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data Access Object implementation for managing role program access permissions.
 * 
 * This DAO provides methods to query and manage default access rights based on roles
 * within the case management system. It handles the persistence of role-based
 * access controls for programs and access types.
 * 
 * @see RoleProgramAccessDAO
 * @see DefaultRoleAccess
 */
@Repository
@Transactional
public class RoleProgramAccessDAOImpl implements RoleProgramAccessDAO {

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the session factory.
     * 
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all default access rights for a specific role.
     * 
     * @param roleId the ID of the role to retrieve access rights for
     * @return list of DefaultRoleAccess objects for the specified role
     */
    @Override
    public List<DefaultRoleAccess> getDefaultAccessRightByRole(Long roleId) {
        String hql = "from DefaultRoleAccess da where da.caisi_role.id = :roleId";
        Query<DefaultRoleAccess> query = getSession().createQuery(hql, DefaultRoleAccess.class);
        query.setParameter("roleId", roleId);
        return query.getResultList();
    }

    /**
     * Retrieves default access rights for a specific role and access type.
     * 
     * @param roleId the ID of the role
     * @param accessType the access type name pattern (supports SQL LIKE patterns)
     * @return list of DefaultRoleAccess objects matching the role and access type
     */
    @Override
    public List<DefaultRoleAccess> getDefaultSpecificAccessRightByRole(Long roleId, String accessType) {
        String hql = "from DefaultRoleAccess da where da.caisi_role.id = :roleId and da.access_type.Name like :accessType";
        Query<DefaultRoleAccess> query = getSession().createQuery(hql, DefaultRoleAccess.class);
        query.setParameter("roleId", roleId);
        query.setParameter("accessType", accessType);
        return query.getResultList();
    }

    /**
     * Checks if a role has access to a specific access type.
     * 
     * @param accessName the name of the access type to check
     * @param roleId the ID of the role
     * @return true if the role has access, false otherwise
     */
    @Override
    public boolean hasAccess(String accessName, Long roleId) {
        String hql = "from DefaultRoleAccess da where da.caisi_role.id = :roleId and da.access_type.Name = :accessName";
        Query<DefaultRoleAccess> query = getSession().createQuery(hql, DefaultRoleAccess.class);
        query.setParameter("roleId", roleId);
        query.setParameter("accessName", accessName);
        query.setMaxResults(1);
        return !query.getResultList().isEmpty();
    }
}
