/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionCriteria;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionResult;
import io.github.carlos_emr.carlos.casemgmt.service.NoteService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Exercises the filter handoff through the real note-list action and selection criteria. */
@Tag("unit")
class CaseManagementViewFilterUnitTest extends CarlosUnitTestBase {
    private CaseManagementView2Action action;
    private MockHttpServletRequest request;
    private NoteService noteService;
    private LoggedInInfo user;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        action.request = request;
        request.setParameter("demographicNo", "101");
        request.getSession().setAttribute("userrole", "doctor");
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        CaseManagementManager manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        noteService = mock(NoteService.class);
        ReflectionTestUtils.setField(action, "noteService", noteService);
        when(noteService.findNotes(eq(user), any(NoteSelectionCriteria.class))).thenReturn(new NoteSelectionResult());
    }

    private NoteSelectionCriteria loadNotes() {
        clearInvocations(noteService);
        assertThat(action.viewNotesOpt()).isEqualTo("ajaxDisplayNotes");
        ArgumentCaptor<NoteSelectionCriteria> criteria = ArgumentCaptor.forClass(NoteSelectionCriteria.class);
        verify(noteService).findNotes(eq(user), criteria.capture());
        return criteria.getValue();
    }

    private void seedSessionFilters() {
        request.getSession().setAttribute("note_sort", "observation_date_desc");
        request.getSession().setAttribute("filter_roles", new String[]{"7"});
        request.getSession().setAttribute("filter_provider", new String[]{"200"});
        request.getSession().setAttribute("issues", new String[]{"73"});
    }

    private void assertUnfiltered(NoteSelectionCriteria criteria) {
        assertThat(criteria.getNoteSort()).isNull();
        assertThat(criteria.getRoles()).isEmpty();
        assertThat(criteria.getProviders()).isEmpty();
        assertThat(criteria.getIssues()).isEmpty();
    }

    @Test
    void shouldDiscardOverriddenSessionFilters_whenTheFollowingRequestClearsSelections() {
        seedSessionFilters();
        request.setParameter("note_sort", "observation_date_asc");
        request.setParameter("filter_roles", "8");
        request.setParameter("filter_provider", "201");
        request.setParameter("issues", "74", "75");
        NoteSelectionCriteria applied = loadNotes();
        assertThat(applied.getNoteSort()).isEqualTo("observation_date_asc");
        assertThat(applied.getRoles()).containsExactly("8");
        assertThat(applied.getProviders()).containsExactly("201");
        assertThat(applied.getIssues()).containsExactly("74", "75");
        for (String name : new String[]{"note_sort", "filter_roles", "filter_provider", "issues"}) {
            assertThat(request.getSession().getAttribute(name)).isNull();
            request.removeParameter(name);
        }
        assertUnfiltered(loadNotes());
    }

    @Test
    void shouldUseSessionFiltersOnlyOnce_whenTheRequestHasNoSelections() {
        seedSessionFilters();
        NoteSelectionCriteria applied = loadNotes();
        assertThat(applied.getNoteSort()).isEqualTo("observation_date_desc");
        assertThat(applied.getRoles()).containsExactly("7");
        assertThat(applied.getProviders()).containsExactly("200");
        assertThat(applied.getIssues()).containsExactly("73");
        assertUnfiltered(loadNotes());
    }

    @Test
    void shouldConsumeLegacySingleIssue_whenSessionStoresAString() {
        request.getSession().setAttribute("issues", "73");
        assertThat(loadNotes().getIssues()).containsExactly("73");
        assertUnfiltered(loadNotes());
    }

    @Test
    void shouldDiscardPreviousSort_whenAnExplicitEmptySortOverridesIt() {
        request.getSession().setAttribute("note_sort", "observation_date_desc");
        request.setParameter("note_sort", "");
        assertUnfiltered(loadNotes());
        request.removeParameter("note_sort");
        assertUnfiltered(loadNotes());
    }
}
