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

import io.github.carlos_emr.carlos.PMmodule.model.Program;

/**
 * Data access interface for managing {@link Program} entities within the
 * Program Management module.
 *
 * <p>Provides comprehensive CRUD and query operations for healthcare programs,
 * including filtering by type, status, facility, gender, and various search
 * criteria. Supports the holding-tank pattern for client intake workflows.</p>
 *
 * @since 2005-01-18
 * @see Program
 * @see ProgramDaoImpl
 */
public interface ProgramDao {

    /**
     * Checks if the specified program is a service program.
     *
     * @param programId Integer the program ID to check
     * @return {@code true} if the program is a service type, {@code false} otherwise
     */
    public boolean isServiceProgram(Integer programId);

    /**
     * Checks if the specified program is a community program.
     *
     * @param programId Integer the program ID to check
     * @return {@code true} if the program is a community type, {@code false} otherwise
     */
    public boolean isCommunityProgram(Integer programId);

    /**
     * Checks if the specified program is an external program.
     *
     * @param programId Integer the program ID to check
     * @return {@code true} if the program is an external type, {@code false} otherwise
     */
    public boolean isExternalProgram(Integer programId);

    /**
     * Retrieves a program by its unique identifier.
     *
     * @param programId Integer the program ID
     * @return Program the program entity, or {@code null} if not found or ID is invalid
     */
    public Program getProgram(Integer programId);

    /**
     * Retrieves a program configured for appointment view.
     *
     * @param programId Integer the program ID
     * @return Program the program if it has exclusive view set to 'appointment', {@code null} otherwise
     */
    public Program getProgramForApptView(Integer programId);

    /**
     * Retrieves the name of a program by its ID.
     *
     * @param programId Integer the program ID
     * @return String the program name, or {@code null} if the program is not found
     */
    public String getProgramName(Integer programId);

    /**
     * Retrieves a program ID by its name.
     *
     * @param programName String the name of the program
     * @return Integer the program ID, or {@code null} if not found
     */
    public Integer getProgramIdByProgramName(String programName);

    /**
     * Retrieves all programs in the system.
     *
     * @return List&lt;Program&gt; all program records
     */
    public List<Program> findAll();

    /**
     * Retrieves all non-community programs.
     *
     * @return List&lt;Program&gt; non-community programs ordered by name
     * @deprecated 2013-12-09 misleading name; use {@link #findAll()} instead
     */
    public List<Program> getAllPrograms();

    /**
     * Retrieves all programs with active status.
     *
     * @return List&lt;Program&gt; all active programs
     */
    public List<Program> getAllActivePrograms();

    /**
     * Retrieves programs filtered by status, type, and facility.
     *
     * @param programStatus String the program status filter ("Any" for all)
     * @param type String the program type filter ("Any" for all)
     * @param facilityId int the facility ID (0 for any facility)
     * @return List&lt;Program&gt; matching programs
     * @deprecated 2013-12-09 use {@link #getProgramsByType(Integer, String, Boolean)} instead
     */
    public List<Program> getAllPrograms(String programStatus, String type, int facilityId);

    /**
     * Retrieves all non-community programs ordered by name.
     *
     * @return List&lt;Program&gt; non-community programs
     * @deprecated 2013-12-09 misleading name; use {@link #findAll()} instead
     */
    public List<Program> getPrograms();

    /**
     * Retrieves all active non-community programs.
     *
     * @return List&lt;Program&gt; active non-community programs
     * @deprecated 2013-12-09 misleading name; use {@link #findAll()} instead
     */
    public List<Program> getActivePrograms();

    /**
     * Retrieves programs associated with a facility, including those with no facility.
     *
     * @param facilityId Integer the facility ID
     * @return List&lt;Program&gt; programs ordered by name
     */
    public List<Program> getProgramsByFacilityId(Integer facilityId);

    /**
     * Retrieves programs by facility ID and functional centre ID.
     *
     * @param facilityId Integer the facility ID
     * @param functionalCentreId String the functional centre identifier
     * @return List&lt;Program&gt; matching programs
     */
    public List<Program> getProgramsByFacilityIdAndFunctionalCentreId(Integer facilityId, String functionalCentreId);

