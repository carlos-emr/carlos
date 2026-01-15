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
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.PMmodule.model.Program;
import ca.openosp.openo.commn.dao.AbstractDaoImpl;
import ca.openosp.openo.utility.MiscUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;

/**
 * Implementation of ProgramDao interface for database access to Program model objects.
 */
@Transactional
public class ProgramDaoImpl extends AbstractDaoImpl<Program> implements ProgramDao {

    private static final Logger log = MiscUtils.getLogger();

    /**
     * Default constructor.
     * Calls the parent constructor with Program model class.
     */
    public ProgramDaoImpl() {
        super(Program.class);
    }


    /**
     * Checks if the specified program is a service program.
     *
     * @param programId the ID of the program to check
     * @return true if the program is a service program, false otherwise
     */
    @Override
    public boolean isServiceProgram(Integer programId) {
        Program result = getProgram(programId);
        if (result == null)
            return (false);
        return (result.isService());
    }

    /**
     * Checks if the specified program is a community program.
     *
     * @param programId the ID of the program to check
     * @return true if the program is a community program, false otherwise
     */
    @Override
    public boolean isCommunityProgram(Integer programId) {
        Program result = getProgram(programId);
        if (result == null)
            return (false);
        return (result.isCommunity());
    }

    /**
     * Checks if the specified program is an external program.
     *
     * @param programId the ID of the program to check
     * @return true if the program is an external program, false otherwise
     */
    @Override
    public boolean isExternalProgram(Integer programId) {
        Program result = getProgram(programId);
        if (result == null)
            return (false);
        return (result.isExternal());
    }

    /**
     * Retrieves a program by its ID.
     *
     * @param programId the ID of the program to retrieve
     * @return the Program object if found, null otherwise
     */
    @Override
    public Program getProgram(Integer programId) {
        if (programId == null || programId.intValue() <= 0) {
            return null;
        }

        return entityManager.find(Program.class, programId);
    }

    /**
     * Retrieves a program by its ID for appointment view.
     *
     * @param programId the ID of the program to retrieve
     * @return the Program object if found and has exclusive view set to 'appointment', null otherwise
     */
    @Override
    public Program getProgramForApptView(Integer programId) {
        if (programId == null || programId <= 0) {
            return null;
        }

        String queryStr = "FROM Program p WHERE p.id = ?1 AND p.exclusiveView = 'appointment'";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, programId);

        List<Program> resultList = query.getResultList();

        if (log.isDebugEnabled()) {
            log.debug("isCommunityProgram: id=" + programId);
        }

