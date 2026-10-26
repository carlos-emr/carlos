/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Pins the audit row written when a provider opens a note's history through
 * {@code CaseManagementEntry?method=history} (#4426): the patient goes in the demographic slot and
 * the note text in the data slot, never the other way round.
 */
@Tag("unit")
@Tag("security")
class CaseManagementNoteHistoryAuditUnitTest extends CarlosUnitTestBase {
    private static final String NOTE_TEXT = "Jane Roe reports chest pain";

    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private CaseManagementNote note;
    private LoggedInInfo user;
    private CaseManagementManager manager;
    private SecurityInfoManager security;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.7");
        action.request = request;
        action.response = new MockHttpServletResponse();
        request.getSession().setAttribute("userrole", "doctor");
        request.setParameter("demographicNo", "101");
        request.setParameter("noteId", "501");
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);

        note = new CaseManagementNote();
        note.setId(501L);
        note.setNote(NOTE_TEXT);
        note.setDemographic_no("101");
        note.setHistory("earlier revision");
        manager = mock(CaseManagementManager.class);
        when(manager.getNote("501")).thenReturn(note);
        when(manager.getHistory("501")).thenReturn(java.util.List.of(note));
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        action.caseManagementMgr = manager;
        security = mock(SecurityInfoManager.class);
        injectDependency(action, "securityInfoManager", security);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("101"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
    }

    @Test
    void shouldAuditReadAgainstNotePatient_withNoteTextAsData() {
        String audit = note.getAuditString();

        assertThat(action.history()).isEqualTo("historyview");

        logActionMock.verify(() -> LogAction.addLog("4249", LogConst.READ, LogConst.CON_CME_NOTE, "501",
                "192.0.2.7", "101", audit));
        logActionMock.verify(() -> LogAction.addLog(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString()), never());
    }

    @Test
    void shouldRefuseHistory_whenRequestNamesAnotherPatient() {
        // The requested chart is authorized before loading any note or considering a group link.
        request.setParameter("demographicNo", "999");

        assertThat(action.history()).isEqualTo("none");
        assertThat(((MockHttpServletResponse) action.response).getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
        assertThat(action.getCaseNote_history()).isNull();
        verify(security).hasPrivilege(user, "_demographic", "r", "999");
        verify(manager, never()).getNote(anyString());
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldRefuseHistory_whenPatientContextIsAbsent() {
        request.removeParameter("demographicNo");

        assertThat(action.history()).isEqualTo("none");
        assertThat(action.response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
        verifyNoInteractions(manager, security);
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-101", "x101"})
    void shouldRefuseHistory_whenNamedPatientIsNotAPatientNumber(String named) {
        request.setParameter("demographicNo", named);

        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldServeHistory_whenRequestNamesTheOwnerWithLeadingZeros() {
        request.setParameter("demographicNo", "0101");

        assertThat(action.history()).isEqualTo("historyview");
        assertThat(request.getAttribute("history")).isEqualTo("earlier revision");
        verify(security).hasPrivilege(user, "_demographic", "r", "101");
        verify(security).hasPrivilege(user, "_casemgmt.notes", "r", "101");
    }

    @Test
    void shouldRefuseHistory_whenCanonicalOwnerIsLocked() {
        request.setParameter("demographicNo", "00101");
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);

        assertThat(action.history()).isEqualTo("none");
        assertThat(action.response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
        verifyNoInteractions(manager);
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldShowOnlyTheLinkedRevision_whenRecipientContextHasLeadingZeros() {
        request.setParameter("demographicNo", "00999");
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("999"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 999)).thenReturn(true);
        when(manager.isClientInProgramDomain("4249", "999")).thenReturn(true);
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        GroupNoteLink link = new GroupNoteLink();
        link.setNoteId(501);
        link.setDemographicNo(999);
        link.setActive(true);
        GroupNoteDao groups = createAndRegisterMock(GroupNoteDao.class);
        when(groups.findLinksByDemographic(999)).thenReturn(java.util.List.of(link));

        assertThat(action.history()).isEqualTo("showHistory");
        assertThat(action.response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("showStoredNoteHistory")).isEqualTo(false);
        @SuppressWarnings("unchecked")
        java.util.List<CaseManagementNote> revisions =
                (java.util.List<CaseManagementNote>) request.getAttribute("history");
        assertThat(revisions).hasSize(1);
        assertThat(revisions.getFirst().getId()).isEqualTo(501L);
        assertThat(revisions.getFirst().getHistory()).isNull();
        assertThat(revisions.getFirst()).isNotSameAs(note);
        assertThat(note.getHistory()).isEqualTo("earlier revision");
        verify(security).hasPrivilege(user, "_casemgmt.notes", "r", "999");
        verify(security, never()).hasPrivilege(user, "_casemgmt.notes", "r", "101");
        verify(manager).getDemoName("999");
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldRefuseHistoryWithoutAuditingClinicalRead_whenRequestNamesAnotherPatient() {
        request.setParameter("demographicNo", "999");
        note.setDemographic_no("101");
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("999"))).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 999)).thenReturn(true);
        when(manager.isClientInProgramDomain("4249", "999")).thenReturn(true);
        Facility facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        GroupNoteDao groups = createAndRegisterMock(GroupNoteDao.class);
        when(groups.findLinksByDemographic(999)).thenReturn(java.util.List.of());
        assertThat(action.history()).isEqualTo("none");
        assertThat(action.response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        verify(manager).getNote("501");
        verify(groups).findLinksByDemographic(999);
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldRefuseHistory_whenPatientIsOutsideProgramAndReferralDomain() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldServeHistory_whenActualOwnerIsReferredIntoProgramDomain() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        when(manager.isClientReferredInProgramDomain("4249", "101")).thenReturn(true);
        assertThat(action.history()).isEqualTo("historyview");
    }

    @Test
    void shouldRefuseCumulativeHistory_whenEarlierRevisionsAreHidden() {
        ChartProgramContext.remember(request, "7");
        CaseManagementNote old = new CaseManagementNote(); old.setDemographic_no("101");
        when(manager.getHistory("501")).thenReturn(java.util.List.of(old, note));
        when(manager.filterNotes(user, "4249", java.util.List.of(old, note), "7"))
                .thenReturn(java.util.List.of(note));
        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"0", "-1", "2147483648", "not-a-patient"})
    void shouldRefuseHistory_whenStoredOwnerIsInvalid(String owner) {
        note.setDemographic_no(owner);
        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        logActionMock.verifyNoInteractions();
    }

}
