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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.EncounterWindowDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.daos.DefaultIssueDao;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The first encounter's default issues (wl_default_issue=true) when the session has no usable
 * CAISI program, or one the login has no role in (#4838): the encounter opens with no issue
 * assigned and one warning, instead of failing with HTTP 500.
 */
@Tag("unit")
@Tag("encounter")
@DisplayName("Default encounter issues need a CAISI program the login belongs to")
class EctIncomingEncounterDefaultIssuesUnitTest extends CarlosUnitTestBase {

    private static final String PATIENT = "4242";
    private static final String PROVIDER = "999998";

    private ProgramProviderDAO programProviders;
    private ProgramAccessDAO programAccess;
    private SecroleDao roles;
    private RoleProgramAccessDAO roleAccess;
    private IssueDAO issues;
    private DefaultIssueDao defaults;
    private CaseManagementIssueDAO patientIssues;
    private CaseManagementNoteDAO notes;
    private SecurityInfoManager security;
    private EctSessionBean bean;

    @BeforeEach
    void registerBeans() {
        // The action's own fields, and what opening an encounter reads.
        notes = createAndRegisterMock(CaseManagementNoteDAO.class);
        createAndRegisterMock(CaseManagementManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(EChartDao.class);
        createAndRegisterMock(EncounterWindowDao.class);
        createAndRegisterMock(DrugDao.class);
        programProviders = createAndRegisterMock(ProgramProviderDAO.class);
        programAccess = createAndRegisterMock(ProgramAccessDAO.class);
        roles = createAndRegisterMock(SecroleDao.class);
        roleAccess = createAndRegisterMock(RoleProgramAccessDAO.class);
        issues = createAndRegisterMock(IssueDAO.class);
        defaults = createAndRegisterMock(DefaultIssueDao.class);
        patientIssues = createAndRegisterMock(CaseManagementIssueDAO.class);
        bean = new EctSessionBean();
        bean.demographicNo = PATIENT;
        bean.curProviderNo = PROVIDER;
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "abc", "0", "-3", "99999999999"})
    void shouldReportNoProgram_forAbsentBlankMalformedOrUnassignedValues(String value) {
        assertThat(EctIncomingEncounter2Action.usableProgramId(value)).isNull();
    }

    @Test
    void shouldReadTheProgram_forAPositiveNumber() {
        assertThat(EctIncomingEncounter2Action.usableProgramId(" 10034 ")).isEqualTo(10034);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0"})
    void shouldAssignNothingAndWarnOnce_whenTheSessionHasNoProgram(String sessionProgramId) {
        try (LogCapture log = LogCapture.forLogger(EctIncomingEncounter2Action.class)) {
            assertThatCode(() -> new EctIncomingEncounter2Action().assignDefaultIssues(bean, sessionProgramId))
                    .doesNotThrowAnyException();
            assertThat(log.messages()).containsExactly(
                    "Default encounter issues were not assigned: wl_default_issue is on, but this session has no CAISI program chosen");
            assertThat(String.join("\n", log.messages())).doesNotContain(PATIENT).doesNotContain(PROVIDER);
        }
        verifyNoInteractions(programProviders, defaults, patientIssues);
    }

    @Test
    void shouldAssignNothingAndWarnOnce_whenTheLoginHasNoRoleInTheProgram() {
        when(programProviders.getProgramProviderByProviderProgramId(PROVIDER, 10034L)).thenReturn(Collections.emptyList());
        try (LogCapture log = LogCapture.forLogger(EctIncomingEncounter2Action.class)) {
            assertThatCode(() -> new EctIncomingEncounter2Action().assignDefaultIssues(bean, "10034"))
                    .doesNotThrowAnyException();
            assertThat(log.messages()).containsExactly(
                    "Default encounter issues were not assigned: wl_default_issue is on, but the login has no role in the session's CAISI program");
            assertThat(String.join("\n", log.messages())).doesNotContain(PATIENT).doesNotContain(PROVIDER);
        }
        verifyNoInteractions(defaults, patientIssues);
    }

    @Test
    void shouldAssignTheDefaultsTheRoleMayWrite_whenTheLoginHasARoleInTheProgram() {
        givenADoctorWhoMayWriteNurseIssue65();
        // 65 is a nurse issue the doctor may write; 51 is not in the role's list.
        when(defaults.findAllDefaultIssueIds()).thenReturn(new String[]{"65", "51"});
        when(issues.getIssue(anyLong())).thenAnswer(call -> issue(call.getArgument(0), "nurse"));
        when(patientIssues.getIssuebyId(eq(PATIENT), anyString())).thenReturn(null);

        try (LogCapture log = LogCapture.forLogger(EctIncomingEncounter2Action.class)) {
            new EctIncomingEncounter2Action().assignDefaultIssues(bean, "10034");
            assertThat(log.messages()).isEmpty();
        }

        ArgumentCaptor<CaseManagementIssue> saved = ArgumentCaptor.forClass(CaseManagementIssue.class);
        verify(patientIssues, times(1)).saveIssue(saved.capture());
        assertThat(saved.getValue().getIssue_id()).isEqualTo(65L);
        assertThat(saved.getValue().getProgram_id()).isEqualTo(10034);
        assertThat(saved.getValue().getDemographic_no()).isEqualTo(4242);
        verify(patientIssues, never()).getIssuebyId(PATIENT, "51");
    }

    @Test
    void shouldSkipAMalformedIdAndAssignTheRest_whenTheStoredListIsNotClean() {
        givenADoctorWhoMayWriteNurseIssue65();
        // A hand-edited or imported list: a padded valid id and an entry that is not a number.
        when(defaults.findAllDefaultIssueIds()).thenReturn(new String[]{" 65 ", "abc"});
        when(issues.getIssue(anyLong())).thenAnswer(call -> issue(call.getArgument(0), "nurse"));
        when(patientIssues.getIssuebyId(eq(PATIENT), anyString())).thenReturn(null);

        try (LogCapture log = LogCapture.forLogger(EctIncomingEncounter2Action.class)) {
            assertThatCode(() -> new EctIncomingEncounter2Action().assignDefaultIssues(bean, "10034"))
                    .doesNotThrowAnyException();
            assertThat(log.messages()).containsExactly(
                    "A default encounter issue was skipped: the stored default issue list holds a non-numeric id");
        }

        ArgumentCaptor<CaseManagementIssue> saved = ArgumentCaptor.forClass(CaseManagementIssue.class);
        verify(patientIssues, times(1)).saveIssue(saved.capture());
        assertThat(saved.getValue().getIssue_id()).isEqualTo(65L);
        verify(patientIssues).getIssuebyId(PATIENT, "65");
    }

    @Test
    void shouldAssignNothingAndWarnOnce_whenThePatientNumberIsNotNumeric() {
        givenADoctorWhoMayWriteNurseIssue65();
        bean.demographicNo = "not-a-number";

        try (LogCapture log = LogCapture.forLogger(EctIncomingEncounter2Action.class)) {
            assertThatCode(() -> new EctIncomingEncounter2Action().assignDefaultIssues(bean, "10034"))
                    .doesNotThrowAnyException();
            assertThat(log.messages()).containsExactly(
                    "Default encounter issues were not assigned: the encounter has no numeric patient number");
        }
        verifyNoInteractions(defaults, patientIssues);
    }

    @Test
    void shouldOpenTheFirstEncounter_whenDefaultIssuesAreOnAndNoProgramIsChosen() throws Exception {
        // The real entry point: wl_default_issue=true, a patient with no notes (the notes mock
        // counts 0), and no CAISI program in the session. Before #4838 this threw
        // NumberFormatException, which the browser saw as HTTP 500.
        LoggedInInfo login = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER);
        login.setLoggedInProvider(provider);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", PROVIDER);
        session.setAttribute("userfirstname", "Synthetic");
        session.setAttribute("userlastname", "Provider");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        when(security.hasPrivilege(eq(login), eq("_demographic"), eq("r"), nullable(String.class))).thenReturn(true);
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        // Past the program-domain check, to the default-issue block.
        when(properties.getProperty("program_domain.show_echart", "false")).thenReturn("true");
        when(properties.getProperty("wl_default_issue", "false")).thenReturn("true");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/carlos/encounter/IncomingEncounter");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("curProviderNo", PROVIDER);
        request.setParameter("encType", "telephone");
        request.setParameter("appointmentDate", "2026-10-15");

        try (var props = mockStatic(CarlosProperties.class);
             var patients = mockStatic(RxPatientData.class);
             var servlet = mockStatic(ServletActionContext.class);
             var beans = mockConstruction(EctSessionBean.class, (constructed, context) -> {
                 when(constructed.getDemographicNo()).thenAnswer(call -> constructed.demographicNo);
                 doAnswer(call -> {
                     constructed.patientFirstName = "Synthetic";
                     constructed.patientLastName = "Patient";
                     constructed.yearOfBirth = "1980";
                     constructed.monthOfBirth = "01";
                     constructed.dateOfBirth = "02";
                     constructed.eChartId = "chart-" + constructed.demographicNo;
                     constructed.encounter = "Synthetic chart";
                     return null;
                 }).when(constructed).setUpEncounterPage(login);
             })) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            assertThat(new EctIncomingEncounter2Action().execute()).isEqualTo("success2");
        }
        verify(notes).getNotesCountByDemographicId(PATIENT);
        verifyNoInteractions(programProviders, defaults, patientIssues);
    }

    /** The login is a doctor in program 10034 whose role may write nurse issues; issue 65 is one. */
    private void givenADoctorWhoMayWriteNurseIssue65() {
        Secrole doctor = role(2L, "doctor");
        Secrole nurse = role(5L, "nurse");
        ProgramProvider membership = new ProgramProvider();
        membership.setRole(doctor);
        when(programProviders.getProgramProviderByProviderProgramId(PROVIDER, 10034L)).thenReturn(List.of(membership));
        when(programAccess.getAccessListByProgramId(10034L)).thenReturn(Collections.emptyList());
        when(roles.getRoles()).thenReturn(List.of(doctor, nurse));
        when(roleAccess.hasAccess(anyString(), anyLong())).thenReturn(false);
        when(roleAccess.hasAccess("write nurse issues", 2L)).thenReturn(true);
        when(issues.getIssueCodeListByRoles(any())).thenReturn(List.of(65L));
    }

    private static Secrole role(Long id, String name) {
        Secrole role = new Secrole();
        role.setId(id);
        role.setName(name);
        return role;
    }

    private static Issue issue(Long id, String role) {
        Issue issue = new Issue();
        issue.setId(id);
        issue.setRole(role);
        return issue;
    }
}
