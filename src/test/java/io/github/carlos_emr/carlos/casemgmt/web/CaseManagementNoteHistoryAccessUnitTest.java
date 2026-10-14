/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.util.List;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises history authorization before the action exposes clinical text to its JSP. */
@Tag("unit")
@Tag("security")
class CaseManagementNoteHistoryAccessUnitTest extends CarlosUnitTestBase {
    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo user;
    private CaseManagementNote note;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.getSession().setAttribute("userrole", "doctor");
        request.setParameter("demographicNo", "101");
        request.setParameter("noteId", "501");
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        security = mock(SecurityInfoManager.class);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), eq("101"))).thenReturn(true);
        note = new CaseManagementNote();
        note.setDemographic_no("101");
        when(manager.getNote("501")).thenReturn(note);
        when(manager.getHistory("501")).thenReturn(List.of(note));
    }

    private void assertRefused() {
        assertThat(action.notehistory()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("showStoredNoteHistory")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_casemgmt.notes"})
    void shouldRefuseHistory_whenPatientReadPrivilegeIsDenied(String object) {
        when(security.hasPrivilege(user, object, "r", "101")).thenReturn(false);
        assertRefused();
        verifyNoInteractions(manager);
    }

    @Test
    void shouldRefuseHistory_whenPatientIsOutsideDomainAndHasNoReferral() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        assertRefused();
        verify(manager).isClientReferredInProgramDomain("4249", "101");
        verify(manager, never()).getNote(anyString());
        verify(manager, never()).getHistory(anyString());
    }

    @Test
    void shouldRenderHistory_whenPatientIsReferredIntoProviderDomain() {
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(false);
        when(manager.isClientReferredInProgramDomain("4249", "101")).thenReturn(true);
        assertThat(action.notehistory()).isEqualTo("showHistory");
        assertThat(request.getAttribute("history")).isEqualTo(List.of(note));
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRefuseHistory_whenSelectedNoteBelongsToAnotherPatient() {
        note.setDemographic_no("102");
        assertRefused();
        verify(manager, never()).getHistory(anyString());
    }

    @Test
    void shouldRefuseHistory_whenSelectedNoteDoesNotExist() {
        when(manager.getNote("501")).thenReturn(null);
        assertRefused();
        verify(manager, never()).getHistory(anyString());
    }

    @Test
    void shouldRefuseHistory_whenUuidGroupContainsAnotherPatient() {
        CaseManagementNote other = new CaseManagementNote();
        other.setDemographic_no("102");
        when(manager.getHistory("501")).thenReturn(List.of(note, other));
        assertRefused();
    }

    @Test
    void shouldRefuseCumulativeHistory_whenProgramOrFacilityFilterHidesARow() {
        request.getSession().setAttribute("case_program_id", "7");
        when(manager.filterNotes(user, "4249", List.of(note), "7")).thenReturn(List.of());
        assertRefused();
        verify(manager).filterNotes(user, "4249", List.of(note), "7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "7"})
    void shouldRenderHistory_whenPatientAndChartFiltersAllowIt(String program) {
        request.getSession().setAttribute("case_program_id", program);
        when(manager.filterNotes(user, "4249", List.of(note), "7")).thenReturn(List.of(note));
        assertThat(action.notehistory()).isEqualTo("showHistory");
        assertThat(request.getAttribute("history")).isEqualTo(List.of(note));
        assertThat(request.getAttribute("showStoredNoteHistory")).isEqualTo(true);
        assertThat(response.getStatus()).isEqualTo(200);
        verify(security).hasPrivilege(user, "_demographic", "r", "101");
        verify(security).hasPrivilege(user, "_casemgmt.notes", "r", "101");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "undefined", "999999999999999999999"})
    void shouldRefuseHistory_whenNoteIdentifierIsInvalid(String id) {
        request.setParameter("noteId", id);
        assertRefused();
        verifyNoInteractions(manager);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "undefined", "2147483648", "999999999999999999999"})
    void shouldRefuseHistory_whenPatientIdentifierIsInvalid(String id) {
        request.setParameter("demographicNo", id);
        assertRefused();
        verifyNoInteractions(manager);
    }
}
