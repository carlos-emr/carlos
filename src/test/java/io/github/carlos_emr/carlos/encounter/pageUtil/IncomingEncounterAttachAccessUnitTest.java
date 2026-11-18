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
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramQueueDao;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramAccess;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramQueue;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises the native PMM copy entry with the real reporter-role and program access policy.
 * Only persisted memberships, grants, security decisions and the final note store are isolated.
 *
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("encounter")
@DisplayName("PMM note copy respects both charts and preserves its source")
class IncomingEncounterAttachAccessUnitTest extends CarlosUnitTestBase {
    private final CaseManagementManagerImpl manager = new CaseManagementManagerImpl();
    private final Map<String, ProgramAccess> permissions = new HashMap<>();
    private final StaticWebApplicationContext context = new StaticWebApplicationContext();
    private CaseManagementNoteDAO notes;
    private OscarAppointmentDao appointments;
    private SecurityInfoManager security;
    private ProgramAccessDAO programAccess;
    private RoleProgramAccessDAO defaults;
    private EctProgramManager programs;
    private AdmissionManager admissions;
    private ProgramQueueDao referrals;
    private CarlosProperties settings;
    private LoggedInInfo login;
    private MockHttpSession session;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementNote source;
    private MockedStatic<CarlosProperties> properties;
    private MockedStatic<RoleCache> roles;
    private MockedStatic<ProgramAccessCache> access;

