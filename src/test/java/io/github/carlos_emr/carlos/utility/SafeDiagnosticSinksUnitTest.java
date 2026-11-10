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
package io.github.carlos_emr.carlos.utility;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.billing.CA.filters.CodeFilterManager;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDefaultProgramDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.dashboard.factory.IndicatorBeanFactory;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorQueryHandler;
import io.github.carlos_emr.carlos.dashboard.handler.IndicatorTemplateXML;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.RxManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.NotesService;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Clinical values must reach callers and persistence, but not diagnostic or audit payloads. */
@Tag("unit")
@Tag("security")
class SafeDiagnosticSinksUnitTest extends CarlosUnitTestBase {
    private static final String PRIVATE = "FAKE-PRIVATE-CLINICAL-MARKER";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void shouldRetainUnfilteredBillingBehavior_withoutFormattingPatientData() {
        Demographic patient = new Demographic() {
            @Override public String toString() { throw new AssertionError("patient dump"); }
        };
        try (var logs = LogCapture.forLogger(CodeFilterManager.class)) {
            assertThat(new CodeFilterManager().isCodeValid(PRIVATE, PRIVATE, false, new Date(), patient)).isTrue();
            assertThat(logs.messages()).containsExactly("Validating billing code");
        }
    }

    @Test
    void shouldBuildIndicatorResults_withoutLoggingClinicalPayloads() {
        var queries = mock(IndicatorQueryHandler.class);
        registerMock(IndicatorQueryHandler.class, queries);
        var rows = List.of(Map.of(PRIVATE, 42));
        when(queries.filterQueryString(anyString())).thenAnswer(call -> call.getArgument(0));
        doReturn(rows).when(queries).execute(anyString());
        var template = mock(IndicatorTemplateXML.class);
        when(template.getId()).thenReturn(7);
        when(template.getName()).thenReturn("Synthetic indicator");
        when(template.getIndicatorQuery()).thenReturn("SELECT synthetic");
        when(template.getNotes()).thenReturn(PRIVATE);
        when(template.getIndicatorParameters()).thenReturn(null);
        when(template.getIndicatorRanges()).thenReturn(null);
        try (var logs = LogCapture.forLogger(IndicatorBeanFactory.class)) {
            var bean = new IndicatorBeanFactory(template).getIndicatorBean();
            assertThat(bean.getQueryResult()).isEqualTo(rows);
            assertThat(bean.getNotes()).isEqualTo(PRIVATE);
            assertThat(bean.getStringArrayPlots()).contains(PRIVATE, "42");
            assertThat(logs.messages()).contains("Indicator query results prepared")
                    .allSatisfy(message -> assertThat(message).doesNotContain(PRIVATE));
        }
    }

    @Test
    void shouldReturnPrescription_withIdentifierOnlyAuditData() {
        var info = new LoggedInInfo();
        Drug drug = new Drug() {
            @Override public String toString() { throw new AssertionError("prescription dump"); }
        };
        drug.setId(17);
        drug.setDemographicId(23);
        drug.setSpecial(PRIVATE);
        var dao = mock(DrugDao.class);
        when(dao.find(17)).thenReturn(drug);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_rx", "r", 23)).thenReturn(true);
        var manager = new RxManagerImpl();
        injectDependency(manager, "drugDao", dao);
        injectDependency(manager, "securityInfoManager", security);
        assertThat(manager.getDrug(info, 17)).isSameAs(drug);
        assertThat(drug.getSpecial()).isEqualTo(PRIVATE);
        logActionMock.verify(() -> LogAction.addLog(info, "RxManager.getDrug", "drugs", "17", "23", "Retrieved prescription"));
        logActionMock.verifyNoMoreInteractions();
        verify(security).hasPrivilege(info, "_rx", "r", 23);
    }

