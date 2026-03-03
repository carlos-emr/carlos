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
import io.github.carlos_emr.carlos.PMmodule.model.ProgramTeam;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.orm.hibernate5.support.HibernateDaoSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;
import io.github.carlos_emr.carlos.utility.HqlQueryHelper;
import org.springframework.transaction.annotation.Transactional;

@Transactional
public class ProgramTeamDAOImpl extends HibernateDaoSupport implements ProgramTeamDAO {

    private Logger log = MiscUtils.getLogger();
    @Autowired
    public void setSessionFactoryOverride(SessionFactory sessionFactory) {
        super.setSessionFactory(sessionFactory);
    }

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.daos.PMmodule.ProgramTeamDAO#teamExists(java.lang.Integer)
     */
    @Override
    public boolean teamExists(Integer teamId) {
        if (teamId == null) {
            log.debug("teamExists: called with null teamId, returning false");
            return false;
        }
        boolean exists = getHibernateTemplate().get(ProgramTeam.class, teamId) != null;
        log.debug("teamExists: " + exists);

        return exists;
    }

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.daos.PMmodule.ProgramTeamDAO#teamNameExists(java.lang.Integer, java.lang.String)
     */
    @Override
    public boolean teamNameExists(Integer programId, String teamName) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        if (teamName == null || teamName.length() <= 0) {
            throw new IllegalArgumentException();
        }
        List teams = HqlQueryHelper.find(currentSession(),
                "from ProgramTeam pt where pt.programId = ?1 and pt.name = ?2",
                programId, teamName);

        if (log.isDebugEnabled()) {
            log.debug("teamNameExists: programId = " + programId + ", teamName = " + teamName + ", result = " + !teams.isEmpty());
        }

        return !teams.isEmpty();
    }

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.daos.PMmodule.ProgramTeamDAO#getProgramTeam(java.lang.Integer)
     */
    @Override
    public ProgramTeam getProgramTeam(Integer id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramTeam result = this.getHibernateTemplate().get(ProgramTeam.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getProgramTeam: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.daos.PMmodule.ProgramTeamDAO#getProgramTeams(java.lang.Integer)
     */
    @Override
    public List<ProgramTeam> getProgramTeams(Integer programId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ProgramTeam tp where tp.programId = ?1";
        List<ProgramTeam> results = (List<ProgramTeam>) HqlQueryHelper.find(currentSession(), sSQL, programId);

        if (log.isDebugEnabled()) {
            log.debug("getProgramTeams: programId=" + programId + ",# of results=" + results.size());
        }

        return results;
    }

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.daos.PMmodule.ProgramTeamDAO#saveProgramTeam(io.github.carlos_emr.carlos.model.PMmodule.ProgramTeam)
     */
    @Override
    public void saveProgramTeam(ProgramTeam team) {
        if (team == null) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().saveOrUpdate(team);

        if (log.isDebugEnabled()) {
            log.debug("saveProgramTeam: id=" + team.getId());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see io.github.carlos_emr.carlos.daos.PMmodule.ProgramTeamDAO#deleteProgramTeam(java.lang.Integer)
     */
    @Override
    public void deleteProgramTeam(Integer id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramTeam team = getProgramTeam(id);
        if (team == null) {
            throw new EmptyResultDataAccessException("No ProgramTeam found with id=" + id, 1);
        }

        this.getHibernateTemplate().delete(team);

        if (log.isDebugEnabled()) {
            log.debug("deleteProgramTeam: id=" + id);
        }
    }

}
 