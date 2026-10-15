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
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);

        note = new CaseManagementNote();
        note.setNote(NOTE_TEXT);
        note.setDemographic_no("101");
        note.setHistory("earlier revision");
        CaseManagementManager manager = mock(CaseManagementManager.class);
        when(manager.getNote("501")).thenReturn(note);
        action.caseManagementMgr = manager;
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
}
