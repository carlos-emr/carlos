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

import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.PMmodule.model.FunctionalUserType;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramFunctionalUser;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;

public class ProgramFunctionalUserDAOImpl extends HibernateDaoSupport implements ProgramFunctionalUserDAO {

    private static Logger log = MiscUtils.getLogger();
    public SessionFactory sessionFactory;

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    @Override
    public List<FunctionalUserType> getFunctionalUserTypes() {
        String sSQL = "from FunctionalUserType";
        List<FunctionalUserType> results = (List<FunctionalUserType>) this.getHibernateTemplate().find(sSQL);

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUserTypes: # of results=" + results.size());
        }
        return results;
    }

    @Override
    public FunctionalUserType getFunctionalUserType(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        FunctionalUserType result = this.getHibernateTemplate().get(FunctionalUserType.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUserType: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    @Override
    public void saveFunctionalUserType(FunctionalUserType fut) {
        if (fut == null) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().saveOrUpdate(fut);

        if (log.isDebugEnabled()) {
            log.debug("saveFunctionalUserType:" + fut.getId());
        }
    }

    @Override
    public void deleteFunctionalUserType(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().delete(getFunctionalUserType(id));

        if (log.isDebugEnabled()) {
            log.debug("deleteFunctionalUserType:" + id);
        }
    }

    @Override
    public List<FunctionalUserType> getFunctionalUsers(Long programId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ProgramFunctionalUser pfu where pfu.ProgramId = ?0";
        List<FunctionalUserType> results = (List<FunctionalUserType>) this.getHibernateTemplate().find(sSQL, programId);

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUsers: programId=" + programId + ",# of results=" + results.size());
        }
        return results;
    }

    @Override
    public ProgramFunctionalUser getFunctionalUser(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramFunctionalUser result = this.getHibernateTemplate().get(ProgramFunctionalUser.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUser: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    @Override
    public void saveFunctionalUser(ProgramFunctionalUser pfu) {
        if (pfu == null) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().saveOrUpdate(pfu);

        if (log.isDebugEnabled()) {
            log.debug("saveFunctionalUser:" + pfu.getId());
        }
    }

    @Override
    public void deleteFunctionalUser(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().delete(getFunctionalUser(id));

        if (log.isDebugEnabled()) {
            log.debug("deleteFunctionalUser:" + id);
        }
    }

    @Override
    public Long getFunctionalUserByUserType(Long programId, Long userTypeId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (userTypeId == null || userTypeId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Long result = null;

        String sSQL = "select pfu.ProgramId from ProgramFunctionalUser pfu where pfu.ProgramId = ?0 and pfu.UserTypeId = ?1";
        @SuppressWarnings("unchecked")
        List<Long> results = (List<Long>) this.getHibernateTemplate().find(sSQL, new Object[]{programId, userTypeId});

        if (!results.isEmpty()) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUserByUserType: programId=" + programId + ",userTypeId=" + userTypeId + ",result="
                    + result);
        }

        return result;
    }
}