    @Test
    void shouldSaveAndReturnNoteText_withoutLoggingItsContent() throws Exception {
        var manager = mock(CaseManagementManager.class);
        var service = noteService(manager);
        when(manager.saveCaseManagementNote(any(), any(), any(), any(), anyString(), anyBoolean(),
                any(), any(), any(), anyString(), anyString(), isNull())).thenAnswer(call -> {
                    CaseManagementNote saved = call.getArgument(1);
                    saved.setId(41L);
                    return saved;
                });
        var body = JSON.createObjectNode().put("note", PRIVATE).put("isSigned", false);
        try (var logs = LogCapture.forLogger(NotesService.class)) {
            var saved = service.saveNote(23, body);
            assertThat(saved.getNote()).isEqualTo(PRIVATE);
            assertThat(saved.getNoteId()).isEqualTo(41);
            assertThat(logs.messages()).contains("Note prepared")
                    .allSatisfy(message -> assertThat(message).doesNotContain(PRIVATE));
        }
    }

    @Test
    void shouldSaveTicklerNoteAndLink_withoutLoggingRequestOrNoteText() {
        var manager = mock(CaseManagementManager.class);
        var service = noteService(manager);
        var links = mock(CaseManagementNoteLinkDAO.class);
        registerMock(CaseManagementNoteLinkDAO.class, links);
        var issueDao = mock(IssueDAO.class);
        var issue = new Issue();
        issue.setId(13L);
        when(issueDao.findIssueByTypeAndCode("system", "TicklerNote")).thenReturn(issue);
        injectDependency(service, "issueDao", issueDao);
        var linkedIssue = new CaseManagementIssue();
        linkedIssue.setId(14L);
        when(manager.getIssueById("23", "13")).thenReturn(linkedIssue);
        doAnswer(call -> { ((CaseManagementNote) call.getArgument(0)).setId(41L); return null; })
                .when(manager).saveNoteSimple(any());
        var body = JSON.createObjectNode().put("note", PRIVATE);
        body.putObject("tickler").put("id", 31).put("demographicNo", 23);
        try (var logs = LogCapture.forLogger(NotesService.class)) {
            assertThat(service.ticklerSaveNote(body)).isNotNull();
            var saved = ArgumentCaptor.forClass(CaseManagementNote.class);
            verify(manager).updateNote(saved.capture());
            assertThat(saved.getValue().getNote()).isEqualTo(PRIVATE);
            assertThat(saved.getValue().getHistory()).isEqualTo(PRIVATE);
            assertThat(saved.getValue().getIssues()).contains(linkedIssue);
            var link = ArgumentCaptor.forClass(CaseManagementNoteLink.class);
            verify(links).save(link.capture());
            assertThat(link.getValue().getNoteId()).isEqualTo(41L);
            assertThat(link.getValue().getTableId()).isEqualTo(31L);
            assertThat(logs.messages()).contains("Note configuration received", "Saving note id=null")
                    .allSatisfy(message -> assertThat(message).doesNotContain(PRIVATE, body.toString()));
        }
    }

