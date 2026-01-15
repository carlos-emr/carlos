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

package ca.openosp.openo.daos.security;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.model.security.Secrole;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object (DAO) implementation for managing security roles in the OpenO EMR system.
 * <p>
 * This DAO provides CRUD operations and queries for {@link Secrole} entities, which represent
 * security roles used for role-based access control (RBAC) within the application.
 * Migrated from HibernateDaoSupport to direct SessionFactory injection for Spring 6 compatibility.
 * </p>
 *
 * @see Secrole
 * @see SecroleDao
 * @since 2025-01-10
 */
public class SecroleDaoImpl implements SecroleDao {

    private Logger logger = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     *
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all security roles ordered by role name.
     *
     * @return a list of all {@link Secrole} entities, ordered by roleName
     */
    @Override
    public List<Secrole> getRoles() {
        Query<Secrole> query = getSession().createQuery("from Secrole r order by roleName", Secrole.class);
        List<Secrole> results = query.list();

        logger.debug("getRoles: # of results=" + results.size());

        return results;
    }

    /**
     * Retrieves a security role by its ID.
     *
     * @param id the role ID to search for
     * @return the {@link Secrole} entity with the specified ID, or null if not found
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     */
    @Override
    public Secrole getRole(Integer id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Secrole result = getSession().get(Secrole.class, Long.valueOf(id));

        logger.debug("getRole: id=" + id + ",found=" + (result != null));

        return result;
    }

    /**
     * Retrieves a security role by its role name.
     * <p>
     * Uses parameterized queries to prevent SQL injection vulnerabilities.
     * </p>
     *
     * @param roleName the name of the role to search for
     * @return the {@link Secrole} entity with the specified roleName, or null if not found
     * @throws IllegalArgumentException if roleName is null or empty
     */
    @Override
    public Secrole getRoleByName(String roleName) {
        Secrole result = null;
        if (roleName == null || roleName.length() <= 0) {
            throw new IllegalArgumentException();
        }

        Query<Secrole> query = getSession().createQuery("from Secrole r where r.roleName = :roleName", Secrole.class);
        query.setParameter("roleName", roleName);
        List<Secrole> lst = query.list();
        
        if (!lst.isEmpty()) {
            result = lst.get(0);
        }

        logger.debug("getRoleByName: roleName=" + roleName + ",found=" + (result != null));

        return result;
    }

    /**
     * Retrieves all default (system-defined) security roles.
     * <p>
     * Default roles are those where userDefined flag is 0.
     * </p>
     *
     * @return a list of default {@link Secrole} entities
     */
    @Override
    @SuppressWarnings("rawtypes")
    public List getDefaultRoles() {
        Query query = getSession().createQuery("from Secrole r where r.userDefined=0");
        return query.list();
    }

    /**
     * Persists or updates a security role.
     * <p>
     * This method will create a new role if it doesn't exist, or update an existing one.
     * </p>
     *
     * @param secrole the {@link Secrole} entity to save or update
     * @throws IllegalArgumentException if secrole is null
     */
    @Override
    public void save(Secrole secrole) {
        if (secrole == null) {
            throw new IllegalArgumentException();
        }

        getSession().saveOrUpdate(secrole);

    }

}
