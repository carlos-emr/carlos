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

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramClientStatus;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;

public class ProgramClientStatusDAOImpl extends HibernateDaoSupport implements ProgramClientStatusDAO {

    private Logger log = MiscUtils.getLogger();
    public SessionFactory sessionFactory;

    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    public List<ProgramClientStatus> getProgramClientStatuses(Integer programId) {
        String sSQL = "from ProgramClientStatus pcs where pcs.programId=?1";
        return (List<ProgramClientStatus>) this.getHibernateTemplate().find(sSQL, programId);
    }

    public void saveProgramClientStatus(ProgramClientStatus status) {
        this.getHibernateTemplate().saveOrUpdate(status);
    }

    public ProgramClientStatus getProgramClientStatus(String id) {
        if (id == null || Integer.valueOf(id) < 0) {
            throw new IllegalArgumentException();
        }

        ProgramClientStatus pcs = null;
        pcs = this.getHibernateTemplate().get(ProgramClientStatus.class, Integer.valueOf(id));
        if (pcs != null) return pcs;
        else return null;
    }

    public void deleteProgramClientStatus(String id) {
        this.getHibernateTemplate().delete(getProgramClientStatus(id));
    }

    public boolean clientStatusNameExists(Integer programId, String statusName) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        if (statusName == null || statusName.length() <= 0) {
            throw new IllegalArgumentException();
        }

        // Session session = getSession();
        Session session = sessionFactory.getCurrentSession();
        List teams = new ArrayList();
        try {
            Query query = session.createQuery("select pt.id from ProgramClientStatus pt where pt.programId = ?1 and pt.name = ?2");
            query.setLong(1, programId.longValue());
            query.setString(2, statusName);

            teams = query.list();

            if (log.isDebugEnabled()) {
                log.debug("teamNameExists: programId = " + programId + ", statusName = " + statusName + ", result = " + !teams.isEmpty());
            }
        } finally {
            //releaseSession(session);
            session.close();
        }
        return !teams.isEmpty();
    }

    public List<Admission> getAllClientsInStatus(Integer programId, Integer statusId) {
        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        if (statusId == null || statusId <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Admission a where a.programId = ?1 and a.teamId = ?2 and a.admissionStatus='current'";
        List<Admission> results = (List<Admission>) this.getHibernateTemplate().find(sSQL, new Object[]{programId, statusId});

        if (log.isDebugEnabled()) {
            log.debug("getAdmissionsInTeam: programId= " + programId + ",statusId=" + statusId + ",# results=" + results.size());
        }

        return results;
    }
}
