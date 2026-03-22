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
package io.github.carlos_emr.carlos.PMmodule.service;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.exception.AdmissionException;
import io.github.carlos_emr.carlos.PMmodule.exception.AlreadyAdmittedException;
import io.github.carlos_emr.carlos.PMmodule.exception.ProgramFullException;
import io.github.carlos_emr.carlos.PMmodule.exception.ServiceRestrictionException;
import io.github.carlos_emr.carlos.PMmodule.model.AdmissionSearchBean;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.JointAdmission;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Service interface for managing client admissions within the CARLOS EMR Program Management module.
 *
 * <p>Provides operations for admitting clients to programs, discharging clients, querying admission
 * records, and managing temporary and community program admissions. Supports joint admissions
 * (family head with dependents) and enforces program capacity limits and service restrictions.</p>
 *
 * @see AdmissionManagerImpl
 * @see Admission
 * @see Program
 * @since 2005
 */
public interface AdmissionManager {

    /**
     * Retrieves archived admission records for a client in a specific program.
     *
     * @param programId String the program identifier
     * @param demographicNo Integer the client demographic number
     * @return List&lt;Admission&gt; list of archived admission records
     */
    List<Admission> getAdmissions_archiveView(String programId, Integer demographicNo);

    /**
     * Retrieves the admission record for a client in a specific program.
     *
     * @param programId String the program identifier
     * @param demographicNo Integer the client demographic number
     * @return Admission the admission record, or {@code null} if not found
     */
    Admission getAdmission(String programId, Integer demographicNo);

    /**
     * Retrieves the current active admission for a client in a specific program.
     *
     * @param programId String the program identifier
     * @param demographicNo Integer the client demographic number
     * @return Admission the current admission, or {@code null} if not currently admitted
     */
    Admission getCurrentAdmission(String programId, Integer demographicNo);

    /**
     * Retrieves all admissions for a client within a specific facility.
     *
     * @param demographicNo Integer the client demographic number
     * @param facilityId Integer the facility identifier
     * @return List&lt;Admission&gt; list of admissions at the specified facility
     */
    List<Admission> getAdmissionsByFacility(Integer demographicNo, Integer facilityId);

    /**
     * Retrieves all current active admissions for a client within a specific facility.
     *
     * @param demographicNo Integer the client demographic number
     * @param facilityId Integer the facility identifier
     * @return List&lt;Admission&gt; list of current admissions at the specified facility
     */
    List<Admission> getCurrentAdmissionsByFacility(Integer demographicNo, Integer facilityId);

    /**
     * Retrieves all admission records across all programs.
     *
     * @return List&lt;Admission&gt; list of all admission records
     */
    List<Admission> getAdmissions();

    /**
     * Retrieves all admission records for a specific client.
     *
     * @param demographicNo Integer the client demographic number
     * @return List&lt;Admission&gt; list of all admissions for the client
     */
    List<Admission> getAdmissions(Integer demographicNo);

    /**
     * Retrieves all current active admissions for a specific client.
     *
     * @param demographicNo Integer the client demographic number
     * @return List&lt;Admission&gt; list of current admissions for the client
     */
    List<Admission> getCurrentAdmissions(Integer demographicNo);


    /**
     * Retrieves current service program admissions for a client.
     *
     * @param demographicNo Integer the client demographic number
     * @return List&lt;Admission&gt; list of current service program admissions
     */
    List<Admission> getCurrentServiceProgramAdmission(Integer demographicNo);

    /**
     * Retrieves the current external program admission for a client.
     *
     * @param demographicNo Integer the client demographic number
     * @return Admission the current external program admission, or {@code null} if none
     */
    Admission getCurrentExternalProgramAdmission(Integer demographicNo);

    /**
     * Retrieves the current community program admission for a client.
     *
     * @param demographicNo Integer the client demographic number
     * @return Admission the current community program admission, or {@code null} if none
     */
    Admission getCurrentCommunityProgramAdmission(Integer demographicNo);

    /**
     * Retrieves all current admissions for a specific program.
     *
     * @param programId String the program identifier
     * @return List&lt;Admission&gt; list of current admissions in the program
     */
    List<Admission> getCurrentAdmissionsByProgramId(String programId);

    /**
     * Retrieves an admission record by its unique identifier.
     *
     * @param id Long the admission identifier
     * @return Admission the admission record, or {@code null} if not found
     */
    Admission getAdmission(Long id);

