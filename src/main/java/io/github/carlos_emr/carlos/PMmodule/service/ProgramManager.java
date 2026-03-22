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

import io.github.carlos_emr.carlos.PMmodule.dao.*;
import io.github.carlos_emr.carlos.PMmodule.model.*;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import java.util.List;

/**
 * Service interface for managing programs within the CARLOS EMR Program Management module.
 *
 * <p>Provides comprehensive operations for program lifecycle management including program CRUD,
 * provider assignments, team management, access control, functional user types, client status
 * tracking, program signatures, vacancy templates, and role-based access configuration.
 * Programs can be of various types including service, community, and external.</p>
 *
 * @see ProgramManagerImpl
 * @see Program
 * @see ProgramProvider
 * @see ProgramAccess
 * @since 2005
 */
public interface ProgramManager {

    /**
     * Checks whether the program management module is enabled.
     *
     * @return boolean {@code true} if program management is enabled
     */
    boolean getEnabled();

    /**
     * Sets whether the program management module is enabled.
     *
     * @param enabled boolean {@code true} to enable program management
     */
    void setEnabled(boolean enabled);

    /**
     * Returns the program signature data access object.
     *
     * @return ProgramSignatureDao the program signature DAO
     */
    ProgramSignatureDao getProgramSignatureDao();

    /**
     * Sets the program signature data access object.
     *
     * @param programSignatureDao ProgramSignatureDao the DAO to inject
     */
    void setProgramSignatureDao(ProgramSignatureDao programSignatureDao);

    /**
     * Sets the program data access object.
     *
     * @param dao ProgramDao the program DAO to inject
     */
    void setProgramDao(ProgramDao dao);

    /**
     * Sets the program provider data access object.
     *
     * @param dao ProgramProviderDAO the DAO to inject
     */
    void setProgramProviderDAO(ProgramProviderDAO dao);

    /**
     * Sets the program functional user data access object.
     *
     * @param dao ProgramFunctionalUserDAO the DAO to inject
     */
    void setProgramFunctionalUserDAO(ProgramFunctionalUserDAO dao);

    /**
     * Sets the program team data access object.
     *
     * @param dao ProgramTeamDAO the DAO to inject
     */
    void setProgramTeamDAO(ProgramTeamDAO dao);

    /**
     * Sets the program access data access object.
     *
     * @param dao ProgramAccessDAO the DAO to inject
     */
    void setProgramAccessDAO(ProgramAccessDAO dao);

    /**
     * Sets the admission data access object.
     *
     * @param dao AdmissionDao the admission DAO to inject
     */
    void setAdmissionDao(AdmissionDao dao);

    /**
     * Sets the default role access data access object.
     *
     * @param dao DefaultRoleAccessDAO the DAO to inject
     */
    void setDefaultRoleAccessDAO(DefaultRoleAccessDAO dao);

    /**
     * Sets the program client status data access object.
     *
     * @param dao ProgramClientStatusDAO the DAO to inject
     */
    void setProgramClientStatusDAO(ProgramClientStatusDAO dao);

    /**
     * Retrieves a program by its identifier (String form).
     *
     * @param programId String the program identifier
     * @return Program the program record, or {@code null} if not found
     */
    Program getProgram(String programId);

    /**
     * Retrieves a program by its identifier.
     *
     * @param programId Integer the program identifier
     * @return Program the program record, or {@code null} if not found
     */
    Program getProgram(Integer programId);

    /**
     * Retrieves a program by its identifier (Long form).
     *
     * @param programId Long the program identifier
     * @return Program the program record, or {@code null} if not found
     */
    Program getProgram(Long programId);

    /**
     * Retrieves active programs accessible by a provider within a specific facility.
     *
     * @param providerNo String the provider number
     * @param facilityId Integer the facility identifier
     * @return List&lt;Program&gt; list of active programs for the provider at the facility
     */
    List<Program> getActiveProgramByFacility(String providerNo, Integer facilityId);

    /**
     * Retrieves the name of a program by its identifier.
     *
     * @param programId String the program identifier
     * @return String the program name
     */
    String getProgramName(String programId);

    /**
     * Retrieves a program identifier by its name.
     *
     * @param programName String the program name to search for
     * @return Integer the program identifier, or {@code null} if not found
     */
    Integer getProgramIdByProgramName(String programName);

    /**
     * Retrieves all programs regardless of status or type.
     *
     * @return List&lt;Program&gt; list of all programs
     */
    List<Program> getAllPrograms();

    /**
     * Retrieves all programs filtered by status, type, and facility.
     *
     * @param programStatus String the program status filter
     * @param type String the program type filter
     * @param facilityId int the facility identifier
     * @return List&lt;Program&gt; list of matching programs
     */
    List<Program> getAllPrograms(String programStatus, String type, int facilityId);