    @BeforeEach
    void prepareCopyAndRealPolicies() {
        createAndRegisterMock(SecroleDao.class);
        createAndRegisterMock(DefaultRoleAccessDAO.class);
        createAndRegisterMock(CaisiAccessTypeDao.class);
        programAccess = createAndRegisterMock(ProgramAccessDAO.class);
        defaults = createAndRegisterMock(RoleProgramAccessDAO.class);
        notes = createAndRegisterMock(CaseManagementNoteDAO.class);
        appointments = createAndRegisterMock(OscarAppointmentDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        ProgramProviderDAO memberships = createAndRegisterMock(ProgramProviderDAO.class);
        manager.setProgramProviderDao(memberships);
        manager.setProgramAccessDAO(programAccess);
        manager.setRoleProgramAccessDAO(defaults);
        manager.setDemographicDao(createAndRegisterMock(DemographicDao.class));
        ProgramProvider member = new ProgramProvider();
        member.setProgramId(7L);
        member.setRole(role(7, "Doctor"));
        when(memberships.getProgramProviderByProviderProgramId("999998", 7L)).thenReturn(List.of(member));
        ProgramProvider otherMember = new ProgramProvider();
        otherMember.setProgramId(8L);
        otherMember.setRole(role(8, "Nurse"));
        when(memberships.getProgramProviderByProviderProgramId("999998", 8L)).thenReturn(List.of(otherMember));
        when(memberships.getProgramProviderByProviderNo("999998")).thenReturn(List.of(member, otherMember));
        admissions = createAndRegisterMock(AdmissionManager.class);
        manager.setAdmissionManager(admissions);
        referrals = createAndRegisterMock(ProgramQueueDao.class);
        manager.setProgramQueueDao(referrals);
        roles = mockStatic(RoleCache.class);
        roles.when(() -> RoleCache.getRole(7L)).thenReturn(member.getRole());
        roles.when(() -> RoleCache.getRole(8L)).thenReturn(role(8, "Nurse"));
        access = mockStatic(ProgramAccessCache.class);
        access.when(() -> ProgramAccessCache.getAccessMap(7L)).thenReturn(permissions);
        access.when(() -> ProgramAccessCache.getAccessMap(8L)).thenReturn(Map.of());
        properties = mockStatic(CarlosProperties.class);
        settings = mock(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(settings);
        when(settings.getProperty("program_domain.show_echart", "false")).thenReturn("true");
        when(settings.getBooleanProperty("FILTER_ON_FACILITY", "true")).thenReturn(false);
        programs = mock(EctProgramManager.class);
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of(
                new LabelValueBean("Doctor program", "7"), new LabelValueBean("Other program", "8")));
        when(programs.getDefaultProgramId("999998")).thenReturn(7);
        context.getBeanFactory().registerSingleton("ectProgramManager", programs);
        context.refresh();
        login = new LoggedInInfo();
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        login.setLoggedInProvider(provider);
        session = new MockHttpSession();
        session.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);
        session.setAttribute("user", "999998");
        session.setAttribute("case_program_id", "88");
        session.setAttribute("caseManagementEntryForm202", "FAKE source draft");
        session.setAttribute("caseManagementEntryForm101", "FAKE target draft");
        session.setAttribute("otherClinicalState", "FAKE other chart state");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        when(security.hasPrivilege(eq(login), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        source = sourceNote();
        when(notes.getNote(5L)).thenReturn(source);
        request = new MockHttpServletRequest("POST", "/carlos/encounter/IncomingEncounter");
        request.setContextPath("/carlos");
        request.setSession(session);
        request.setParameter("demographicNo", "101");
        request.setParameter("selectId", "101");
        request.setParameter("noteId", "5");
        request.setParameter("PEAttach", "yes");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void closePolicyScope() {
        if (properties != null) properties.close();
        if (access != null) access.close();
        if (roles != null) roles.close();
        context.close();
    }

    @Test
    void shouldRefuseHiddenReporterRole_withoutCopyingItsTextOrChangingEitherChart() {
        source.setReporter_caisi_role("8");
        assertThat(manager.filterNotes(login, "999998", List.of(source), "7")).isEmpty();

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);

        verify(notes, never()).saveNote(any());
        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"same-role", "program-grant", "default-grant"})
    void shouldCopyAnActuallyVisibleCrossProgramNote_asANewIndependentClientNote(String grant) throws Exception {
        if (!"same-role".equals(grant)) source.setReporter_caisi_role("8");
        if ("program-grant".equals(grant)) {
            ProgramAccess allowed = new ProgramAccess();
            allowed.setAllRoles(true);
            permissions.put("read nurse notes", allowed);
        }
        if ("default-grant".equals(grant)) when(defaults.hasAccess("read nurse notes", 7L)).thenReturn(true);
        assertThat(manager.filterNotes(login, "999998", List.of(source), "7")).containsExactly(source);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        CaseManagementNote copy = saved.getValue();
        assertThat(copy).isNotSameAs(source);
        assertThat(copy.getId()).isNull();
        assertThat(copy.getUuid()).isNull();
        assertThat(copy.getDemographic_no()).isEqualTo("101");
        assertThat(copy.getAppointmentNo()).isZero();
        assertThat(copy.getIssues()).isEmpty();
        assertThat(copy.getNote()).isEqualTo("FAKE current clinical text");
        assertThat(copy.getHistory()).isEqualTo(copy.getNote());
        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/encounter/IncomingEncounter?demographicNo=101&appointmentNo=0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_eChart", "_casemgmt.notes"})
    void shouldRefuseTargetModuleWriteDenial_beforeCopyingAnything(String object) {
        when(security.hasPrivilege(login, object, "w", "101")).thenReturn(false);

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);

        verify(notes, never()).saveNote(any());
        assertSourceAndWorkspacesUnchanged();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRefuseTargetReadOnlyProgram_withOrWithoutAnExplicitTargetToken(boolean explicitContext) {
        long targetProgram = explicitContext ? 8L : 7L;
        if (explicitContext) {
            ChartProgramContext.remember(request, "8");
            request.setParameter("chartContext", ChartProgramContext.token(request));
        }
        io.github.carlos_emr.carlos.PMmodule.model.AccessType restriction =
                new io.github.carlos_emr.carlos.PMmodule.model.AccessType();
        restriction.setId(700L);
        restriction.setName("note-read-only");
        restriction.setType("access");
        ProgramAccess readOnly = new ProgramAccess();
        readOnly.setAllRoles(true);
        readOnly.setAccessType(restriction);
        when(programAccess.getAccessListByProgramId(targetProgram)).thenReturn(List.of(readOnly));
        assertThat(manager.hasAccessRight("note-read-only", "access", "999998", "101", Long.toString(targetProgram))).isTrue();

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);

        verify(notes, never()).saveNote(any());
        assertSourceAndWorkspacesUnchanged();
    }

    @Test
    void shouldRefuseTokenlessCopy_whenRememberedWritableTargetDiffersFromTheReadOnlyEntryDefault() {
        rememberAnotherTargetWindow();
        makeProgramReadOnly(7L);
        assertThat(manager.hasAccessRight("note-read-only", "access", "999998", "101", "7")).isTrue();
        assertThat(manager.hasAccessRight("note-read-only", "access", "999998", "101", "8")).isFalse();

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("Program does not allow note writes");

        verify(notes, never()).saveNote(any());
        assertSourceAndWorkspacesUnchanged();
        assertThat(ChartProgramContext.resolve(request)).isEqualTo("8");
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test
    void shouldCopyThroughTheWritableEntryDefault_whenAnotherTargetWindowRemembersAReadOnlyProgram() throws Exception {
        rememberAnotherTargetWindow();
        makeProgramReadOnly(8L);
        assertThat(manager.hasAccessRight("note-read-only", "access", "999998", "101", "8")).isTrue();
        assertThat(manager.hasAccessRight("note-read-only", "access", "999998", "101", "7")).isFalse();

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        assertThat(saved.getValue().getDemographic_no()).isEqualTo("101");
        assertSourceAndWorkspacesUnchanged();
        assertThat(ChartProgramContext.resolve(request)).isEqualTo("8");
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/encounter/IncomingEncounter?demographicNo=101&appointmentNo=0");
        verify(programs).getDefaultProgramId("999998");
    }

    private void rememberAnotherTargetWindow() {
        MockHttpServletRequest otherTarget = new MockHttpServletRequest();
        otherTarget.setSession(session);
        otherTarget.setParameter("demographicNo", "101");
        ChartProgramContext.remember(otherTarget, "8");
        request.setParameter("sourceChartContext", sourceContext("202", "7"));
        assertThat(request.getParameterValues("chartContext")).isNull();
        assertThat(ChartProgramContext.token(request)).isNull();
        assertThat(ChartProgramContext.resolve(request)).isEqualTo("8");
    }

    private void makeProgramReadOnly(long program) {
        io.github.carlos_emr.carlos.PMmodule.model.AccessType restriction =
                new io.github.carlos_emr.carlos.PMmodule.model.AccessType();
        restriction.setId(700L);
        restriction.setName("note-read-only");
        restriction.setType("access");
        ProgramAccess readOnly = new ProgramAccess();
        readOnly.setAllRoles(true);
        readOnly.setAccessType(restriction);
        when(programAccess.getAccessListByProgramId(program)).thenReturn(List.of(readOnly));
    }

    @Test
    void shouldRefuseTheUnreachableSourceWithoutReferral_whenProgramDomainRestrictionsAreEnabled() {
        prepareSourceDomain("outside");
        assertThat(manager.isClientInProgramDomain("999998", "101")).isTrue();
        assertThat(manager.isClientInProgramDomain("999998", "202")).isFalse();
        assertThat(manager.isClientReferredInProgramDomain("999998", "202")).isFalse();

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class)
                .hasMessage("Source patient program domain access denied");

        verify(notes, never()).saveNote(any());
        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"admitted", "referred", "broad-mode"})
    void shouldRetainAnAuthorizedSourceCopy_underTheExistingAdmissionReferralOrBroadModePolicy(String policy) throws Exception {
        prepareSourceDomain(policy);
        assertThat(manager.isClientInProgramDomain("999998", "202")).isEqualTo("admitted".equals(policy));
        assertThat(manager.isClientReferredInProgramDomain("999998", "202")).isEqualTo("referred".equals(policy));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        assertThat(saved.getValue().getDemographic_no()).isEqualTo("101");
        assertThat(saved.getValue().getProgram_no()).isEqualTo(source.getProgram_no());
        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/encounter/IncomingEncounter?demographicNo=101&appointmentNo=0");
    }

