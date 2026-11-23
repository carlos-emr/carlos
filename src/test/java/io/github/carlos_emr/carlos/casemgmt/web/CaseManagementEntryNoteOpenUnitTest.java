/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManagerImpl;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.CaseManagementTmpSave;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.encounter.data.EctProgramManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.LabelValueBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opening a note for reading writes nothing; the edit lock is taken when editing starts.
 *
 * <p>A GET of the note editor (a link, a note search result) only reads: it writes no note lock
 * row, no old-note history and no note link, and it leaves this session's cached lock as it is.
 * Opening by POST, as the chart's own editor does, still takes and caches the lock and stores an
 * old note's history. The classic editor page takes its lock by POST when the clinician chooses Edit
 * ({@link CaseManagementEntryNoteLockJspRegressionTest}). Every patient and note here is FAKE.</p>
 */
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEntryNoteOpenUnitTest extends CarlosUnitTestBase {
    private static final String PATIENT = "101";
    private static final String PROVIDER = "999998";
    private static final String OTHER_PROVIDER = "999997";
    private static final String CACHE_KEY = "casemgmtNoteLock" + PATIENT;
    private static final String ORIGINAL_TEXT = "FAKE original note text";
    private static final String REVISED_TEXT = "FAKE revised note text";

    /** The ways the editor picks the note it opens, with the note id its lock is for. */
    enum OpenPath {
        SAVED_NOTE(5001L),
        LAST_UNSIGNED(5002L),
        NEW_WHEN_NOTHING_UNSIGNED(0L),
        EXPLICIT_NEW(0L),
        DRAFT(5003L);

        final long lockedNoteId;

        OpenPath(long lockedNoteId) {
            this.lockedNoteId = lockedNoteId;
        }
    }

    private MockHttpSession session;
    private CaseManagementManager manager;
    private CasemgmtNoteLockDao locks;
    private SecurityInfoManager security;
    private LoggedInInfo login;
    private Map<String, Object> forms;
    private CarlosProperties properties;

    @BeforeEach
    void setUp() {
        manager = createAndRegisterMock(CaseManagementManager.class);
        locks = createAndRegisterMock(CasemgmtNoteLockDao.class);
        createAndRegisterMock(OscarAppointmentDao.class);
        createAndRegisterMock(DemographicManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn(PROVIDER);
        for (String module : List.of("_demographic", "_eChart", "_casemgmt.notes")) {
            when(security.hasPrivilege(login, module, "r", PATIENT)).thenReturn(true);
        }
        when(security.hasPrivilege(login, "_demographic", "r", null)).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(login, Integer.valueOf(PATIENT))).thenReturn(true);
        when(manager.isClientInProgramDomain(PROVIDER, PATIENT)).thenReturn(true);
        when(manager.filterNotes(eq(login), eq(PROVIDER), anyCollection(), anyString()))
                .thenAnswer(call -> List.copyOf(call.<java.util.Collection<CaseManagementNote>>getArgument(2)));

        session = new MockHttpSession();
        session.setAttribute("user", PROVIDER);
        session.setAttribute("userrole", "doctor");
        session.setAttribute("case_program_id", "7");
        session.setAttribute("casemgmt_oscar_baseurl", "https://clinic.example");
        LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        EctProgramManager defaults = mock(EctProgramManager.class);
        when(defaults.getProgramBeans(PROVIDER, null)).thenReturn(List.of(new LabelValueBean("Default", "7")));
        when(defaults.getDefaultProgramId(PROVIDER)).thenReturn(7);
        WebApplicationContext context = mock(WebApplicationContext.class);
        when(context.getBean("ectProgramManager")).thenReturn(defaults);
        session.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context);

        forms = new HashMap<>();
        properties = mock(CarlosProperties.class);
        when(properties.getProperty(anyString(), anyString())).thenAnswer(call -> call.getArgument(1));
        when(properties.getProperty("billregion", "")).thenReturn("ON");
        when(properties.isPropertyActive("encounter.empty_new_note")).thenReturn(true);
    }

    @ParameterizedTest
    @EnumSource(OpenPath.class)
    @DisplayName("should write no lock, note or link and keep the session's lock when the editor opens by GET")
    void shouldWriteNothing_whenTheEditorOpensByGet(OpenPath path) throws Exception {
        CasemgmtNoteLock otherEditor = lock(0L, PROVIDER, session.getId());
        session.setAttribute(CACHE_KEY, otherEditor);

        open("GET", path);

        verify(locks, never()).persist(any());
        verify(locks, never()).merge(any());
        verify(manager, never()).saveNoteSimple(any());
        verify(manager, never()).addNewNoteLink(anyLong());
        assertThat(session.getAttribute(CACHE_KEY)).isSameAs(otherEditor);
        assertThat(form()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(OpenPath.class)
    @DisplayName("should still take and cache this session's lock when the editor opens by POST")
    void shouldTakeTheLock_whenTheEditorOpensByPost(OpenPath path) throws Exception {
        open("POST", path);

        ArgumentCaptor<CasemgmtNoteLock> taken = ArgumentCaptor.forClass(CasemgmtNoteLock.class);
        verify(locks).persist(taken.capture());
        CasemgmtNoteLock lock = taken.getValue();
        assertThat(lock.getNoteId()).isEqualTo(path.lockedNoteId);
        assertThat(lock.getDemographicNo()).isEqualTo(Integer.valueOf(PATIENT));
        assertThat(lock.getProviderNo()).isEqualTo(PROVIDER);
        assertThat(lock.getSessionId()).isEqualTo(session.getId());
        assertThat(session.getAttribute(CACHE_KEY)).isSameAs(lock);
    }

    @Test
    @DisplayName("should keep an old note's original text as its history in the form only when opened by GET")
    void shouldKeepOldNoteHistoryInTheForm_whenOpenedByGet() throws Exception {
        open("GET", OpenPath.SAVED_NOTE);

        assertThat(form().getCaseNote().getId()).isEqualTo(5001L);
        assertThat(form().getCaseNote().getHistory()).isEqualTo(ORIGINAL_TEXT);
        verify(manager, never()).saveNoteSimple(any());
        verify(manager, never()).addNewNoteLink(anyLong());
    }

    @Test
    @DisplayName("should store an old note's history and link when opened by POST")
    void shouldStoreOldNoteHistory_whenOpenedByPost() throws Exception {
        open("POST", OpenPath.SAVED_NOTE);

        verify(manager).saveNoteSimple(argThat(note -> ORIGINAL_TEXT.equals(note.getHistory())));
        verify(manager).addNewNoteLink(5001L);
    }

    @Test
    @DisplayName("should show the saved note and leave every lock alone when another provider holds it, by GET")
    void shouldReadTheSavedNote_whenAnotherProviderHoldsItsLock() throws Exception {
        CasemgmtNoteLock theirs = lock(5001L, OTHER_PROVIDER, "FAKE-their-session");
        when(locks.findByNoteDemo(Integer.valueOf(PATIENT), 5001L)).thenReturn(theirs);
        CasemgmtNoteLock otherEditor = lock(0L, PROVIDER, session.getId());
        session.setAttribute(CACHE_KEY, otherEditor);

        open("GET", OpenPath.SAVED_NOTE);

        assertThat(form().getCaseNote().getId()).isEqualTo(5001L);
        assertThat(form().getCaseNote().getDemographic_no()).isEqualTo(PATIENT);
        verify(locks, never()).persist(any());
        verify(locks, never()).merge(any());
        assertThat(theirs.isLocked()).isFalse();
        assertThat(session.getAttribute(CACHE_KEY)).isSameAs(otherEditor);
    }

    @Test
    @DisplayName("should still open a new note when another provider holds the note, by POST")
    void shouldOpenANewNote_whenAnotherProviderHoldsTheNoteByPost() throws Exception {
        CasemgmtNoteLock theirs = lock(5001L, OTHER_PROVIDER, "FAKE-their-session");
        when(locks.findByNoteDemo(Integer.valueOf(PATIENT), 5001L)).thenReturn(theirs);

        open("POST", OpenPath.SAVED_NOTE);

        assertThat(form().getCaseNote().getId()).isNull();
        verify(locks, never()).persist(any());
        assertThat(theirs.isLocked()).isTrue();
    }

    @Test
    @DisplayName("should keep an old note's original text in the saved history when it is opened by GET, then saved")
    void shouldKeepTheOriginalTextInTheSavedHistory_whenAnOldNoteOpenedByGetIsSaved() throws Exception {
        open("GET", OpenPath.SAVED_NOTE);
        CaseManagementEntryFormBean opened = form();
        assertThat(opened.getCaseNote().getHistory()).isEqualTo(ORIGINAL_TEXT);

        // Struts' session map writes the form through to the HTTP session, and the classic editor's
        // Edit button posts method=isNoteEdited to take this session's lock on the note.
        session.setAttribute("caseManagementEntryForm" + PATIENT, opened);
        CasemgmtNoteLock taken = lock(5001L, PROVIDER, session.getId());
        taken.setId(10L);
        session.setAttribute(CACHE_KEY, taken);
        when(locks.find(10L)).thenReturn(taken);
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", PATIENT)).thenReturn(true);
        CaseManagementNote stored = opened.getCaseNote().copyForRevision();
        stored.setId(6001L);
        ArgumentCaptor<CaseManagementNote> handedOver = ArgumentCaptor.forClass(CaseManagementNote.class);
        when(manager.saveCaseManagementNote(any(), handedOver.capture(), any(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(stored);

        MockHttpServletRequest save = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        save.setContextPath("/carlos");
        save.setSession(session);
        save.setParameter("demographicNo", PATIENT);
        save.setParameter("from", "casemgmt");
        save.setParameter("nId", "5001");
        save.setParameter("noteId", "5001");
        save.setParameter("noteTxt", REVISED_TEXT);
        save.setParameter("numIssues", "0");
        CaseManagementEntry2Action saver = action(save);
        injectDependency(saver, "casemgmtNoteLockDao", locks);
        WebApplicationContext springContext = mock(WebApplicationContext.class);
        ProgramManager programs = mock(ProgramManager.class);
        AdmissionManager admissions = mock(AdmissionManager.class);
        when(springContext.getBean(ProgramManager.class)).thenReturn(programs);
        when(springContext.getBean(AdmissionManager.class)).thenReturn(admissions);
        doReturn(springContext).when(saver).getSpringContext();
        doReturn(REVISED_TEXT).when(saver).getCaseNote_note();
        doReturn("Note saved").when(saver).getText("note.saved");
        doNothing().when(saver).addActionMessage(anyString());
        try (var props = mockStatic(CarlosProperties.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(saver.save()).isEqualTo("view");
        }

        CaseManagementNote revision = handedOver.getValue();
        assertThat(revision.getHistory()).isEqualTo(ORIGINAL_TEXT);
        // The manager's own history step, run on the revision the action handed over.
        CaseManagementNoteDAO notes = mock(CaseManagementNoteDAO.class);
        EChartDao charts = mock(EChartDao.class);
        when(charts.saveEchart(any(CaseManagementNote.class), any(), anyString(), any())).thenReturn("");
        CaseManagementManagerImpl real = new CaseManagementManagerImpl();
        injectDependency(real, "caseManagementNoteDAO", notes);
        injectDependency(real, "eChartDao", charts);
        real.saveNote(null, revision, PROVIDER, "FAKE Doctor", null, "doctor");
        verify(notes).saveNote(revision);
        assertThat(revision.getHistory()).startsWith(REVISED_TEXT).contains(ORIGINAL_TEXT);
    }

    @Test
    void shouldConfirmLockOwnership_onlyForTheSessionThatHoldsIt() throws Exception {
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", PATIENT)).thenReturn(true);
        when(manager.getNote("5001")).thenReturn(note(5001L, ORIGINAL_TEXT));
        CasemgmtNoteLock existing = lock(5001L, PROVIDER, "FAKE-other-session");
        when(locks.findByNoteDemo(Integer.valueOf(PATIENT), 5001L)).thenReturn(existing);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("noteId", "5001");
        ObjectMapper json = new ObjectMapper();

        CaseManagementEntry2Action refused = action(request);
        refused.isNoteEdited();
        var conflict = json.readTree(((MockHttpServletResponse) refused.response).getContentAsString());
        assertThat(conflict.path("isNoteEdited").asText()).isEqualTo("user");
        assertThat(conflict.path("ownedBySession").booleanValue()).isFalse();
        assertThat(session.getAttribute(CACHE_KEY)).isNull();

        CasemgmtNoteLock owned = lock(5001L, PROVIDER, session.getId());
        when(locks.findByNoteDemo(Integer.valueOf(PATIENT), 5001L)).thenReturn(owned);
        CaseManagementEntry2Action accepted = action(request);
        accepted.isNoteEdited();
        var confirmed = json.readTree(((MockHttpServletResponse) accepted.response).getContentAsString());
        assertThat(confirmed.path("isNoteEdited").asText()).isEqualTo("user");
        assertThat(confirmed.path("ownedBySession").booleanValue()).isTrue();
        assertThat(session.getAttribute(CACHE_KEY)).isSameAs(owned);
        verify(locks, never()).persist(any());
        verify(locks, never()).merge(any());
    }

    /** Puts a form where a read-only GET of its note leaves it, as the action keeps them (per note). */
    @SuppressWarnings("unchecked")
    private void putReader(CaseManagementEntryFormBean form) {
        String key = "caseManagementEntryForm" + PATIENT + CaseManagementEntry2Action.READER_FORM_SUFFIX;
        java.util.LinkedHashMap<Long, CaseManagementEntryFormBean> readers =
                (java.util.LinkedHashMap<Long, CaseManagementEntryFormBean>) forms.computeIfAbsent(key, k -> new java.util.LinkedHashMap<Long, CaseManagementEntryFormBean>());
        readers.put(form.getCaseNote().getId(), form);
    }

    /** The reader form kept for a note, or null. */
    @SuppressWarnings("unchecked")
    private CaseManagementEntryFormBean readerOf(long noteId) {
        var readers = (java.util.LinkedHashMap<Long, CaseManagementEntryFormBean>) forms.get(
                "caseManagementEntryForm" + PATIENT + CaseManagementEntry2Action.READER_FORM_SUFFIX);
        return readers == null ? null : readers.get(noteId);
    }

    /** A form for a saved note, as an editor this session opened would hold it. */
    private static CaseManagementEntryFormBean editorForm(long noteId) {
        CaseManagementEntryFormBean form = new CaseManagementEntryFormBean();
        form.setCaseNote(note(noteId, null));
        return form;
    }

    /** This session holds the persisted lock on a note, cached as an open editor's is. */
    private CasemgmtNoteLock holdLock(long noteId, long lockRowId) {
        CasemgmtNoteLock held = lock(noteId, PROVIDER, session.getId());
        held.setId(lockRowId);
        session.setAttribute(CACHE_KEY, held);
        when(locks.find(lockRowId)).thenReturn(held);
        return held;
    }

    @Test
    @DisplayName("should keep an open editor's form when another note is opened by GET only to read it")
    void shouldKeepTheActiveEditorsForm_whenAnotherNoteIsOpenedByGetToRead() throws Exception {
        CaseManagementEntryFormBean editing = editorForm(5001L);
        forms.put("caseManagementEntryForm" + PATIENT, editing);
        holdLock(5001L, 10L);

        MockHttpServletRequest request = openSaved("GET", 5009L);

        // The editor of A is untouched, so A's Save loads A's form; B is shown from its own slot.
        assertThat(form()).isSameAs(editing);
        CaseManagementEntryFormBean reader = readerOf(5009L);
        assertThat(reader.getCaseNote().getId()).isEqualTo(5009L);
        assertThat(request.getAttribute(CaseManagementEntry2Action.READER_FORM_ATTRIBUTE)).isSameAs(reader);
        verify(locks, never()).persist(any());
    }

    @Test
    @DisplayName("should show the opened note from the active slot as before when no editor holds a lock")
    void shouldFillTheActiveSlot_whenNoEditorHoldsItsLock() throws Exception {
        CaseManagementEntryFormBean idle = editorForm(5001L);
        forms.put("caseManagementEntryForm" + PATIENT, idle);

        MockHttpServletRequest request = openSaved("GET", 5009L);

        assertThat(form()).isNotSameAs(idle);
        assertThat(form().getCaseNote().getId()).isEqualTo(5009L);
        assertThat(request.getAttribute(CaseManagementEntry2Action.READER_FORM_ATTRIBUTE)).isSameAs(form());
    }

    @Test
    @DisplayName("should replace the active form when the chart editor opens another note by POST")
    void shouldReplaceTheActiveForm_whenTheEditorOpensAnotherNoteByPost() throws Exception {
        forms.put("caseManagementEntryForm" + PATIENT, editorForm(5001L));
        holdLock(5001L, 10L);
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);

        MockHttpServletRequest request = openSaved("POST", 5009L);

        assertThat(form().getCaseNote().getId()).isEqualTo(5009L);
        assertThat(request.getAttribute(CaseManagementEntry2Action.READER_FORM_ATTRIBUTE)).isNull();
    }

    /** Runs the classic Edit button's request and gives the JSON it answers. */
    private com.fasterxml.jackson.databind.JsonNode pressEdit(long noteId) throws Exception {
        return pressEdit(noteId, note(noteId, ORIGINAL_TEXT));
    }

    /** As {@link #pressEdit(long)}, with the note the database holds under that id. */
    private com.fasterxml.jackson.databind.JsonNode pressEdit(long noteId, CaseManagementNote stored) throws Exception {
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", PATIENT)).thenReturn(true);
        when(manager.getNote(Long.toString(noteId))).thenReturn(stored);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("noteId", Long.toString(noteId));
        // The classic page's Edit button says so; the chart's own editor does not.
        request.setParameter("classicEdit", "true");
        CaseManagementEntry2Action edit = action(request);
        edit.isNoteEdited();
        return new ObjectMapper().readTree(((MockHttpServletResponse) edit.response).getContentAsString());
    }

    @Test
    @DisplayName("should bind the displayed note and its form to the editor when Edit succeeds")
    void shouldBindTheReadersFormToTheEditor_whenEditSucceeds() throws Exception {
        CaseManagementEntryFormBean reader = editorForm(5009L);
        putReader(reader);

        var answer = pressEdit(5009L);

        assertThat(answer.path("ownedBySession").booleanValue()).isTrue();
        // This request created the lock, so a Cancel that races it may give it back.
        assertThat(answer.path("lockTaken").booleanValue()).isTrue();
        assertThat(form()).isSameAs(reader);
    }

    @Test
    @DisplayName("should refuse Edit and keep the active form when another note of this chart is open for editing")
    void shouldRefuseEdit_whenAnotherNoteIsOpenForEditing() throws Exception {
        CaseManagementEntryFormBean editing = editorForm(5001L);
        forms.put("caseManagementEntryForm" + PATIENT, editing);
        holdLock(5001L, 10L);
        putReader(editorForm(5009L));

        var answer = pressEdit(5009L);

        assertThat(answer.path("isNoteEdited").asText()).isEqualTo("editorOpen");
        assertThat(answer.path("ownedBySession").booleanValue()).isFalse();
        assertThat(form()).isSameAs(editing);
        verify(locks, never()).persist(any());
    }

    @Test
    @DisplayName("should not confirm ownership of a displayed revision that is no longer the latest")
    void shouldRefuseEdit_whenTheDisplayedRevisionIsStale() throws Exception {
        CaseManagementNote shown = note(5009L, ORIGINAL_TEXT);
        shown.setUuid("FAKE-chain");
        CaseManagementNote newer = note(5010L, REVISED_TEXT);
        newer.setUuid("FAKE-chain");
        when(manager.getMostRecentNote("FAKE-chain")).thenReturn(newer);
        putReader(editorForm(5009L));
        when(manager.getNote("5009")).thenReturn(shown);
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", PATIENT)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("noteId", "5009");
        CaseManagementEntry2Action edit = action(request);

        edit.isNoteEdited();

        var answer = new ObjectMapper().readTree(((MockHttpServletResponse) edit.response).getContentAsString());
        assertThat(answer.path("isNoteEdited").asText()).isEqualTo("stale");
        assertThat(answer.path("ownedBySession").booleanValue()).isFalse();
        // Nothing was locked and the stale form did not become the editor.
        verify(locks, never()).persist(any());
        assertThat(form()).isNull();
    }

    @Test
    @DisplayName("should give back the lock this request took when the note went stale between the check and the lock")
    void shouldReleaseTheNewLock_whenTheRevisionWentStaleAfterTheFirstCheck() throws Exception {
        CaseManagementNote shown = note(5009L, ORIGINAL_TEXT);
        shown.setUuid("FAKE-chain");
        CaseManagementNote newer = note(5010L, REVISED_TEXT);
        newer.setUuid("FAKE-chain");
        // Latest at the first check, replaced by the time the lock is held.
        when(manager.getMostRecentNote("FAKE-chain")).thenReturn(shown, newer);
        putReader(editorForm(5009L));
        when(manager.getNote("5009")).thenReturn(shown);
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", PATIENT)).thenReturn(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("noteId", "5009");
        CaseManagementEntry2Action edit = action(request);

        edit.isNoteEdited();

        var answer = new ObjectMapper().readTree(((MockHttpServletResponse) edit.response).getContentAsString());
        assertThat(answer.path("isNoteEdited").asText()).isEqualTo("stale");
        assertThat(answer.path("ownedBySession").booleanValue()).isFalse();
        verify(locks).removeForSession(PROVIDER, Integer.valueOf(PATIENT), 5009L, session.getId());
        assertThat(form()).isNull();
    }

    @Test
    @DisplayName("should keep each window's reader form apart: reading C does not hide B's, so B's Edit is still checked")
    void shouldCheckEdit_whenAnotherWindowReadAnotherNoteOfTheChart() throws Exception {
        // A is open for editing; window X reads B, then window Y reads C.
        CaseManagementEntryFormBean editing = editorForm(5001L);
        forms.put("caseManagementEntryForm" + PATIENT, editing);
        holdLock(5001L, 10L);
        openSaved("GET", 5009L);
        openSaved("GET", 5010L);
        assertThat(readerOf(5009L)).isNotNull();
        assertThat(readerOf(5010L)).isNotNull();
        assertThat(form()).isSameAs(editing);

        // X's Edit of B still sees B's reader form: another note is open for editing, so it is refused.
        var refused = pressEdit(5009L);
        assertThat(refused.path("isNoteEdited").asText()).isEqualTo("editorOpen");
        assertThat(refused.path("ownedBySession").booleanValue()).isFalse();
        assertThat(form()).isSameAs(editing);
        verify(locks, never()).persist(any());
    }

    @Test
    @DisplayName("should refuse a stale Edit even when another window has read a different note since")
    void shouldRefuseAStaleEdit_whenAnotherWindowReadAnotherNote() throws Exception {
        CaseManagementNote shown = note(5009L, ORIGINAL_TEXT);
        shown.setUuid("FAKE-chain");
        CaseManagementNote newer = note(5011L, REVISED_TEXT);
        newer.setUuid("FAKE-chain");
        when(manager.getMostRecentNote("FAKE-chain")).thenReturn(newer);
        openSaved("GET", 5009L);
        openSaved("GET", 5010L);

        var answer = pressEdit(5009L, shown);

        assertThat(answer.path("isNoteEdited").asText()).isEqualTo("stale");
        verify(locks, never()).persist(any());
    }

    @Test
    @DisplayName("should refuse the classic Edit, asking for a reload, when its reader form was dropped")
    void shouldRefuseEditAndAskForAReload_whenTheReaderFormWasEvicted() throws Exception {
        // B is read; then more notes than the chart keeps are opened; B's page then presses Edit.
        CaseManagementEntryFormBean editing = editorForm(5001L);
        forms.put("caseManagementEntryForm" + PATIENT, editing);
        holdLock(5001L, 10L);
        openSaved("GET", 5009L);
        for (long id = 6001L; id < 6001L + CaseManagementEntry2Action.MAX_READER_FORMS; id++) {
            openSaved("GET", id);
        }
        assertThat(readerOf(5009L)).isNull();

        var answer = pressEdit(5009L);

        // The stale and editorOpen checks could not be made, so nothing is granted and nothing is locked.
        assertThat(answer.path("isNoteEdited").asText()).isEqualTo("reload");
        assertThat(answer.path("ownedBySession").booleanValue()).isFalse();
        assertThat(answer.path("lockTaken").booleanValue()).isFalse();
        verify(locks, never()).persist(any());
        assertThat(form()).isSameAs(editing);
    }

    @Test
    @DisplayName("should not refuse a lock request from the chart's own editor that has no reader form")
    void shouldNotRequireAReaderForm_ofTheChartsOwnEditor() throws Exception {
        when(security.hasPrivilege(login, "_eChart", "w", PATIENT)).thenReturn(true);
        when(security.hasPrivilege(login, "_casemgmt.notes", "w", PATIENT)).thenReturn(true);
        when(manager.getNote("5001")).thenReturn(note(5001L, ORIGINAL_TEXT));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("noteId", "5001");
        CaseManagementEntry2Action edit = action(request);

        edit.isNoteEdited();

        var answer = new ObjectMapper().readTree(((MockHttpServletResponse) edit.response).getContentAsString());
        assertThat(answer.path("ownedBySession").booleanValue()).isTrue();
        assertThat(answer.path("isNoteEdited").asText()).isNotEqualTo("reload");
    }

    @Test
    @DisplayName("should say it took no lock when the same user's other window already holds the row, so Cancel keeps it")
    void shouldReportLockTakenFalse_whenTheSameUsersOtherWindowHoldsTheRow() throws Exception {
        putReader(editorForm(5009L));
        // The other window of this session already holds the persisted row.
        CasemgmtNoteLock held = lock(5009L, PROVIDER, session.getId());
        held.setId(11L);
        when(locks.findByNoteDemo(Integer.valueOf(PATIENT), 5009L)).thenReturn(held);

        var answer = pressEdit(5009L);

        assertThat(answer.path("ownedBySession").booleanValue()).isTrue();
        // Not this request's lock: the page's Cancel must never give it back.
        assertThat(answer.path("lockTaken").booleanValue()).isFalse();
        verify(locks, never()).persist(any());
        verify(locks, never()).removeForSession(anyString(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("should keep only the most recent reader forms of a chart")
    void shouldBoundTheReaderForms() throws Exception {
        for (long id = 6001L; id < 6001L + CaseManagementEntry2Action.MAX_READER_FORMS + 3; id++) {
            openSaved("GET", id);
        }
        assertThat(readerOf(6001L)).isNull();
        assertThat(readerOf(6003L)).isNull();
        assertThat(readerOf(6004L)).isNotNull();
        assertThat(readerOf(6001L + CaseManagementEntry2Action.MAX_READER_FORMS + 2)).isNotNull();
    }

    @Test
    @DisplayName("should tell the classic page it still owns the note after a POST, and never after a GET")
    void shouldMarkTheLockOwned_onlyForAPostThatRendersTheNoteItHolds() throws Exception {
        forms.put("caseManagementEntryForm" + PATIENT, editorForm(5001L));
        holdLock(5001L, 10L);

        MockHttpServletRequest post = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        post.setSession(session);
        post.setParameter("demographicNo", PATIENT);
        ReflectionTestUtils.invokeMethod(action(post), "markNoteLockOwned", PATIENT);
        assertThat(post.getAttribute(CaseManagementEntry2Action.NOTE_LOCK_OWNED_ATTRIBUTE)).isEqualTo("true");

        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/carlos/CaseManagementEntry");
        get.setSession(session);
        get.setParameter("demographicNo", PATIENT);
        ReflectionTestUtils.invokeMethod(action(get), "markNoteLockOwned", PATIENT);
        assertThat(get.getAttribute(CaseManagementEntry2Action.NOTE_LOCK_OWNED_ATTRIBUTE)).isNull();

        // A lock another session holds does not count.
        CasemgmtNoteLock theirs = lock(5001L, PROVIDER, "FAKE-other-session");
        theirs.setId(10L);
        when(locks.find(10L)).thenReturn(theirs);
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/carlos/CaseManagementEntry");
        other.setSession(session);
        other.setParameter("demographicNo", PATIENT);
        ReflectionTestUtils.invokeMethod(action(other), "markNoteLockOwned", PATIENT);
        assertThat(other.getAttribute(CaseManagementEntry2Action.NOTE_LOCK_OWNED_ATTRIBUTE)).isNull();
    }

    private void open(String httpMethod, OpenPath path) throws Exception {
        openRequest(httpMethod, path);
    }

    /** Opens a saved note other than the one {@link OpenPath#SAVED_NOTE} stands for. */
    private MockHttpServletRequest openSaved(String httpMethod, long noteId) throws Exception {
        CaseManagementNote saved = note(noteId, null);
        when(manager.getNote(Long.toString(noteId))).thenReturn(saved);
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/carlos/CaseManagementEntry");
        request.setContextPath("/carlos");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("from", "casemgmt");
        request.setParameter("noteId", Long.toString(noteId));
        try (var props = mockStatic(CarlosProperties.class);
             var _ = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }
        return request;
    }

    private MockHttpServletRequest openRequest(String httpMethod, OpenPath path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/carlos/CaseManagementEntry");
        request.setContextPath("/carlos");
        request.setSession(session);
        request.setParameter("demographicNo", PATIENT);
        request.setParameter("from", "casemgmt");
        switch (path) {
            case SAVED_NOTE -> {
                request.setParameter("noteId", "5001");
                when(manager.getNote("5001")).thenReturn(note(5001L, null));
            }
            case LAST_UNSIGNED -> when(manager.getLastSaved(any(), eq(PATIENT), eq(PROVIDER)))
                    .thenReturn(note(5002L, "FAKE earlier history"));
            case NEW_WHEN_NOTHING_UNSIGNED -> {
                // The unstubbed lookup returns no unsigned note.
            }
            case EXPLICIT_NEW -> request.setParameter("note_edit", "new");
            case DRAFT -> {
                CaseManagementTmpSave draft = new CaseManagementTmpSave();
                draft.setNoteId(5003);
                draft.setNote("FAKE draft text");
                when(manager.restoreTmpSave(eq(PROVIDER), eq(PATIENT), any())).thenReturn(draft);
                when(manager.getNote("5003")).thenReturn(note(5003L, "FAKE earlier history"));
            }
        }
        try (var props = mockStatic(CarlosProperties.class);
             var _ = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }
        return request;
    }

    private CaseManagementEntry2Action action(MockHttpServletRequest request) {
        CaseManagementEntry2Action action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        action.request = request;
        action.response = new MockHttpServletResponse();
        action.caseManagementMgr = manager;
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        action.withSession(forms);
        ReflectionTestUtils.setField(action, "casemgmtNoteLockDao", locks);
        doReturn("FAKE Patient").when(action).getDemoName(anyString());
        doReturn("40").when(action).getDemoAge(anyString());
        doReturn("1986-01-01").when(action).getDemoDOB(anyString());
        return action;
    }

    private CaseManagementEntryFormBean form() {
        return (CaseManagementEntryFormBean) forms.get("caseManagementEntryForm" + PATIENT);
    }

    private static CaseManagementNote note(long id, String history) {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(id);
        note.setDemographic_no(PATIENT);
        note.setNote(ORIGINAL_TEXT);
        note.setHistory(history);
        note.setProviderNo(PROVIDER);
        Provider provider = new Provider();
        provider.setProviderNo(PROVIDER);
        note.setProvider(provider);
        return note;
    }

    private static CasemgmtNoteLock lock(long noteId, String providerNo, String sessionId) {
        CasemgmtNoteLock lock = new CasemgmtNoteLock();
        lock.setNoteId(noteId);
        lock.setDemographicNo(Integer.valueOf(PATIENT));
        lock.setProviderNo(providerNo);
        lock.setSessionId(sessionId);
        return lock;
    }
}
