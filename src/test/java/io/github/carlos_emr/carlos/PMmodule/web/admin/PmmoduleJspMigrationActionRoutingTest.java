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

import java.util.Collections;

import io.github.carlos_emr.carlos.PMmodule.dao.VacancyDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ClientRestrictionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ClientManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramQueueManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.FacilityDao;
import io.github.carlos_emr.carlos.commn.dao.FunctionalCentreDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.services.LookupManager;
import io.github.carlos_emr.carlos.services.security.RolesManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PMmodule JSP Migration Action Routing Tests")
@Tag("integration")
@Tag("pmmodule")
class PmmoduleJspMigrationActionRoutingTest extends CarlosWebTestBase {

    private ProgramManager programManager;
    private ProgramQueueManager programQueueManager;
    private AdmissionManager admissionManager;
    private ClientRestrictionManager clientRestrictionManager;
    private FacilityDao facilityDao;
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
        facilityDao = mock(FacilityDao.class);
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
        replaceSpringUtilsBean(FacilityDao.class, facilityDao);
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

    @Test
    @DisplayName("ProgramManager should route the clients tab to the concrete JSP result")
    void shouldRouteProgramManagerClientsTabToConcreteJsp() throws Exception {
        Program program = new Program();
        program.setId(17);
        program.setName("Shelter");

        when(programManager.getProgram("17")).thenReturn(program);
        when(programManager.getProgramFirstSignature(17)).thenReturn(null);

        addRequestParameter("method", "edit");
        addRequestParameter("id", "17");
        addRequestParameter("view.tab", "Clients");

        ProgramManager2Action action = new ProgramManager2Action();

        String result = executeAction(action);

        assertThat(result).isEqualTo("editClients");
        assertThat(getMockRequest().getAttribute("admissions")).isNotNull();
        verifySecurityCheck("_admin", "r");
    }

    @Test
    @DisplayName("ProgramManagerView should route the clients tab to the concrete JSP result")
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

        assertThat(result).isEqualTo("viewClients");
        assertThat(getMockRequest().getAttribute("admissions")).isNotNull();
        verifySecurityCheck("_pmm_management", "r");
    }

    @Test
    @DisplayName("ProgramManagerView should load vacancies through the live controller")
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

        assertThat(result).isEqualTo("viewVacancies");
        assertThat(getMockRequest().getAttribute("vacancies")).isNotNull();
        verifySecurityCheck("_pmm_management", "r");
    }

    @Test
    @DisplayName("FacilityManager should serve the moved edit JSP through the live action")
    void shouldRouteFacilityEditThroughLiveAction() throws Exception {
        Facility facility = new Facility("", "");
        facility.setId(5);
        facility.setOrgId(7);
        facility.setSectorId(8);

        when(facilityDao.find(5)).thenReturn(facility);

        addRequestParameter("method", "edit");
        addRequestParameter("id", "5");

        FacilityManager2Action action = new FacilityManager2Action();

        String result = executeAction(action);

        assertThat(result).isEqualTo("edit");
        assertThat(getMockRequest().getAttribute("sectorID")).isEqualTo(8);
        assertThat(getMockRequest().getAttribute("orgList")).isNotNull();
        verifySecurityCheck("_admin", "r");
    }
}
