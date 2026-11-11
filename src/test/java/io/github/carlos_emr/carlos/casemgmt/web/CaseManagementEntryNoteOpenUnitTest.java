/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

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
 * old note's history. The classic editor page takes its lock by POST once it has loaded
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
    @DisplayName("should open a new note and leave every lock alone when another provider holds the note, by GET")
    void shouldOpenANewNote_whenAnotherProviderHoldsTheNoteByGet() throws Exception {
        CasemgmtNoteLock theirs = lock(5001L, OTHER_PROVIDER, "FAKE-their-session");
        when(locks.findByNoteDemo(Integer.valueOf(PATIENT), 5001L)).thenReturn(theirs);
        CasemgmtNoteLock otherEditor = lock(0L, PROVIDER, session.getId());
        session.setAttribute(CACHE_KEY, otherEditor);

        open("GET", OpenPath.SAVED_NOTE);

        assertThat(form().getCaseNote().getId()).isNull();
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
        // page-load POST (method=isNoteEdited) takes this session's lock on the note.
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
        when(springContext.getBean(ProgramManager.class)).thenReturn(mock(ProgramManager.class));
        when(springContext.getBean(AdmissionManager.class)).thenReturn(mock(AdmissionManager.class));
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

    private void open(String httpMethod, OpenPath path) throws Exception {
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
            case NEW_WHEN_NOTHING_UNSIGNED -> { }
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
             var views = mockConstruction(CaseManagementView2Action.class)) {
            props.when(CarlosProperties::getInstance).thenReturn(properties);
            assertThat(action(request).edit()).isEqualTo("view");
        }
    }

    private CaseManagementEntry2Action action(MockHttpServletRequest request) {
        CaseManagementEntry2Action action = mock(CaseManagementEntry2Action.class, CALLS_REAL_METHODS);
        action.request = request;
        action.response = new MockHttpServletResponse();
        action.caseManagementMgr = manager;
        ReflectionTestUtils.setField(action, "securityInfoManager", security);
        action.withSession(forms);
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