    /**
     * Retrieves community programs, optionally filtered by facility based on system configuration.
     *
     * @param facilityId Integer the facility identifier, may be {@code null}
     * @return List&lt;Program&gt; list of community programs
     */
    List<Program> getCommunityPrograms(Integer facilityId);

    /**
     * Retrieves programs optionally filtered by facility based on system configuration.
     *
     * @param facilityId Integer the facility identifier
     * @return List&lt;Program&gt; list of programs
     */
    List<Program> getPrograms(Integer facilityId);

    /**
     * Retrieves all programs of community type.
     *
     * @return List&lt;Program&gt; list of community programs
     */
    List<Program> getPrograms();

    /**
     * Retrieves all service-type programs.
     *
     * @return List&lt;Program&gt; list of service programs
     */
    List<Program> getServicePrograms();

    /**
     * Retrieves all external-type programs as an array.
     *
     * @return Program[] array of external programs
     */
    Program[] getExternalPrograms();

    /**
     * Checks whether a program is of the service type.
     *
     * @param programId String the program identifier
     * @return boolean {@code true} if the program is a service program
     */
    boolean isServiceProgram(String programId);

    /**
     * Checks whether a program is of the community type.
     *
     * @param programId String the program identifier
     * @return boolean {@code true} if the program is a community program
     */
    boolean isCommunityProgram(String programId);

    /**
     * Persists a program record. If the program is marked as the holding tank,
     * resets the holding tank flag on all other programs first.
     *
     * @param program Program the program to save
     */
    void saveProgram(Program program);

    /**
     * Removes a program by its identifier.
     *
     * @param programId String the program identifier
     */
    void removeProgram(String programId);

    /**
     * Retrieves all providers assigned to a specific program.
     *
     * @param programId String the program identifier
     * @return List&lt;ProgramProvider&gt; list of program provider assignments
     */
    List<ProgramProvider> getProgramProviders(String programId);

    /**
     * Retrieves all program assignments for a specific provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;ProgramProvider&gt; list of program provider assignments
     */
    List<ProgramProvider> getProgramProvidersByProvider(String providerNo);

    /**
     * Retrieves a program provider assignment by its identifier.
     *
     * @param id String the assignment identifier
     * @return ProgramProvider the assignment record
     */
    ProgramProvider getProgramProvider(String id);

    /**
     * Retrieves a program provider assignment by provider and program.
     *
     * @param providerNo String the provider number
     * @param programId String the program identifier
     * @return ProgramProvider the assignment record, or {@code null} if not found
     */
    ProgramProvider getProgramProvider(String providerNo, String programId);

    /**
     * Saves a program provider assignment.
     *
     * @param pp ProgramProvider the assignment to save
     */
    void saveProgramProvider(ProgramProvider pp);

    /**
     * Deletes a program provider assignment by its identifier.
     *
     * @param id String the assignment identifier
     */
    void deleteProgramProvider(String id);

    /**
     * Deletes all provider assignments for a specific program.
     *
     * @param programId Long the program identifier
     */
    void deleteProgramProviderByProgramId(Long programId);

    /**
     * Retrieves all functional user type definitions.
     *
     * @return List&lt;FunctionalUserType&gt; list of functional user types
     */
    List<FunctionalUserType> getFunctionalUserTypes();

    /**
     * Retrieves a functional user type by its identifier.
     *
     * @param id String the functional user type identifier
     * @return FunctionalUserType the type record
     */
    FunctionalUserType getFunctionalUserType(String id);

    /**
     * Saves a functional user type definition.
     *
     * @param fut FunctionalUserType the type to save
     */
    void saveFunctionalUserType(FunctionalUserType fut);

    /**
     * Deletes a functional user type by its identifier.
     *
     * @param id String the functional user type identifier
     */
    void deleteFunctionalUserType(String id);

    /**
     * Retrieves functional users assigned to a specific program.
     *
     * @param programId String the program identifier
     * @return List&lt;FunctionalUserType&gt; list of functional users for the program
     */
    List<FunctionalUserType> getFunctionalUsers(String programId);

    /**
     * Retrieves a program functional user by its identifier.
     *
     * @param id String the functional user identifier
     * @return ProgramFunctionalUser the functional user record
     */
    ProgramFunctionalUser getFunctionalUser(String id);

    /**
     * Saves a program functional user assignment.
     *
     * @param pfu ProgramFunctionalUser the assignment to save
     */
    void saveFunctionalUser(ProgramFunctionalUser pfu);

    /**
     * Deletes a program functional user by its identifier.
     *
     * @param id String the functional user identifier
     */
    void deleteFunctionalUser(String id);

