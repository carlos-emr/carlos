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

package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.caisi_integrator.ws.DemographicTransfer;
import io.github.carlos_emr.carlos.commn.Gender;
import io.github.carlos_emr.carlos.commn.exception.PatientDirectiveException;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchRequest;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;

import java.util.*;

/**
 * Will provide access to demographic data, as well as closely related data such as
 * extensions (DemographicExt), merge data, archive data, etc.
 * <p>
 * Future Use: Add privacy, sec, and consent profiles
 */

public interface DemographicManager {

    public Demographic getDemographic(LoggedInInfo loggedInInfo, Integer demographicId)
            throws PatientDirectiveException;

    public Demographic getDemographic(LoggedInInfo loggedInInfo, String demographicNo);

    public Demographic getDemographicWithExt(LoggedInInfo loggedInInfo, Integer demographicId);

    public String getDemographicFormattedName(LoggedInInfo loggedInInfo, Integer demographicId);

    public String getDemographicEmail(LoggedInInfo loggedInInfo, Integer demographicId);


    public List<Demographic> searchDemographicByName(LoggedInInfo loggedInInfo, String searchString, int startIndex,
                                                     int itemsToReturn);

    public List<Demographic> getActiveDemographicAfter(LoggedInInfo loggedInInfo, Date afterDateExclusive);

    public List<DemographicExt> getDemographicExts(LoggedInInfo loggedInInfo, Integer id);

    public DemographicExt getDemographicExt(LoggedInInfo loggedInInfo, Integer demographicNo,
                                            DemographicExt.DemographicProperty key);

    public DemographicExt getDemographicExt(LoggedInInfo loggedInInfo, Integer demographicNo, String key);

    public DemographicCust getDemographicCust(LoggedInInfo loggedInInfo, Integer id);

    public void createUpdateDemographicCust(LoggedInInfo loggedInInfo, DemographicCust demoCust);

    public List<DemographicContact> getDemographicContacts(LoggedInInfo loggedInInfo, Integer id, String category);

    public List<DemographicContact> getDemographicContacts(LoggedInInfo loggedInInfo, Integer id);

    public List<Provider> getDemographicMostResponsibleProviders(LoggedInInfo loggedInInfo, int demographicNo);

    public List<Demographic> getDemographicsNameRangeByProvider(LoggedInInfo loggedInInfo, Provider provider,
                                                                String regex);

    public List<Demographic> getDemographicsByProvider(LoggedInInfo loggedInInfo, Provider provider);

    public void createDemographic(LoggedInInfo loggedInInfo, Demographic demographic, Integer admissionProgramId);

    public void updateDemographic(LoggedInInfo loggedInInfo, Demographic demographic);

    public Demographic findExactMatchToDemographic(LoggedInInfo loggedInInfo, Demographic demographic);

    public List<Demographic> findFuzzyMatchToDemographic(LoggedInInfo loggedInInfo, Demographic demographic);

    public void addDemographic(LoggedInInfo loggedInInfo, Demographic demographic);

    public void createExtension(LoggedInInfo loggedInInfo, DemographicExt ext);

    public void updateExtension(LoggedInInfo loggedInInfo, DemographicExt ext);

    public void archiveExtension(DemographicExt ext);

    public void createUpdateDemographicContact(LoggedInInfo loggedInInfo, DemographicContact demoContact);

    public void deleteDemographic(LoggedInInfo loggedInInfo, Demographic demographic);

    public void deleteExtension(LoggedInInfo loggedInInfo, DemographicExt ext);

    public void mergeDemographics(LoggedInInfo loggedInInfo, Integer parentId, List<Integer> children);

    public void unmergeDemographics(LoggedInInfo loggedInInfo, Integer parentId, List<Integer> children);

    public Long getActiveDemographicCount(LoggedInInfo loggedInInfo);

    public List<Demographic> getActiveDemographics(LoggedInInfo loggedInInfo, int offset, int limit);

    public List<DemographicMerged> getMergedDemographics(LoggedInInfo loggedInInfo, Integer parentId);


    public String getDemographicWorkPhoneAndExtension(LoggedInInfo loggedInInfo, Integer demographicNo);

    public List<Demographic> searchDemographicsByAttributes(LoggedInInfo loggedInInfo, String hin, String firstName,
                                                            String lastName, Gender gender, Calendar dateOfBirth, String city, String province, String phone,
                                                            String email, String alias, int startIndex, int itemsToReturn);

    public List<String> getPatientStatusList();

    public List<String> getRosterStatusList();

    public List<DemographicSearchResult> searchPatients(LoggedInInfo loggedInInfo,
                                                        DemographicSearchRequest searchRequest, int startIndex, int itemsToReturn);

    public int searchPatientsCount(LoggedInInfo loggedInInfo, DemographicSearchRequest searchRequest);

    public List<Integer> getAdmittedDemographicIdsByProgramAndProvider(LoggedInInfo loggedInInfo, Integer programId,
                                                                       String providerNo);