    /**
     * Retrieves an admission record by its unique identifier.
     *
     * @param id Integer the admission identifier
     * @return Admission the admission record, or {@code null} if not found
     */
    Admission getAdmission(Integer id);

    /**
     * Persists an admission record to the database.
     *
     * @param admission Admission the admission record to save
     */
    void saveAdmission(Admission admission);

    /**
     * Processes a client admission into a program with default settings.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes a client admission with temporary admission flag.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @param tempAdmission boolean whether this is a temporary bed admission
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes a client admission including dependents (joint admission).
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @param tempAdmission boolean whether this is a temporary bed admission
     * @param dependents List&lt;Integer&gt; demographic numbers of dependents to admit alongside
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, List<Integer> dependents) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes a client admission with an option to override service restrictions.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @param tempAdmission boolean whether this is a temporary bed admission
     * @param overrideRestriction boolean whether to bypass service restriction checks
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place and not overridden
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, boolean overrideRestriction) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes a client admission with a specific admission date.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @param admissionDate Date the date of admission
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, Date admissionDate) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes a client admission with dependents and a specific admission date.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @param tempAdmission boolean whether this is a temporary bed admission
     * @param dependents List&lt;Integer&gt; demographic numbers of dependents to admit alongside
     * @param admissionDate Date the date of admission
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, List<Integer> dependents, Date admissionDate) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes a client admission with all configurable options including restriction override and dependents.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param dischargeNotes String notes for any prior discharge
     * @param admissionNotes String notes for the admission
     * @param tempAdmission boolean whether this is a temporary bed admission
     * @param admissionDate Date the date of admission, or {@code null} for current date
     * @param overrideRestriction boolean whether to bypass service restriction checks
     * @param dependents List&lt;Integer&gt; demographic numbers of dependents to admit alongside
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AdmissionException if the admission cannot be processed
     * @throws ServiceRestrictionException if a service restriction is in place and not overridden
     */
    void processAdmission(Integer demographicNo, String providerNo, Program program, String dischargeNotes, String admissionNotes, boolean tempAdmission, Date admissionDate, boolean overrideRestriction, List<Integer> dependents) throws ProgramFullException, AdmissionException, ServiceRestrictionException;

    /**
     * Processes an initial admission for a client who has never been in the program.
     * Fails if the client is already admitted to the program.
     *
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the admitting provider number
     * @param program Program the target program
     * @param admissionNotes String notes for the admission
     * @param admissionDate Date the date of admission, or {@code null} for current date
     * @throws ProgramFullException if the program has reached its maximum capacity
     * @throws AlreadyAdmittedException if the client is already admitted to the program
     * @throws ServiceRestrictionException if a service restriction is in place for this client
     */
    void processInitialAdmission(Integer demographicNo, String providerNo, Program program, String admissionNotes, Date admissionDate) throws ProgramFullException, AlreadyAdmittedException, ServiceRestrictionException;

    /**
     * Retrieves the current temporary bed admission for a client.
     *
     * @param demographicNo Integer the client demographic number
     * @return Admission the temporary admission, or {@code null} if none exists
     */
    Admission getTemporaryAdmission(Integer demographicNo);

    /**
     * Retrieves current temporary program admissions for a client as a list.
     *
     * @param demographicNo Integer the client demographic number
     * @return List&lt;Admission&gt; list containing the temporary admission, or {@code null} if none
     */
    List<Admission> getCurrentTemporaryProgramAdmission(Integer demographicNo);

    /**
     * Checks whether a dependent client is in a different program than the family head.
     *
     * @param demographicNo Integer the dependent client demographic number
     * @param dependentList List&lt;JointAdmission&gt; list of joint admissions representing dependents
     * @return boolean {@code true} if the dependent is in a different program from the head
     */
    boolean isDependentInDifferentProgramFromHead(Integer demographicNo, List<JointAdmission> dependentList);

    /**
     * Searches for admission records matching the specified criteria.
     *
     * @param searchBean AdmissionSearchBean the search criteria
     * @return List list of matching admission records
     */
    List search(AdmissionSearchBean searchBean);