    /**
     * Retrieves the functional user for a given program and user type.
     *
     * @param programId Long the program identifier
     * @param userTypeId Long the user type identifier
     * @return Long the functional user identifier, or {@code null} if not found
     */
    Long getFunctionalUserByUserType(Long programId, Long userTypeId);

    /**
     * Retrieves all teams within a specific program.
     *
     * @param programId String the program identifier
     * @return List&lt;ProgramTeam&gt; list of program teams
     */
    List<ProgramTeam> getProgramTeams(String programId);

    /**
     * Retrieves a program team by its identifier.
     *
     * @param id String the team identifier
     * @return ProgramTeam the team record
     */
    ProgramTeam getProgramTeam(String id);

    /**
     * Saves a program team record.
     *
     * @param team ProgramTeam the team to save
     */
    void saveProgramTeam(ProgramTeam team);

    /**
     * Deletes a program team by its identifier.
     *
     * @param id String the team identifier
     */
    void deleteProgramTeam(String id);

    /**
     * Checks whether a team name already exists within a program.
     *
     * @param programId Integer the program identifier
     * @param teamName String the team name to check
     * @return boolean {@code true} if the team name exists in the program
     */
    boolean teamNameExists(Integer programId, String teamName);

    /**
     * Retrieves access control entries for a specific program.
     *
     * @param programId String the program identifier
     * @return List&lt;ProgramAccess&gt; list of program access entries
     */
    List<ProgramAccess> getProgramAccesses(String programId);

    /**
     * Retrieves a program access entry by its identifier.
     *
     * @param id String the access entry identifier
     * @return ProgramAccess the access record
     */
    ProgramAccess getProgramAccess(String id);

    /**
     * Saves a program access control entry.
     *
     * @param pa ProgramAccess the access entry to save
     */
    void saveProgramAccess(ProgramAccess pa);

    /**
     * Deletes a program access entry by its identifier.
     *
     * @param id String the access entry identifier
     */
    void deleteProgramAccess(String id);

    /**
     * Retrieves all defined access types.
     *
     * @return List&lt;AccessType&gt; list of access type definitions
     */
    List<AccessType> getAccessTypes();

    /**
     * Retrieves an access type by its identifier.
     *
     * @param id Long the access type identifier
     * @return AccessType the access type record
     */
    AccessType getAccessType(Long id);

    /**
     * Retrieves all providers assigned to a specific team within a program.
     *
     * @param programId Integer the program identifier
     * @param teamId Integer the team identifier
     * @return List&lt;ProgramProvider&gt; list of providers in the team
     */
    List<ProgramProvider> getAllProvidersInTeam(Integer programId, Integer teamId);

    /**
     * Retrieves all client admissions for a specific team within a program.
     *
     * @param programId Integer the program identifier
     * @param teamId Integer the team identifier
     * @return List&lt;Admission&gt; list of admissions in the team
     */
    List<Admission> getAllClientsInTeam(Integer programId, Integer teamId);

    /**
     * Searches for programs matching the specified criteria.
     *
     * @param criteria Program the search criteria
     * @return List&lt;Program&gt; list of matching programs
     */
    List<Program> search(Program criteria);

    /**
     * Searches for programs matching the criteria within a specific facility.
     *
     * @param criteria Program the search criteria
     * @param facilityId Integer the facility identifier
     * @return List&lt;Program&gt; list of matching programs
     */
    List<Program> searchByFacility(Program criteria, Integer facilityId);

    /**
     * Retrieves the designated holding tank program for incoming clients.
     *
     * @return Program the holding tank program
     */
    Program getHoldingTankProgram();

    /**
     * Retrieves a specific access entry for a program and access type combination.
     *
     * @param programId String the program identifier
     * @param accessTypeId String the access type identifier
     * @return ProgramAccess the access record, or {@code null} if not found
     */
    ProgramAccess getProgramAccess(String programId, String accessTypeId);

    /**
     * Retrieves the program domain (all assigned programs) for a provider.
     *
     * @param providerNo String the provider number
     * @return List&lt;Program&gt; list of programs the provider is assigned to
     */
    List<Program> getProgramDomain(String providerNo);

    /**
     * Retrieves only the active programs in a provider's domain.
     *
     * @param providerNo String the provider number
     * @return List&lt;Program&gt; list of active programs the provider is assigned to
     */
    List<Program> getActiveProgramDomain(String providerNo);

    /**
     * Retrieves programs in the current provider's domain filtered to the current facility.
     *
     * @param loggedInInfo LoggedInInfo the current session info with provider and facility context
     * @param activeOnly boolean whether to return only active programs
     * @return List&lt;Program&gt; list of programs in the provider's domain at the current facility
     */
    List<Program> getProgramDomainInCurrentFacilityForCurrentProvider(LoggedInInfo loggedInInfo, boolean activeOnly);

