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

package io.github.carlos_emr.carlos.PMmodule.dao;

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.SecUserRole;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;

@Transactional
public class SecUserRoleDaoImpl extends HibernateDaoSupport implements SecUserRoleDao {

    private static Logger log = MiscUtils.getLogger();

    @Autowired
    /**
     * Sets the session factory override.
     */
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    @Override
    public List<SecUserRole> getUserRoles(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from SecUserRole s where s.ProviderNo = ?1";
        @SuppressWarnings("unchecked")
        List<SecUserRole> results = (List<SecUserRole>) getHibernateTemplate().find(sSQL, providerNo);

        if (log.isDebugEnabled()) {
            log.debug("getUserRoles: providerNo=" + providerNo + ",# of results=" + results.size());
        }

        return results;
    }

    @Override
    public List<SecUserRole> getSecUserRolesByRoleName(String roleName) {
        String sSQL = "from SecUserRole s where s.RoleName = ?1";
        @SuppressWarnings("unchecked")
        List<SecUserRole> results = (List<SecUserRole>) getHibernateTemplate().find(sSQL, roleName);

        return results;
    }

    @Override
    public List<SecUserRole> findByRoleNameAndProviderNo(String roleName, String providerNo) {
        String sSQL = "from SecUserRole s where s.RoleName = ?1 and s.ProviderNo=?2";
        @SuppressWarnings("unchecked")
        List<SecUserRole> results = (List<SecUserRole>) getHibernateTemplate().find(sSQL, new Object[]{roleName, providerNo});

        return results;
    }

    @Override
    public boolean hasAdminRole(String providerNo) {
        if (providerNo == null) {
            throw new IllegalArgumentException();
        }

        boolean result = false;
        String sSQL = "from SecUserRole s where s.ProviderNo = ?1 and s.RoleName = 'admin'";
        @SuppressWarnings("unchecked")
        List<SecUserRole> results = (List<SecUserRole>) this.getHibernateTemplate().find(sSQL, providerNo);
        if (!results.isEmpty()) {
            result = true;
        }

        if (log.isDebugEnabled()) {
            log.debug("hasAdminRole: providerNo=" + providerNo + ",result=" + result);
        }

        return result;
    }

    @Override
    public SecUserRole find(Long id) {
        return this.getHibernateTemplate().get(SecUserRole.class, id);
    }

    @Override
    public void save(SecUserRole sur) {
        sur.setLastUpdateDate(new Date());
        this.getHibernateTemplate().save(sur);
    }

    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String sSQL = "select p.ProviderNo From SecUserRole p WHERE p.lastUpdateDate > ?1";
        @SuppressWarnings("unchecked")
        List<String> records = (List<String>) getHibernateTemplate().find(sSQL, date);

        return records;
    }

}