    @Test
    void shouldReturnSelectedNoteContent_withoutFormattingSelectionPayloadsOrResultObjects() {
        var manager = mock(CaseManagementManager.class);
        var session = mock(jakarta.servlet.http.HttpSession.class);
        when(session.getAttribute("userrole")).thenReturn("doctor");
        when(session.getAttribute("user")).thenReturn("999998");
        when(manager.isClientInProgramDomain("999998", "23")).thenReturn(true);
        var service = noteService(manager, session);
        var selection = mock(io.github.carlos_emr.carlos.casemgmt.service.NoteService.class);
        injectDependency(service, "noteService", selection);
        var display = mock(io.github.carlos_emr.carlos.casemgmt.web.NoteDisplay.class);
        when(display.getNoteId()).thenReturn(73);
        when(display.getNote()).thenReturn(PRIVATE);
        when(display.toString()).thenReturn(PRIVATE);
        var result = new io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionResult();
        result.setMoreNotes(true); result.setNotes(List.of(display));
        when(selection.findNotes(any(), any())).thenReturn(result);
        var body = JSON.createObjectNode().put("unusedPrivatePayload", PRIVATE);
        body.putArray("filterIssues").add(PRIVATE);
        try (var logs = LogCapture.forLogger(NotesService.class)) {
            var returned = service.getNotesWithFilter(23, 10, 0, body);
            assertThat(returned.getNotelist()).hasSize(1);
            assertThat(returned.getNotelist().getFirst().getNote()).isEqualTo(PRIVATE);
            assertThat(returned.getNotelist().getFirst().getNoteId()).isEqualTo(73);
            var criteria = ArgumentCaptor.forClass(io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionCriteria.class);
            verify(selection).findNotes(any(), criteria.capture());
            assertThat(criteria.getValue().getIssues()).contains(PRIVATE);
            assertThat(logs.messages()).contains("Note search returned 1 records")
                    .allSatisfy(message -> assertThat(message).doesNotContain(PRIVATE, body.toString()));
        }
    }

    @Test
    void shouldKeepFailedAutosavesExplicit_withoutLoggingPrivateExceptionPayloads() {
        var manager = mock(CaseManagementManager.class);
        var service = noteService(manager);
        doThrow(new IllegalStateException(PRIVATE, new java.io.IOException(PRIVATE)))
                .when(manager).replaceTmpSave(anyString(), anyString(), anyString(), anyString(), anyString());
        var note = new io.github.carlos_emr.carlos.webserv.rest.to.model.NoteTo1(); note.setNote(PRIVATE);
        try (var logs = LogCapture.forLogger(NotesService.class)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.tmpSaveNote(23, note))
                    .isInstanceOf(jakarta.ws.rs.WebApplicationException.class)
                    .satisfies(failure -> {
                        assertThat(((jakarta.ws.rs.WebApplicationException) failure).getResponse().getStatus()).isEqualTo(500);
                        assertThat(failure.getMessage()).doesNotContain(PRIVATE);
                        assertThat(failure.getCause()).isNull();
                    });
            verify(manager).replaceTmpSave("999998", "23", "7", "0", PRIVATE);
            assertThat(logs.messages()).anyMatch(message -> message.contains("AutoSave failed"))
                    .allSatisfy(message -> assertThat(message).doesNotContain(PRIVATE));
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
        }
    }

    private NotesService noteService(CaseManagementManager manager) { return noteService(manager, null); }

    private NotesService noteService(CaseManagementManager manager, jakarta.servlet.http.HttpSession session) {
        var provider = new Provider();
        provider.setProviderNo("999998");
        var info = new LoggedInInfo(session, null, provider, null, Locale.CANADA);
        var service = new NotesService() {
            @Override protected LoggedInInfo getLoggedInInfo() { return info; }
            @Override protected Locale getLocale() { return Locale.CANADA; }
        };
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(info, 23)).thenReturn(true);
        injectDependency(service, "securityInfoManager", security);
        injectDependency(service, "caseManagementMgr", manager);
        var ticklers = mock(io.github.carlos_emr.carlos.managers.TicklerManager.class);
        when(ticklers.getTicklerDemographicNo(info,31)).thenReturn(23);
        injectDependency(service,"noteTicklerManager",ticklers);
        var program = new ProgramProvider();
        program.setProgramId(7L);
        program.setRoleId(1L);
        var programs = mock(ProgramManager2.class);
        when(programs.getCurrentProgramInDomain(info, "999998")).thenReturn(program);
        injectDependency(service, "programManager2", programs);
        var programProviders = mock(ProgramProviderDAO.class);
        when(programProviders.getProgramProvider("999998", 7L)).thenReturn(program);
        registerMock(ProgramProviderDAO.class, programProviders);
        registerMock(ProviderDefaultProgramDao.class, mock(ProviderDefaultProgramDao.class));
        return service;
    }
}
