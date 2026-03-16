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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.daos.security;

import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.hibernate.LockOptions;
import org.hibernate.Session;
import org.hibernate.query.Query;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.model.security.SecProvider;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

@Transactional
public class SecProviderDaoImpl extends AbstractHibernateDao implements SecProviderDao {
    private static final Logger logger = MiscUtils.getLogger();

    private static final Set<String> ALLOWED_PROPERTIES = Set.of(
            LAST_NAME, FIRST_NAME, PROVIDER_TYPE, SPECIALTY, TEAM, SEX,
            ADDRESS, PHONE, WORK_PHONE, OHIP_NO, RMA_NO, BILLING_NO,
            HSO_NO, STATUS, COMMENTS, PROVIDER_ACTIVITY);

    @Override
    public void save(SecProvider transientInstance) {
        logger.debug("saving Provider instance");
        try {
            currentSession().persist(transientInstance);
            logger.debug("save successful");
        } catch (RuntimeException re) {
            logger.error("save failed", re);
            throw re;
        }
    }

    @Override
    public void saveOrUpdate(SecProvider transientInstance) {
        logger.debug("saving Provider instance");
        try {
            currentSession().merge(transientInstance);
            logger.debug("save successful");
        } catch (RuntimeException re) {
            logger.error("save failed", re);
            throw re;
        }
    }

    @Override
    public void delete(SecProvider persistentInstance) {
        logger.debug("deleting Provider instance");
        try {
            currentSession().remove(persistentInstance);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public SecProvider findById(java.lang.String id) {
        logger.debug("getting Provider instance with id: " + id);
        try {
            SecProvider instance = currentSession().get(SecProvider.class, id);
            return instance;
        } catch (RuntimeException re) {
            logger.error("get failed", re);
            throw re;
        }
    }

    @Override
    public SecProvider findById(java.lang.String id, String status) {
        logger.debug("getting Provider instance with id: " + id);
        try {
            String sql = "from SecProvider where id=?1 and status=?2";
            List lst = HqlQueryHelper.find(currentSession(), sql, id, status);
            if (lst.size() == 0)
                return null;
            else
                return (SecProvider) lst.get(0);

        } catch (RuntimeException re) {
            logger.error("get failed", re);
            throw re;
        }
    }

    @Override
    public List findByExample(SecProviderDao instance) {
        logger.debug("finding Provider instance by example (delegates to findAll)");
        // Legacy method signature takes the DAO interface instead of the entity,
        // making Example.create() non-functional. Delegate to findAll().
        return findAll();
    }

    @Override
    /**
     * Finds Provider instances by a specified property and value.
     */
    public List findByProperty(String propertyName, Object value) {
        logger.debug("finding Provider instance with property: " + propertyName
                + ", value: " + value);
        try {
            if (!ALLOWED_PROPERTIES.contains(propertyName)) {
                throw new IllegalArgumentException("Invalid property name: " + propertyName);
            }
            return HqlQueryHelper.find(currentSession(),
                    "FROM SecProvider WHERE " + propertyName + " = ?1", value);
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    @Override
    public List findByLastName(Object lastName) {
        return findByProperty(LAST_NAME, lastName);
    }

    @Override
    public List findByFirstName(Object firstName) {
        return findByProperty(FIRST_NAME, firstName);
    }

    @Override
    public List findByProviderType(Object providerType) {
        return findByProperty(PROVIDER_TYPE, providerType);
    }

    @Override
    public List findBySpecialty(Object specialty) {
        return findByProperty(SPECIALTY, specialty);
    }

    @Override
    public List findByTeam(Object team) {
        return findByProperty(TEAM, team);
    }

    @Override
    public List findBySex(Object sex) {
        return findByProperty(SEX, sex);
    }

    @Override
    public List findByAddress(Object address) {
        return findByProperty(ADDRESS, address);
    }

    @Override
    public List findByPhone(Object phone) {
        return findByProperty(PHONE, phone);
    }

    @Override
    public List findByWorkPhone(Object workPhone) {
        return findByProperty(WORK_PHONE, workPhone);
    }

    @Override
    public List findByOhipNo(Object ohipNo) {
        return findByProperty(OHIP_NO, ohipNo);
    }

    @Override
    public List findByRmaNo(Object rmaNo) {
        return findByProperty(RMA_NO, rmaNo);
    }

    @Override
    public List findByBillingNo(Object billingNo) {
        return findByProperty(BILLING_NO, billingNo);
    }

    @Override
    public List findByHsoNo(Object hsoNo) {
        return findByProperty(HSO_NO, hsoNo);
    }

    @Override
    public List findByStatus(Object status) {
        return findByProperty(STATUS, status);
    }

    @Override
    public List findByComments(Object comments) {
        return findByProperty(COMMENTS, comments);
    }

    @Override
    public List findByProviderActivity(Object providerActivity) {
        return findByProperty(PROVIDER_ACTIVITY, providerActivity);
    }

    @Override
    /**
     * Retrieves all Provider instances from the database.
     */
    public List findAll() {
        logger.debug("finding all Provider instances");
        try {
            Query<SecProvider> queryObject = currentSession().createQuery("from SecProvider", SecProvider.class);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find all failed", re);
            throw re;
        }
    }

    @Override
    public SecProviderDao merge(SecProviderDao detachedInstance) {
        logger.debug("merging Provider instance");
        Session session = currentSession();
        try {
            SecProviderDao result = (SecProviderDao) session.merge(detachedInstance);
            logger.debug("merge successful");
            return result;
        } catch (RuntimeException re) {
            logger.error("merge failed", re);
            throw re;
        }
    }

    @Override
    public void attachDirty(SecProviderDao instance) {
        logger.debug("attaching dirty Provider instance");
        Session session = currentSession();
        try {
            session.merge(instance);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
    }

    @Override
    public void attachClean(SecProviderDao instance) {
        logger.debug("attaching clean Provider instance");
        try {
            currentSession().buildLockRequest(LockOptions.NONE).lock(instance);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
    }
}
