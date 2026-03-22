/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.model.AdmissionSearchBean;
import io.github.carlos_emr.carlos.commn.model.Admission;

/**
 * DAO interface for managing patient program admissions and discharges.
 * <p>
 * Provides operations to query, create, and manage admissions across programs,
 * facilities, and teams within the CARLOS EMR case management module.
 * Supports filtering by admission status (current/discharged), facility,
 * program type (external, service, community), and date ranges.
 *
 * @since 2001
 */
public interface AdmissionDao extends AbstractDao<Admission> {

    /**
     * Retrieves discharged admissions for a patient in a specific program, for archive viewing.
     *
     * @param programId     Integer the program identifier
     * @param demographicNo Integer the patient demographic number
     * @return List of discharged {@link Admission} records ordered by ID descending
     * @throws IllegalArgumentException if programId or demographicNo is null or not positive
     */
    public List<Admission> getAdmissions_archiveView(Integer programId, Integer demographicNo);

    /**
     * Retrieves the first admission for a patient in a specific program, regardless of status.
     *
     * @param programId     Integer the program identifier
     * @param demographicNo Integer the patient demographic number
     * @return the first matching {@link Admission}, or {@code null} if not found
     * @throws IllegalArgumentException if programId or demographicNo is null or not positive
     */
    public Admission getAdmission(Integer programId, Integer demographicNo);

    /**
     * Retrieves the most recent current (active) admission for a patient in a specific program.
     *
     * @param programId     Integer the program identifier
     * @param demographicNo Integer the patient demographic number
     * @return the current {@link Admission}, or {@code null} if none exists
     * @throws IllegalArgumentException if programId or demographicNo is null or not positive
     */
    public Admission getCurrentAdmission(Integer programId, Integer demographicNo);

    /**
     * Retrieves all admissions across all programs, ordered by admission date descending.
     *
     * @return List of all {@link Admission} records
     */
    public List<Admission> getAdmissions();

    /**
     * Retrieves all admissions for a specific patient, ordered by admission date descending.
     *
     * @param demographicNo Integer the patient demographic number
     * @return List of {@link Admission} records for the patient
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getAdmissions(Integer demographicNo);

    /**
     * Retrieves all admissions for a specific patient, ordered by admission date ascending.
     *
     * @param demographicNo Integer the patient demographic number
     * @return List of {@link Admission} records for the patient in chronological order
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getAdmissionsASC(Integer demographicNo);

    /**
     * Retrieves admissions for a patient filtered by facility, ordered by admission date descending.
     *
     * @param demographicNo Integer the patient demographic number
     * @param facilityId    Integer the facility identifier
     * @return List of {@link Admission} records for the patient at the specified facility
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getAdmissionsByFacility(Integer demographicNo, Integer facilityId);

    /**
     * Retrieves current admissions for a patient in a specific program.
     *
     * @param demographicNo Integer the patient demographic number
     * @param programId     Integer the program identifier
     * @return List of current {@link Admission} records ordered by admission date descending
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getAdmissionsByProgramAndClient(Integer demographicNo, Integer programId);

    /**
     * Retrieves admissions by program that were automatically discharged within a given number of days.
     *
     * @param programId          Integer the program identifier
     * @param automaticDischarge Boolean whether the admission was automatically discharged
     * @param days               Integer the number of days in the past to search (negative value for past dates)
     * @return List of matching {@link Admission} records ordered by discharge date descending
     * @throws IllegalArgumentException if programId is null or not positive
     */
    public List<Admission> getAdmissionsByProgramId(Integer programId, Boolean automaticDischarge, Integer days);

    /**
     * Returns distinct demographic IDs of patients admitted to a program by a specific provider.
     *
     * @param programId  Integer the program identifier, or {@code null} to search across all programs
     * @param providerNo String the provider number
     * @return List of distinct patient demographic IDs
     */
    public List<Integer> getAdmittedDemographicIdByProgramAndProvider(Integer programId, String providerNo);