    /**
     * Processes a client discharge from a program.
     *
     * @param programId Integer the program identifier
     * @param demographicNo Integer the client demographic number
     * @param dischargeNotes String notes for the discharge
     * @param radioDischargeReason String the reason for discharge
     * @throws AdmissionException if the discharge cannot be processed
     */
    void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason) throws AdmissionException;

    /**
     * Processes a client discharge from a program with a specific discharge date.
     *
     * @param programId Integer the program identifier
     * @param demographicNo Integer the client demographic number
     * @param dischargeNotes String notes for the discharge
     * @param radioDischargeReason String the reason for discharge
     * @param dischargeDate Date the date of discharge, or {@code null} for current date
     * @throws AdmissionException if the discharge cannot be processed
     */
    void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason, Date dischargeDate) throws AdmissionException;

    /**
     * Processes a client discharge with full options including dependents and transfer tracking.
     *
     * @param programId Integer the program identifier
     * @param demographicNo Integer the client demographic number
     * @param dischargeNotes String notes for the discharge
     * @param radioDischargeReason String the reason for discharge
     * @param dischargeDate Date the date of discharge, or {@code null} for current date
     * @param dependents List&lt;Integer&gt; demographic numbers of dependents to discharge
     * @param fromTransfer boolean whether this discharge is part of a program transfer
     * @param automaticDischarge boolean whether this is an automatic system-initiated discharge
     * @throws AdmissionException if the discharge cannot be processed
     */
    void processDischarge(Integer programId, Integer demographicNo, String dischargeNotes, String radioDischargeReason, Date dischargeDate, List<Integer> dependents, boolean fromTransfer, boolean automaticDischarge) throws AdmissionException;

    /**
     * Discharges a client from their current community program and admits them to a new one.
     *
     * @param communityProgramId Integer the target community program identifier
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the provider number
     * @param notes String notes for the discharge and new admission
     * @param radioDischargeReason String the reason for discharge
     * @param dischargeDate Date the date of discharge
     * @throws AdmissionException if the discharge cannot be processed
     */
    void processDischargeToCommunity(Integer communityProgramId, Integer demographicNo, String providerNo, String notes, String radioDischargeReason, Date dischargeDate) throws AdmissionException;

    /**
     * Discharges a client and dependents from their current community program and admits them to a new one.
     *
     * @param communityProgramId Integer the target community program identifier
     * @param demographicNo Integer the client demographic number
     * @param providerNo String the provider number
     * @param notes String notes for the discharge and new admission
     * @param radioDischargeReason String the reason for discharge
     * @param dependents List&lt;Integer&gt; demographic numbers of dependents to process
     * @param dischargeDate Date the date of discharge
     * @throws AdmissionException if the discharge cannot be processed
     */
    void processDischargeToCommunity(Integer communityProgramId, Integer demographicNo, String providerNo, String notes, String radioDischargeReason, List<Integer> dependents, Date dischargeDate) throws AdmissionException;

    /**
     * Checks whether a client has any active admissions in the current facility.
     *
     * @param loggedInInfo LoggedInInfo the current session info containing facility context
     * @param demographicId int the client demographic identifier
     * @return boolean {@code true} if the client is active in the current facility
     */
    boolean isActiveInCurrentFacility(LoggedInInfo loggedInInfo, int demographicId);

    /**
     * Retrieves all active admissions for anonymous clients.
     *
     * @return List list of active anonymous admissions
     */
    List getActiveAnonymousAdmissions();

    /**
     * Checks whether a client was ever admitted to a specific program.
     *
     * @param programId Integer the program identifier
     * @param clientId Integer the client demographic number
     * @return boolean {@code true} if the client has any admission record for the program
     */
    boolean wasInProgram(Integer programId, Integer clientId);

    /**
     * Finds admissions for a program on a specific date with pagination support.
     *
     * @param loggedInInfo LoggedInInfo the current session info for audit logging
     * @param programNo Integer the program identifier
     * @param day Date the target date
     * @param startIndex int the starting index for pagination
     * @param numToReturn int the maximum number of results to return
     * @return List&lt;Admission&gt; list of admissions matching the criteria
     */
    List<Admission> findAdmissionsByProgramAndDate(LoggedInInfo loggedInInfo, Integer programNo, Date day, int startIndex, int numToReturn);

    /**
     * Counts admissions for a program on a specific date.
     *
     * @param loggedInInfo LoggedInInfo the current session info for audit logging
     * @param programNo Integer the program identifier
     * @param day Date the target date
     * @return Integer the count of admissions matching the criteria
     */
    Integer findAdmissionsByProgramAndDateAsCount(LoggedInInfo loggedInInfo, Integer programNo, Date day);
}
