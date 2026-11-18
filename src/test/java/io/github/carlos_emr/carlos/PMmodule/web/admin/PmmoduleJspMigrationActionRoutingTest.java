/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.PMmodule.web.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ClientRestrictionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramQueueManager;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.PMmodule.service.VacancyTemplateManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.FunctionalCentreDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.services.LookupManager;
import io.github.carlos_emr.carlos.services.security.RolesManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("PMmodule JSP Migration Action Routing Tests")
@Tag("integration")
@Tag("pmmodule")
class PmmoduleJspMigrationActionRoutingTest extends CarlosWebTestBase {

    private ProgramManager programManager;
    private ProgramQueueManager programQueueManager;
    private AdmissionManager admissionManager;
    private ClientRestrictionManager clientRestrictionManager;
    private ProviderManager providerManager;
    private VacancyTemplateManager vacancyTemplateManager;
    private FacilityDao facilityDao;
    private AdmissionDao admissionDao;
    private DemographicDao demographicDao;
    private FunctionalCentreDao functionalCentreDao;
    private RolesManager rolesManager;
    private CaseManagementManager caseManagementManager;
    private ClientManager clientManager;
    private VacancyDao vacancyDao;
    private LookupManager lookupManager;
    private EFormDao eFormDao;

    @BeforeEach
    void setUpPmmoduleBeans() {
        programManager = mock(ProgramManager.class);
        programQueueManager = mock(ProgramQueueManager.class);
        admissionManager = mock(AdmissionManager.class);
        clientRestrictionManager = mock(ClientRestrictionManager.class);
        providerManager = mock(ProviderManager.class);
        vacancyTemplateManager = mock(VacancyTemplateManager.class);
        facilityDao = mock(FacilityDao.class);
        admissionDao = mock(AdmissionDao.class);
        demographicDao = mock(DemographicDao.class);
        functionalCentreDao = mock(FunctionalCentreDao.class);
        rolesManager = mock(RolesManager.class);
        caseManagementManager = mock(CaseManagementManager.class);
        clientManager = mock(ClientManager.class);
        vacancyDao = mock(VacancyDao.class);
        lookupManager = mock(LookupManager.class);
        eFormDao = mock(EFormDao.class);

        replaceSpringUtilsBean(ProgramManager.class, programManager);
        replaceSpringUtilsBean(ProgramQueueManager.class, programQueueManager);
        replaceSpringUtilsBean(AdmissionManager.class, admissionManager);
        replaceSpringUtilsBean(ClientRestrictionManager.class, clientRestrictionManager);
        replaceSpringUtilsBean(ProviderManager.class, providerManager);
        replaceSpringUtilsBean(VacancyTemplateManager.class, vacancyTemplateManager);
        replaceSpringUtilsBean(FacilityDao.class, facilityDao);
        replaceSpringUtilsBean(AdmissionDao.class, admissionDao);
        replaceSpringUtilsBean(DemographicDao.class, demographicDao);
        replaceSpringUtilsBean(FunctionalCentreDao.class, functionalCentreDao);
        replaceSpringUtilsBean(RolesManager.class, rolesManager);
        replaceSpringUtilsBean(CaseManagementManager.class, caseManagementManager);
        replaceSpringUtilsBean(ClientManager.class, clientManager);
        replaceSpringUtilsBean(VacancyDao.class, vacancyDao);
        replaceSpringUtilsBean(LookupManager.class, lookupManager);
        replaceSpringUtilsBean(EFormDao.class, eFormDao);

        when(functionalCentreDao.findAll()).thenReturn(Collections.emptyList());
        when(clientRestrictionManager.getActiveRestrictionsForProgram(anyInt(), any())).thenReturn(Collections.emptyList());
        when(clientRestrictionManager.getDisabledRestrictionsForProgram(anyInt(), any())).thenReturn(Collections.emptyList());
        when(programManager.getProgramProviders(anyString())).thenReturn(Collections.emptyList());
        when(programManager.getFunctionalUsers(anyString())).thenReturn(Collections.emptyList());
        when(programManager.getProgramTeams(anyString())).thenReturn(Collections.emptyList());
        when(programManager.getProgramClientStatuses(anyInt())).thenReturn(Collections.emptyList());
        when(programManager.getProgramAccesses(anyString())).thenReturn(Collections.emptyList());
        when(programManager.getFunctionalUserTypes()).thenReturn(Collections.emptyList());
        when(programManager.getAccessTypes()).thenReturn(Collections.emptyList());
        when(programManager.getServicePrograms()).thenReturn(Collections.emptyList());
        when(programManager.getCommunityPrograms()).thenReturn(new Program[0]);
        when(programManager.getAllPrograms()).thenReturn(Collections.emptyList());
        when(programManager.getAllPrograms(anyString(), anyString(), anyInt())).thenReturn(Collections.emptyList());
        when(programManager.getProgramDomain(anyString())).thenReturn(Collections.emptyList());
        when(programQueueManager.getActiveProgramQueuesByProgramId(anyLong())).thenReturn(Collections.emptyList());
        when(admissionManager.getCurrentAdmissionsByProgramId(anyString())).thenReturn(Collections.emptyList());
        when(facilityDao.findAll(true)).thenReturn(Collections.emptyList());
        when(rolesManager.getRoles()).thenReturn(Collections.emptyList());
        when(caseManagementManager.hasAccessRight(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        when(programManager.getEnabled()).thenReturn(Boolean.TRUE);
        when(programManager.getProgramProvider(anyString(), anyString())).thenReturn(null);
        when(vacancyDao.getVacanciesByWlProgramId(anyInt())).thenReturn(Collections.emptyList());
        when(lookupManager.LoadCodeList(anyString(), anyBoolean(), any(), any())).thenReturn(Collections.emptyList());
        when(eFormDao.getEfromInGroupByGroupName(anyString())).thenReturn(Collections.emptyList());

        setSessionAttribute("user", "999998");
    }

    private ProgramManager2Action newProgramManagerAction() {
        ProgramManager2Action action = new ProgramManager2Action() {
            @Override
            public String getText(String key, String defaultValue) {
                return key;
            }
        };
        action.setProgramManager(programManager);
        action.setProgramQueueManager(programQueueManager);
        action.setAdmissionManager(admissionManager);
        action.setClientRestrictionManager(clientRestrictionManager);
        action.setProviderManager(providerManager);
        action.setVacancyTemplateManager(vacancyTemplateManager);
        action.setFacilityDao(facilityDao);
        action.setFunctionalCentreDao(functionalCentreDao);
        action.setRolesManager(rolesManager);
        return action;
    }

    private FacilityManager2Action newFacilityManagerAction() {
        // These dispatch tests construct actions without Struts' localization container.
        return new FacilityManager2Action() {
            @Override
            public String getText(String key, String defaultValue) {
                return defaultValue;
            }
        };
    }

    @Test
    @DisplayName("ProgramManager should keep the clients tab inside the parent edit result")
    void shouldRouteProgramManagerClientsTabToConcreteJsp() throws Exception {
        Program program = new Program();
        program.setId(17);
        program.setName("Shelter");

        when(programManager.getProgram("17")).thenReturn(program);
        when(programManager.getProgramFirstSignature(17)).thenReturn(null);

        addRequestParameter("method", "edit");
        addRequestParameter("id", "17");
        addRequestParameter("view.tab", "Clients");

        ProgramManager2Action action = newProgramManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("edit");
        assertThat(getMockRequest().getAttribute("admissions")).isNotNull();
        verifySecurityCheck("_pmm_management", "w");
    }

    @Test
    @DisplayName("ProgramManager should keep the program list reachable through the live action")
    void shouldRouteProgramManagerListThroughLiveAction() throws Exception {
        ProgramManager2Action action = newProgramManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("list");
        assertThat(getMockRequest().getAttribute("programs")).isNotNull();
        assertThat(getMockRequest().getAttribute("facilities")).isNotNull();
        verifySecurityCheck("_pmm_management", "w");
    }

    @Test
    @DisplayName("ProgramManager should keep the general edit view reachable through the live action")
    void shouldRouteProgramManagerGeneralEditThroughLiveAction() throws Exception {
        Program program = new Program();
        program.setId(17);
        program.setName("Shelter");

        when(programManager.getProgram("17")).thenReturn(program);
        when(programManager.getProgramFirstSignature(17)).thenReturn(null);

        addRequestParameter("method", "edit");
        addRequestParameter("id", "17");

        ProgramManager2Action action = newProgramManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("edit");
        assertThat(getMockRequest().getAttribute("program")).isEqualTo(program);
        verifySecurityCheck("_pmm_management", "w");
    }

    @Test
    void shouldExposeStaffSelectionAndChoices_whenOpeningTheStaffTab() throws Exception {
        Program program = new Program();
        program.setId(17);
        when(programManager.getProgram("17")).thenReturn(program);
        List<Provider> providers = List.of(new Provider());
        when(providerManager.getActiveProviders()).thenReturn(providers);
        addRequestParameter("method", "edit");
        addRequestParameter("id", "17");
        addRequestParameter("view.tab", "Staff");

        Provider signedInProvider = new Provider("902");
        getMockRequest().getSession().setAttribute("provider", signedInProvider);
        ProgramProvider staffProvider = new ProgramProvider();
        staffProvider.setProviderNo("123");
        staffProvider.setRoleId(33L);
        ProgramManager2Action action = newProgramManagerAction();
        action.setProvider(staffProvider);

        assertThat(executeAction(action)).isEqualTo("edit");

        assertThat(getMockRequest().getAttribute("availableProviders")).isEqualTo(providers);
        assertThat(getMockRequest().getAttribute("staffProvider")).isSameAs(staffProvider);
        assertThat(getMockRequest().getSession().getAttribute("provider")).isSameAs(signedInProvider);
    }

    @Test
    void shouldSaveRestrictionSettings_whenTheFormOnlySubmitsRestrictionFields() throws Exception {
        Program program = new Program();
        program.setId(17);
        program.setName("Shelter");
        program.setMaximumServiceRestrictionDays(60);
        program.setMaxAllowed(null);
        when(programManager.getProgram(17)).thenReturn(program);
        when(programManager.getProgram("17")).thenReturn(program);
        Provider provider = new Provider();
        provider.setFirstName("FAKE");
        provider.setLastName("Provider");
        when(providerManager.getProvider("999998")).thenReturn(provider);
        Program form = new Program();
        form.setId(17);
        form.setDefaultServiceRestrictionDays(30);
        form.setMaximumServiceRestrictionDays(0);
        ProgramManager2Action action = newProgramManagerAction();
        action.setProgram(form);
        addRequestParameter("method", "save_restriction_settings");
        addRequestParameter("id", "17");
        addRequestParameter("view.tab", "Service Restrictions");

        try (var log = mockStatic(LogAction.class)) {
            assertThat(executeAction(action)).isEqualTo("edit");
        }

        assertThat(program.getMaximumServiceRestrictionDays()).isZero();
        assertThat(program.getDefaultServiceRestrictionDays()).isEqualTo(30);
        verify(programManager).saveProgram(program);
        verify(programManager).saveProgramSignature(argThat(signature -> "Shelter".equals(signature.getProgramName())));
    }

    @Test
    @DisplayName("ProgramManager should keep the vacancies tab reachable through the live action")
    void shouldRouteProgramManagerVacanciesTabThroughLiveAction() throws Exception {
        Program program = new Program();
        program.setId(17);
        program.setName("Shelter");

        when(programManager.getProgram("17")).thenReturn(program);
        when(programManager.getProgramFirstSignature(17)).thenReturn(null);

        addRequestParameter("method", "edit");
        addRequestParameter("id", "17");
        addRequestParameter("view.tab", "vacancies");

        ProgramManager2Action action = newProgramManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("edit");
        assertThat(getMockRequest().getAttribute("view.tab")).isEqualTo("vacancies");
        assertThat(getMockRequest().getAttribute("vacancyOrTemplateId")).isNull();
        verifySecurityCheck("_pmm_management", "w");
    }

    @Test
    @DisplayName("ProgramManagerView should keep the clients tab inside the parent view result")
    void shouldRouteProgramManagerViewClientsTabToConcreteJsp() throws Exception {
        Program program = new Program();
        program.setId(11);
        program.setFacilityId(3);
        program.setName("Program View");
        program.setType(Program.SERVICE_TYPE);

        Facility facility = new Facility("", "");
        facility.setId(3);
        facility.setName("Main Facility");

        when(programManager.getProgram("11")).thenReturn(program);
        when(facilityDao.find(3)).thenReturn(facility);

        addRequestParameter("id", "11");

        ProgramManagerView2Action action = new ProgramManagerView2Action();
        action.setTab("Clients");

        String result = executeAction(action);

        assertThat(result).isEqualTo("view");
        assertThat(getMockRequest().getAttribute("admissions")).isNotNull();
        verifySecurityCheck("_pmm_management", "r");
    }

    @Test
    @DisplayName("ProgramManagerView should load vacancies through the parent view result")
    void shouldRouteProgramManagerViewVacanciesTabToConcreteJsp() throws Exception {
        Program program = new Program();
        program.setId(11);
        program.setFacilityId(3);
        program.setName("Program View");

        Facility facility = new Facility("", "");
        facility.setId(3);
        facility.setName("Main Facility");

        when(programManager.getProgram("11")).thenReturn(program);
        when(facilityDao.find(3)).thenReturn(facility);

        addRequestParameter("id", "11");

        ProgramManagerView2Action action = new ProgramManagerView2Action();
        action.setTab("Vacancies");

        String result = executeAction(action);

        assertThat(result).isEqualTo("view");
        assertThat(getMockRequest().getAttribute("vacancies")).isNotNull();
        verifySecurityCheck("_pmm_management", "r");
    }

    @Test
    @DisplayName("ProgramManagerView should keep the general view reachable through the live action")
    void shouldRouteProgramManagerViewGeneralTabThroughLiveAction() throws Exception {
        Program program = new Program();
        program.setId(11);
        program.setFacilityId(3);
        program.setName("Program View");

        Facility facility = new Facility("", "");
        facility.setId(3);
        facility.setName("Main Facility");

        when(programManager.getProgram("11")).thenReturn(program);
        when(facilityDao.find(3)).thenReturn(facility);

        addRequestParameter("id", "11");

        ProgramManagerView2Action action = new ProgramManagerView2Action();

        String result = executeAction(action);

        assertThat(result).isEqualTo("view");
        assertThat(getMockRequest().getAttribute("program")).isEqualTo(program);
        verifySecurityCheck("_pmm_management", "r");
    }

    @Test
    @DisplayName("ProgramManager wrappers should keep Function User and vacancy edit fragments behind WEB-INF")
    void shouldKeepFunctionUserAndVacancyFragmentsBehindWebInf() throws IOException {
        String editWrapper = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/PMmodule/Admin/ProgramManagerForm.jsp"));
        String viewWrapper = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/PMmodule/Admin/ProgramManagerView.jsp"));

        assertThat(editWrapper).contains("Function User");
        assertThat(editWrapper).contains("view.tab=vacancies");
        assertThat(editWrapper).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramEdit/function_user.jsp");
        assertThat(editWrapper).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramEdit/vacancy_add.jsp");
        assertThat(editWrapper).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramEdit/vacancies.jsp");

        assertThat(viewWrapper).contains("Function User");
        assertThat(viewWrapper).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramView/function_user.jsp");
    }

    @Test
    @DisplayName("FacilityManager should resolve its Spring dependencies when Struts constructs the action")
    void shouldListFacilitiesWithoutManualSetterInjection() throws Exception {
        Facility facility = new Facility("FAKE facility", "FAKE contact");
        facility.setId(5);
        when(facilityDao.findAll(true)).thenReturn(List.of(facility));
        String result = executeAction(newFacilityManagerAction());
        assertThat(result).isEqualTo("list");
        assertThat(getMockRequest().getAttribute("facilities")).isEqualTo(List.of(facility));
        assertThat(getMockRequest().getAttribute("orgList")).isNotNull();
        assertThat(getMockRequest().getAttribute("sectorList")).isNotNull();
        verifySecurityCheck("_admin", "r");
    }

    @Test
    @DisplayName("FacilityManager should serve the moved edit JSP through the live action")
    void shouldRouteFacilityEditThroughLiveAction() throws Exception {
        Facility facility = new Facility("", "");
        facility.setId(5);
        facility.setOrgId(7);
        facility.setSectorId(8);

        when(facilityDao.find(any())).thenReturn(facility);

        addRequestParameter("method", "edit");
        addRequestParameter("id", "5");

        FacilityManager2Action action = newFacilityManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("edit");
        assertThat(getMockRequest().getAttribute("sectorID")).isEqualTo(8);
        assertThat(getMockRequest().getAttribute("orgList")).isNotNull();
        verifySecurityCheck("_admin", "r");
    }

    @Test
    @DisplayName("FacilityManager should keep the summary view reachable through the live action")
    void shouldRouteFacilityViewThroughLiveAction() throws Exception {
        Facility facility = new Facility("", "");
        facility.setId(5);
        facility.setName("Main Facility");

        when(facilityDao.find(any())).thenReturn(facility);
        when(programManager.getPrograms(5)).thenReturn(Collections.emptyList());

        addRequestParameter("method", "view");
        addRequestParameter("id", "5");

        FacilityManager2Action action = newFacilityManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("view");
        assertThat(getMockRequest().getAttribute("facility")).isEqualTo(facility);
        verifySecurityCheck("_admin", "r");
    }

    @Test
    @DisplayName("FacilityManager should require admin write privilege for delete")
    void shouldRequireFacilityWritePrivilegeForDelete() throws Exception {
        getMockRequest().setMethod("POST");
        Facility facility = new Facility("", "");
        facility.setId(5);

        when(facilityDao.find(any())).thenReturn(facility);

        addRequestParameter("method", "delete");
        addRequestParameter("id", "5");

        FacilityManager2Action action = newFacilityManagerAction();

        String result = executeAction(action);

        assertThat(result).isEqualTo("list");
        assertThat(facility.isDisabled()).isTrue();
        verify(facilityDao).merge(facility);
        verifySecurityCheck("_admin", "w");
    }

    @ParameterizedTest(name = "{0} must not invoke facility {1}")
    @CsvSource({
        "GET,delete", "HEAD,delete", "PUT,delete", "PATCH,delete", "DELETE,delete", "OPTIONS,delete",
        "GET,save", "HEAD,save", "PUT,save", "PATCH,save", "DELETE,save", "OPTIONS,save"
    })
    void shouldRejectNonPostFacilityMutationsBeforeDaoAccess(String verb, String method) throws Exception {
        getMockRequest().setMethod(verb);
        addRequestParameter("method", method);
        addRequestParameter("id", "5");
        Facility facility = new Facility("FAKE facility", "FAKE contact");
        facility.setId(5);
        when(facilityDao.find(any())).thenReturn(facility);
        FacilityManager2Action action = newFacilityManagerAction();
        action.setFacility(facility);

        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(405);
        assertThat(getMockResponse().getHeader("Allow")).isEqualTo("POST");
        assertThat(facility.isDisabled()).isFalse();
        verifyNoInteractions(facilityDao, admissionDao, demographicDao, programManager, lookupManager, eFormDao);
        verifySecurityCheck("_admin", "w");
    }

    @Test
    @DisplayName("FacilityManager should still dispatch an authorized POST save")
    void shouldSaveFacilityThroughPost() throws Exception {
        getMockRequest().setMethod("POST");
        addRequestParameter("method", "save");
        Facility facility = new Facility("FAKE facility", "FAKE contact");
        facility.setId(5);
        when(mockLoggedInInfo.getCurrentFacility()).thenReturn(facility);
        FacilityManager2Action action = newFacilityManagerAction();
        action.setFacility(facility);

        assertThat(executeAction(action)).isEqualTo("list");
        verify(facilityDao).merge(facility);
        verifySecurityCheck("_admin", "w");
    }

    @Test
    @DisplayName("FacilityManager should reject delete for read-only admin access")
    void shouldRejectFacilityDeleteWithoutWritePrivilege() {
        getMockRequest().setMethod("POST");
        Facility facility = new Facility("", "");
        facility.setId(5);

        when(facilityDao.find(any())).thenReturn(facility);
        denyPrivilege("_admin", "w");

        addRequestParameter("method", "delete");
        addRequestParameter("id", "5");

        FacilityManager2Action action = newFacilityManagerAction();

        assertThatThrownBy(() -> executeAction(action))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("_admin");
        assertThat(facility.isDisabled()).isFalse();
        verifySecurityCheck("_admin", "w");
    }

    @Test
    @DisplayName("PMmodule Struts results should not use dead page.pmm.admin mappings")
    void shouldUseConcretePmmoduleStrutsResults() throws IOException {
        String strutsXml = Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-pmmodule.xml"));

        assertThat(strutsXml).doesNotContain("page.pmm.admin.list");
        assertThat(strutsXml).doesNotContain("page.pmm.admin.edit");
        assertThat(strutsXml).doesNotContain("page.pmm.admin.view");
        assertThat(strutsXml).doesNotContain("page.pmm.admin.facility.view");
        assertThat(strutsXml).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramManagerList.jsp");
        assertThat(strutsXml).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramManagerForm.jsp");
        assertThat(strutsXml).contains("/WEB-INF/jsp/PMmodule/Admin/ProgramManagerView.jsp");
        assertThat(strutsXml).contains("/WEB-INF/jsp/PMmodule/Admin/Facility/ViewFacility.jsp");
    }
}
