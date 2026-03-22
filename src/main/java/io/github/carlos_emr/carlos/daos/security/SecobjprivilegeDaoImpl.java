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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.daos.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.dao.AbstractHibernateDao;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;

import java.util.Set;

/**
 * Hibernate-based implementation of {@link SecobjprivilegeDao} for security object-privilege mapping persistence.
 *
 * @since 2005-01-01
 */
@Transactional
public class SecobjprivilegeDaoImpl extends AbstractHibernateDao implements SecobjprivilegeDao {

    private Logger logger = MiscUtils.getLogger();

    private static final Set<String> ALLOWED_PROPERTIES = Set.of(
            "roleusergroup", "objectname_code", "privilege_code", "priority", "providerNo");

    @Override
    public void save(Secobjprivilege secobjprivilege) {
        if (secobjprivilege == null) {
            throw new IllegalArgumentException();
        }

        if (secobjprivilege.getRoleusergroup() == null || secobjprivilege.getObjectname_code() == null) {
            currentSession().persist(secobjprivilege);
        } else {
            currentSession().merge(secobjprivilege);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("SecobjprivilegeDao : save: " + secobjprivilege.getRoleusergroup() + ":"
                    + secobjprivilege.getObjectname_desc());
        }

    }

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

    @Override
    public int update(Secobjprivilege instance) {
        logger.debug("update Secobjprivilege instance");
        // providerNo is nullable in the SET clause; null means new record — fall through to save()
        if (instance.getProviderNo() == null) {
            logger.debug("update Secobjprivilege: providerNo is null, routing to save()");
            return 0;
        }
        try {
            String queryString = "update Secobjprivilege as model set model.providerNo = ?1 where model.objectname_code = ?2 and model.privilege_code = ?3 and model.roleusergroup = ?4";

            return HqlQueryHelper.bulkUpdate(currentSession(), queryString,
                    instance.getProviderNo(), instance.getObjectname_code(),
                    instance.getPrivilege_code(), instance.getRoleusergroup());

        } catch (RuntimeException re) {
            logger.error("Update failed", re);
            throw re;
        }
    }

    @Override
    public int deleteByRoleName(String roleName) {
        logger.debug("deleting Secobjprivilege by roleName");
        try {

            return HqlQueryHelper.bulkUpdate(currentSession(), "delete Secobjprivilege as model where model.roleusergroup =?1",
                    roleName);

        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public void delete(Secobjprivilege persistentInstance) {
        logger.debug("deleting Secobjprivilege instance");
        try {
            currentSession().remove(persistentInstance);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public String getFunctionDesc(String function_code) {
        try {
            String queryString = "select description from Secobjectname obj where obj.objectname=?1";

            List lst = HqlQueryHelper.find(currentSession(), queryString, function_code);
            if (lst.size() > 0 && lst.get(0) != null)
                return lst.get(0).toString();
            else
                return "";
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    @Override
    public String getAccessDesc(String accessType_code) {
        try {
            String queryString = "select description from Secprivilege obj where obj.privilege=?1";

            List lst = HqlQueryHelper.find(currentSession(), queryString, accessType_code);
            if (lst.size() > 0 && lst.get(0) != null)
                return lst.get(0).toString();
            else
                return "";
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

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

    @Override
    public List findByProperty(String propertyName, Object value) {
        logger.debug("finding Secobjprivilege instance with property: " + propertyName
                + ", value: " + value);
        try {
            if (!ALLOWED_PROPERTIES.contains(propertyName)) {
                throw new IllegalArgumentException("Invalid property name: " + propertyName);
            }
            String queryString = "from Secobjprivilege as model where model."
                    + propertyName + "= ?1 order by objectname_code";
            return HqlQueryHelper.find(currentSession(), queryString, value);
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        }
    }

    @Override
    public List<Secobjprivilege> getByObjectNameAndRoles(String o, List<String> roles) {
        String queryString = "from Secobjprivilege obj where obj.objectname_code = ?1";
        List<Secobjprivilege> results = new ArrayList<Secobjprivilege>();

        @SuppressWarnings("unchecked")
        List<Secobjprivilege> lst = (List<Secobjprivilege>) HqlQueryHelper.find(currentSession(), queryString, o);

        for (Secobjprivilege p : lst) {
            if (roles.contains(p.getRoleusergroup())) {
                results.add(p);
            }
        }
        return results;
    }

    @Override
    public List<Secobjprivilege> getByRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return Collections.emptyList();
        String hql = "from Secobjprivilege obj where obj.roleusergroup IN (:roles)";
        Map<String, Object> params = new HashMap<>();
        params.put("roles", roles);
        return (List<Secobjprivilege>) HqlQueryHelper.find(currentSession(), hql, params);
    }
}
