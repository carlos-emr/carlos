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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Checks real note entry points before their locks, session drafts or clinical writes are touched. */
@Tag("unit")
@Tag("release-review")
class CaseManagementNoteWriteAuthorizationUnitTest extends CarlosUnitTestBase {
    private static final String[] METHODS = {
        "issueNoteSave", "issueNoteSaveJson", "ajaxsave", "save", "saveAndExit", "ticklerSaveNote"
    };
    private CaseManagementEntry2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private LoggedInInfo login;
    private SecurityInfoManager security;
    private CaseManagementNoteDAO notes;
    private IssueDAO issues;
    private TicklerManager ticklers;

    @BeforeEach
    void prepareNoteWriter() {
        action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest("POST", "/CaseManagementEntry");
        response = new MockHttpServletResponse();
        action.request = request;
        action.response = response;
        request.setParameter("demographicNo", "101");
        login = mock(LoggedInInfo.class);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), login);
        security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(login, "_demographic", "r", null)).thenReturn(true);
        when(security.hasPrivilege(login, "_eChart", "w", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "r", "101")).thenReturn(true);
        when(security.hasPrivilege(login, "_tickler", "r", null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(true);
        injectDependency(action, "securityInfoManager", security);
        notes = mock(CaseManagementNoteDAO.class);
        issues = mock(IssueDAO.class);
        ticklers = mock(TicklerManager.class);
        injectDependency(action, "caseManagementNoteDao", notes);
        injectDependency(action, "issueDao", issues);
        injectDependency(action, "ticklerManager", ticklers);
        action.caseManagementMgr = mock(CaseManagementManager.class);
    }

    static Stream<Arguments> unsafeMethods() {
        return Stream.of(METHODS).flatMap(operation -> Stream.of("GET", "HEAD", "PUT")
                .map(verb -> Arguments.of(operation, verb)));
    }

    @ParameterizedTest
    @MethodSource("unsafeMethods")
    void shouldRequirePostBeforeAnyNoteAccess_whenCalledDirectly(String operation, String verb) throws Exception {
        request.setMethod(verb);
        assertThat(invoke(operation)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(action.caseManagementMgr, notes, ticklers, issues);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issueNoteSave", "issueNoteSaveJson", "ajaxsave", "save", "saveAndExit", "ticklerSaveNote"})
    void shouldRefuseBeforeRestoringSession_whenThePatientIsLocked(String operation) throws Exception {
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        request.setParameter("method", operation);
        Object retained = new Object();
        request.getSession().setAttribute("caseManagementEntryForm101", retained);
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(request.getSession().getAttribute("caseManagementEntryForm101")).isSameAs(retained);
        assertThat(request.getAttribute("casemgmt_DemoNo")).isNull();
        verifyNoInteractions(action.caseManagementMgr, notes, ticklers, issues);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issueNoteSave", "issueNoteSaveJson", "ajaxsave", "save", "saveAndExit", "ticklerSaveNote"})
    void shouldRefuseNoteWrite_whenTheCallerHasOnlyChartRead(String operation) throws Exception {
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", "101")).thenReturn(false);
        assertThat(invoke(operation)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(action.caseManagementMgr, notes, ticklers, issues);
    }

    @ParameterizedTest
    @ValueSource(strings = {"issueNoteSave", "issueNoteSaveJson", "ajaxsave", "save", "saveAndExit", "ticklerSaveNote"})
    void shouldRefuseNoteWrite_whenPatientAliasesConflict(String operation) throws Exception {
        request.setParameter("demographic_no", "202");
        assertThat(invoke(operation)).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(action.caseManagementMgr, notes, ticklers, issues);
    }

    @Test
    void shouldRefuseTicklerSave_whenTheTicklerBelongsToAnotherPatient() {
        tickler(202);
        assertThat(action.ticklerSaveNote()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(action.caseManagementMgr, notes, issues);
    }

    @Test
    void shouldRefuseTicklerRevisionBeforeCopyingHistory_whenTheNoteBelongsToAnotherPatient() {
        tickler(101);
        request.setParameter("noteId", "77");
        var foreign = new CaseManagementNote();
        foreign.setDemographic_no("202");
        foreign.setHistory("Foreign private history");
        when(notes.getNote(77L)).thenReturn(foreign);
        assertThat(action.ticklerSaveNote()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(action.caseManagementMgr, issues);
        assertThat(foreign.getHistory()).isEqualTo("Foreign private history");
    }

    @Test
    void shouldRefuseTicklerRevision_whenTheSubmittedNoteIsNotLinkedToThisTickler() {
        tickler(101);
        request.setParameter("noteId", "77");
        var own = new CaseManagementNote();
        own.setDemographic_no("101");
        when(notes.getNote(77L)).thenReturn(own);
        createAndRegisterMock(CaseManagementNoteLinkDAO.class);
        assertThat(action.ticklerSaveNote()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(action.caseManagementMgr, issues);
    }

    @Test
    void shouldAbortBeforeCreatingPartialClinicalRows_whenTheRequiredTicklerIssueIsMissing() {
        tickler(101);
        assertThat(action.ticklerSaveNote()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(500);
        verifyNoInteractions(action.caseManagementMgr, notes);
    }

    @Test
    void shouldRefuseTicklerReadBeforeLoadingLinks_whenPatientRecordAccessIsDenied() throws Exception {
        tickler(101);
        when(security.isAllowedAccessToPatientRecord(login, 101)).thenReturn(false);
        var links = createAndRegisterMock(CaseManagementNoteLinkDAO.class);
        assertThat(action.ticklerGetNote()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
        verifyNoInteractions(links, notes);
    }

    @Test
    void shouldRefuseTicklerRead_whenItsStoredLinkPointsToAnotherPatientsNote() throws Exception {
        tickler(101);
        var links = createAndRegisterMock(CaseManagementNoteLinkDAO.class);
        var link = new CaseManagementNoteLink();
        link.setNoteId(77L);
        when(links.getLastLinkByTableId(CaseManagementNoteLink.TICKLER, 17L)).thenReturn(link);
        var foreign = new CaseManagementNote();
        foreign.setDemographic_no("202");
        foreign.setNote("Foreign private note");
        when(notes.getNote(77L)).thenReturn(foreign);
        assertThat(action.ticklerGetNote()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("Foreign private note");
    }

    private void tickler(int patient) {
        request.setParameter("ticklerNo", "17");
        request.setParameter("value", "Owned tickler note");
        var tickler = new Tickler();
        tickler.setId(17);
        tickler.setDemographicNo(patient);
        when(ticklers.getTickler(login, 17)).thenReturn(tickler);
    }

    private String invoke(String operation) throws Exception {
        return switch (operation) {
            case "issueNoteSave" -> action.issueNoteSave();
            case "issueNoteSaveJson" -> action.issueNoteSaveJson();
            case "ajaxsave" -> action.ajaxsave();
            case "save" -> action.save();
            case "saveAndExit" -> action.saveAndExit();
            case "ticklerSaveNote" -> action.ticklerSaveNote();
            default -> throw new IllegalArgumentException(operation);
        };
    }
}