        return resultList.isEmpty() ? null : resultList.get(0);
    }

    /**
     * Retrieves the name of a program by its ID.
     *
     * @param programId the ID of the program
     * @return the name of the program if found, null otherwise
     */
    @Override
    public String getProgramName(Integer programId) {
        Program result = getProgram(programId);
        if (result == null)
            return (null);
        return (result.getName());
    }

    /**
     * Retrieves the ID of a program by its name.
     *
     * @param programName the name of the program
     * @return the ID of the program if found, null otherwise
     */
    @Override
    public Integer getProgramIdByProgramName(String programName) {

        if (programName == null)
            return null;

        String queryStr = "FROM Program p WHERE p.name = ?1 ORDER BY p.id";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, programName);

        List<Program> programs = query.getResultList();
        return programs.isEmpty() ? null : programs.get(0).getId();
    }

    /**
     * Retrieves all programs.
     *
     * @return a list of all Program objects
     */
    @Override
    public List<Program> findAll() {
        String queryStr = "FROM Program p";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        return query.getResultList();
    }

    /**
     * This method doesn't work, it doesn't find all programs, it only finds all
     * community programs. See findAll instead.
     *
     * @deprecated 2013-12-09 don't use this anymore it's misleading
     */
    @Deprecated
    @Override
    public List<Program> getAllPrograms() {
        String queryStr = "FROM Program p WHERE p.type != ?1 ORDER BY p.name";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, Program.COMMUNITY_TYPE);

        if (log.isDebugEnabled()) {
            log.debug("getAllPrograms: # of programs: " + query.getResultList().size());
        }

        return query.getResultList();
    }

    /**
     * Retrieves all active programs.
     *
     * @return a list of all active Program objects
     */
    @Override
    public List<Program> getAllActivePrograms() {
        String queryStr = "FROM Program p WHERE p.programStatus = ?1";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, Program.PROGRAM_STATUS_ACTIVE);

        return query.getResultList();
    }

    /**
     * @deprecated 2013-12-09 don't use this anymore use getProgramByType, reason is
     * parameters should never have been "Any"
     */
    /**
     * Retrieves programs based on program status, type, and facility ID.
     *
     * @param programStatus the program status filter (can be "Any")
     * @param type the program type filter (can be "Any")
     * @param facilityId the facility ID filter (0 for any facility)
     * @return a list of Program objects matching the specified criteria
     */
    @Deprecated
    @Override
    public List<Program> getAllPrograms(String programStatus, String type, int facilityId) {
        try {
            StringBuilder queryStr = new StringBuilder("FROM Program p WHERE 1=1");
            if (!"Any".equals(programStatus)) {
                queryStr.append(" AND p.programStatus = :programStatus");
            }
            if (!"Any".equals(type)) {
                queryStr.append(" AND p.type = :type");
            }
            if (facilityId > 0) {
                queryStr.append(" AND p.facilityId = :facilityId");
            }
            TypedQuery<Program> query = entityManager.createQuery(queryStr.toString(), Program.class);

            if (!"Any".equals(programStatus)) {
                query.setParameter("programStatus", programStatus);
            }
            if (!"Any".equals(type)) {
                query.setParameter("type", type);
            }
            if (facilityId > 0) {
                query.setParameter("facilityId", facilityId);
            }

            return query.getResultList();
        } finally {

        }
    }

    /**
     * This method doesn't work, it doesn't find all programs, it only finds all
     * community programs. See findAll instead.
     *
     * @deprecated 2013-12-09 don't use this anymore it's misleading
     */
    @Deprecated
    @Override
    public List<Program> getPrograms() {
        String queryStr = "FROM Program p WHERE p.type != ?1 ORDER BY p.name";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, Program.COMMUNITY_TYPE);

        return query.getResultList();
    }

    /**
     * This method doesn't work, it doesn't find all programs, it only finds all
     * community programs. See findAll instead.
     *
     * @deprecated 2013-12-09 don't use this anymore it's misleading
     */
    @Deprecated
    @Override
    public List<Program> getActivePrograms() {
        String queryStr = "FROM Program p WHERE p.type <> ?1 AND p.programStatus = ?2";

        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, Program.COMMUNITY_TYPE);
        query.setParameter(2, Program.PROGRAM_STATUS_ACTIVE);

        return query.getResultList();
    }

    /**
     * Retrieves programs by facility ID.
     *
     * @param facilityId the ID of the facility (can be null for programs with no associated facility)
     * @return a list of Program objects associated with the specified facility ID or with no facility
     */
    @Override
    public List<Program> getProgramsByFacilityId(Integer facilityId) {
        String queryStr = "FROM Program p WHERE (p.facilityId = ?1 OR p.facilityId IS NULL) ORDER BY p.name";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, facilityId);

        return query.getResultList();
    }

    /**
     * Retrieves programs by facility ID and functional centre ID.
     *
     * @param facilityId the ID of the facility
     * @param functionalCentreId the functional centre ID
     * @return a list of Program objects matching the specified facility ID and functional centre ID
     */
    @Override
    public List<Program> getProgramsByFacilityIdAndFunctionalCentreId(Integer facilityId, String functionalCentreId) {
        String queryStr = "FROM Program p WHERE p.facilityId = ?1 AND p.functionalCentreId = ?2";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, facilityId);
        query.setParameter(2, functionalCentreId);

        return query.getResultList();
    }

    /**
     * Retrieves community programs by facility ID.
     *
     * @param facilityId the ID of the facility (can be null for programs with no associated facility)
     * @return a list of community Program objects associated with the specified facility ID or with no facility
     */
    @Override
    public List<Program> getCommunityProgramsByFacilityId(Integer facilityId) {
        String queryStr = "FROM Program p WHERE (p.facilityId = ?1 OR p.facilityId IS NULL) " +
                      "AND p.type != ?2 ORDER BY p.name";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, facilityId);
        query.setParameter(2, Program.COMMUNITY_TYPE);

        return query.getResultList();
    }

    /**
     * Retrieves programs by facility ID, type, and active status.
     *
     * @param facilityId the ID of the facility (can be null for any facility)
     * @param type the program type
     * @param active the active status (can be null for both active and inactive)
     * @return a list of Program objects matching the specified criteria, ordered by name
     */
    @Override
    public List<Program> getProgramsByType(Integer facilityId, String type, Boolean active) {
        StringBuilder sqlCommand = new StringBuilder("FROM Program p WHERE p.type = :type");

        if (facilityId != null) {
            sqlCommand.append(" AND p.facilityId = :facilityId");
        }
        if (active != null) {
            sqlCommand.append(" AND p.programStatus = :programStatus");
        }

        sqlCommand.append(" ORDER BY p.name");

        TypedQuery<Program> query = entityManager.createQuery(sqlCommand.toString(), Program.class);
        query.setParameter("type", type);

        if (facilityId != null) {
            query.setParameter("facilityId", facilityId);
        }
        if (active != null) {
            query.setParameter("programStatus", active ? Program.PROGRAM_STATUS_ACTIVE : Program.PROGRAM_STATUS_INACTIVE);
        }

        return query.getResultList();
    }

    /**
     * Retrieves programs by gender type.
     *
     * @param genderType the gender type
     * @return a list of Program objects matching the specified gender type
     */
    @Override
    public List<Program> getProgramByGenderType(String genderType) {
        TypedQuery<Program> query = entityManager.createQuery(
            "FROM Program p WHERE p.manOrWoman = ?1", Program.class);
        query.setParameter(1, genderType);
        return query.getResultList();
    }

    /**
     * Saves a program.
     *
     * @param program the Program object to save
     * @throws IllegalArgumentException if the program is null
     */
    @Override
    public void saveProgram(Program program) {
        if (program == null) {
            throw new IllegalArgumentException();
        }
        program.setLastUpdateDate(new Date());

        entityManager.merge(program); // Use merge to save or update
        if (log.isDebugEnabled()) {
            log.debug("saveProgram: " + program.getId());
        }
    }

    /**
     * Removes a program by its ID.
     *
     * @param programId the ID of the program to remove
     * @throws IllegalArgumentException if the programId is null or less than or equal to 0
     */
    @Override
    public void removeProgram(Integer programId) {
        if (programId == null || programId <= 0) {
            throw new IllegalArgumentException();
        }

        Program program = entityManager.find(Program.class, programId);
        if (program != null) {
            entityManager.remove(program);

            if (log.isDebugEnabled()) {
                log.debug("deleteProgram: " + programId);
            }
        } else {
            MiscUtils.getLogger().warn("Unable to delete program " + programId);
        }
    }

    /**
     * Searches for programs based on the provided Program object.
     *
     * @param program the Program object containing the search criteria
     * @return a list of Program objects matching the search criteria
     * @throws IllegalArgumentException if the program is null
     */
    @Override
    public List<Program> search(Program program) {
        if (program == null) {
            throw new IllegalArgumentException();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Program> query = cb.createQuery(Program.class);
        Root<Program> root = query.from(Program.class);

        List<Predicate> predicates = new ArrayList<>();

        if (program.getName() != null && !program.getName().isEmpty()) {
            String programName = program.getName();

            // Create SOUNDEX expression for the program name
            Expression<String> soundexProgramName = cb.function("SOUNDEX", String.class, cb.literal(programName));
            Expression<String> soundexName = cb.function("SOUNDEX", String.class, root.get("name"));

            Predicate namePredicate = cb.or(
                cb.like(root.get("name"), "%" + programName + "%"),
                cb.equal(cb.function("LEFT", String.class, soundexName),
                         cb.function("LEFT", String.class, soundexProgramName))
            );
            predicates.add(namePredicate);
        }

        // Filter by type
        if (program.getType() != null && !program.getType().isEmpty()) {
            predicates.add(cb.equal(root.get("type"), program.getType()));
        }

        if (program.getType() == null || program.getType().isEmpty() || !program.getType().equals("community")) {
            predicates.add(cb.notEqual(root.get("type"), "community"));
        }

        predicates.add(cb.equal(root.get("programStatus"), Program.PROGRAM_STATUS_ACTIVE));

        // Filter by manOrWoman
        if (program.getManOrWoman() != null && !program.getManOrWoman().isEmpty()) {
            predicates.add(cb.equal(root.get("manOrWoman"), program.getManOrWoman()));
        }

        // Other filters
        if (program.isTransgender()) {
            predicates.add(cb.isTrue(root.get("transgender")));
        }

        if (program.isFirstNation()) {
            predicates.add(cb.isTrue(root.get("firstNation")));
        }


        if (program.isAlcohol()) {
            predicates.add(cb.isTrue(root.get("alcohol")));
        }

        if (program.getAbstinenceSupport() != null && !program.getAbstinenceSupport().isEmpty()) {
            predicates.add(cb.equal(root.get("abstinenceSupport"), program.getAbstinenceSupport()));
        }

        if (program.isPhysicalHealth()) {
            predicates.add(cb.isTrue(root.get("physicalHealth")));
        }

        if (program.isHousing()) {
            predicates.add(cb.isTrue(root.get("housing")));
        }

        if (program.isMentalHealth()) {
            predicates.add(cb.isTrue(root.get("mentalHealth")));
        }

        // Combine all predicates into the query
        query.select(root).where(predicates.toArray(new Predicate[0])).orderBy(cb.asc(root.get("name")));

        // Create the query and get the results
        TypedQuery<Program> typedQuery = entityManager.createQuery(query);
        List<Program> results = new ArrayList<>();
        try {
            results = typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error while searching programs", e);
        }

        if (log.isDebugEnabled()) {
            log.debug("search: # results: " + results.size());
        }

        return results;
    }

    /**
     * Searches for programs based on the provided Program object and facility ID.
     *
     * @param program the Program object containing the search criteria
     * @param facilityId the ID of the facility to search within
     * @return a list of Program objects matching the search criteria and facility ID
     * @throws IllegalArgumentException if the program or facilityId is null
     */
    @Override
    public List<Program> searchByFacility(Program program, Integer facilityId) {
        if (program == null) {
            throw new IllegalArgumentException();
        }
        if (facilityId == null) {
            throw new IllegalArgumentException();
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Program> query = cb.createQuery(Program.class);
        Root<Program> root = query.from(Program.class);

        List<Predicate> predicates = new ArrayList<>();

        // Filter by name
        if (program.getName() != null && !program.getName().isEmpty()) {
            String programName = program.getName();
            // Create SOUNDEX expression for the program name
            Expression<String> soundexProgramName = cb.function("SOUNDEX", String.class, cb.literal(programName));
            Expression<String> soundexName = cb.function("SOUNDEX", String.class, root.get("name"));

            Predicate namePredicate = cb.or(
                cb.like(root.get("name"), "%" + programName + "%"),
                cb.equal(cb.function("LEFT", String.class, soundexName),
                        cb.function("LEFT", String.class, soundexProgramName))
            );
            predicates.add(namePredicate);
        }

        // Filter by type
        if (program.getType() != null && !program.getType().isEmpty()) {
            predicates.add(cb.equal(root.get("type"), program.getType()));
        }

        if (program.getType() == null || program.getType().isEmpty() || !program.getType().equals(Program.COMMUNITY_TYPE)) {
            predicates.add(cb.notEqual(root.get("type"), Program.COMMUNITY_TYPE));
        }

        predicates.add(cb.equal(root.get("programStatus"), Program.PROGRAM_STATUS_ACTIVE));

        // Filter by manOrWoman
        if (program.getManOrWoman() != null && !program.getManOrWoman().isEmpty()) {
            predicates.add(cb.equal(root.get("manOrWoman"), program.getManOrWoman()));
        }

        // Other filters
        if (program.isTransgender()) {
            predicates.add(cb.isTrue(root.get("transgender")));
        }

        if (program.isFirstNation()) {
            predicates.add(cb.isTrue(root.get("firstNation")));
        }


        if (program.isAlcohol()) {
            predicates.add(cb.isTrue(root.get("alcohol")));
        }

        if (program.getAbstinenceSupport() != null && !program.getAbstinenceSupport().isEmpty()) {
            predicates.add(cb.equal(root.get("abstinenceSupport"), program.getAbstinenceSupport()));
        }

        if (program.isPhysicalHealth()) {
            predicates.add(cb.isTrue(root.get("physicalHealth")));
        }

        if (program.isHousing()) {
            predicates.add(cb.isTrue(root.get("housing")));
        }

        if (program.isMentalHealth()) {
            predicates.add(cb.isTrue(root.get("mentalHealth")));
        }

        // Filter by facilityId
        predicates.add(cb.equal(root.get("facilityId"), facilityId));

        // Combine all predicates into the query
        query.select(root).where(predicates.toArray(new Predicate[0])).orderBy(cb.asc(root.get("name")));

        // Create the query and get the results
        TypedQuery<Program> typedQuery = entityManager.createQuery(query);
        List<Program> results = new ArrayList<>();
        try {
            results = typedQuery.getResultList();
        } catch (Exception e) {
            log.error("Error while searching programs by facility", e);
        }

        if (log.isDebugEnabled()) {
            log.debug("searchByFacility: # results: " + results.size());
        }

        return results;
    }

    /**
     * Resets the holding tank by setting the holdingTank flag to false for all programs.
     */
    @Override
    public void resetHoldingTank() {
        List<Program> programs = this.getAllPrograms();
        for (Program p : programs) {
            if (p.getHoldingTank()) {
                p.setHoldingTank(false);
                this.saveProgram(p);
            }

        }

        if (log.isDebugEnabled()) {
            log.debug("resetHoldingTank:");
        }
    }

    /**
     * Retrieves the holding tank program.
     *
     * @return the Program object representing the holding tank program, or null if not found
     */
    @Override
    public Program getHoldingTankProgram() {
        Program result = null;

        TypedQuery<Program> query = entityManager.createQuery("SELECT p FROM Program p WHERE p.holdingTank = true", Program.class);
        List<Program> results = query.getResultList();

        if (!results.isEmpty()) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug((result != null) ? "getHoldingTankProgram: program: " + result.getId()
                    : "getHoldingTankProgram: none found");
        }

        return result;
    }

    /**
     * Checks if a program exists by its ID.
     *
     * @param programId the ID of the program to check
     * @return true if the program exists, false otherwise
     */
    @Override
    public boolean programExists(Integer programId) {
        return (getProgram(programId) != null);
    }


    /**
     * Checks if two programs are in the same facility.
     *
     * @param programId1 the ID of the first program
     * @param programId2 the ID of the second program
     * @return true if the programs are in the same facility, false otherwise
     * @throws IllegalArgumentException if either programId is null or less than or equal to 0
     */
    @Override
    public boolean isInSameFacility(Integer programId1, Integer programId2) {
        if (programId1 == null || programId1 <= 0) {
            throw new IllegalArgumentException();
        }

        if (programId2 == null || programId2 <= 0) {
            throw new IllegalArgumentException();
        }

        Program p1 = getProgram(programId1);
        Program p2 = getProgram(programId2);

        if (p1 == null || p2 == null)
            return false;

        return (p1.getFacilityId() == p2.getFacilityId());
    }

    /**
     * Retrieves a program by its site-specific field value.
     *
     * @param value the value of the site-specific field
     * @return the Program object if found, null otherwise
     */
    @Override
    public Program getProgramBySiteSpecificField(String value) {
        Program result = null;

        String queryStr = "SELECT p FROM Program p WHERE p.siteSpecificField = ?1";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, value);

        List<Program> results = query.getResultList();
        if (!results.isEmpty()) {
            result = results.get(0);
        }

        return result;
    }

    /**
     * Retrieves a program by its name.
     *
     * @param value the name of the program
     * @return the Program object if found, null otherwise
     */
    @Override
    public Program getProgramByName(String value) {
        Program result = null;

        String queryStr = "SELECT p FROM Program p WHERE p.name = ?1";
        TypedQuery<Program> query = entityManager.createQuery(queryStr, Program.class);
        query.setParameter(1, value);

        List<Program> results = query.getResultList();
        if (!results.isEmpty()) {
            result = results.get(0);
        }

        return result;
    }

    /**
     * Retrieves the IDs of programs added or updated since a specific time for a given facility.
     *
     * @param facilityId the ID of the facility
     * @param date the reference date
     * @return a list of program IDs added or updated since the specified time
     */
    @Override
    public List<Integer> getRecordsAddedAndUpdatedSinceTime(Integer facilityId, Date date) {
        String queryStr = "SELECT DISTINCT p.id FROM Program p WHERE p.facilityId = ?1 AND p.lastUpdateDate > ?2";
        TypedQuery<Integer> query = entityManager.createQuery(queryStr, Integer.class);
        query.setParameter(1, facilityId);
        query.setParameter(2, date);

        return query.getResultList();
    }

    /**
     * Retrieves the IDs of programs associated with a specific facility.
     *
     * @param facilityId the ID of the facility
     * @return a list of program IDs associated with the specified facility
     */
    @Override
    public List<Integer> getRecordsByFacilityId(Integer facilityId) {
        String queryStr = "SELECT DISTINCT p.id FROM Program p WHERE p.facilityId = ?1";
        TypedQuery<Integer> query = entityManager.createQuery(queryStr, Integer.class);
        query.setParameter(1, facilityId);

        return query.getResultList();
    }

    /**
     * Retrieves the providers numbers of providers added or updated since a specific time.
     *
     * @param date the reference date
     * @return a list of providers numbers added or updated since the specified time
     */
    @Override
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date) {
        String queryStr = "SELECT DISTINCT p.providerNo FROM Provider p WHERE p.lastUpdateDate > ?1";
        TypedQuery<String> query = entityManager.createQuery(queryStr, String.class);
        query.setParameter(1, date);

        return query.getResultList();
    }
}
