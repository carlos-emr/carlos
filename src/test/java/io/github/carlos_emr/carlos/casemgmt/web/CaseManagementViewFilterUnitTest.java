/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

import io.github.carlos_emr.carlos.casemgmt.common.ChartSessionKeys;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionCriteria;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionResult;
import io.github.carlos_emr.carlos.casemgmt.service.NoteService;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exercises the filter handoff through the real note-list action and selection criteria. The
 * handoff and the filters remembered for the chart print belong to one patient's chart (issue
 * #4673): another chart's note list neither consumes nor applies them, and a note list that names
 * no patient is refused instead of being served for the chart opened last.
 *
 * @since 2026-10-14
 */
@Tag("unit")
class CaseManagementViewFilterUnitTest extends CarlosUnitTestBase {
    private CaseManagementView2Action action;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private NoteService noteService;
    private LoggedInInfo user;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        action.request = request;
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        action.securityInfoManager = security;
        action.response = response;
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
        seedSessionFilters("101");
    }

    /** The editor's one-use handoff to the next note list of {@code patient}'s chart. */
    private void seedSessionFilters(String patient) {
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("note_sort", patient), "observation_date_desc");
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("filter_roles", patient), new String[]{"7"});
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("filter_provider", patient), new String[]{"200"});
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("issues", patient), new String[]{"73"});
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
            assertThat(request.getSession().getAttribute(ChartSessionKeys.noteFilterHandoff(name, "101"))).isNull();
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
    void shouldPreferFormProviderSelection_whenLegacyProviderHandoffsArePending() {
        seedSessionFilters();
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("filter_providers", "101"),
                new String[]{"202"});
        request.setParameter("filter_providers", "203", "204");
        request.setParameter("filter_provider", "205");

        assertThat(loadNotes().getProviders()).containsExactly("203", "204");
        assertThat(request.getSession().getAttribute(ChartSessionKeys.noteFilterHandoff("filter_providers", "101")))
                .isNull();
        assertThat(request.getSession().getAttribute(ChartSessionKeys.noteFilterHandoff("filter_provider", "101")))
                .isNull();
        request.removeParameter("filter_providers");
        request.removeParameter("filter_provider");
        assertUnfiltered(loadNotes());
    }

    @Test
    void shouldClearLegacyProviderHandoff_whenFormExplicitlySelectsNoProviders() {
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("filter_provider", "101"),
                new String[]{"200"});
        request.setParameter("filter_providers", new String[0]);

        assertThat(loadNotes().getProviders()).isEmpty();
        request.removeParameter("filter_providers");
        assertUnfiltered(loadNotes());
    }

    @Test
    void shouldConsumeLegacySingleIssue_whenSessionStoresAString() {
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("issues", "101"), "73");
        assertThat(loadNotes().getIssues()).containsExactly("73");
        assertUnfiltered(loadNotes());
    }

    @Test
    void shouldDiscardPreviousSort_whenAnExplicitEmptySortOverridesIt() {
        request.getSession().setAttribute(ChartSessionKeys.noteFilterHandoff("note_sort", "101"), "observation_date_desc");
        request.setParameter("note_sort", "");
        assertUnfiltered(loadNotes());
        request.removeParameter("note_sort");
        assertUnfiltered(loadNotes());
    }
    @Test
    void shouldLeaveAnotherChartsHandoffUntouched_whenThisChartLoadsItsNotes() {
        seedSessionFilters("202");
        request.getSession().setAttribute("issues", new String[]{"99"});

        assertUnfiltered(loadNotes());

        // B's editor handed its filters to B's next note list; A's note list must not take them.
        assertThat((String[]) request.getSession().getAttribute(ChartSessionKeys.noteFilterHandoff("issues", "202")))
                .containsExactly("73");
        assertThat(request.getSession().getAttribute(ChartSessionKeys.noteFilterHandoff("note_sort", "202")))
                .isEqualTo("observation_date_desc");
    }

    @Test
    void shouldKeepNoCopyOfTheAppliedFilters_whenTheNoteListIsFiltered() {
        request.setParameter("filter_roles", "8");
        request.setParameter("filter_provider", "201");
        request.setParameter("issues", "74");

        loadNotes();

        // The applied filters used to be copied into login-wide session attributes for chart-print
        // helpers that nothing called (issue #4673); no copy is kept, keyed or not.
        assertThat(java.util.Collections.list(request.getSession().getAttributeNames()))
                .noneMatch(name -> name.startsWith("CaseManagementViewAction_filter_"));
    }

    @Test
    void shouldRefuseWithoutReadingNotes_whenTheRequestNamesNoPatient() {
        request.removeParameter("demographicNo");
        // What the action used to fall back to: the chart opened last in any window.
        request.getSession().setAttribute("casemgmt_DemoNo", "202");

        assertThat(action.viewNotesOpt()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(noteService);
    }

    @Test
    void shouldRefuseWithoutReadingNotes_whenTheRequestNamesTwoPatients() {
        request.setParameter("demographic_no", "202");

        assertThat(action.viewNotesOpt()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(noteService);
    }

    @Test
    void shouldNotPublishItsPatientToTheSession_whenNotesLoad() {
        loadNotes();

        assertThat(request.getSession().getAttribute("casemgmt_DemoNo")).isNull();
    }
}
