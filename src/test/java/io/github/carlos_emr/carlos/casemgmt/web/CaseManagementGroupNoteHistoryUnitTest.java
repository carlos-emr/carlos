/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.GroupNoteLink;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Exact-revision sharing must never become access to an owner's whole note chain. */
@Tag("unit")
@Tag("security")
class CaseManagementGroupNoteHistoryUnitTest extends CarlosUnitTestBase {
    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private CaseManagementManager manager;
    private SecurityInfoManager security;
    private GroupNoteDao groups;
    private LoggedInInfo user;
    private Facility facility;
    private CaseManagementNote selected;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.getSession().setAttribute("userrole", "doctor");
        request.setParameter("demographicNo", "101");
        request.setParameter("noteId", "502");
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        facility = new Facility();
        facility.setEnableGroupNotes(true);
        when(user.getCurrentFacility()).thenReturn(facility);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        security = mock(SecurityInfoManager.class);
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        when(security.hasPrivilege(user, "_demographic", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(user, "_casemgmt.notes", "r", "101")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
        groups = createAndRegisterMock(GroupNoteDao.class);
        selected = note(502, "Shared assessment");
        when(manager.getNote("502")).thenReturn(selected);
        when(manager.getHistory("502")).thenReturn(List.of(selected));
        when(groups.findLinksByDemographic(101)).thenReturn(List.of(link(502, true)));
    }

    private CaseManagementNote note(long id, String text) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setUuid("owner-chain");
        note.setDemographic_no("202");
        note.setReporter_caisi_role("1");
        note.setNote(text);
        note.setHistory("UNSHARED cumulative history");
        return note;
    }

    private GroupNoteLink link(int noteId, boolean active) {
        GroupNoteLink link = new GroupNoteLink();
        link.setDemographicNo(101);
        link.setNoteId(noteId);
        link.setActive(active);
        return link;
    }

    private void refused() {
        assertThat(action.notehistory()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getAttribute("history")).isNull();
        assertThat(request.getAttribute("demoName")).isNull();
        assertThat(request.getAttribute("showStoredNoteHistory")).isNull();
    }

    @Test
    void shouldAllowSharedRevision_whenOwnerChartIsUnavailableToRecipient() {
        request.getSession().setAttribute("case_program_id", "7");
        assertThat(action.notehistory()).isEqualTo("showHistory");
        assertThat(response.getStatus()).isEqualTo(200);
        List<?> history = (List<?>) request.getAttribute("history");
        assertThat(history).hasSize(1);
        CaseManagementNote shared = (CaseManagementNote) history.getFirst();
        assertThat(shared.getId()).isEqualTo(502);
        assertThat(shared.getNote()).isEqualTo("Shared assessment");
        assertThat(shared.getHistory()).isNull();
        assertThat(shared).isNotSameAs(selected);
        assertThat(selected.getHistory()).contains("UNSHARED");
        assertThat(selected.getDemographic_no()).isEqualTo("202");
        assertThat(request.getAttribute("showStoredNoteHistory")).isEqualTo(false);
        verify(security, never()).hasPrivilege(user, "_demographic", "r", "202");
        verify(security, never()).isAllowedAccessToPatientRecord(user, 202);
        verify(manager, never()).filterNotes(any(), anyString(), anyList(), anyString());
    }