    private void prepareSourceDomain(String policy) {
        when(settings.getProperty("program_domain.show_echart", "false"))
                .thenReturn("broad-mode".equals(policy) ? "true" : "false");
        Admission targetAdmission = new Admission();
        targetAdmission.setClientId(101);
        targetAdmission.setProgramId(7);
        when(admissions.getAdmissions(101)).thenReturn(List.of(targetAdmission));
        Admission sourceAdmission = new Admission();
        sourceAdmission.setClientId(202);
        sourceAdmission.setProgramId("admitted".equals(policy) ? 7 : 31);
        when(admissions.getAdmissions(202)).thenReturn(List.of(sourceAdmission));
        if ("referred".equals(policy)) {
            ProgramQueue referral = new ProgramQueue();
            referral.setProgramId(7L);
            when(referrals.getActiveProgramQueue(7L, 202L)).thenReturn(referral);
        }
        request.setParameter("sourceChartContext", sourceContext("202", "7"));
        assertThat(manager.filterNotes(login, "999998", List.of(source), "7")).containsExactly(source);
    }

    @Test
    void shouldPreserveTheSourceAndDrafts_whenTheTargetStoreFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("FAKE storage failure")).when(notes).saveNote(any());

        assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class)
                .hasMessage("FAKE storage failure");

        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"forged", "wrong-patient", "conflicting", "blank"})
    void shouldRefuseAnInvalidSourceContext_beforeCopyingTheNote(String invalid) {
        String valid = sourceContext("202", "7");
        switch (invalid) {
            case "forged" -> request.setParameter("sourceChartContext", valid.substring(0, valid.length() - 1) + "!");
            case "wrong-patient" -> request.setParameter("sourceChartContext", sourceContext("303", "7"));
            case "conflicting" -> request.setParameter("sourceChartContext", valid, sourceContext("202", "8"));
            default -> request.setParameter("sourceChartContext", "");
        }

        assertThatThrownBy(this::execute).isInstanceOf(SecurityException.class);

        verify(notes, never()).saveNote(any());
        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void shouldUseTheOriginatingNondefaultSourceProgram_whenOnlyThatProgramCanReadTheReporter() throws Exception {
        source.setReporter_caisi_role("8");
        request.setParameter("sourceChartContext", sourceContext("202", "8"));
        assertThat(manager.filterNotes(login, "999998", List.of(source), "7")).isEmpty();
        assertThat(manager.filterNotes(login, "999998", List.of(source), "8")).containsExactly(source);

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        assertThat(saved.getValue().getDemographic_no()).isEqualTo("101");
        assertSourceAndWorkspacesUnchanged();
    }

    @Test
    void shouldKeepSourceAndTargetProgramsIndependent_whenBothPatientsSupplyTheirOwnTokens() throws Exception {
        request.setParameter("sourceChartContext", sourceContext("202", "7"));
        ChartProgramContext.remember(request, "8");
        request.setParameter("chartContext", ChartProgramContext.token(request));
        assertThat(manager.filterNotes(login, "999998", List.of(source), "7")).containsExactly(source);
        assertThat(manager.filterNotes(login, "999998", List.of(source), "8")).isEmpty();

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        assertSourceAndWorkspacesUnchanged();
        assertThat(response.getRedirectedUrl()).contains("&chartContext=" + ChartProgramContext.token(request))
                .doesNotContain("sourceChartContext");
    }

    @Test
    void shouldPreserveGenuineNoProgramLegacyCopy_withoutAdoptingAnotherChartsProgram() throws Exception {
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of());
        source.setReporter_caisi_role("8");

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        assertSourceAndWorkspacesUnchanged();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "0"})
    void shouldRetainAnAlreadyIssuedLegacySourceContext_withoutFallingBackToTheDefault(String legacyProgram) throws Exception {
        when(programs.getProgramBeans("999998", null)).thenReturn(List.of());
        source.setReporter_caisi_role("8");
        request.setParameter("sourceChartContext", sourceContext("202", "null".equals(legacyProgram) ? null : "0"));

        assertThat(execute()).isEqualTo(ActionSupport.NONE);

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNote(saved.capture());
        assertThat(saved.getValue()).isNotSameAs(source);
        verify(programs, org.mockito.Mockito.times(1)).getProgramBeans("999998", null);
        assertSourceAndWorkspacesUnchanged();
    }

    private String sourceContext(String patient, String program) {
        MockHttpServletRequest origin = new MockHttpServletRequest();
        origin.setSession(session);
        origin.setParameter("demographicNo", patient);
        ChartProgramContext.remember(origin, program);
        return ChartProgramContext.token(origin);
    }

    private String execute() throws Exception {
        try (var servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            return new EctIncomingEncounter2Action(notes, manager, security, appointments).execute();
        }
    }

    private void assertSourceAndWorkspacesUnchanged() {
        assertThat(source.getId()).isEqualTo(5L);
        assertThat(source.getUuid()).isEqualTo("FAKE-source-note-uuid");
        assertThat(source.getDemographic_no()).isEqualTo("202");
        assertThat(source.getProgram_no()).isEqualTo("19");
        assertThat(source.getAppointmentNo()).isEqualTo(9002);
        assertThat(source.getIssues()).hasSize(1);
        assertThat(source.getNote()).isEqualTo("FAKE current clinical text");
        assertThat(source.getHistory()).isEqualTo("FAKE private earlier text");
        assertThat(session.getAttribute("case_program_id")).isEqualTo("88");
        assertThat(session.getAttribute("caseManagementEntryForm202")).isEqualTo("FAKE source draft");
        assertThat(session.getAttribute("caseManagementEntryForm101")).isEqualTo("FAKE target draft");
        assertThat(session.getAttribute("otherClinicalState")).isEqualTo("FAKE other chart state");
        verifyNoInteractions(appointments);
    }

    private static CaseManagementNote sourceNote() {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(5L);
        note.setUuid("FAKE-source-note-uuid");
        note.setDemographic_no("202");
        note.setProgram_no("19");
        note.setReporter_caisi_role("7");
        note.setAppointmentNo(9002);
        note.setNote("FAKE current clinical text");
        note.setHistory("FAKE private earlier text");
        note.setIssues(Set.of(new CaseManagementIssue()));
        return note;
    }

    private static Secrole role(long id, String name) {
        Secrole role = new Secrole();
        role.setId(id);
        role.setName(name);
        return role;
    }
}
