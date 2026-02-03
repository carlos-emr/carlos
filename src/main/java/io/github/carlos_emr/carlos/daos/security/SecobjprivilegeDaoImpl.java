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

package io.github.carlos_emr.carlos.daos.security;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;

import io.github.carlos_emr.carlos.model.security.Secobjprivilege;

public class SecobjprivilegeDaoImpl extends HibernateDaoSupport implements SecobjprivilegeDao {

    private Logger logger = MiscUtils.getLogger();
    public SessionFactory sessionFactory;

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    @Override
    public void save(Secobjprivilege secobjprivilege) {
        if (secobjprivilege == null) {
            throw new IllegalArgumentException();
        }

        getHibernateTemplate().saveOrUpdate(secobjprivilege);

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
        Session session = sessionFactory.getCurrentSession();
        try {
            String queryString = "update Secobjprivilege as model set model.providerNo ='" + instance.getProviderNo()
                    + "'"
                    + " where model.objectname_code ='" + instance.getObjectname_code() + "'"
                    + " and model.privilege_code ='" + instance.getPrivilege_code() + "'"
                    + " and model.roleusergroup ='" + instance.getRoleusergroup() + "'";

            Query queryObject = session.createQuery(queryString);

            return queryObject.executeUpdate();

        } catch (RuntimeException re) {
            logger.error("Update failed", re);
            throw re;
        } finally {
            // this.releaseSession(session);
            session.close();
        }
    }

    @Override
    public int deleteByRoleName(String roleName) {
        logger.debug("deleting Secobjprivilege by roleName");
        try {

            return getHibernateTemplate().bulkUpdate("delete Secobjprivilege as model where model.roleusergroup =?0",
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
            getHibernateTemplate().delete(persistentInstance);
            logger.debug("delete successful");
        } catch (RuntimeException re) {
            logger.error("delete failed", re);
            throw re;
        }
    }

    @Override
    public String getFunctionDesc(String function_code) {
        try {
            String queryString = "select description from Secobjectname obj where obj.objectname='" + function_code
                    + "'";

            List lst = getHibernateTemplate().find(queryString);
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
            String queryString = "select description from Secprivilege obj where obj.privilege='" + accessType_code
                    + "'";

            List lst = getHibernateTemplate().find(queryString);
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
        Session session = sessionFactory.getCurrentSession();
        try {
            String queryString = "from Secobjprivilege as model where model."
                    + propertyName + "= ?1 order by objectname_code";
            Query queryObject = session.createQuery(queryString);
            queryObject.setParameter(1, value);
            return queryObject.list();
        } catch (RuntimeException re) {
            logger.error("find by property name failed", re);
            throw re;
        } finally {
            // this.releaseSession(session);
            session.close();
        }
    }

    @Override
    public List<Secobjprivilege> getByObjectNameAndRoles(String o, List<String> roles) {
        String queryString = "from Secobjprivilege obj where obj.objectname_code='" + o + "'";
        List<Secobjprivilege> results = new ArrayList<Secobjprivilege>();

        @SuppressWarnings("unchecked")
        List<Secobjprivilege> lst = (List<Secobjprivilege>) getHibernateTemplate().find(queryString);

        for (Secobjprivilege p : lst) {
            if (roles.contains(p.getRoleusergroup())) {
                results.add(p);
            }
        }
        return results;
    }

    @Override
    public List<Secobjprivilege> getByRoles(List<String> roles) {
        String queryString = "from Secobjprivilege obj where obj.roleusergroup IN (?1)";
        List<Secobjprivilege> results = new ArrayList<Secobjprivilege>();

        Session session = sessionFactory.getCurrentSession();
        Query q = session.createQuery(queryString);

        q.setParameterList(1, roles);

        results = q.list();

        return results;
    }
}
