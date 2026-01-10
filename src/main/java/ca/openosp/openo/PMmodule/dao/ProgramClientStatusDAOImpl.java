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

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import ca.openosp.openo.PMmodule.model.ProgramClientStatus;
import ca.openosp.openo.commn.model.Admission;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.hibernate.SessionFactory;

/**
 * Data Access Object implementation for ProgramClientStatus entities.
 * Provides database operations for managing program client status records.
 * 
 * <p>This implementation uses Hibernate SessionFactory for database access,
 * migrated from the deprecated HibernateDaoSupport pattern.</p>
 * 
 * @see ProgramClientStatusDAO
 * @see ProgramClientStatus
 */
public class ProgramClientStatusDAOImpl implements ProgramClientStatusDAO {

    private Logger log = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session from the session factory.
     * 
     * @return the current Hibernate session
     */
    protected Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all client statuses for a specific program.
     * 
     * @param programId the ID of the program
     * @return list of ProgramClientStatus objects for the specified program
     */
    public List<ProgramClientStatus> getProgramClientStatuses(Integer programId) {
        String sSQL = "from ProgramClientStatus pcs where pcs.programId=:programId";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("programId", programId);
        @SuppressWarnings("unchecked")
        List<ProgramClientStatus> results = (List<ProgramClientStatus>) query.list();
        return results;
    }

    /**
     * Saves or updates a program client status.
     * 
     * @param status the ProgramClientStatus object to save or update
     */
    public void saveProgramClientStatus(ProgramClientStatus status) {
        getSession().saveOrUpdate(status);
    }

    /**
     * Retrieves a program client status by ID.
     * 
     * @param id the ID of the program client status
     * @return the ProgramClientStatus object, or null if not found
     * @throws IllegalArgumentException if id is null or negative
     */
    public ProgramClientStatus getProgramClientStatus(String id) {
        if (id == null || Integer.valueOf(id) < 0) {
            throw new IllegalArgumentException();
        }

        ProgramClientStatus pcs = null;
        pcs = getSession().get(ProgramClientStatus.class, Integer.valueOf(id));
        if (pcs != null) return pcs;
        else return null;
    }

    /**
     * Deletes a program client status by ID.
     * 
     * @param id the ID of the program client status to delete
     * @throws IllegalArgumentException if id is null or negative
     */
    public void deleteProgramClientStatus(String id) {
        getSession().delete(getProgramClientStatus(id));
    }

    /**
     * Checks if a client status name exists for a given program.
     * 
     * @param programId the ID of the program
     * @param statusName the name of the status to check
     * @return true if the status name exists, false otherwise
     * @throws IllegalArgumentException if programId is null/invalid or statusName is null/empty
     */
    public boolean clientStatusNameExists(Integer programId, String statusName) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        if (statusName == null || statusName.length() <= 0) {
            throw new IllegalArgumentException();
        }

        Session session = getSession();
        Query query = session.createQuery("select pt.id from ProgramClientStatus pt where pt.programId = :programId and pt.name = :statusName");
        query.setParameter("programId", programId.longValue());
        query.setParameter("statusName", statusName);

        @SuppressWarnings("unchecked")
        List<Object> teams = query.list();

        if (log.isDebugEnabled()) {
            log.debug("clientStatusNameExists: programId = " + programId + ", statusName = " + statusName + ", result = " + !teams.isEmpty());
        }

        return !teams.isEmpty();
    }

    /**
     * Retrieves all clients (admissions) with a specific status in a program.
     * 
     * @param programId the ID of the program
     * @param statusId the ID of the status
     * @return list of Admission objects with the specified status
     * @throws IllegalArgumentException if programId or statusId is null/invalid
     */
    public List<Admission> getAllClientsInStatus(Integer programId, Integer statusId) {
        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        if (statusId == null || statusId <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from Admission a where a.ProgramId = :programId and a.TeamId = :statusId and a.AdmissionStatus='current'";
        Query query = getSession().createQuery(sSQL);
        query.setParameter("programId", programId);
        query.setParameter("statusId", statusId);
        @SuppressWarnings("unchecked")
        List<Admission> results = (List<Admission>) query.list();

        if (log.isDebugEnabled()) {
            log.debug("getAllClientsInStatus: programId= " + programId + ",statusId=" + statusId + ",# results=" + results.size());
        }

        return results;
    }
}
