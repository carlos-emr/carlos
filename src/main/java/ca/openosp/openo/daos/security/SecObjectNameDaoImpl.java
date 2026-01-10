//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.daos.security;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.model.security.Secobjectname;

/**
 * Data Access Object implementation for managing security object names.
 * <p>
 * This DAO provides persistence operations for {@link Secobjectname} entities,
 * which define named security objects used in the OpenO EMR security framework.
 * Security object names are used to control access to various system features
 * and resources through role-based access control (RBAC).
 * </p>
 * <p>
 * This implementation uses direct Hibernate SessionFactory injection instead of
 * the deprecated HibernateDaoSupport to ensure compatibility with Spring 6 and
 * Jakarta EE migration.
 * </p>
 *
 * @author jackson
 * @since 2024
 */
public class SecObjectNameDaoImpl implements SecObjectNameDao {

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Retrieves the current Hibernate session.
     * <p>
     * This method provides access to the current session managed by the SessionFactory.
     * The session is managed by Spring's transaction management infrastructure.
     * </p>
     *
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Persists or updates a security object name entity.
     * <p>
     * This method will insert a new security object name if it doesn't exist,
     * or update an existing one if it does. The operation is performed within
     * the context of the current transaction.
     * </p>
     *
     * @param t the security object name entity to save or update
     * @throws RuntimeException if the persistence operation fails
     */
    @Override
    public void saveOrUpdate(Secobjectname t) {

        try {

            getSession().saveOrUpdate(t);

        } catch (RuntimeException re) {

            throw re;
        }
    }
}
