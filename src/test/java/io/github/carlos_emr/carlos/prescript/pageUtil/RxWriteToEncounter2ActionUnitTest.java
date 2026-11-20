/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.StaleNoteRevisionException;
import io.github.carlos_emr.carlos.commn.dao.CaseManagementTmpSaveDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class RxWriteToEncounter2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private CaseManagementManager notes;
    private CaseManagementTmpSaveDao tmpDao;
    private LoggedInInfo login;
    private RxSessionBean sessionBean;
    private CaseManagementNote note;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> loggedIn;
    private MockedConstruction<EctProgram> program;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/rx/WriteToEncounter");
        response = new MockHttpServletResponse();
        request.addParameter("expectedDemographicNo", "42");
        request.addParameter("demographicNo", "42");
        request.addParameter("body", "exact prescription text");
        request.getSession().setAttribute("user", "999998");
        ChartProgramContext.remember(request, "0");
        sessionBean = new RxSessionBean();
        sessionBean.setDemographicNo(42);
        RxSessionBeanResolver.register(request.getSession(), sessionBean);
        security = mock(SecurityInfoManager.class);
        notes = mock(CaseManagementManager.class);
        tmpDao = mock(CaseManagementTmpSaveDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(login), eq("_rx"), eq("w"), nullable(String.class))).thenReturn(true);
        // Patient-level Rx access (the shared Rx write check, #3908) is granted unless a test denies it.
        when(security.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(security.hasPrivilege(login, "_eChart", "w", "42")).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", "42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);
        registerMock(CaseManagementManager.class, notes);
        registerMock(CaseManagementTmpSaveDao.class, tmpDao);
        note = new CaseManagementNote();
        note.setNote("existing text");
        note.setDemographic_no("42");
        note.setProviderNo("999998");
        note.setUpdate_date(new Date(0));
        note.setId(7L);
        note.setUuid("77777777-7777-4777-8777-777777777777");
        note.setRevision("1");
        note.setHistory("existing history");
        when(notes.filterNotes(eq(login), eq("999998"), anyCollection(), anyString()))
                .thenAnswer(invocation -> new ArrayList<>(invocation.<Collection<CaseManagementNote>>getArgument(2)));
        when(notes.getLastSaved("0", "42", "999998")).thenReturn(note);
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        loggedIn = mockStatic(LoggedInInfo.class);
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        program = mockConstruction(EctProgram.class, (mock, context) ->
                when(mock.getProgram(anyString())).thenReturn("0"));
    }

    @AfterEach
    void tearDown() {
        program.close();
        loggedIn.close();
        servlet.close();
    }

    @Test
    void shouldReject_whenPatientChangedBeforeAnyNoteAccess() throws Exception {
        sessionBean.setDemographicNo(43);
        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRequireOriginatingPatient_whenSessionExists() throws Exception {
        request.removeParameter("expectedDemographicNo");
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectBeforeNoteAccess_whenRequestNamesNoPatient() throws Exception {
        // expectedDemographicNo matches the session's active (fallback) patient, but the request
        // does not name its patient: the write resolver refuses it before any note access (#3875).
        request.removeParameter("demographicNo");
        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectWithoutRedirect_whenRxSessionIsMissing() throws Exception {
        request.getSession().removeAttribute(RxSessionBeanResolver.BEANS_ATTRIBUTE);
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectBeforeNoteAccess_whenMethodIsNotPost() throws Exception {
        request.setMethod("GET");
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectAnonymous_beforeMethodDetails() {
        request.setMethod("GET");
        loggedIn.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        RxWriteToEncounter2Action action = new RxWriteToEncounter2Action();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(security, notes, tmpDao);
    }

    @Test
    void shouldRejectLowercaseMethod_becauseHttpMethodTokensAreCaseSensitive() throws Exception {
        request.setMethod("post");
        new RxWriteToEncounter2Action().execute();
        assertThat(response.getStatus()).isEqualTo(405);
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectBeforeNoteAccess_whenPatientWriteIsDenied() {
        when(security.hasPrivilege(login, "_rx", "w", 42)).thenReturn(false);
        RxWriteToEncounter2Action action = new RxWriteToEncounter2Action();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectBeforeNoteAccess_whenPatientRecordAccessIsDenied() {
        // Patient-level _rx write alone is not enough: the caller must be allowed to open the record.
        when(security.isAllowedAccessToPatientRecord(login, 42)).thenReturn(false);
        RxWriteToEncounter2Action action = new RxWriteToEncounter2Action();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldAcknowledge_afterSavingBoundPatientNote() throws Exception {
        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");
        CaseManagementNote revision = submittedRevision();
        assertThat(revision.getNote()).isEqualTo("existing text\nexact prescription text");
        assertThat(revision.getRevision()).isEqualTo("2");
        assertThat(revision.getHistory()).contains("exact prescription text", "existing history");
        assertThat(note.getNote()).isEqualTo("existing text");
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @Test
    void shouldUseCapturedChartProgram_whenProviderDefaultDiffers() throws Exception {
        ChartProgramContext.remember(request, "7");
        when(notes.getLastSaved("7", "42", "999998")).thenReturn(note);
        CaseManagementTmpSave capturedDraft = mock(CaseManagementTmpSave.class);
        when(capturedDraft.getId()).thenReturn(17);
        when(capturedDraft.getNoteId()).thenReturn(7);
        when(capturedDraft.getUpdateDate()).thenReturn(new Date(1));
        when(capturedDraft.getNote()).thenReturn("captured program draft");
        when(capturedDraft.getProviderNo()).thenReturn("999998");
        when(capturedDraft.getDemographicNo()).thenReturn(42);
        when(capturedDraft.getProgramId()).thenReturn(7);
        note.setUpdate_date(new Date(0));
        note.setProgram_no("7");
        when(notes.getTmpSave("999998", "42", "7")).thenReturn(capturedDraft);
        when(notes.getNote("7")).thenReturn(note);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        verify(notes).getTmpSave("999998", "42", "7");
        verify(notes, never()).getTmpSave("999998", "42", "0");
        verify(tmpDao).removeCaptured(17);
        assertThat(submittedRevision().getNote()).isEqualTo("captured program draft\nexact prescription text");
        assertThat(note.getNote()).isEqualTo("existing text");
        assertThat(program.constructed()).isEmpty();
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @Test
    void shouldCreateNoteInCapturedProgram_whenNoNoteOrDraftExists() throws Exception {
        ChartProgramContext.remember(request, "7");
        registerMock(ProgramManager.class, mock(ProgramManager.class));

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        verify(notes).saveNoteSimple(argThat(saved -> "7".equals(saved.getProgram_no())
                && "42".equals(saved.getDemographic_no())
                && saved.getNote().endsWith("exact prescription text")));
        assertThat(program.constructed()).isEmpty();
    }

    @Test
    void shouldRememberAuthenticatedProviderDefault_whenRxHasNoChartContext() throws Exception {
        ChartProgramContext.remember(request, null);
        request.getSession().setAttribute("case_program_id", "99");
        request.getSession().setAttribute("user", "different-session-provider");

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        verify(program.constructed().getFirst()).getProgram("999998");
        assertThat(ChartProgramContext.resolve(request)).isEqualTo("0");
        verify(notes).getLastSaved("0", "42", "999998");
        verify(notes).getTmpSave("999998", "42", "0");
        assertThat(submittedRevision().getNote()).isEqualTo("existing text\nexact prescription text");
    }

    @Test
    void shouldPreserveExactPrescriptionText_whenAppendingToAnEncounterDraft() throws Exception {
        String prescriptionText = "Take 1/2 tablet\nDon't repeat <PRN> & call \"clinic\".";
        request.setParameter("body", prescriptionText);
        CaseManagementTmpSave tmp = mock(CaseManagementTmpSave.class);
        when(tmp.getId()).thenReturn(17);
        when(tmp.getNoteId()).thenReturn(7);
        when(tmp.getUpdateDate()).thenReturn(new Date(1));
        when(tmp.getNote()).thenReturn("draft text");
        when(tmp.getProviderNo()).thenReturn("999998");
        when(tmp.getDemographicNo()).thenReturn(42);
        when(tmp.getProgramId()).thenReturn(0);
        note.setUpdate_date(new Date(0));
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(tmp);
        when(notes.getNote("7")).thenReturn(note);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        assertThat(submittedRevision().getNote()).isEqualTo("draft text\n" + prescriptionText);
        assertThat(note.getNote()).isEqualTo("existing text");
        verify(tmpDao).removeCaptured(17);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @Test
    void shouldRejectWithoutConsumingDraft_whenReferencedNoteBelongsToAnotherPatient() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        note.setDemographic_no("43");
        note.setUpdate_date(new Date(2));
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertThat(note.getNote()).isEqualTo("existing text");
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @Test
    void shouldRejectWithoutConsumingDraft_whenReferencedNoteNoLongerExists() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @Test
    void shouldRetainExplicitlySelectedSharedNoteProgram_whenDraftPatientMatches() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        note.setProgram_no("8");
        note.setUpdate_date(new Date(0));
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        CaseManagementNote revision = submittedRevision();
        assertThat(revision.getProgram_no()).isEqualTo("8");
        assertThat(revision.getNote()).isEqualTo("draft text\nexact prescription text");
        assertThat(note.getNote()).isEqualTo("existing text");
        verify(tmpDao).removeCaptured(17);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @ParameterizedTest
    @ValueSource(strings = {"signed", "locked", "archived", "other-author"})
    void shouldRejectWithoutConsumingDraft_whenReferencedNoteCannotBeDirectlyAppended(String state) throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        protectNote(state);
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertThat(note.getNote()).isEqualTo("existing text");
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"newer-note", "unknown-note-time", "unknown-draft-time"})
    void shouldPreserveBothVersions_whenReferencedNoteIsNewerThanDraftOrCannotBeCompared(String state) throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        note.setNote("newer independently saved text");
        note.setUpdate_date("unknown-note-time".equals(state) ? null : new Date(2));
        if ("unknown-draft-time".equals(state)) when(draft.getUpdateDate()).thenReturn(null);
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);

        assertThat(new RxWriteToEncounter2Action().execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertThat(note.getNote()).isEqualTo("newer independently saved text");
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @Test
    void shouldAppendMatchingDraft_whenStoredNoteTimestampEqualsDraftTimestamp() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        note.setUpdate_date(new Date(1));
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);

        new RxWriteToEncounter2Action().execute();

        assertThat(submittedRevision().getNote()).isEqualTo("draft text\nexact prescription text");
        assertThat(note.getNote()).isEqualTo("existing text");
        verify(tmpDao).removeCaptured(17);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
    }

    @ParameterizedTest
    @ValueSource(strings = {"_eChart", "_casemgmt.notes"})
    void shouldRejectBeforeAnyClinicalAccess_whenPatientChartWriteIsDenied(String object) throws Exception {
        when(security.hasPrivilege(login, object, "w", "42")).thenReturn(false);

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        verifyNoInteractions(notes, tmpDao);
    }

    @Test
    void shouldRejectBeforeReadingDraft_whenProgramGrantsReadOnlyNotes() throws Exception {
        when(notes.hasAccessRight("note-read-only", "access", "999998", "42", "0")).thenReturn(true);

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        verify(notes, never()).getTmpSave(anyString(), anyString(), anyString());
        verify(notes, never()).getLastSaved(anyString(), anyString(), anyString());
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @Test
    void shouldRejectWithoutConsumingDraft_whenReferencedNoteIsNoLongerVisibleInCapturedProgram() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        ChartProgramContext.remember(request, "7");
        when(notes.getTmpSave("999998", "42", "7")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);
        when(notes.filterNotes(eq(login), eq("999998"), anyCollection(), eq("7")))
                .thenReturn(new ArrayList<>());

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertThat(note.getNote()).isEqualTo("existing text");
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"signed", "locked", "archived", "other-author"})
    void shouldCreateFreshOwnedNote_whenAutomaticCandidateCannotBeDirectlyAppended(String state) throws Exception {
        protectNote(state);
        registerMock(ProgramManager.class, mock(ProgramManager.class));

        new RxWriteToEncounter2Action().execute();

        assertThat(note.getNote()).isEqualTo("existing text");
        verify(notes).saveNoteSimple(argThat(saved -> saved != note
                && "42".equals(saved.getDemographic_no()) && "0".equals(saved.getProgram_no())
                && "999998".equals(saved.getProviderNo()) && !saved.isSigned()
                && saved.getNote().endsWith("exact prescription text")
                && !saved.getNote().contains("existing text")));
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
        verifyNoInteractions(tmpDao);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldReturnConflictWithoutMutatingHistory_whenRevisionChangesBeforeTransactionalSave(boolean hasDraft) throws Exception {
        if (hasDraft) {
            CaseManagementTmpSave draft = referencedDraft();
            when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
            when(notes.getNote("7")).thenReturn(note);
        }
        when(notes.saveNoteRevisionSimple(any())).thenThrow(new StaleNoteRevisionException());

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertThat(note.getNote()).isEqualTo("existing text");
        assertThat(note.getHistory()).isEqualTo("existing history");
        assertThat(note.getUpdate_date()).isEqualTo(new Date(0));
        verify(notes, never()).saveNoteSimple(any());
        verifyNoInteractions(tmpDao);
    }

    @Test
    void shouldPreserveNewerAutosave_whenItReplacesTheCapturedDraftDuringRevisionSave() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);
        when(notes.getNote("7")).thenReturn(note);
        Map<Integer, String> persistedDrafts = new HashMap<>(Map.of(17, "draft text"));
        when(notes.saveNoteRevisionSimple(any())).thenAnswer(invocation -> {
            persistedDrafts.remove(17);
            persistedDrafts.put(18, "newer autosaved text");
            return invocation.getArgument(0);
        });
        when(tmpDao.removeCaptured(anyInt())).thenAnswer(invocation -> persistedDrafts.remove(invocation.getArgument(0)) == null ? 0 : 1);

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
        assertThat(persistedDrafts).containsExactlyEntriesOf(Map.of(18, "newer autosaved text"));
        assertThat(submittedRevision().getNote()).isEqualTo("draft text\nexact prescription text");
        verify(tmpDao).removeCaptured(17);
        verify(tmpDao, never()).remove(anyString(), anyInt(), anyInt());
    }

    @Test
    void shouldRejectBeforeWriting_whenCapturedDraftHasNoPersistedIdentity() throws Exception {
        CaseManagementTmpSave draft = referencedDraft();
        when(draft.getId()).thenReturn(null);
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(draft);

        new RxWriteToEncounter2Action().execute();

        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("not-written");
        assertNoClinicalWrite();
        verifyNoInteractions(tmpDao);
    }

    @Test
    void shouldPropagateUnrelatedRevisionFailure_withoutClaimingSafeRetry() {
        when(notes.saveNoteRevisionSimple(any())).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> new RxWriteToEncounter2Action().execute())
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");

        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isNull();
        assertThat(note.getNote()).isEqualTo("existing text");
        verifyNoInteractions(tmpDao);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "legacy")
    void shouldRetainLegacyRevisionCompatibility_withoutMutatingHistoricalSource(String revisionNumber) throws Exception {
        note.setRevision(revisionNumber);

        new RxWriteToEncounter2Action().execute();

        assertThat(submittedRevision().getRevision()).isEqualTo("1");
        assertThat(note.getRevision()).isEqualTo(revisionNumber);
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isEqualTo("written");
        verifyNoInteractions(tmpDao);
    }

    private CaseManagementNote submittedRevision() {
        ArgumentCaptor<CaseManagementNote> submitted = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(notes).saveNoteRevisionSimple(submitted.capture());
        assertThat(submitted.getValue()).isNotSameAs(note);
        assertThat(submitted.getValue().getId()).isEqualTo(note.getId());
        assertThat(submitted.getValue().getUuid()).isEqualTo(note.getUuid());
        assertThat(note.getNote()).isEqualTo("existing text");
        assertThat(note.getHistory()).isEqualTo("existing history");
        return submitted.getValue();
    }

    private void assertNoClinicalWrite() {
        verify(notes, never()).saveNoteSimple(any());
        verify(notes, never()).saveNoteRevisionSimple(any());
    }

    private void protectNote(String state) {
        switch (state) {
            case "signed" -> note.setSigned(true);
            case "locked" -> note.setLocked(true);
            case "archived" -> note.setArchived(true);
            case "other-author" -> note.setProviderNo("999997");
            default -> throw new IllegalArgumentException("Unknown protected note state");
        }
    }

    private CaseManagementTmpSave referencedDraft() {
        CaseManagementTmpSave draft = mock(CaseManagementTmpSave.class);
        when(draft.getId()).thenReturn(17);
        when(draft.getNoteId()).thenReturn(7);
        when(draft.getUpdateDate()).thenReturn(new Date(1));
        when(draft.getNote()).thenReturn("draft text");
        when(draft.getProviderNo()).thenReturn("999998");
        when(draft.getDemographicNo()).thenReturn(42);
        when(draft.getProgramId()).thenReturn(0);
        return draft;
    }

    @Test
    void shouldNeverClaimNoWrite_whenPostSaveCleanupFails() {
        CaseManagementTmpSave tmp = mock(CaseManagementTmpSave.class);
        when(tmp.getId()).thenReturn(17);
        when(tmp.getNoteId()).thenReturn(7);
        when(tmp.getUpdateDate()).thenReturn(new Date(1));
        when(tmp.getNote()).thenReturn("draft text");
        when(tmp.getProviderNo()).thenReturn("999998");
        when(tmp.getDemographicNo()).thenReturn(42);
        when(tmp.getProgramId()).thenReturn(0);
        note.setUpdate_date(new Date(0));
        when(notes.getTmpSave("999998", "42", "0")).thenReturn(tmp);
        when(notes.getNote("7")).thenReturn(note);
        doThrow(new IllegalStateException("cleanup failed after commit"))
                .when(tmpDao).removeCaptured(17);
        RxWriteToEncounter2Action action = new RxWriteToEncounter2Action();
        assertThatThrownBy(action::execute).isInstanceOf(IllegalStateException.class);
        assertThat(submittedRevision().getNote()).isEqualTo("draft text\nexact prescription text");
        assertThat(response.getHeader("X-Carlos-Encounter-Write")).isNull();
    }
}
