/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionCriteria;
import io.github.carlos_emr.carlos.casemgmt.service.NoteSelectionResult;
import io.github.carlos_emr.carlos.casemgmt.service.NoteService;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The eChart loads its notes through {@code viewNotesOpt}, and ChartNotesAjax.jsp reads the logged-in
 * provider's stale date ({@code cme_note_date}) from the request. Without it, it applied a one-year
 * stale date to everyone. The single-line format ({@code cme_note_format}) stays unpassed: its page
 * script expands the notes it should fold onto one line.
 *
 * @since 2026-10-16
 */
@Tag("unit")
@DisplayName("eChart note list passes the stale date")
class CaseManagementViewStaleNotePreferencesUnitTest extends CarlosUnitTestBase {
    private CaseManagementView2Action action;
    private MockHttpServletRequest request;
    private CaseManagementManager manager;

    @BeforeEach
    void prepare() {
        action = mock(CaseManagementView2Action.class, CALLS_REAL_METHODS);
        request = new MockHttpServletRequest();
        action.request = request;
        request.setParameter("demographicNo", "101");
        request.getSession().setAttribute("userrole", "doctor");
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("4249");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        manager = mock(CaseManagementManager.class);
        action.caseManagementMgr = manager;
        when(manager.isClientInProgramDomain("4249", "101")).thenReturn(true);
        NoteService noteService = mock(NoteService.class);
        ReflectionTestUtils.setField(action, "noteService", noteService);
        when(noteService.findNotes(eq(user), any(NoteSelectionCriteria.class))).thenReturn(new NoteSelectionResult());
    }

    private static UserProperty property(String name, String value) {
        UserProperty property = new UserProperty();
        property.setProviderNo("4249");
        property.setName(name);
        property.setValue(value);
        return property;
    }

    @Test
    @DisplayName("should pass the logged-in provider's stale date to the note list")
    void shouldPassStaleDate_whenProviderHasOne() {
        UserProperty staleDate = property(UserProperty.STALE_NOTEDATE, "-1");
        when(manager.getUserProperty("4249", UserProperty.STALE_NOTEDATE)).thenReturn(staleDate);

        assertThat(action.viewNotesOpt()).isEqualTo("ajaxDisplayNotes");

        assertThat(request.getAttribute(UserProperty.STALE_NOTEDATE)).isSameAs(staleDate);
        verify(manager).getUserProperty("4249", UserProperty.STALE_NOTEDATE);
    }

    @Test
    @DisplayName("should leave the note list's one-year default when the provider has no stale date")
    void shouldLeaveDefault_whenProviderHasNoStaleDate() {
        assertThat(action.viewNotesOpt()).isEqualTo("ajaxDisplayNotes");

        assertThat(request.getAttribute(UserProperty.STALE_NOTEDATE)).isNull();
    }

    @Test
    @DisplayName("should not pass the single-line format, whose page script would expand stale notes")
    void shouldNotPassSingleLineFormat_forDefaultCase() {
        when(manager.getUserProperty("4249", UserProperty.STALE_FORMAT)).thenReturn(property(UserProperty.STALE_FORMAT, "yes"));

        action.viewNotesOpt();

        assertThat(request.getAttribute(UserProperty.STALE_FORMAT)).isNull();
    }
}
