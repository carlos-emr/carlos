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
package io.github.carlos_emr.carlos.PMmodule.service;

import io.github.carlos_emr.carlos.PMmodule.dao.*;
import io.github.carlos_emr.carlos.PMmodule.model.*;
import io.github.carlos_emr.carlos.PMmodule.dao.*;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.LabelValueBean;

import java.util.List;

public interface ProgramManager {

    boolean getEnabled();

    void setEnabled(boolean enabled);

    ProgramSignatureDao getProgramSignatureDao();

    void setProgramSignatureDao(ProgramSignatureDao programSignatureDao);

    void setProgramDao(ProgramDao dao);

    void setProgramProviderDAO(ProgramProviderDAO dao);

    void setProgramFunctionalUserDAO(ProgramFunctionalUserDAO dao);

    void setProgramTeamDAO(ProgramTeamDAO dao);

    void setProgramAccessDAO(ProgramAccessDAO dao);

    void setAdmissionDao(AdmissionDao dao);

    void setDefaultRoleAccessDAO(DefaultRoleAccessDAO dao);

    void setProgramClientStatusDAO(ProgramClientStatusDAO dao);

    Program getProgram(String programId);

    Program getProgram(Integer programId);

    Program getProgram(Long programId);

    List<Program> getActiveProgramByFacility(String providerNo, Integer facilityId);

    String getProgramName(String programId);

    Integer getProgramIdByProgramName(String programName);

    List<Program> getAllPrograms();

    List<Program> getAllPrograms(String programStatus, String type, int facilityId);

    List<Program> getCommunityPrograms(Integer facilityId);

    List<Program> getPrograms(Integer facilityId);

    List<Program> getPrograms();


    List<Program> getServicePrograms();

    Program[] getExternalPrograms();


    boolean isServiceProgram(String programId);

    boolean isCommunityProgram(String programId);

    void saveProgram(Program program);

    void removeProgram(String programId);

    List<ProgramProvider> getProgramProviders(String programId);

    List<ProgramProvider> getProgramProvidersByProvider(String providerNo);

    ProgramProvider getProgramProvider(String id);

    ProgramProvider getProgramProvider(String providerNo, String programId);

    void saveProgramProvider(ProgramProvider pp);

    void deleteProgramProvider(String id);

    void deleteProgramProviderByProgramId(Long programId);

    List<FunctionalUserType> getFunctionalUserTypes();

    FunctionalUserType getFunctionalUserType(String id);

    void saveFunctionalUserType(FunctionalUserType fut);

    void deleteFunctionalUserType(String id);

    List<FunctionalUserType> getFunctionalUsers(String programId);

    ProgramFunctionalUser getFunctionalUser(String id);

    void saveFunctionalUser(ProgramFunctionalUser pfu);

    void deleteFunctionalUser(String id);

    Long getFunctionalUserByUserType(Long programId, Long userTypeId);

    List<ProgramTeam> getProgramTeams(String programId);

    ProgramTeam getProgramTeam(String id);

    void saveProgramTeam(ProgramTeam team);

    void deleteProgramTeam(String id);

    boolean teamNameExists(Integer programId, String teamName);

    List<ProgramAccess> getProgramAccesses(String programId);

    ProgramAccess getProgramAccess(String id);

    void saveProgramAccess(ProgramAccess pa);

    void deleteProgramAccess(String id);

    List<AccessType> getAccessTypes();

    AccessType getAccessType(Long id);

    List<ProgramProvider> getAllProvidersInTeam(Integer programId, Integer teamId);

    List<Admission> getAllClientsInTeam(Integer programId, Integer teamId);

    List<Program> search(Program criteria);

    List<Program> searchByFacility(Program criteria, Integer facilityId);

    Program getHoldingTankProgram();

    ProgramAccess getProgramAccess(String programId, String accessTypeId);

    List<Program> getProgramDomain(String providerNo);

    List<Program> getActiveProgramDomain(String providerNo);

    List<Program> getProgramDomainInCurrentFacilityForCurrentProvider(LoggedInInfo loggedInInfo, boolean activeOnly);

    Program[] getCommunityPrograms();

    List<LabelValueBean> getProgramBeans(String providerNo);

    List<DefaultRoleAccess> getDefaultRoleAccesses();

    DefaultRoleAccess getDefaultRoleAccess(String id);

    void saveDefaultRoleAccess(DefaultRoleAccess dra);

    void deleteDefaultRoleAccess(String id);

    DefaultRoleAccess findDefaultRoleAccess(long roleId, long accessTypeId);

    List<ProgramClientStatus> getProgramClientStatuses(Integer programId);

    void saveProgramClientStatus(ProgramClientStatus status);

    void deleteProgramClientStatus(String id);

    boolean clientStatusNameExists(Integer programId, String statusName);

    List<Admission> getAllClientsInStatus(Integer programId, Integer statusId);

    ProgramClientStatus getProgramClientStatus(String statusId);

    ProgramSignature getProgramFirstSignature(Integer programId);

    List<ProgramSignature> getProgramSignatures(Integer programId);

    void saveProgramSignature(ProgramSignature programSignature);

    VacancyTemplate getVacancyTemplate(Integer templateId);

    void setVacancyTemplateDao(VacancyTemplateDao vacancyTemplateDao);

    boolean hasAccessBasedOnCurrentFacility(LoggedInInfo loggedInInfo, Integer programId);

    List<Program> getAllProgramsByRole(String providerNo, int roleId);
}
