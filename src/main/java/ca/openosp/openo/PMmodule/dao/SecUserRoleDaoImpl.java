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

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.openo.PMmodule.model.SecUserRole;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object (DAO) implementation for SecUserRole entities.
 * <p>
 * This DAO provides persistence and retrieval operations for user role assignments
 * in the PMmodule. It manages the relationship between providers and their assigned
 * security roles within the system.
 * </p>
 * <p>
 * Key responsibilities include:
 * <ul>
 *   <li>Retrieving user roles by provider number</li>
 *   <li>Querying roles by role name</li>
 *   <li>Checking for admin role assignments</li>
 *   <li>Managing user role persistence operations</li>
 *   <li>Tracking role updates for integration purposes</li>
 * </ul>
 * </p>
 * <p>
 * This implementation uses direct SessionFactory injection for Hibernate operations,
 * following the migration from deprecated HibernateDaoSupport.
 * </p>
 *
 * @see SecUserRole
 * @see SecUserRoleDao
 */
@Transactional
public class SecUserRoleDaoImpl implements SecUserRoleDao {

    private static Logger log = MiscUtils.getLogger();

    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the SessionFactory.
     * <p>
     * This method retrieves the session bound to the current thread,
     * which is managed by Spring's transaction management.
     * </p>
     *
     * @return the current Hibernate Session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all user roles assigned to a specific provider.
     *
     * @param providerNo the provider number to search for
     * @return list of SecUserRole entities assigned to the provider
     * @throws IllegalArgumentException if providerNo is null
     */
    @Override
    public List<SecUserRole> getUserRoles(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        String hql = "from SecUserRole s where s.ProviderNo = :providerNo";
        Query<SecUserRole> query = getSession().createQuery(hql, SecUserRole.class);
        query.setParameter("providerNo", providerNo);
        List<SecUserRole> results = query.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("getUserRoles: providerNo=" + providerNo + ",# of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves all user roles with a specific role name.
     *
     * @param roleName the name of the role to search for
     * @return list of SecUserRole entities with the specified role name
     */
    @Override
    public List<SecUserRole> getSecUserRolesByRoleName(String roleName) {
        String hql = "from SecUserRole s where s.RoleName = :roleName";
        Query<SecUserRole> query = getSession().createQuery(hql, SecUserRole.class);
        query.setParameter("roleName", roleName);
        return query.getResultList();
    }

    /**
     * Finds user roles matching both role name and provider number.
     *
     * @param roleName   the name of the role to search for
     * @param providerNo the provider number to search for
     * @return list of SecUserRole entities matching both criteria
     */
    @Override
    public List<SecUserRole> findByRoleNameAndProviderNo(String roleName, String providerNo) {
        String hql = "from SecUserRole s where s.RoleName = :roleName and s.ProviderNo = :providerNo";
        Query<SecUserRole> query = getSession().createQuery(hql, SecUserRole.class);
        query.setParameter("roleName", roleName);
        query.setParameter("providerNo", providerNo);
        return query.getResultList();
    }

    /**
     * Checks if a provider has the admin role assigned.
     *
     * @param providerNo the provider number to check
     * @return true if the provider has the admin role, false otherwise
     * @throws IllegalArgumentException if providerNo is null
     */
    @Override
    public boolean hasAdminRole(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        boolean result = false;
        String hql = "from SecUserRole s where s.ProviderNo = :providerNo and s.RoleName = 'admin'";
        Query<SecUserRole> query = getSession().createQuery(hql, SecUserRole.class);
        query.setParameter("providerNo", providerNo);
        List<SecUserRole> results = query.getResultList();
        
        if (!results.isEmpty()) {
            result = true;
        }

        if (log.isDebugEnabled()) {
            log.debug("hasAdminRole: providerNo=" + providerNo + ",result=" + result);
        }

        return result;
    }

    /**
     * Finds a SecUserRole entity by its primary key ID.
     *
     * @param id the primary key of the SecUserRole entity
     * @return the SecUserRole entity, or null if not found
     */
    @Override
    public SecUserRole find(Long id) {
        return getSession().get(SecUserRole.class, id);
    }

    /**
     * Persists a SecUserRole entity to the database.
     * <p>
     * Automatically updates the lastUpdateDate to the current timestamp before saving.
     * </p>
     *
     * @param sur the SecUserRole entity to save
     */
    @Override
    public void save(SecUserRole sur) {
        sur.setLastUpdateDate(new Date());
        getSession().save(sur);
    }

    /**
     * Retrieves provider numbers for records that have been added or updated since a specified date.
     * <p>
     * This method is useful for integration purposes to identify changed records.
     * </p>
     *
     * @param date the cutoff date - only records updated after this date are returned
     * @return list of provider numbers with updates after the specified date
     */
    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String hql = "select p.ProviderNo from SecUserRole p where p.lastUpdateDate > :date";
        Query<String> query = getSession().createQuery(hql, String.class);
        query.setParameter("date", date);
        return query.getResultList();
    }

}