    /**
     * Retrieves all community-type programs as an array.
     *
     * @return Program[] array of community programs
     */
    Program[] getCommunityPrograms();

    /**
     * Retrieves community programs as label-value beans for UI dropdowns.
     *
     * @param providerNo String the provider number (currently unused in filtering)
     * @return List&lt;LabelValueBean&gt; list of program label-value pairs
     */
    List<LabelValueBean> getProgramBeans(String providerNo);

    /**
     * Retrieves all default role access configurations.
     *
     * @return List&lt;DefaultRoleAccess&gt; list of default role access records
     */
    List<DefaultRoleAccess> getDefaultRoleAccesses();

    /**
     * Retrieves a default role access configuration by its identifier.
     *
     * @param id String the default role access identifier
     * @return DefaultRoleAccess the configuration record
     */
    DefaultRoleAccess getDefaultRoleAccess(String id);

    /**
     * Saves a default role access configuration.
     *
     * @param dra DefaultRoleAccess the configuration to save
     */
    void saveDefaultRoleAccess(DefaultRoleAccess dra);

    /**
     * Deletes a default role access configuration by its identifier.
     *
     * @param id String the default role access identifier
     */
    void deleteDefaultRoleAccess(String id);

    /**
     * Finds a default role access by role and access type combination.
     *
     * @param roleId long the role identifier
     * @param accessTypeId long the access type identifier
     * @return DefaultRoleAccess the matching configuration, or {@code null} if not found
     */
    DefaultRoleAccess findDefaultRoleAccess(long roleId, long accessTypeId);

    /**
     * Retrieves client status options defined for a specific program.
     *
     * @param programId Integer the program identifier
     * @return List&lt;ProgramClientStatus&gt; list of client status definitions
     */
    List<ProgramClientStatus> getProgramClientStatuses(Integer programId);

    /**
     * Saves a program client status definition.
     *
     * @param status ProgramClientStatus the status to save
     */
    void saveProgramClientStatus(ProgramClientStatus status);

    /**
     * Deletes a program client status definition by its identifier.
     *
     * @param id String the client status identifier
     */
    void deleteProgramClientStatus(String id);

    /**
     * Checks whether a client status name already exists within a program.
     *
     * @param programId Integer the program identifier
     * @param statusName String the status name to check
     * @return boolean {@code true} if the status name already exists
     */
    boolean clientStatusNameExists(Integer programId, String statusName);

    /**
     * Retrieves all client admissions with a specific status in a program.
     *
     * @param programId Integer the program identifier
     * @param statusId Integer the client status identifier
     * @return List&lt;Admission&gt; list of admissions with the specified status
     */
    List<Admission> getAllClientsInStatus(Integer programId, Integer statusId);

    /**
     * Retrieves a program client status by its identifier.
     *
     * @param statusId String the status identifier
     * @return ProgramClientStatus the status record
     */
    ProgramClientStatus getProgramClientStatus(String statusId);

    /**
     * Retrieves the first (primary) signature for a program.
     *
     * @param programId Integer the program identifier
     * @return ProgramSignature the first signature, or {@code null} if none
     */
    ProgramSignature getProgramFirstSignature(Integer programId);

    /**
     * Retrieves all signatures for a specific program.
     *
     * @param programId Integer the program identifier
     * @return List&lt;ProgramSignature&gt; list of program signatures
     */
    List<ProgramSignature> getProgramSignatures(Integer programId);

    /**
     * Saves a program signature record.
     *
     * @param programSignature ProgramSignature the signature to save
     */
    void saveProgramSignature(ProgramSignature programSignature);

    /**
     * Retrieves a vacancy template by its identifier.
     *
     * @param templateId Integer the template identifier
     * @return VacancyTemplate the vacancy template record
     */
    VacancyTemplate getVacancyTemplate(Integer templateId);

    /**
     * Sets the vacancy template data access object.
     *
     * @param vacancyTemplateDao VacancyTemplateDao the DAO to inject
     */
    void setVacancyTemplateDao(VacancyTemplateDao vacancyTemplateDao);

    /**
     * Checks whether a provider has access to a program based on the current facility.
     *
     * @param loggedInInfo LoggedInInfo the current session info with facility context
     * @param programId Integer the program identifier to check
     * @return boolean {@code true} if the program belongs to the current facility or programId is null
     */
    boolean hasAccessBasedOnCurrentFacility(LoggedInInfo loggedInInfo, Integer programId);

    /**
     * Retrieves all programs where a provider has a specific role.
     *
     * @param providerNo String the provider number
     * @param roleId int the role identifier to filter by
     * @return List&lt;Program&gt; list of programs where the provider has the specified role
     */
    List<Program> getAllProgramsByRole(String providerNo, int roleId);
}
