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

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.openo.PMmodule.model.ProgramSignature;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for ProgramSignature entities.
 * Provides methods to manage program signatures including retrieval and persistence operations.
 * 
 * <p>This implementation uses Hibernate SessionFactory for database operations.</p>
 * 
 * @see ProgramSignatureDao
 * @see ProgramSignature
 */
@Transactional
public class ProgramSignatureDaoImpl implements ProgramSignatureDao {

    private static final Logger log = MiscUtils.getLogger();
    
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
     * Retrieves the first (original) signature for a program.
     * The first signature represents the creator of the program.
     * 
     * @param programId the ID of the program
     * @return the first ProgramSignature for the program, or null if not found or programId is invalid
     */
    @Override
    public ProgramSignature getProgramFirstSignature(Integer programId) {
        if (programId == null || programId.intValue() <= 0) {
            return null;
        }
        
        String hql = "FROM ProgramSignature ps WHERE ps.programId = :programId ORDER BY ps.updateDate ASC";
        Query query = getSession().createQuery(hql);
        query.setParameter("programId", programId);
        query.setMaxResults(1);
        
        @SuppressWarnings("unchecked")
        List<ProgramSignature> results = query.list();
        
        ProgramSignature programSignature = null;
        if (!results.isEmpty()) {
            programSignature = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getProgramFirstSignature: " + ((programSignature != null) ? String.valueOf(programSignature.getId()) : "null"));
        }

        return programSignature;
    }

    /**
     * Retrieves all signatures for a program, ordered by update date.
     * 
     * @param programId the ID of the program
     * @return list of ProgramSignature objects for the program, or null if programId is invalid
     */
    @Override
    public List<ProgramSignature> getProgramSignatures(Integer programId) {
        if (programId == null || programId.intValue() <= 0) {
            return null;
        }

        String hql = "FROM ProgramSignature ps WHERE ps.programId = :programId ORDER BY ps.updateDate ASC";
        Query query = getSession().createQuery(hql);
        query.setParameter("programId", programId);
        
        @SuppressWarnings("unchecked")
        List<ProgramSignature> results = query.list();

        if (log.isDebugEnabled()) {
            log.debug("getProgramSignatures: # of programs: " + results.size());
        }
        
        return results;
    }

    /**
     * Saves or updates a program signature.
     * Sets the update date to the current time before persisting.
     * 
     * @param programSignature the ProgramSignature to save
     * @throws IllegalArgumentException if programSignature is null
     */
    @Override
    public void saveProgramSignature(ProgramSignature programSignature) {
        if (programSignature == null) {
            throw new IllegalArgumentException("programSignature cannot be null");
        }
        
        programSignature.setUpdateDate(new Date());
        getSession().saveOrUpdate(programSignature);
        getSession().flush();

        if (log.isDebugEnabled()) {
            log.debug("saveProgramSignature: id= " + programSignature.getId());
        }
    }
}