    /**
     * Retrieves all current (active) admissions for a patient across all programs.
     *
     * @param demographicNo Integer the patient demographic number
     * @return List of current {@link Admission} records ordered by admission date descending
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getCurrentAdmissions(Integer demographicNo);

    /**
     * Retrieves fully discharged admissions for a patient, excluding programs where
     * the patient still has an active admission.
     *
     * @param demographicNo Integer the patient demographic number
     * @return List of fully discharged {@link Admission} records
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getDischargedAdmissions(Integer demographicNo);

    /**
     * Retrieves current admissions for a patient filtered by facility.
     *
     * @param demographicNo Integer the patient demographic number
     * @param facilityId    Integer the facility identifier
     * @return List of current {@link Admission} records at the specified facility
     * @throws IllegalArgumentException if demographicNo or facilityId is null or invalid
     */
    public List<Admission> getCurrentAdmissionsByFacility(Integer demographicNo, Integer facilityId);

    /**
     * Retrieves the current admission to an external program for a patient.
     *
     * @param programDAO    ProgramDao used to determine program types
     * @param demographicNo Integer the patient demographic number
     * @return the current external program {@link Admission}, or {@code null} if none found
     * @throws IllegalArgumentException if programDAO is null or demographicNo is null or not positive
     */
    public Admission getCurrentExternalProgramAdmission(ProgramDao programDAO, Integer demographicNo);

    /**
     * Retrieves current service program admissions for a patient.
     *
     * @param programDAO    ProgramDao used to determine program types
     * @param demographicNo Integer the patient demographic number
     * @return List of current service program {@link Admission} records, or {@code null} if none found
     * @throws IllegalArgumentException if programDAO is null or demographicNo is null or not positive
     */
    public List<Admission> getCurrentServiceProgramAdmission(ProgramDao programDAO, Integer demographicNo);

    /**
     * Retrieves the current community program admission for a patient.
     *
     * @param programDAO    ProgramDao used to determine program types
     * @param demographicNo Integer the patient demographic number
     * @return the current community program {@link Admission}, or {@code null} if none found
     * @throws IllegalArgumentException if programDAO is null or demographicNo is null or not positive
     */
    public Admission getCurrentCommunityProgramAdmission(ProgramDao programDAO, Integer demographicNo);

    /**
     * Retrieves all current admissions for a specific program.
     *
     * @param programId Integer the program identifier
     * @return List of current {@link Admission} records for the program
     * @throws IllegalArgumentException if programId is null or not positive
     */
    public List<Admission> getCurrentAdmissionsByProgramId(Integer programId);

    /**
     * Retrieves an admission by its integer ID.
     *
     * @param id int the admission identifier
     * @return the {@link Admission}, or {@code null} if not found
     */
    public Admission getAdmission(int id);

    /**
     * Retrieves an admission by its Long ID.
     *
     * @param id Long the admission identifier
     * @return the {@link Admission}, or {@code null} if not found
     * @throws IllegalArgumentException if id is null
     */
    public Admission getAdmission(Long id);

    /**
     * Saves or updates an admission record, setting the last update date to now.
     *
     * @param admission the {@link Admission} to save
     * @throws IllegalArgumentException if admission is null
     */
    public void saveAdmission(Admission admission);

    /**
     * Retrieves current admissions for a program filtered by team.
     *
     * @param programId Integer the program identifier
     * @param teamId    Integer the team identifier
     * @return List of current {@link Admission} records for the team
     * @throws IllegalArgumentException if programId or teamId is null or not positive
     */
    public List<Admission> getAdmissionsInTeam(Integer programId, Integer teamId);

    /**
     * Retrieves the current temporary admission for a patient.
     *
     * @param demographicNo Integer the patient demographic number
     * @return the temporary {@link Admission}, or {@code null} if none exists
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public Admission getTemporaryAdmission(Integer demographicNo);

    /**
     * Searches for admissions using the criteria specified in the search bean.
     *
     * @param searchBean AdmissionSearchBean containing search criteria (provider, status, client, program, dates)
     * @return List of matching {@link Admission} records
     * @throws IllegalArgumentException if searchBean is null
     */
    public List search(AdmissionSearchBean searchBean);

