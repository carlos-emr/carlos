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

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.LockMode;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Example;
import ca.openosp.openo.PMmodule.web.formbean.StaffForm;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.model.security.Secuserrole;
import org.springframework.transaction.annotation.Transactional;

/**
 * A data access object (DAO) providing persistence and search support for
 * Secuserrole entities.
 * <p>
 * This DAO uses direct SessionFactory injection instead of the deprecated HibernateDaoSupport.
 * Transaction control of the save(), update() and delete() operations is managed by
 * Spring container-managed transactions via the @Transactional annotation.
 * </p>
 *
 * @author MyEclipse Persistence Tools
 * @see Secuserrole
 */
@Transactional
public class SecuserroleDaoImpl implements SecuserroleDao {
    private static final Logger logger = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;
    
    /**
     * Get the current Hibernate session.
     * 
     * @return the current session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    @Override
    public void saveAll(List list) {
        logger.debug("saving ALL Secuserrole instances");
        Session session = getSession();
        try {
            for (int i = 0; i < list.size(); i++) {
                Secuserrole obj = (Secuserrole) list.get(i);
                obj.setLastUpdateDate(new Date());
                int rowcount = update(obj);

                if (rowcount <= 0) {
                    session.save(obj);
                }

            }
            // this.getHibernateTemplate().saveOrUpdateAll(list);
            logger.debug("save ALL successful");
        } catch (RuntimeException re) {
            logger.error("save ALL failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public void save(Secuserrole transientInstance) {
        logger.debug("saving Secuserrole instance");
        Session session = getSession();
        try {
            transientInstance.setLastUpdateDate(new Date());
            session.saveOrUpdate(transientInstance);
            logger.debug("save successful");
        } catch (RuntimeException re) {
            logger.error("save failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public void updateRoleName(Integer id, String roleName) {
        Secuserrole sur = getSession().get(Secuserrole.class, id);
        if (sur != null) {
            sur.setRoleName(roleName);
            sur.setLastUpdateDate(new Date());
            getSession().update(sur);
        }
    }

    @Override
    public void delete(Secuserrole persistentInstance) {
        logger.debug("deleting Secuserrole instance");
        Session session = getSession();
        try {
            session.delete(persistentInstance);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public int deleteByOrgcd(String orgcd) {
        logger.debug("deleting Secuserrole by orgcd");
        try {
            Query query = getSession().createQuery("delete Secuserrole as model where model.orgcd = :orgcd");
            query.setParameter("orgcd", orgcd);
            return query.executeUpdate();
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int deleteByProviderNo(String providerNo) {
        logger.debug("deleting Secuserrole by providerNo");
        try {
            Query query = getSession().createQuery("delete Secuserrole as model where model.providerNo = :providerNo");
            query.setParameter("providerNo", providerNo);
            return query.executeUpdate();
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int deleteById(Integer id) {
        logger.debug("deleting Secuserrole by ID");
        try {
            Query query = getSession().createQuery("delete Secuserrole as model where model.id = :id");
            query.setParameter("id", id);
            return query.executeUpdate();
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int update(Secuserrole instance) {
        logger.debug("Update Secuserrole instance");
        Session session = getSession();
        try {
            String queryString = "update Secuserrole as model set model.activeyn ='" + instance.getActiveyn()
                    + "' , lastUpdateDate=now() "
                    + " where model.providerNo ='" + instance.getProviderNo() + "'"
                    + " and model.roleName ='" + instance.getRoleName() + "'"
                    + " and model.orgcd ='" + instance.getOrgcd() + "'";

            Query queryObject = session.createQuery(queryString);

            return queryObject.executeUpdate();

        } catch (RuntimeException re) {
            logger.error("Update failed", re);
            throw re;
        }
    }

    @Override
    public Secuserrole findById(java.lang.Integer id) {
        logger.debug("getting Secuserrole instance with id: " + id);
        Session session = getSession();
        try {
            Secuserrole instance = (Secuserrole) session.get(
                    Secuserrole.class, id);
            return instance;
        } catch (RuntimeException re) {
            logger.error("get failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public List findByExample(Secuserrole instance) {
        Session session = getSession();
        logger.debug("finding Secuserrole instance by example");
        try {
            List results = session.createCriteria(
                            Secuserrole.class).add(
                            Example.create(instance))
                    .list();
            logger.debug("find by example successful, result size: "
                    + results.size());
            return results;
        } catch (RuntimeException re) {
            logger.error("find by example failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public List findByProperty(String propertyName, Object value) {
        logger.debug("finding Secuserrole instance with property: " + propertyName
                + ", value: " + value);
        Session session = getSession();
        try {
            String queryString = "from Secuserrole as model where model."
                    + propertyName + "= ?1";
            Query queryObject = session.createQuery(queryString);
            queryObject.setParameter(1, value);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public List findByProviderNo(Object providerNo) {
        return findByProperty(PROVIDER_NO, providerNo);
    }

    @Override
    public List findByRoleName(Object roleName) {
        return findByProperty(ROLE_NAME, roleName);
    }

    @Override
    public List findByOrgcd(Object orgcd, boolean activeOnly) {
        // return findByProperty(ORGCD, orgcd);
        /*
         * SQL:
         * select * from secUserRole s,
         * (select codecsv from lst_orgcd where code = 'P200011') b
         * where b.codecsv like '%' || s.orgcd || ',%'
         * and not (s.orgcd like 'R%' or s.orgcd like 'O%')
         *
         */
        logger.debug("Find staff instance .");
        try {

            String queryString = "select a from Secuserrole a, LstOrgcd b, SecProvider p"
                    + " where a.providerNo=p.providerNo and b.code ='" + orgcd + "'";
            if (activeOnly)
                queryString += " and p.status='1'";

            queryString = queryString
                    + " and b.codecsv like '%' || a.orgcd || ',%'"
                    + " and not (a.orgcd like 'R%' or a.orgcd like 'O%')";

            return getSession().createQuery(queryString).list();

        } catch (RuntimeException re) {
            logger.error("Find staff failed", re);
            throw re;
        }

    }

    @Override
    public List searchByCriteria(StaffForm staffForm) {

        logger.debug("Search staff instance .");
        try {

            String AND = " and ";
            // String OR = " or ";

            String orgcd = staffForm.getOrgcd();

            String queryString = "select a from Secuserrole a, LstOrgcd b"
                    + " where b.code ='" + orgcd + "'"
                    + " and b.codecsv like '%' || a.orgcd || ',%'"
                    + " and not (a.orgcd like 'R%' or a.orgcd like 'O%')";

            String fname = staffForm.getFirstName();
            String lname = staffForm.getLastName();

            if (fname != null && fname.length() > 0) {
                fname = fname.toLowerCase();
                queryString = queryString + AND + "lower(a.providerFName) like '%" + fname + "%'";
            }
            if (lname != null && lname.length() > 0) {
                lname = lname.toLowerCase();
                queryString = queryString + AND + "lower(a.providerLName) like '%" + lname + "%'";
            }

            return getSession().createQuery(queryString).list();

        } catch (RuntimeException re) {
            logger.error("Search staff failed", re);
            throw re;
        }
    }

    @Override
    public List findByActiveyn(Object activeyn) {
        return findByProperty(ACTIVEYN, activeyn);
    }

    @Override
    public List findAll() {
        Session session = getSession();
        logger.debug("finding all Secuserrole instances");
        try {
            String queryString = "from Secuserrole";
            Query queryObject = session.createQuery(queryString);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find all failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public Secuserrole merge(Secuserrole detachedInstance) {
        logger.debug("merging Secuserrole instance");
        Session session = getSession();
        try {
            detachedInstance.setLastUpdateDate(new Date());
            Secuserrole result = (Secuserrole) session.merge(
                    detachedInstance);
            logger.debug("merge successful");
            return result;
        } catch (RuntimeException re) {
            logger.error("merge failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public void attachDirty(Secuserrole instance) {
        logger.debug("attaching dirty Secuserrole instance");
        Session session = getSession();
        try {
            instance.setLastUpdateDate(new Date());
            session.saveOrUpdate(instance);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }

    @Override
    public void attachClean(Secuserrole instance) {
        logger.debug("attaching clean Secuserrole instance");
        Session session = getSession();
        try {
            session.lock(instance, LockMode.NONE);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
        // finally {
        // this.releaseSession(session);
        // }
    }
}
