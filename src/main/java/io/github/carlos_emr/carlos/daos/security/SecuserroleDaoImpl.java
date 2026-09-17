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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import jakarta.persistence.EntityManager;
import io.github.carlos_emr.carlos.PMmodule.web.formbean.StaffForm;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractJpaDao;

import io.github.carlos_emr.carlos.model.security.Secuserrole;
import org.springframework.transaction.annotation.Transactional;
import io.github.carlos_emr.carlos.utility.JpqlQueryHelper;

import java.util.Set;

/**
 * A data access object (DAO) providing persistence and search support for
 * Secuserrole entities. Transaction control of the save(), update() and
 * delete() operations can directly support Spring container-managed
 * transactions or they can be augmented to handle user-managed Spring
 * transactions. Each of these methods provides additional information for how
 * to configure it for the desired type of transaction control.
 *
 * @see Secuserrole
 */
@Transactional
public class SecuserroleDaoImpl extends AbstractJpaDao implements SecuserroleDao {
    private static final Logger logger = MiscUtils.getLogger();

    private static final Set<String> ALLOWED_PROPERTIES = Set.of(
            PROVIDER_NO, ROLE_NAME, ORGCD, ACTIVEYN);

    @Override
    public void saveAll(List list) {
        logger.debug("saving ALL Secuserrole instances");
        EntityManager em = entityManager();
        try {
            for (int i = 0; i < list.size(); i++) {
                Secuserrole obj = (Secuserrole) list.get(i);
                obj.setLastUpdateDate(new Date());
                int rowcount = update(obj);

                if (rowcount <= 0) {
                    em.persist(obj);
                }

            }
            logger.debug("save ALL successful");
        } catch (RuntimeException re) {
            logger.error("save ALL failed", re);
            throw re;
        }
    }

    @Override
    public void save(Secuserrole transientInstance) {
        logger.debug("saving Secuserrole instance");
        EntityManager em = entityManager();
        try {
            transientInstance.setLastUpdateDate(new Date());
            if (transientInstance.getId() == null) {
                em.persist(transientInstance);
            } else {
                em.merge(transientInstance);
            }
            logger.debug("save successful");
        } catch (RuntimeException re) {
            logger.error("save failed", re);
            throw re;
        }
    }

    @Override
    public void updateRoleName(Integer id, String roleName) {
        EntityManager em = entityManager();
        Secuserrole sur = em.find(Secuserrole.class, id);
        if (sur != null) {
            sur.setRoleName(roleName);
            sur.setLastUpdateDate(new Date());
            em.merge(sur);
        }
    }

