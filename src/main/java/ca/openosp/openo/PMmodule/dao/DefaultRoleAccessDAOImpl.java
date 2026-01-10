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

package ca.openosp.openo.PMmodule.dao;

import java.util.List;

import ca.openosp.openo.PMmodule.model.DefaultRoleAccess;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Data Access Object implementation for managing DefaultRoleAccess entities.
 * 
 * <p>This DAO provides CRUD operations and specialized queries for managing
 * default role access configurations in the system. It handles the persistence
 * of role-based access control mappings between roles and access types.</p>
 * 
 * <p>Migrated from HibernateDaoSupport to direct SessionFactory injection
 * for improved maintainability and alignment with modern Spring/Hibernate practices.</p>
 * 
 * @see DefaultRoleAccessDAO
 * @see DefaultRoleAccess
 */
@SuppressWarnings("unchecked")
public class DefaultRoleAccessDAOImpl implements DefaultRoleAccessDAO {

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     * 
     * @return the current session from the session factory
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Deletes a DefaultRoleAccess entity by its ID.
     * 
     * @param id the ID of the DefaultRoleAccess to delete
     */
    public void deleteDefaultRoleAccess(Long id) {
        getSession().delete(getDefaultRoleAccess(id));
    }

    /**
     * Retrieves a DefaultRoleAccess entity by its ID.
     * 
     * @param id the ID of the DefaultRoleAccess to retrieve
     * @return the DefaultRoleAccess entity, or null if not found
     */
    public DefaultRoleAccess getDefaultRoleAccess(Long id) {
        return getSession().get(DefaultRoleAccess.class, id);
    }

    /**
     * Retrieves all DefaultRoleAccess entities ordered by role_id.
     * 
     * @return list of all DefaultRoleAccess entities, sorted by role_id
     */
    public List<DefaultRoleAccess> getDefaultRoleAccesses() {
        Query<DefaultRoleAccess> query = getSession().createQuery("from DefaultRoleAccess dra ORDER BY role_id", DefaultRoleAccess.class);
        return query.list();
    }

    /**
     * Retrieves all DefaultRoleAccess entities without specific ordering.
     * 
     * @return list of all DefaultRoleAccess entities
     */
    public List<DefaultRoleAccess> findAll() {
        Query<DefaultRoleAccess> query = getSession().createQuery("from DefaultRoleAccess dra", DefaultRoleAccess.class);
        return query.list();
    }

    /**
     * Saves or updates a DefaultRoleAccess entity.
     * 
     * @param dra the DefaultRoleAccess entity to save or update
     */
    public void saveDefaultRoleAccess(DefaultRoleAccess dra) {
        getSession().saveOrUpdate(dra);
    }

    /**
     * Finds a DefaultRoleAccess entity by roleId and accessTypeId.
     * 
     * @param roleId the role ID to search for
     * @param accessTypeId the access type ID to search for
     * @return the matching DefaultRoleAccess entity, or null if not found
     */
    public DefaultRoleAccess find(Long roleId, Long accessTypeId) {
        String hql = "from DefaultRoleAccess dra where dra.roleId=:roleId and dra.accessTypeId=:accessTypeId";
        Query<DefaultRoleAccess> query = getSession().createQuery(hql, DefaultRoleAccess.class);
        query.setParameter("roleId", roleId);
        query.setParameter("accessTypeId", accessTypeId);
        List<DefaultRoleAccess> results = query.list();

        if (!results.isEmpty()) {
            return results.get(0);
        }
        return null;
    }

    /**
     * Retrieves all roles and their associated access types.
     * 
     * <p>This method performs a cross join between DefaultRoleAccess and AccessType
     * entities where the IDs match, returning pairs of objects.</p>
     * 
     * @return list of Object arrays containing DefaultRoleAccess and AccessType pairs
     */
    public List<Object[]> findAllRolesAndAccessTypes() {
        Query<Object[]> query = getSession().createQuery("FROM DefaultRoleAccess a, AccessType b WHERE a.id = b.Id", Object[].class);
        return query.list();
    }

}
