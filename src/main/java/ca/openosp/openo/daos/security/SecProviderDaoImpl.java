//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.daos.security;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Example;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.model.security.SecProvider;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object (DAO) implementation for SecProvider entity.
 * 
 * <p>This DAO provides CRUD operations and query methods for managing security provider
 * information in the OpenO EMR system. It has been migrated from HibernateDaoSupport
 * to direct SessionFactory injection to support Spring 6 and Jakarta EE migration.</p>
 * 
 * <p>The DAO supports various query operations including:
 * <ul>
 *   <li>Finding providers by ID, with optional status filtering</li>
 *   <li>Finding providers by various properties (name, type, specialty, etc.)</li>
 *   <li>Query by Example (QBE) searches</li>
 *   <li>Entity lifecycle operations (save, update, delete, merge, attach)</li>
 * </ul>
 * </p>
 * 
 * @author JZhang
 * @since 2025-01-10
 */
public class SecProviderDaoImpl implements SecProviderDao {
    private static final Logger logger = MiscUtils.getLogger();
    
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
    
    // property constants

    /**
     * Persists a new SecProvider instance to the database.
     * 
     * @param transientInstance the SecProvider entity to save
     * @throws RuntimeException if the save operation fails
     */
    @Override
    public void save(SecProvider transientInstance) {
        logger.debug("saving Provider instance");
        try {
            getSession().save(transientInstance);
            logger.debug("save successful");
        } catch (RuntimeException re) {
            logger.error("save failed", re);
            throw re;
        }
    }

    /**
     * Saves or updates a SecProvider instance.
     * If the instance exists, it will be updated; otherwise, it will be saved as new.
     * 
     * @param transientInstance the SecProvider entity to save or update
     * @throws RuntimeException if the operation fails
     */
    @Override
    public void saveOrUpdate(SecProvider transientInstance) {
        logger.debug("saving Provider instance");
        try {
            getSession().saveOrUpdate(transientInstance);
            logger.debug("save successful");
        } catch (RuntimeException re) {
            logger.error("save failed", re);
            throw re;
        }
    }