    @Test
    void shouldRenderOnlySharedRevisionsForLegacyHistory_whenCumulativeOwnerHistoryContainsPrivateText() {
        CaseManagementNote privateRow = note(501, "Private sibling");
        when(manager.getHistory("502")).thenReturn(List.of(privateRow, selected));

        assertThat(action.history()).isEqualTo("showHistory");

        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked") List<CaseManagementNote> visible = (List<CaseManagementNote>) request.getAttribute("history");
        assertThat(visible).extracting(CaseManagementNote::getId).containsExactly(502L);
        assertThat(visible.getFirst().getHistory()).isNull();
        assertThat(request.getAttribute("showStoredNoteHistory")).isEqualTo(false);
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldExposeOnlyLinkedEarlierRevisions_whenTheUuidChainContainsPrivateAndFutureRows() {
        CaseManagementNote older = note(500, "Earlier explicitly shared");
        CaseManagementNote privateRow = note(501, "Private sibling");
        CaseManagementNote future = note(503, "Future sibling");
        when(manager.getHistory("502")).thenReturn(List.of(older, privateRow, selected, future));
        when(groups.findLinksByDemographic(101)).thenReturn(List.of(link(500, true), link(502, true), link(503, true)));
        assertThat(action.notehistory()).isEqualTo("showHistory");
        @SuppressWarnings("unchecked") List<CaseManagementNote> history = (List<CaseManagementNote>) request.getAttribute("history");
        assertThat(history).extracting(CaseManagementNote::getId).containsExactly(500L, 502L);
        assertThat(history).allMatch(note -> note.getHistory() == null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "unlinked", "wrong-patient", "wrong-note"})
    void shouldRefuseHistory_whenTheSelectedRevisionHasNoActiveRecipientLink(String kind) {
        GroupNoteLink link = link(kind.equals("wrong-note") ? 501 : 502, !kind.equals("inactive"));
        if (kind.equals("wrong-patient")) link.setDemographicNo(303);
        when(groups.findLinksByDemographic(101)).thenReturn(kind.equals("unlinked") ? List.of() : List.of(link));
        refused();
        verify(manager, never()).getHistory(anyString());
    }

    @Test
    void shouldRefuseFutureRevision_whenOnlyItsEarlierSiblingWasShared() {
        request.setParameter("noteId", "503");
        when(manager.getNote("503")).thenReturn(note(503, "Unlinked future"));
        refused();
        verify(manager, never()).getHistory(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"patient", "uuid"})
    void shouldRefuseMixedHistory_whenAnUnrelatedRowIsInTheChain(String kind) {
        CaseManagementNote other = note(501, "Unrelated");
        if (kind.equals("patient")) other.setDemographic_no("303"); else other.setUuid("unrelated-chain");
        when(manager.getHistory("502")).thenReturn(List.of(other, selected));
        refused();
    }

    @Test
    void shouldRefuseHistory_whenTheRecipientChartIsRestricted() {
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(false);
        refused();
        verifyNoInteractions(manager, groups);
    }

    @ParameterizedTest
    @ValueSource(strings = {"_demographic", "_casemgmt.notes"})
    void shouldRefuseBeforeClinicalAccess_whenActiveRolesDoNotGrantRecipientRead(String object) {
        request.getSession().setAttribute("userrole", "custom_no_notes");
        // SecurityInfoManager resolves active assignments; its refusal must stop the read.
        when(security.hasPrivilege(user, object, "r", "101")).thenReturn(false);
        refused();
        verifyNoInteractions(manager, groups);
    }

    @Test
    void shouldRefuseHistory_whenTheCurrentFacilityDisablesGroupNotes() {
        facility.setEnableGroupNotes(false);
        refused();
        verifyNoInteractions(groups);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"2", "2147483000", ""})
    void shouldReadTheGrantedRevision_whenTheReporterRoleIsDifferentOrUnknown(String reporterRole) {
        request.getSession().setAttribute("userrole", "custom_reader");
        selected.setReporter_caisi_role(reporterRole);
        assertThat(action.notehistory()).isEqualTo("showHistory");
        @SuppressWarnings("unchecked") List<CaseManagementNote> history = (List<CaseManagementNote>) request.getAttribute("history");
        assertThat(history).extracting(CaseManagementNote::getId).containsExactly(502L);
        assertThat(history.getFirst().getReporter_caisi_role()).isEqualTo(reporterRole);
        assertThat(history.getFirst().getHistory()).isNull();
    }

    @Test
    void shouldReadExplicitlyLinkedEarlierRevisions_withDifferentReporterRoles() {
        CaseManagementNote earlier = note(501, "Earlier shared assessment");
        earlier.setReporter_caisi_role("2147483000");
        when(groups.findLinksByDemographic(101)).thenReturn(List.of(link(501, true), link(502, true)));
        when(manager.getHistory("502")).thenReturn(List.of(earlier, selected));
        assertThat(action.notehistory()).isEqualTo("showHistory");
        @SuppressWarnings("unchecked") List<CaseManagementNote> history = (List<CaseManagementNote>) request.getAttribute("history");
        assertThat(history).extracting(CaseManagementNote::getId).containsExactly(501L, 502L);
        assertThat(history).allMatch(note -> note.getHistory() == null);
    }

    @Test
    void shouldRenderOnlyTheExactLinkedRow_whenTheLegacyNoteHasNoUuid() {
        selected.setUuid(null);
        assertThat(action.notehistory()).isEqualTo("showHistory");
        assertThat((List<?>) request.getAttribute("history")).hasSize(1);
        verify(manager, never()).getHistory(anyString());
    }

    @Test
    void shouldRefuseHistory_whenTheLinkedNoteWasDeleted() {
        when(manager.getNote("502")).thenReturn(null);
        refused();
        verifyNoInteractions(groups);
    }
}
