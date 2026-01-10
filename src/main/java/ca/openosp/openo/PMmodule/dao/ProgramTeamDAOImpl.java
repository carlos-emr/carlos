//CHECKSTYLE:OFF
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

package ca.openosp.openo.PMmodule.dao;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import ca.openosp.openo.PMmodule.model.ProgramTeam;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.hibernate.SessionFactory;

/**
 * Data Access Object (DAO) implementation for ProgramTeam entities.
 * 
 * <p>This DAO provides database operations for managing program teams within the PMmodule.
 * It handles CRUD operations and validation logic for program team entities.</p>
 * 
 * <p>Migrated from HibernateDaoSupport to direct SessionFactory injection for better
 * alignment with modern Spring/Hibernate practices.</p>
 * 
 * @see ProgramTeamDAO
 * @see ProgramTeam
 */
@Repository
public class ProgramTeamDAOImpl implements ProgramTeamDAO {

    private Logger log = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     * 
     * @return the current session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Checks if a program team exists by its ID.
     * 
     * @param teamId the ID of the team to check
     * @return true if the team exists, false otherwise
     */
    @Override
    public boolean teamExists(Integer teamId) {
        boolean exists = getSession().get(ProgramTeam.class, teamId) != null;
        log.debug("teamExists: " + exists);

        return exists;
    }

    /**
     * Checks if a team with the given name exists for a specific program.
     * 
     * @param programId the ID of the program
     * @param teamName the name of the team to check
     * @return true if a team with the given name exists in the program, false otherwise
     * @throws IllegalArgumentException if programId is null or invalid, or if teamName is null or empty
     */
    @Override
    public boolean teamNameExists(Integer programId, String teamName) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        if (teamName == null || teamName.length() <= 0) {
            throw new IllegalArgumentException();
        }
        
        Session session = getSession();
        Query query = session.createQuery("select pt.id from ProgramTeam pt where pt.programId = ?1 and pt.name = ?2" );
        query.setParameter(1, programId.longValue());
        query.setParameter(2, teamName);

        @SuppressWarnings("unchecked")
        List<Long> teams = query.list();

        if (log.isDebugEnabled()) {
            log.debug("teamNameExists: programId = " + programId + ", teamName = " + teamName + ", result = " + !teams.isEmpty());
        }

        return !teams.isEmpty();
    }

    /**
     * Retrieves a program team by its ID.
     * 
     * @param id the ID of the program team to retrieve
     * @return the ProgramTeam entity, or null if not found
     * @throws IllegalArgumentException if id is null or invalid
     */
    @Override
    public ProgramTeam getProgramTeam(Integer id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramTeam result = getSession().get(ProgramTeam.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getProgramTeam: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Retrieves all program teams for a specific program.
     * 
     * @param programId the ID of the program
     * @return list of ProgramTeam entities belonging to the specified program
     * @throws IllegalArgumentException if programId is null or invalid
     */
    @Override
    public List<ProgramTeam> getProgramTeams(Integer programId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Session session = getSession();
        Query query = session.createQuery("from ProgramTeam tp where tp.programId = ?1");
        query.setParameter(1, programId);
        
        @SuppressWarnings("unchecked")
        List<ProgramTeam> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getProgramTeams: programId=" + programId + ",# of results=" + results.size());
        }

        return results;
    }

    /*
     * (non-Javadoc)
     *
     * @see ca.openosp.openo.daos.PMmodule.ProgramTeamDAO#saveProgramTeam(ca.openosp.openo.model.PMmodule.ProgramTeam)
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
     * @see ca.openosp.openo.daos.PMmodule.ProgramTeamDAO#deleteProgramTeam(java.lang.Integer)
     */
    @Override
    public void deleteProgramTeam(Integer id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        this.getHibernateTemplate().delete(getProgramTeam(id));

        if (log.isDebugEnabled()) {
            log.debug("deleteProgramTeam: id=" + id);
        }
    }

}
 