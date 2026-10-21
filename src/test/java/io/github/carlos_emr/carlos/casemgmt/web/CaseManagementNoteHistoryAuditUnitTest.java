/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
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
    private CaseManagementManager manager;
    private io.github.carlos_emr.carlos.managers.SecurityInfoManager security;
    private LoggedInInfo user;

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
        note.setNote(NOTE_TEXT);
        note.setDemographic_no("101");
        note.setHistory("earlier revision");
        manager = mock(CaseManagementManager.class);
        when(manager.getNote("501")).thenReturn(note);
        action.caseManagementMgr = manager;
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        when(manager.getDemoName("101")).thenReturn("Actual owner");
        security = mock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        org.springframework.test.util.ReflectionTestUtils.setField(action, "securityInfoManager", security);
        when(security.hasPrivilege(user, "_demographic", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(user, "_casemgmt.notes", "r", "101")).thenReturn(true);
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
    void shouldAuditAgainstNotesOwnPatient_notTheRequestParameter() {
        request.setParameter("demographicNo", "999");
        note.setDemographic_no("101");
        String audit = note.getAuditString();

        action.history();

        logActionMock.verify(() -> LogAction.addLog("4249", LogConst.READ, LogConst.CON_CME_NOTE, "501",
                "192.0.2.7", "101", audit));
    }

    @Test
    void forgedOpenPatientCannotAuthorizeLockedStoredNoteOwner() {
        request.setParameter("demographicNo", "999");
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);
        assertThat(action.history()).isEqualTo("none");
        assertThat(((MockHttpServletResponse) action.response).getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
        assertThat(action.getCaseNote_history()).isNull();
        verify(security, never()).isAllowedAccessToPatientRecord(user, 999);
        verify(manager, never()).getDemoName(anyString());
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"_demographic", "_casemgmt.notes"})
    void legacyHistoryRequiresActualOwnerDomainRead(String domain) {
        when(security.hasPrivilege(user, domain, "r", "101")).thenReturn(false);
        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
        logActionMock.verifyNoInteractions();
    }

    @Test
    void forgedRequestDoesNotChangeAuthorizedOwnersDisplayedIdentity() {
        request.setParameter("demographicNo", "999");
        assertThat(action.history()).isEqualTo("historyview");
        assertThat(request.getAttribute("demoName")).isEqualTo("Actual owner");
        verify(manager, never()).getDemoName("999");
        verify(manager, never()).getDemoAge("999");
        verify(manager, never()).getDemoDOB("999");
    }

    @Test
    void legacyHistoryRefusesPatientOutsideProgramAndReferralDomain() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        logActionMock.verifyNoInteractions();
    }

    @Test
    void referredActualOwnerRemainsReadable() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        when(manager.isClientReferredInProgramDomain("4249", "101")).thenReturn(true);
        assertThat(action.history()).isEqualTo("historyview");
    }

    @Test
    void cumulativeLegacyHistoryRefusesHiddenEarlierRevisions() {
        request.getSession().setAttribute("case_program_id", "7");
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
    void legacyHistoryRefusesInvalidStoredOwner(String owner) {
        note.setDemographic_no(owner);
        assertThat(action.history()).isEqualTo("none");
        assertThat(request.getAttribute("history")).isNull();
        logActionMock.verifyNoInteractions();
    }

}
