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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.DefaultRoleAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManagerImpl;
import io.github.carlos_emr.carlos.PMmodule.utility.ProgramAccessCache;
import io.github.carlos_emr.carlos.PMmodule.utility.RoleCache;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.commn.dao.CaisiAccessTypeDao;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.daos.security.SecroleDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.model.security.Secrole;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Uses the real candidate, role and facility filters at the automatic Rx writer boundary.
 * @since 2026-10-25
 */
@Tag("unit")
@Tag("security")
class RxWriteToEncounterFacilityPolicyUnitTest extends CarlosUnitTestBase {
    private static final String PROVIDER = "999998";
    private static final String PATIENT = "42";
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;
    private CaseManagementManagerImpl manager;
    private CaseManagementNoteDAO noteDao;
    private CaseManagementTmpSaveDao drafts;
    private ProgramProviderDAO memberships;
    private CarlosProperties settings;
    private CaseManagementNote original;
    private MockedStatic<RoleCache> roles;
    private MockedStatic<ProgramAccessCache> access;
    private MockedStatic<CarlosProperties> properties;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;

    @BeforeEach
    void setUp() {
        createAndRegisterMock(SecroleDao.class);
        createAndRegisterMock(DefaultRoleAccessDAO.class);
        createAndRegisterMock(CaisiAccessTypeDao.class);
        createAndRegisterMock(ProgramAccessDAO.class);
        roles = mockStatic(RoleCache.class);
        access = mockStatic(ProgramAccessCache.class);
        properties = mockStatic(CarlosProperties.class);
        settings = mock(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(settings);
        when(settings.getBooleanProperty("FILTER_ON_FACILITY", "true")).thenReturn(true);

        request = new MockHttpServletRequest("POST", "/rx/WriteToEncounter");
        response = new MockHttpServletResponse();
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("expectedDemographicNo", PATIENT);
        request.setParameter("body", "exact prescription text");
        ChartProgramContext.remember(request, "7");
        var rx = new RxSessionBean();
        rx.setDemographicNo(42);
        RxSessionBeanResolver.register(request.getSession(), rx);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn(PROVIDER);
        var currentFacility = new Facility();
        currentFacility.setId(10);
        when(login.getCurrentFacility()).thenReturn(currentFacility);

        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(login), anyString(), eq("w"), nullable(String.class))).thenReturn(true);
        when(security.hasPrivilege(eq(login), eq("_rx"), eq("w"), eq(42))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(true);
        drafts = createAndRegisterMock(CaseManagementTmpSaveDao.class);
        var programs = new ProgramManagerImpl();
        var programDao = mock(ProgramDao.class);
        programs.setProgramDao(programDao);
        var chartProgram = new Program();
        chartProgram.setId(7);
        chartProgram.setFacilityId(20);
        when(programDao.getProgram(7)).thenReturn(chartProgram);
        registerMock(ProgramManager.class, programs);
        memberships = mock(ProgramProviderDAO.class);
        programs.setProgramProviderDAO(memberships);
        var role = new Secrole();
        role.setId(1L);
        role.setName("Doctor");
        var membership = new ProgramProvider();
        membership.setRole(role);
        when(memberships.getProgramProviderByProviderProgramId(PROVIDER, 7L)).thenReturn(List.of(membership));
        when(memberships.getProgramProvider(PROVIDER, 7L)).thenReturn(membership);
        roles.when(() -> RoleCache.getRole(1L)).thenReturn(role);
        access.when(() -> ProgramAccessCache.getAccessMap(7L)).thenReturn(Map.of());

        manager = spy(new CaseManagementManagerImpl());
        manager.setProgramManager(programs);
        manager.setProgramProviderDao(memberships);
        manager.setRoleProgramAccessDAO(mock(RoleProgramAccessDAO.class));
        noteDao = mock(CaseManagementNoteDAO.class);
        manager.setCaseManagementNoteDAO(noteDao);
        doReturn(false).when(manager).hasAccessRight("note-read-only", "access", PROVIDER, PATIENT, "7");
        doReturn(null).when(manager).getTmpSave(PROVIDER, PATIENT, "7");
        doAnswer(call -> call.getArgument(0)).when(manager).saveNoteRevisionSimple(any());
        doNothing().when(manager).saveNoteSimple(any());
        registerMock(CaseManagementManager.class, manager);
        original = new CaseManagementNote();
        original.setId(77L);
        original.setUuid("77777777-7777-4777-8777-777777777777");
        original.setDemographic_no(PATIENT);
        original.setProviderNo(PROVIDER);
        original.setProgram_no("7");
        original.setReporter_caisi_role("1");
        original.setNote("hidden original text");
        original.setHistory("original history");
        original.setRevision("1");
        original.setUpdate_date(new Date(0));
        when(noteDao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of(rawNote()));
        when(noteDao.getNote(77L)).thenReturn(original);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
    }

    @AfterEach
    void closeStatics() {
        if (loggedIn != null) loggedIn.close();
        if (servlet != null) servlet.close();
        if (properties != null) properties.close();
        if (access != null) access.close();
        if (roles != null) roles.close();
    }

