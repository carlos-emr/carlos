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

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.model.security.Secobjprivilege;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for managing Secobjprivilege entities.
 * Provides CRUD operations and query methods for security object privileges.
 * 
 * <p>This DAO manages the security privileges assigned to roles/user groups for various
 * security objects in the system. It handles operations such as creating, updating, 
 * deleting, and querying privilege assignments.</p>
 * 
 * @since OpenO EMR
 */
public class SecobjprivilegeDaoImpl implements SecobjprivilegeDao {

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
     * Saves or updates a Secobjprivilege entity.
     * 
     * @param secobjprivilege the security object privilege to save
     * @throws IllegalArgumentException if secobjprivilege is null
     */
    @Override
    public void save(Secobjprivilege secobjprivilege) {
        if (secobjprivilege == null) {
            throw new IllegalArgumentException();
        }

        getSession().saveOrUpdate(secobjprivilege);

        if (logger.isDebugEnabled()) {
            logger.debug("SecobjprivilegeDao : save: " + secobjprivilege.getRoleusergroup() + ":"
                    + secobjprivilege.getObjectname_desc());
        }

    }

    /**
     * Saves multiple Secobjprivilege entities.
     * Updates existing entities or creates new ones as needed.
     * 
     * @param list the list of security object privileges to save
     */
    @Override
    public void saveAll(List list) {
        logger.debug("saving ALL Secobjprivilege instances");
        try {
            for (int i = 0; i < list.size(); i++) {
                Secobjprivilege obj = (Secobjprivilege) list.get(i);

                int rowcount = update(obj);

                if (rowcount <= 0) {
                    save(obj);
                }

            }

            logger.debug("save ALL successful");
        } catch (RuntimeException re) {
            logger.error("save ALL failed", re);
            throw re;
        }
    }

    /**
     * Updates a Secobjprivilege entity.
     * 
     * @param instance the security object privilege to update
     * @return the number of rows affected
     * @throws RuntimeException if the update operation fails
     */
    @Override
    public int update(Secobjprivilege instance) {
        logger.debug("update Secobjprivilege instance");
        try {
            String queryString = "update Secobjprivilege as model set model.providerNo ='" + instance.getProviderNo()
                    + "'"
                    + " where model.objectname_code ='" + instance.getObjectname_code() + "'"
                    + " and model.privilege_code ='" + instance.getPrivilege_code() + "'"
                    + " and model.roleusergroup ='" + instance.getRoleusergroup() + "'";

            Query queryObject = getSession().createQuery(queryString);

            return queryObject.executeUpdate();

        } catch (RuntimeException re) {
            logger.error("Update failed", re);
            throw re;
        }
    }

    /**
     * Deletes all Secobjprivilege entities associated with a specific role name.
     * 
     * @param roleName the role name to delete privileges for
     * @return the number of rows deleted
     * @throws RuntimeException if the delete operation fails
     */
    @Override
    public int deleteByRoleName(String roleName) {
        logger.debug("deleting Secobjprivilege by roleName");
        try {
            Query query = getSession().createQuery("delete Secobjprivilege as model where model.roleusergroup =:roleName");
            query.setParameter("roleName", roleName);
            return query.executeUpdate();

        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    /**
     * Deletes a specific Secobjprivilege entity.
     * 
     * @param persistentInstance the security object privilege to delete
     * @throws RuntimeException if the delete operation fails
     */
    @Override
    public void delete(Secobjprivilege persistentInstance) {
        logger.debug("deleting Secobjprivilege instance");
        try {
            getSession().delete(persistentInstance);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    /**
     * Retrieves the description for a given function code.
     * 
     * @param function_code the function code to look up
     * @return the description of the function, or empty string if not found
     * @throws RuntimeException if the query fails
     */
    @Override
    public String getFunctionDesc(String function_code) {
        try {
            String queryString = "select description from Secobjectname obj where obj.objectname=:functionCode";
            
            Query query = getSession().createQuery(queryString);
            query.setParameter("functionCode", function_code);
            
            List lst = query.list();
            if (lst.size() > 0 && lst.get(0) != null)
                return lst.get(0).toString();
            else
                return "";
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    /**
     * Retrieves the description for a given access type code.
     * 
     * @param accessType_code the access type code to look up
     * @return the description of the access type, or empty string if not found
     * @throws RuntimeException if the query fails
     */
    @Override
    public String getAccessDesc(String accessType_code) {
        try {
            String queryString = "select description from Secprivilege obj where obj.privilege=:accessTypeCode";
            
            Query query = getSession().createQuery(queryString);
            query.setParameter("accessTypeCode", accessType_code);
            
            List lst = query.list();
            if (lst.size() > 0 && lst.get(0) != null)
                return lst.get(0).toString();
            else
                return "";
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    /**
     * Retrieves all security object privileges for a given role name.
     * 
     * @param roleName the role name to retrieve functions for
     * @return list of security object privileges for the role
     * @throws IllegalArgumentException if roleName is null
     */
    @Override
    public List getFunctions(String roleName) {
        if (roleName == null) {
            throw new IllegalArgumentException();
        }

        List result = findByProperty("roleusergroup", roleName);
        if (logger.isDebugEnabled()) {
            logger.debug("SecobjprivilegeDao : getFunctions: ");
        }
        return result;
    }

    /**
     * Finds Secobjprivilege entities by a specific property and value.
     * Results are ordered by objectname_code.
     * 
     * @param propertyName the name of the property to search by
     * @param value the value to search for
     * @return list of matching security object privileges
     * @throws RuntimeException if the query fails
     */
    @Override
    public List findByProperty(String propertyName, Object value) {
        logger.debug("finding Secobjprivilege instance with property: " + propertyName
                + ", value: " + value);
        try {
            String queryString = "from Secobjprivilege as model where model."
                    + propertyName + "= :value order by objectname_code";
            Query queryObject = getSession().createQuery(queryString);
            queryObject.setParameter("value", value);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    /**
     * Retrieves security object privileges by object name and filters by role list.
     * 
     * @param o the object name code to search for
     * @param roles the list of role names to filter by
     * @return list of security object privileges matching the criteria
     */
    @Override
    public List<Secobjprivilege> getByObjectNameAndRoles(String o, List<String> roles) {
        String queryString = "from Secobjprivilege obj where obj.objectname_code=:objectName";
        List<Secobjprivilege> results = new ArrayList<Secobjprivilege>();

        Query query = getSession().createQuery(queryString);
        query.setParameter("objectName", o);
        
        @SuppressWarnings("unchecked")
        List<Secobjprivilege> lst = (List<Secobjprivilege>) query.list();

        for (Secobjprivilege p : lst) {
            if (roles.contains(p.getRoleusergroup())) {
                results.add(p);
            }
        }
        return results;
    }

    /**
     * Retrieves security object privileges for a list of role names.
     * 
     * @param roles the list of role names to search for
     * @return list of security object privileges for the specified roles
     */
    @Override
    public List<Secobjprivilege> getByRoles(List<String> roles) {
        String queryString = "from Secobjprivilege obj where obj.roleusergroup IN (:roles)";
        List<Secobjprivilege> results = new ArrayList<Secobjprivilege>();

        Query q = getSession().createQuery(queryString);
        q.setParameterList("roles", roles);

        results = q.list();

        return results;
    }
}