    public List<Demographic> getDemographics(LoggedInInfo loggedInInfo, List<Integer> demographicIds);

    public List<Demographic> searchDemographic(LoggedInInfo loggedInInfo, String searchStr);

    public List<Demographic> getActiveDemosByHealthCardNo(LoggedInInfo loggedInInfo, String hcn, String hcnType);

    public List<Integer> getMergedDemographicIds(LoggedInInfo loggedInInfo, Integer demographicNo);

    public List<Demographic> getDemosByChartNo(LoggedInInfo loggedInInfo, String chartNo);

    public List<Demographic> searchByHealthCard(LoggedInInfo loggedInInfo, String hin);

    public Demographic getDemographicByNamePhoneEmail(LoggedInInfo loggedInInfo, String firstName, String lastName,
                                                      String hPhone, String wPhone, String email);

    public List<Demographic> getDemographicWithLastFirstDOB(LoggedInInfo loggedInInfo, String lastname,
                                                            String firstname, String year_of_birth, String month_of_birth, String date_of_birth);

    public List<Integer> getDemographicNumbersByMidwifeNumberAndDemographicLastNameRegex(
            LoggedInInfo loggedInInfo,
            final String midwifeNumber,
            final String lastNameRegex);

    public List<Integer> getDemographicNumbersByNurseNumberAndDemographicLastNameRegex(
            LoggedInInfo loggedInInfo,
            final String nurseNumber,
            final String lastNameRegex);

    public List<Integer> getDemographicNumbersByResidentNumberAndDemographicLastNameRegex(
            LoggedInInfo loggedInInfo,
            final String residentNumber,
            final String lastNameRegex);

    public List<DemographicExt> getMultipleMidwifeForDemographicNumbersByProviderNumber(
            LoggedInInfo loggedInInfo,
            final Collection<Integer> demographicNumbers,
            final String midwifeNumber);

    public List<DemographicExt> getMultipleNurseForDemographicNumbersByProviderNumber(
            LoggedInInfo loggedInInfo,
            final Collection<Integer> demographicNumbers,
            final String nurseNumber);

    public List<DemographicExt> getMultipleResidentForDemographicNumbersByProviderNumber(
            LoggedInInfo loggedInInfo,
            final Collection<Integer> demographicNumbers,
            final String residentNumber);

    public Demographic getRemoteDemographic(LoggedInInfo loggedInInfo, int remoteFacilityId, int remoteDemographicId);

    public Demographic copyRemoteDemographic(LoggedInInfo loggedInInfo, Demographic remoteDemographic,
                                             int remoteFacilityId, int remoteDemographicId);

    public void updatePatientConsent(LoggedInInfo loggedInInfo, int demographic_no, String consentType,
                                     boolean consented);

    public List<DemographicContact> findSDMByDemographicNo(LoggedInInfo loggedInInfo, int demographicNo);

    public boolean isPatientConsented(LoggedInInfo loggedInInfo, int demographic_no, String consentType);

    public boolean linkDemographicToRemoteDemographic(LoggedInInfo loggedInInfo, int demographicNo,
                                                      int remoteFacilityId, int remoteDemographicNo);

    public List<Integer> getLinkedDemographicIds(LoggedInInfo loggedInInfo, int demographicNo, int sourceFacilityId);

    public List<DemographicTransfer> getLinkedDemographics(LoggedInInfo loggedInInfo, int demographicNo);

    void checkPrivilege(LoggedInInfo loggedInInfo, String privilege);

    void checkPrivilege(LoggedInInfo loggedInInfo, String privilege, int demographicNo);

    public List<DemographicContact> getHealthCareTeam(LoggedInInfo loggedInInfo, Integer demographicNo);

    public List<Object[]> getArchiveMeta(LoggedInInfo loggedInInfo, Integer demographicNo);

    public DemographicContact getMostResponsibleProviderFromHealthCareTeam(LoggedInInfo loggedInInfo,
                                                                           Integer demographicNo);

    public DemographicContact getHealthCareMemberbyRole(LoggedInInfo loggedInInfo, Integer demographicNo, String role);

    public DemographicContact getHealthCareMemberbyId(LoggedInInfo loggedInInfo, Integer demographicContactId);

    public List<DemographicContact> getPersonalEmergencyContacts(LoggedInInfo loggedInInfo, Integer demographicNo);

    public DemographicContact getPersonalEmergencyContactById(LoggedInInfo loggedInInfo, Integer demographicContactId);

    public List<DemographicContact> getEmergencyContacts(LoggedInInfo loggedInInfo, Integer demographicNo);

    public Provider getMRP(LoggedInInfo loggedInInfo, Integer demographicNo);

    public Provider getMRP(LoggedInInfo loggedInInfo, Demographic demographic);

    public String getNextAppointmentDate(LoggedInInfo loggedInInfo, Integer demographicNo);

    public String getNextAppointmentDate(LoggedInInfo loggedInInfo, Demographic demographic);
}
