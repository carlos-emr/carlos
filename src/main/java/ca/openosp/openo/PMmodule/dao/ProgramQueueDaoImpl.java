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
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.springframework.beans.factory.annotation.Autowired;

import ca.openosp.openo.PMmodule.model.ProgramQueue;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for ProgramQueue entity.
 * <p>
 * This DAO provides CRUD operations and query methods for managing program queues,
 * which represent client enrollment queues for various programs in the system.
 * </p>
 * 
 * <p>
 * The implementation uses Hibernate SessionFactory for database operations,
 * migrated from the deprecated HibernateDaoSupport pattern.
 * </p>
 * 
 * @see ProgramQueue
 * @see ProgramQueueDao
 */
public class ProgramQueueDaoImpl implements ProgramQueueDao {

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
     * Retrieves a program queue by its unique identifier.
     * 
     * @param queueId the unique identifier of the queue
     * @return the program queue, or null if not found
     * @throws IllegalArgumentException if queueId is null or less than or equal to zero
     */
    @Override
    public ProgramQueue getProgramQueue(Long queueId) {
        if (queueId == null || queueId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramQueue result = getSession().get(ProgramQueue.class, queueId);

        if (log.isDebugEnabled()) {
            log.debug("getProgramQueue: queueId=" + queueId + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Retrieves all program queues for a specific program, ordered by queue ID.
     * 
     * @param programId the unique identifier of the program
     * @return list of program queues for the specified program
     * @throws IllegalArgumentException if programId is null
     */
    @Override
    public List<ProgramQueue> getProgramQueuesByProgramId(Long programId) {
        if (programId == null) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM ProgramQueue q WHERE q.ProgramId=:programId ORDER BY q.Id";
        Query<ProgramQueue> query = getSession().createQuery(queryStr, ProgramQueue.class);
        query.setParameter("programId", programId);
        List<ProgramQueue> results = query.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("getProgramQueue: programId=" + programId + ",# of results=" + results.size());
        }

        return results;
    }

    /**
     * Retrieves all active program queues for a specific program, ordered by referral date.
     * 
     * @param programId the unique identifier of the program
     * @return list of active program queues for the specified program
     * @throws IllegalArgumentException if programId is null
     */
    @Override
    public List<ProgramQueue> getActiveProgramQueuesByProgramId(Long programId) {
        if (programId == null) {
            throw new IllegalArgumentException();
        }

        String queryStr = "FROM ProgramQueue pq WHERE pq.ProgramId = :programId AND pq.Status = 'active' ORDER BY pq.ReferralDate";
        Query<ProgramQueue> query = getSession().createQuery(queryStr, ProgramQueue.class);
        query.setParameter("programId", programId);
        List<ProgramQueue> results = query.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("getActiveProgramQueuesByProgramId: programId=" + programId + ",# of results=" + results.size());
        }

        return results;
    }

    /**
     * Saves or updates a program queue entity.
     * 
     * @param programQueue the program queue to save or update
     */
    @Override
    public void saveProgramQueue(ProgramQueue programQueue) {
        if (programQueue == null) {
            return;
        }

        getSession().saveOrUpdate(programQueue);

        if (log.isDebugEnabled()) {
            log.debug("saveProgramQueue: id=" + programQueue.getId());
        }

    }

    /**
     * Retrieves a program queue for a specific program and client.
     * 
     * @param programId the unique identifier of the program
     * @param clientId the unique identifier of the client
     * @return the program queue, or null if not found
     * @throws IllegalArgumentException if programId or clientId is null
     */
    @Override
    public ProgramQueue getQueue(Long programId, Long clientId) {
        if (programId == null) {
            throw new IllegalArgumentException();
        }
        if (clientId == null) {
            throw new IllegalArgumentException();
        }

        ProgramQueue result = null;
        String queryStr = "FROM ProgramQueue pq WHERE pq.ProgramId = :programId AND pq.ClientId = :clientId";
        Query<ProgramQueue> query = getSession().createQuery(queryStr, ProgramQueue.class);
        query.setParameter("programId", programId);
        query.setParameter("clientId", clientId);
        List<ProgramQueue> results = query.getResultList();

        if (!results.isEmpty()) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getQueue: programId=" + programId + ",clientId=" + clientId + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Retrieves the active program queue for a specific program and demographic.
     * 
     * @param programId the unique identifier of the program
     * @param demographicNo the unique identifier of the demographic
     * @return the active program queue, or null if not found
     * @throws IllegalArgumentException if programId or demographicNo is null or less than or equal to zero
     */
    @Override
    public ProgramQueue getActiveProgramQueue(Long programId, Long demographicNo) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (demographicNo == null || demographicNo.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramQueue result = null;

        String queryStr = "FROM ProgramQueue pq WHERE pq.ProgramId = :programId AND pq.ClientId = :demographicNo AND pq.Status='active'";
        Query<ProgramQueue> query = getSession().createQuery(queryStr, ProgramQueue.class);
        query.setParameter("programId", programId);
        query.setParameter("demographicNo", demographicNo);
        List<ProgramQueue> results = query.getResultList();
        if (!results.isEmpty()) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getActiveProgramQueue: programId=" + programId + ",demogaphicNo=" + demographicNo + ",found="
                    + (result != null));
        }

        return result;
    }
}