    /**
     * Retrieves admissions that were active on a specific date for a program.
     *
     * @param programId int the program identifier
     * @param dt        Date the date to check (admissions where admission date &lt;= dt and discharge date &gt;= dt)
     * @return List of {@link Admission} records active on the given date
     */
    public List<Admission> getClientIdByProgramDate(int programId, Date dt);

    /**
     * Returns the client status ID from the most recently discharged admission
     * for a patient in a specific program.
     *
     * @param programId    Integer the program identifier
     * @param demographicId Integer the patient demographic number
     * @return Integer the client status ID, or 0 if no discharged admission found
     * @throws IllegalArgumentException if programId or demographicId is null or not positive
     */
    public Integer getLastClientStatusFromAdmissionByProgramIdAndClientId(Integer programId, Integer demographicId);

    /**
     * Retrieves admissions by program where the admission date falls within the given range.
     * The start date is inclusive and end date is exclusive.
     *
     * @param programId int the program identifier
     * @param startDate Date the inclusive start of the date range
     * @param endDate   Date the exclusive end of the date range
     * @return List of {@link Admission} records admitted during the date range
     */
    public List<Admission> getAdmissionsByProgramAndAdmittedDate(int programId, Date startDate, Date endDate);

    /**
     * Retrieves admissions that were active in a program during the given date range.
     * Includes admissions whose tenure overlaps with the specified period.
     *
     * @param programId int the program identifier
     * @param startDate Date the start of the date range
     * @param endDate   Date the end of the date range
     * @return List of {@link Admission} records that overlapped with the date range
     */
    public List<Admission> getAdmissionsByProgramAndDate(int programId, Date startDate, Date endDate);

    /**
     * Checks whether a patient was ever admitted to a specific program.
     *
     * @param programId Integer the program identifier
     * @param clientId  Integer the patient demographic number
     * @return {@code true} if the patient has any admission record in the program
     */
    public boolean wasInProgram(Integer programId, Integer clientId);

    /**
     * Retrieves active admissions for anonymous (one-time) patients in non-community programs.
     *
     * @return List of active anonymous admissions
     */
    public List getActiveAnonymousAdmissions();

    /**
     * Retrieves admissions for a patient at a facility updated after the specified date.
     *
     * @param demographicNo  Integer the patient demographic number
     * @param facilityId     Integer the facility identifier
     * @param lastUpdateDate Date the cutoff date for updates
     * @return List of {@link Admission} records updated after the given date
     * @throws IllegalArgumentException if demographicNo is null or not positive
     */
    public List<Admission> getAdmissionsByFacilitySince(Integer demographicNo, Integer facilityId, Date lastUpdateDate);

    /**
     * Retrieves demographic IDs of patients with admissions at a facility updated after the specified date.
     * Used for integrator synchronization.
     *
     * @param facilityId     Integer the facility identifier
     * @param lastUpdateDate Date the cutoff date for updates
     * @return List of patient demographic IDs
     */
    public List<Integer> getAdmissionsByFacilitySince(Integer facilityId, Date lastUpdateDate);

    /**
     * Retrieves a paginated list of admissions active on a given day for a specific program.
     *
     * @param programNo    Integer the program identifier
     * @param day          Date the day to check
     * @param startIndex   int the zero-based pagination offset
     * @param numToReturn  int the maximum number of results to return
     * @return List of {@link Admission} records active on the specified day
     */
    public List<Admission> findAdmissionsByProgramAndDate(Integer programNo, Date day, int startIndex, int numToReturn);

    /**
     * Counts admissions active on a given day for a specific program.
     *
     * @param programNo Integer the program identifier
     * @param day       Date the day to check
     * @return Integer the count of admissions active on the specified day
     */
    public Integer findAdmissionsByProgramAndDateAsCount(Integer programNo, Date day);
}
