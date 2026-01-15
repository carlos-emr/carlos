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

import javax.persistence.TypedQuery;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.AccessType;
import ca.openosp.openo.PMmodule.model.ProgramAccess;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.QueueCache;
import org.springframework.stereotype.Repository;

/**
 * Implementation of ProgramAccessDAO interface for database access to ProgramAccess model objects.
 */
@Repository
public class ProgramAccessDAOImpl extends AbstractDaoImpl<ProgramAccess> implements ProgramAccessDAO {

    private static Logger log = MiscUtils.getLogger();

    private static QueueCache<Long, List<ProgramAccess>> programAccessListByProgramIdCache = new QueueCache<Long, List<ProgramAccess>>(
            4, 100, DateUtils.MILLIS_PER_HOUR, null);

    /**
     * Default constructor.
     * Calls the parent constructor with ProgramAccess model class.
     */
    public ProgramAccessDAOImpl() {
        super(ProgramAccess.class);
    }

    /**
     * Retrieves the list of ProgramAccess objects associated with a specific program ID.
     *
     * @param programId the ID of the program
     * @return a list of ProgramAccess objects associated with the specified program ID
     */
    @Override
    public List<ProgramAccess> getAccessListByProgramId(Long programId) {
        List<ProgramAccess> results = programAccessListByProgramIdCache.get(programId);
        if (results == null) {
            String q = "select pp from ProgramAccess pp where pp.ProgramId=?1";
            TypedQuery<ProgramAccess> query = entityManager.createQuery(q, ProgramAccess.class);
            query.setParameter(1, programId);
            results = query.getResultList();
            if (results != null)
                programAccessListByProgramIdCache.put(programId, results);
        }

        return results;
    }

    /**
     * Retrieves a ProgramAccess object by its ID.
     *
     * @param id the ID of the ProgramAccess object to retrieve
     * @return the ProgramAccess object if found, null otherwise
     * @throws IllegalArgumentException if the ID is null or less than or equal to 0
     */
    @Override
    public ProgramAccess getProgramAccess(Long id) {

        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramAccess result = entityManager.find(ProgramAccess.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getProgramAccess: id=" + id + ",found=" + (result != null));
        }
        return result;
    }

    /**
     * Retrieves a ProgramAccess object by program ID and access type ID.
     *
     * @param programId the ID of the program
     * @param accessTypeId the ID of the access type
     * @return the ProgramAccess object if found, null otherwise
     * @throws IllegalArgumentException if either programId or accessTypeId is null or less than or equal to 0
     */
    @Override
    public ProgramAccess getProgramAccess(Long programId, Long accessTypeId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (accessTypeId == null || accessTypeId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        String accessTypeIdString = accessTypeId.toString();
        ProgramAccess result = null;
        String q = "from ProgramAccess pa where pa.ProgramId = ?1 and pa.AccessTypeId = ?2";
        TypedQuery<ProgramAccess> query = entityManager.createQuery(q, ProgramAccess.class);
        query.setParameter(1, programId);
        query.setParameter(2, accessTypeIdString);
        List<ProgramAccess> results = query.getResultList();
        if (!results.isEmpty()) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getProgramAccess: programId=" + programId + ",accessTypeId=" + accessTypeId + ",found="
                    + (result != null));
        }

        return result;
    }

    /**
     * Retrieves a list of ProgramAccess objects by program ID and access type name.
     *
     * @param programId the ID of the program
     * @param accessType the name of the access type
     * @return a list of ProgramAccess objects matching the specified program ID and access type name
     */
    @Override
    public List<ProgramAccess> getProgramAccessListByType(Long programId, String accessType) {
        String q = "from ProgramAccess pa where pa.ProgramId = ?1 and pa.AccessType.Name like ?2";
        TypedQuery<ProgramAccess> query = entityManager.createQuery(q, ProgramAccess.class);
        query.setParameter(1, programId);
        query.setParameter(2, accessType);
        return query.getResultList();
    }

    /**
     * Saves a ProgramAccess object.
     *
     * @param pa the ProgramAccess object to save
     * @throws IllegalArgumentException if the ProgramAccess object is null
     */
    @Override
    public void saveProgramAccess(ProgramAccess pa) {
        if (pa == null) {
            throw new IllegalArgumentException();
        }

        entityManager.merge(pa);
        programAccessListByProgramIdCache.remove(pa.getProgramId());

        if (log.isDebugEnabled()) {
            log.debug("saveProgramAccess:" + pa.getId());
        }
    }

    /**
     * Deletes a ProgramAccess object by its ID.
     *
     * @param id the ID of the ProgramAccess object to delete
     * @throws IllegalArgumentException if the ID is null or less than or equal to 0
     */
    @Override
    public void deleteProgramAccess(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramAccess pa = entityManager.find(ProgramAccess.class, id);
        if (pa != null) {
            programAccessListByProgramIdCache.remove(pa.getProgramId());
            entityManager.remove(pa);
        }

        if (log.isDebugEnabled()) {
            log.debug("deleteProgramAccess:" + id);
        }

    }

    /**
     * Retrieves a list of all AccessType objects.
     *
     * @return a list of AccessType objects
     */
    @Override
    public List<AccessType> getAccessTypes() {
        List<AccessType> results = entityManager.createQuery("from AccessType at", AccessType.class).getResultList();

        if (log.isDebugEnabled()) {
            log.debug("getAccessTypes: # of results=" + results.size());
        }
        return results;
    }

    /**
     * Retrieves an AccessType object by its ID.
     *
     * @param id the ID of the AccessType object to retrieve
     * @return the AccessType object if found, null otherwise
     * @throws IllegalArgumentException if the ID is null or less than or equal to 0
     */
    @Override
    public AccessType getAccessType(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        AccessType result = entityManager.find(AccessType.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getAccessType: id=" + id + ",found=" + (result != null));
        }

        return result;
    }
}