    /**
     * Retrieves non-community programs for a facility, including those with no facility.
     *
     * @param facilityId Integer the facility ID
     * @return List&lt;Program&gt; community programs ordered by name
     */
    public List<Program> getCommunityProgramsByFacilityId(Integer facilityId);

    /**
     * Retrieves programs filtered by facility, type, and active status.
     *
     * @param facilityId Integer the facility ID, or {@code null} for any facility
     * @param type String the program type
     * @param active Boolean the active status, or {@code null} for both
     * @return List&lt;Program&gt; matching programs ordered by name
     */
    public List<Program> getProgramsByType(Integer facilityId, String type, Boolean active);

    /**
     * Retrieves programs by gender type.
     *
     * @param genderType String the gender type to filter by
     * @return List&lt;Program&gt; matching programs
     */
    public List<Program> getProgramByGenderType(String genderType);

    /**
     * Saves or updates a program, setting the last update date.
     *
     * @param program Program the program entity to save
     * @throws IllegalArgumentException if program is {@code null}
     */
    public void saveProgram(Program program);

    /**
     * Removes a program by its ID.
     *
     * @param programId Integer the program ID to remove
     * @throws IllegalArgumentException if programId is {@code null} or not positive
     */
    public void removeProgram(Integer programId);

    /**
     * Searches for programs using criteria from the provided Program object,
     * including name (with SOUNDEX matching), type, gender, and health flags.
     *
     * @param program Program the search criteria
     * @return List&lt;Program&gt; matching active programs ordered by name
     * @throws IllegalArgumentException if program is {@code null}
     */
    public List<Program> search(Program program);

    /**
     * Searches for programs within a specific facility using criteria from the
     * provided Program object.
     *
     * @param program Program the search criteria
     * @param facilityId Integer the facility ID to restrict results to
     * @return List&lt;Program&gt; matching active programs ordered by name
     * @throws IllegalArgumentException if program or facilityId is {@code null}
     */
    public List<Program> searchByFacility(Program program, Integer facilityId);

    /**
     * Resets the holding tank flag for all programs to {@code false}.
     */
    public void resetHoldingTank();

    /**
     * Retrieves the program designated as the holding tank.
     *
     * @return Program the holding tank program, or {@code null} if none is set
     */
    public Program getHoldingTankProgram();

    /**
     * Checks whether a program with the given ID exists.
     *
     * @param programId Integer the program ID to check
     * @return {@code true} if the program exists, {@code false} otherwise
     */
    public boolean programExists(Integer programId);

    /**
     * Checks whether two programs belong to the same facility.
     *
     * @param programId1 Integer the first program ID
     * @param programId2 Integer the second program ID
     * @return {@code true} if both programs share the same facility ID
     * @throws IllegalArgumentException if either ID is {@code null} or not positive
     */
    public boolean isInSameFacility(Integer programId1, Integer programId2);

    /**
     * Retrieves a program by its site-specific field value.
     *
     * @param value String the site-specific field value
     * @return Program the matching program, or {@code null} if not found
     */
    public Program getProgramBySiteSpecificField(String value);

    /**
     * Retrieves a program by its exact name.
     *
     * @param value String the program name
     * @return Program the matching program, or {@code null} if not found
     */
    public Program getProgramByName(String value);

    /**
     * Retrieves IDs of programs added or updated after a given date for a specific facility.
     *
     * @param facilityId Integer the facility ID
     * @param date Date the reference date
     * @return List&lt;Integer&gt; program IDs updated since the specified date
     */
    public List<Integer> getRecordsAddedAndUpdatedSinceTime(Integer facilityId, Date date);

    /**
     * Retrieves IDs of all programs associated with a specific facility.
     *
     * @param facilityId Integer the facility ID
     * @return List&lt;Integer&gt; program IDs for the specified facility
     */
    public List<Integer> getRecordsByFacilityId(Integer facilityId);

    /**
     * Retrieves provider numbers of providers updated after a given date.
     *
     * @param date Date the reference date
     * @return List&lt;String&gt; provider numbers updated since the specified date
     */
    public List<String> getRecordsAddedAndUpdatedSinceTime(Date date);
}