    @Override
    public void delete(Secuserrole persistentInstance) {
        logger.debug("deleting Secuserrole instance");
        try {
            // Pre-migration Hibernate Session.delete() accepted detached entities.
            // JPA EntityManager.remove() requires a managed instance, so reattach via
            // merge() first when the caller passes a detached entity.
            Secuserrole managed = entityManager().contains(persistentInstance)
                    ? persistentInstance
                    : entityManager().merge(persistentInstance);
            entityManager().remove(managed);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int deleteByOrgcd(String orgcd) {
        logger.debug("deleting Secuserrole by orgcd");
        try {

            return JpqlQueryHelper.bulkUpdate(entityManager(), "delete Secuserrole as model where model.orgcd =?1", orgcd);

        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int deleteByProviderNo(String providerNo) {
        logger.debug("deleting Secuserrole by providerNo");
        try {

            return JpqlQueryHelper.bulkUpdate(entityManager(), "delete Secuserrole as model where model.providerNo =?1",
                    providerNo);

        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int deleteById(Integer id) {
        logger.debug("deleting Secuserrole by ID");
        try {

            return JpqlQueryHelper.bulkUpdate(entityManager(), "delete Secuserrole as model where model.id =?1", id);

        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public int update(Secuserrole instance) {
        logger.debug("Update Secuserrole instance");
        // activeyn is nullable; a null value means this is a new record — fall through to session.persist()
        if (instance.getActiveyn() == null) {
            return 0;
        }
        try {
            String queryString = "update Secuserrole as model set model.activeyn = ?1, model.lastUpdateDate=now() where model.providerNo = ?2 and model.roleName = ?3 and model.orgcd = ?4";

            return JpqlQueryHelper.bulkUpdate(entityManager(), queryString,
                    instance.getActiveyn(), instance.getProviderNo(),
                    instance.getRoleName(), instance.getOrgcd());

        } catch (RuntimeException re) {
            logger.error("Update failed", re);
            throw re;
        }
    }

    @Override
    public Secuserrole findById(java.lang.Integer id) {
        logger.debug("getting Secuserrole instance with id: " + id);
        try {
            Secuserrole instance = entityManager().find(Secuserrole.class, id);
            return instance;
        } catch (RuntimeException re) {
            logger.error("get failed", re);
            throw re;
        }
    }

    @Override
    public List findByExample(Secuserrole instance) {
        logger.debug("finding Secuserrole instance by example");
        try {
            // Build HQL dynamically using only known property names (no user input)
            StringBuilder hql = new StringBuilder("from Secuserrole s where 1=1");
            Map<String, Object> params = new HashMap<>();
            if (instance.getProviderNo() != null) {
                hql.append(" and s.providerNo = :providerNo");
                params.put("providerNo", instance.getProviderNo());
            }
            if (instance.getRoleName() != null) {
                hql.append(" and s.roleName = :roleName");
                params.put("roleName", instance.getRoleName());
            }
            if (instance.getOrgcd() != null) {
                hql.append(" and s.orgcd = :orgcd");
                params.put("orgcd", instance.getOrgcd());
            }
            if (instance.getActiveyn() != null) {
                hql.append(" and s.activeyn = :activeyn");
                params.put("activeyn", instance.getActiveyn());
            }
            List results = JpqlQueryHelper.find(entityManager(), hql.toString(), params);
            logger.debug("find by example successful, result size: "
                    + results.size());
            return results;
        } catch (RuntimeException re) {
            logger.error("find by example failed", re);
            throw re;
        }
    }

    @Override
    public List findByProperty(String propertyName, Object value) {
        logger.debug("finding Secuserrole instance with property: " + propertyName
                + ", value: " + value);
        try {
            if (!ALLOWED_PROPERTIES.contains(propertyName)) {
                throw new IllegalArgumentException("Invalid property name: " + propertyName);
            }
            String queryString = "from Secuserrole as model where model."
                    + propertyName + "= ?1";
            return JpqlQueryHelper.find(entityManager(), queryString, value);
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    @Override
    public List findByProviderNo(Object providerNo) {
        return findByProperty(PROVIDER_NO, providerNo);
    }

    @Override
    public List findActiveByProviderNo(Object providerNo) {
        logger.debug("finding active Secuserrole instances by providerNo");
        try {
            // Only roles with activeyn = 1 grant access; rows that are inactive (0) or have a
            // legacy NULL activeyn must not participate in authorization. Mirrors the active-only
            // filter already used by SecObjPrivilegeDaoImpl.findByFormNamePrivilegeAndProviderNo.
            String queryString = "from Secuserrole as model where model.providerNo = ?1 and model.activeyn = 1";
            return JpqlQueryHelper.find(entityManager(), queryString, providerNo);
        } catch (RuntimeException re) {
            logger.error("find active by providerNo failed", re);
            throw re;
        }
    }

    @Override
    public List findByRoleName(Object roleName) {
        return findByProperty(ROLE_NAME, roleName);
    }

    @Override
    public List findByOrgcd(Object orgcd, boolean activeOnly) {
        logger.debug("Find staff instance .");
        try {

            String queryString;
            if (activeOnly) {
                queryString = "select a from Secuserrole a, LstOrgcd b, SecProvider p where a.providerNo=p.providerNo and b.code =?1 and p.status='1' and b.codecsv like '%' || a.orgcd || ',%' and not (a.orgcd like 'R%' or a.orgcd like 'O%')";
            } else {
                queryString = "select a from Secuserrole a, LstOrgcd b, SecProvider p where a.providerNo=p.providerNo and b.code =?1 and b.codecsv like '%' || a.orgcd || ',%' and not (a.orgcd like 'R%' or a.orgcd like 'O%')";
            }

            return JpqlQueryHelper.find(entityManager(), queryString, orgcd);

        } catch (RuntimeException re) {
            logger.error("Find staff failed", re);
            throw re;
        }

    }

    @Override
    public List searchByCriteria(StaffForm staffForm) {

        logger.debug("Search staff instance .");
        try {

            String orgcd = staffForm.getOrgcd();
            String fname = staffForm.getFirstName();
            String lname = staffForm.getLastName();
            boolean hasFname = fname != null && fname.length() > 0;
            boolean hasLname = lname != null && lname.length() > 0;

            String baseHql = "select a from Secuserrole a, LstOrgcd b where b.code = :orgcd and b.codecsv like '%' || a.orgcd || ',%' and not (a.orgcd like 'R%' or a.orgcd like 'O%')";
            String hql = baseHql;
            if (hasFname) hql = hql.concat(" and lower(a.providerFName) like :fname");
            if (hasLname) hql = hql.concat(" and lower(a.providerLName) like :lname");

            Map<String, Object> params = new HashMap<>();
            params.put("orgcd", orgcd);
            if (hasFname) params.put("fname", "%" + fname.toLowerCase() + "%");
            if (hasLname) params.put("lname", "%" + lname.toLowerCase() + "%");

            return JpqlQueryHelper.find(entityManager(), hql, params);

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
        logger.debug("finding all Secuserrole instances");
        try {
            return JpqlQueryHelper.find(entityManager(), "from Secuserrole");
        } catch (RuntimeException re) {
            logger.error("find all failed", re);
            throw re;
        }
    }

    @Override
    public Secuserrole merge(Secuserrole detachedInstance) {
        logger.debug("merging Secuserrole instance");
        try {
            detachedInstance.setLastUpdateDate(new Date());
            Secuserrole result = (Secuserrole) entityManager().merge(
                    detachedInstance);
            logger.debug("merge successful");
            return result;
        } catch (RuntimeException re) {
            logger.error("merge failed", re);
            throw re;
        }
    }

    @Override
    public void attachDirty(Secuserrole instance) {
        logger.debug("attaching dirty Secuserrole instance");
        try {
            instance.setLastUpdateDate(new Date());
            entityManager().merge(instance);
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
    }

    @Override
    public void attachClean(Secuserrole instance) {
        logger.debug("attaching clean Secuserrole instance");
        try {
            // JPA has no direct equivalent of Hibernate Session.lock(entity, LockMode.NONE) for reattach.
            // If the entity is already managed, there is nothing to do. If it is detached, merge() is
            // the only JPA-standard reattach path — unlike lock(NONE), merge may trigger UPDATE on flush
            // if the detached state differs from the database row. Callers relying on the old "clean"
            // (no-UPDATE) semantics must ensure the instance is not dirty before calling.
            if (!entityManager().contains(instance)) {
                entityManager().merge(instance);
            }
            logger.debug("attach successful");
        } catch (RuntimeException re) {
            logger.error("attach failed", re);
            throw re;
        }
    }
}