    @Test
    void shouldCreateFreshOwnedNote_whenAutomaticCandidateIsHiddenByCurrentFacility() throws Exception {
        // The real legacy candidate selector deliberately omits facility filtering.
        assertThat(manager.getLastSaved("7", PATIENT, PROVIDER)).isSameAs(original);
        assertThat(manager.filterNotes(login, PROVIDER, List.of(original), "7")).isEmpty();

        new RxWriteToEncounter2Action().execute();

        verify(manager, never()).saveNoteRevisionSimple(any());
        assertFreshNote();
        assertOriginalUnchanged();
        verifyNoInteractions(drafts);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @Test
    void shouldReuseVisibleAutomaticNote_whenCurrentFacilityMatches() throws Exception {
        login.getCurrentFacility().setId(20);
        assertThat(manager.filterNotes(login, PROVIDER, List.of(original), "7")).containsExactly(original);

        new RxWriteToEncounter2Action().execute();

        assertAppendedRevision();
    }

    @Test
    void shouldReuseAutomaticNote_whenFacilityFilteringIsDisabled() throws Exception {
        when(settings.getBooleanProperty("FILTER_ON_FACILITY", "true")).thenReturn(false);
        assertThat(manager.filterNotes(login, PROVIDER, List.of(original), "7")).containsExactly(original);

        new RxWriteToEncounter2Action().execute();

        assertAppendedRevision();
    }

    @Test
    void shouldCreateFreshOwnedNote_whenNoUnsignedCandidateExists() throws Exception {
        when(noteDao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of());

        new RxWriteToEncounter2Action().execute();

        verify(manager, never()).saveNoteRevisionSimple(any());
        assertFreshNote();
        assertOriginalUnchanged();
    }

    @Test
    void shouldPreserveLegacyZeroProgramReuse_withoutProgramMembership() throws Exception {
        reset(memberships);
        when(memberships.getProgramProviderByProviderProgramId(anyString(), anyLong())).thenReturn(List.of());
        ChartProgramContext.remember(request, "0");
        original.setProgram_no("0");
        when(noteDao.getUnsignedRawNoteInfoMapByDemographic(PATIENT)).thenReturn(List.of(rawNote()));
        doReturn(false).when(manager).hasAccessRight("note-read-only", "access", PROVIDER, PATIENT, "0");
        doReturn(null).when(manager).getTmpSave(PROVIDER, PATIENT, "0");
        assertThat(manager.getLastSaved("0", PATIENT, PROVIDER)).isSameAs(original);
        assertThat(manager.filterNotes(login, PROVIDER, List.of(original), "0")).isEmpty();

        new RxWriteToEncounter2Action().execute();

        assertAppendedRevision();
    }

    @Test
    void shouldRejectExplicitDraftWithoutConsumingIt_whenReferencedNoteIsFacilityHidden() throws Exception {
        var draft = new CaseManagementTmpSave();
        draft.setId(9);
        draft.setNoteId(77);
        draft.setNote("captured draft");
        draft.setUpdateDate(new Date(1));
        doReturn(draft).when(manager).getTmpSave(PROVIDER, PATIENT, "7");

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        verify(manager, never()).saveNoteRevisionSimple(any());
        verify(manager, never()).saveNoteSimple(any());
        verifyNoInteractions(drafts);
        assertOriginalUnchanged();
    }

    private Map<String, Object> rawNote() {
        return Map.of("uuid", original.getUuid(), "id", original.getId(),
                "observation_date", new Date(0), "providerNo", PROVIDER,
                "program_no", original.getProgram_no(), "reporter_caisi_role", "1");
    }

    private void assertFreshNote() {
        var saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).saveNoteSimple(saved.capture());
        var fresh = saved.getValue();
        assertThat(fresh).isNotSameAs(original);
        assertThat(fresh.getId()).isNull();
        assertThat(fresh.getProviderNo()).isEqualTo(PROVIDER);
        assertThat(fresh.getDemographic_no()).isEqualTo(PATIENT);
        assertThat(fresh.getProgram_no()).isEqualTo("7");
        assertThat(fresh.isSigned()).isFalse();
        assertThat(fresh.getNote()).endsWith("exact prescription text").doesNotContain(original.getNote());
        assertThat(fresh.getHistory()).isEqualTo(fresh.getNote()).doesNotContain(original.getHistory());
    }

    private void assertAppendedRevision() {
        var saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(manager).saveNoteRevisionSimple(saved.capture());
        var revision = saved.getValue();
        assertThat(revision.getId()).isEqualTo(original.getId());
        assertThat(revision.getUuid()).isEqualTo(original.getUuid());
        assertThat(revision.getNote()).isEqualTo("hidden original text\nexact prescription text");
        assertThat(revision.getHistory()).contains("original history");
        verify(manager, never()).saveNoteSimple(any());
        assertOriginalUnchanged();
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    private void assertOriginalUnchanged() {
        assertThat(original.getNote()).isEqualTo("hidden original text");
        assertThat(original.getHistory()).isEqualTo("original history");
        assertThat(original.getUpdate_date()).isEqualTo(new Date(0));
    }
}