    /**
     * Deletes a SecProvider instance from the database.
     * 
     * @param persistentInstance the SecProvider entity to delete
     * @throws RuntimeException if the delete operation fails
     */
    @Override
    public void delete(SecProvider persistentInstance) {
        logger.debug("deleting Provider instance");
        try {
            getSession().delete(persistentInstance);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    /**
     * Finds a SecProvider by its unique identifier.
     * 
     * @param id the provider ID to search for
     * @return the SecProvider instance, or null if not found
     * @throws RuntimeException if the query fails
     */
    @Override
    public SecProvider findById(java.lang.String id) {
        logger.debug("getting Provider instance with id: " + id);
        try {
            SecProvider instance = (SecProvider) getSession().get(
                    SecProvider.class, id);
            return instance;
        } catch (RuntimeException re) {
            logger.error("get failed", re);
            throw re;
        }
    }

    /**
     * Finds a SecProvider by ID and status.
     * 
     * @param id the provider ID to search for
     * @param status the provider status to filter by
     * @return the SecProvider instance matching both criteria, or null if not found
     * @throws RuntimeException if the query fails
     */
    @Override
    public SecProvider findById(java.lang.String id, String status) {
        logger.debug("getting Provider instance with id: " + id);
        try {
            String sql = "from SecProvider where providerNo=?0 and status=?1";
            Query query = getSession().createQuery(sql);
            query.setParameter(0, id);
            query.setParameter(1, status);
            List lst = query.list();
            if (lst.size() == 0)
                return null;
            else
                return (SecProvider) lst.get(0);

        } catch (RuntimeException re) {
            logger.error("get failed", re);
            throw re;
        }
    }

    /**
     * Finds SecProvider instances by example using Query by Example (QBE).
     * 
     * @param instance the example SecProvider instance to match
     * @return list of SecProvider instances matching the example
     * @throws RuntimeException if the query fails
     */
    @Override
    public List findByExample(SecProviderDao instance) {
        logger.debug("finding Provider instance by example");
        Session session = getSession();
        try {
            List results = session.createCriteria(
                            SecProvider.class).add(
                            Example.create(instance))
                    .list();
            logger.debug("find by example successful, result size: "
                    + results.size());
            return results;
        } catch (RuntimeException re) {
            logger.error("find by example failed", re);
            throw re;
        }
    }

    /**
     * Finds SecProvider instances by a specific property and value.
     * 
     * @param propertyName the name of the property to search by
     * @param value the value to match
     * @return list of SecProvider instances matching the criteria
     * @throws RuntimeException if the query fails
     */
    @Override
    public List findByProperty(String propertyName, Object value) {
        logger.debug("finding Provider instance with property: " + propertyName
                + ", value: " + value);
        Session session = getSession();
        try {
            String queryString = "from SecProvider as model where model."
                    + propertyName + "= ?1";
            Query queryObject = session.createQuery(queryString);
            queryObject.setParameter(1, value);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    /**
     * Finds SecProvider instances by last name.
     * 
     * @param lastName the last name to search for
     * @return list of providers with matching last name
     */
    @Override
    public List findByLastName(Object lastName) {
        return findByProperty(LAST_NAME, lastName);
    }

    /**
     * Finds SecProvider instances by first name.
     * 
     * @param firstName the first name to search for
     * @return list of providers with matching first name
     */
    @Override
    public List findByFirstName(Object firstName) {
        return findByProperty(FIRST_NAME, firstName);
    }

    /**
     * Finds SecProvider instances by provider type.
     * 
     * @param providerType the provider type to search for
     * @return list of providers with matching type
     */
    @Override
    public List findByProviderType(Object providerType) {
        return findByProperty(PROVIDER_TYPE, providerType);
    }

    /**
     * Finds SecProvider instances by specialty.
     * 
     * @param specialty the specialty to search for
     * @return list of providers with matching specialty
     */
    @Override
    public List findBySpecialty(Object specialty) {
        return findByProperty(SPECIALTY, specialty);
    }

    /**
     * Finds SecProvider instances by team.
     * 
     * @param team the team to search for
     * @return list of providers in the specified team
     */
    @Override
    public List findByTeam(Object team) {
        return findByProperty(TEAM, team);
    }

    /**
     * Finds SecProvider instances by sex.
     * 
     * @param sex the sex to search for
     * @return list of providers with matching sex
     */
    @Override
    public List findBySex(Object sex) {
        return findByProperty(SEX, sex);
    }

    /**
     * Finds SecProvider instances by address.
     * 
     * @param address the address to search for
     * @return list of providers with matching address
     */
    @Override
    public List findByAddress(Object address) {
        return findByProperty(ADDRESS, address);
    }

    /**
     * Finds SecProvider instances by phone number.
     * 
     * @param phone the phone number to search for
     * @return list of providers with matching phone
     */
    @Override
    public List findByPhone(Object phone) {
        return findByProperty(PHONE, phone);
    }

    /**
     * Finds SecProvider instances by work phone number.
     * 
     * @param workPhone the work phone number to search for
     * @return list of providers with matching work phone
     */
    @Override
    public List findByWorkPhone(Object workPhone) {
        return findByProperty(WORK_PHONE, workPhone);
    }

    /**
     * Finds SecProvider instances by OHIP number.
     * 
     * @param ohipNo the OHIP number to search for
     * @return list of providers with matching OHIP number
     */
    @Override
    public List findByOhipNo(Object ohipNo) {
        return findByProperty(OHIP_NO, ohipNo);
    }

    /**
     * Finds SecProvider instances by RMA number.
     * 
     * @param rmaNo the RMA number to search for
     * @return list of providers with matching RMA number
     */
    @Override
    public List findByRmaNo(Object rmaNo) {
        return findByProperty(RMA_NO, rmaNo);
    }

    /**
     * Finds SecProvider instances by billing number.
     * 
     * @param billingNo the billing number to search for
     * @return list of providers with matching billing number
     */
    @Override
    public List findByBillingNo(Object billingNo) {
        return findByProperty(BILLING_NO, billingNo);
    }

    /**
     * Finds SecProvider instances by HSO number.
     * 
     * @param hsoNo the HSO number to search for
     * @return list of providers with matching HSO number
     */
    @Override
    public List findByHsoNo(Object hsoNo) {
        return findByProperty(HSO_NO, hsoNo);
    }

    /**
     * Finds SecProvider instances by status.
     * 
     * @param status the status to search for
     * @return list of providers with matching status
     */
    @Override
    public List findByStatus(Object status) {
        return findByProperty(STATUS, status);
    }

    /**
     * Finds SecProvider instances by comments.
     * 
     * @param comments the comments text to search for
     * @return list of providers with matching comments
     */
    @Override
    public List findByComments(Object comments) {
        return findByProperty(COMMENTS, comments);
    }

    /**
     * Finds SecProvider instances by provider activity.
     * 
     * @param providerActivity the provider activity to search for
     * @return list of providers with matching activity
     */
    @Override
    public List findByProviderActivity(Object providerActivity) {
        return findByProperty(PROVIDER_ACTIVITY, providerActivity);
    }

    /**
     * Retrieves all SecProvider instances from the database.
     * 
     * @return list of all providers
     * @throws RuntimeException if the query fails
     */
    @Override
    public List findAll() {
        logger.debug("finding all Provider instances");
        Session session = getSession();
        try {
            String queryString = "from SecProvider";
            Query queryObject = session.createQuery(queryString);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find all failed", re);
            throw re;
        }
    }

    /**
     * Merges a detached SecProviderDao instance into the current session.
     * 
     * @param detachedInstance the detached instance to merge
     * @return the merged instance
     * @throws RuntimeException if the merge operation fails
     */
    @Override
    public SecProviderDao merge(SecProviderDao detachedInstance) {
        logger.debug("merging Provider instance");
        Session session = getSession();
        try {
            SecProviderDao result = (SecProviderDao) session.merge(detachedInstance);
            logger.debug("merge successful");
            return result;
        } catch (RuntimeException re) {
            logger.error("merge failed", re);
            throw re;
        }
    }

    /**
     * Attaches a dirty (modified) SecProviderDao instance to the session.
     * This will trigger an UPDATE when the session is flushed.
     * 
     * @param instance the dirty instance to attach
     * @throws RuntimeException if the attach operation fails
     */
    @Override
    public void attachDirty(SecProviderDao instance) {
        logger.debug("attaching dirty Provider instance");
        Session session = getSession();
        try {
            session.saveOrUpdate(instance);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
    }

    /**
     * Attaches a clean (unmodified) SecProviderDao instance to the session.
     * This will not trigger an UPDATE when the session is flushed.
     * 
     * @param instance the clean instance to attach
     * @throws RuntimeException if the attach operation fails
     */
    @Override
    public void attachClean(SecProviderDao instance) {
        logger.debug("attaching clean Provider instance");
        Session session = getSession();
        try {
            session.lock(instance, LockMode.NONE);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
    }
}
