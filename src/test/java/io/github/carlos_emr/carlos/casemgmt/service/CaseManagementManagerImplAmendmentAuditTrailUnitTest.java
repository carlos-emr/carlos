/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.service;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests that lock in the clinical-note amendment audit-trail contract
 * enforced by {@link CaseManagementManagerImpl#saveNote}.
 *
 * <p>Context: issue #207 (<em>Review: Ensure clinical note amendments maintain audit
 * trail</em>) was opened from an automated comparison with the JunoEMR codebase.
 * Investigation confirmed that CARLOS EMR already satisfies the amendment audit
 * requirements:</p>
 * <ul>
 *   <li>original content is preserved in the {@code history} field with a
 *       {@code ----------------History Record----------------} separator (never
 *       deleted);</li>
 *   <li>{@code revision} is incremented on every save in
 *       {@link CaseManagementManagerImpl#saveCaseManagementNote};</li>
 *   <li>{@code update_date} and {@code providerNo} record who amended the note
 *       and when;</li>
 *   <li>every save is recorded through {@code LogAction.addLog} with
 *       {@code ADD}/{@code UPDATE}/{@code EDIT} and the note's audit string.</li>
 * </ul>
 *
 * <p>The domain-expert consensus on the issue (per @phc007: <em>"once it's saved
 * it's saved, you can edit and resave but that is a different version and should
 * be logged"</em>) matches this current behavior. These tests guard the
 * history-preservation half of the contract — the portion of the audit trail that
 * lives entirely in {@code saveNote} and is most susceptible to a silent
 * refactoring regression.</p>
 *
 * @since 2026-04-21
 * @see CaseManagementManagerImpl#saveNote
 * @see CaseManagementNote
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CaseManagementManagerImpl.saveNote() amendment audit-trail contract")
@Tag("unit")
@Tag("fast")
@Tag("manager")
@Tag("casemgmt")
@Tag("audit")
public class CaseManagementManagerImplAmendmentAuditTrailUnitTest extends CarlosUnitTestBase {

    /**
     * Verbatim separator written by {@link CaseManagementManagerImpl#saveNote}.
     *
     * <p>Intentionally hard-coded here (not shared with production code) so that any
     * change to the separator format — including whitespace — trips this test. An
     * amendment separator is part of the medical-legal audit-record format; silent
     * changes must surface immediately.</p>
     */
    private static final String HISTORY_SEPARATOR =
            "   ----------------History Record----------------   ";

    private static final String TEST_PROVIDER_NO = "999998";
    private static final String TEST_DEMOGRAPHIC_NO = "1001";
    private static final String TEST_USER_NAME = "Dr. Smith";
    private static final String TEST_ROLE_NAME = "doctor";

    @Mock
    private CaseManagementNoteDAO mockNoteDao;

    @Mock
    private EChartDao mockEChartDao;

    // Required by CaseManagementNote's static field initializer (SpringUtils.getBean).
    @Mock
    private CaseManagementNoteLinkDAO mockNoteLinkDao;

    @Mock
    private ProgramProviderDAO mockProgramProviderDao;

    @Mock
    private ProgramAccessDAO mockProgramAccessDao;

    @Mock
    private RoleProgramAccessDAO mockRoleProgramAccessDao;

    private CaseManagementManagerImpl manager;

    @BeforeEach
    void setUp() {
        // Register mocks required by CaseManagementNote's field initializers
        // (the model calls SpringUtils.getBean() during construction).
        registerMock(CaseManagementNoteLinkDAO.class, mockNoteLinkDao);
        registerMock(ProgramProviderDAO.class, mockProgramProviderDao);
        registerMock(ProgramAccessDAO.class, mockProgramAccessDao);
        registerMock(RoleProgramAccessDAO.class, mockRoleProgramAccessDao);

        manager = new CaseManagementManagerImpl();
        injectDependency(manager, "caseManagementNoteDAO", mockNoteDao);
        injectDependency(manager, "eChartDao", mockEChartDao);

        // eChartDao.saveEchart is the tail call of saveNote; stub a harmless return.
        when(mockEChartDao.saveEchart(any(CaseManagementNote.class), any(), anyString(), any()))
                .thenReturn("");
    }

    private CaseManagementNote noteWithContent(String content) {
        CaseManagementNote note = new CaseManagementNote();
        note.setNote(content);
        note.setDemographic_no(TEST_DEMOGRAPHIC_NO);
        note.setProviderNo(TEST_PROVIDER_NO);
        return note;
    }

    @Test
    @DisplayName("should set history to current note content when saving a brand-new note")
    @Tag("create")
    void shouldSetHistoryToNoteContent_whenSavingFirstTime() {
        // Given: a brand-new note with no prior history
        CaseManagementNote note = noteWithContent("Initial assessment: patient stable.");

        // When: it is saved for the first time
        manager.saveNote(null, note, TEST_PROVIDER_NO, TEST_USER_NAME, null, TEST_ROLE_NAME);

        // Then: history is seeded with the current content so the first revision is
        // part of the permanent record.
        assertThat(note.getHistory()).isEqualTo("Initial assessment: patient stable.");
        verify(mockNoteDao, times(1)).saveNote(note);
    }

    @Test
    @DisplayName("should prepend new content with separator when amending existing note")
    @Tag("update")
    void shouldPrependNewContentWithSeparator_whenAmendingExistingNote() {
        // Given: a note that already carries history from a previous save
        CaseManagementNote note = noteWithContent("Amended: added BP reading.");
        note.setHistory("Initial assessment: patient stable.");

        // When: it is saved again (i.e. amended)
        manager.saveNote(null, note, TEST_PROVIDER_NO, TEST_USER_NAME, null, TEST_ROLE_NAME);

        // Then: the new content is prepended, the "History Record" separator is
        // emitted verbatim, and the original content is still present at the tail.
        String history = note.getHistory();
        assertThat(history).startsWith("Amended: added BP reading.\n");
        assertThat(history).contains(HISTORY_SEPARATOR);
        assertThat(history).contains("Initial assessment: patient stable.");
        verify(mockNoteDao, times(1)).saveNote(note);
    }

    @Test
    @DisplayName("should preserve original content across multiple amendments")
    @Tag("update")
    void shouldPreserveOriginalContent_acrossMultipleAmendments() {
        // Given: the first save establishes the baseline history
        CaseManagementNote note = noteWithContent("Original: presenting complaint.");
        manager.saveNote(null, note, TEST_PROVIDER_NO, TEST_USER_NAME, null, TEST_ROLE_NAME);

        // When: two successive amendments are applied
        note.setNote("Amendment 1: added allergy info.");
        manager.saveNote(null, note, TEST_PROVIDER_NO, TEST_USER_NAME, null, TEST_ROLE_NAME);

        note.setNote("Amendment 2: added medication list.");
        manager.saveNote(null, note, TEST_PROVIDER_NO, TEST_USER_NAME, null, TEST_ROLE_NAME);

        // Then: both amendments AND the original content are all still present.
        // This is the key medical-legal guarantee: amendments append; originals
        // are never overwritten or deleted.
        String history = note.getHistory();
        assertThat(history).contains("Original: presenting complaint.");
        assertThat(history).contains("Amendment 1: added allergy info.");
        assertThat(history).contains("Amendment 2: added medication list.");

        // And the separator appears once per amendment (two amendments -> two separators).
        int separatorCount = history.split(java.util.regex.Pattern.quote(HISTORY_SEPARATOR), -1).length - 1;
        assertThat(separatorCount).isEqualTo(2);

        // And every save was persisted (DAO invoked three times: 1 create + 2 amendments).
        verify(mockNoteDao, times(3)).saveNote(note);
    }

    @Test
    @DisplayName("should normalize CRLF and CR line endings to LF before storing in history")
    @Tag("update")
    void shouldNormalizeLineEndings_beforeStoringInHistory() {
        // Given: note content with mixed CRLF / CR line endings (as might arrive
        // from a Windows- or classic-Mac-encoded client)
        CaseManagementNote note = noteWithContent("line1\r\nline2\rline3");

        // When: it is saved
        manager.saveNote(null, note, TEST_PROVIDER_NO, TEST_USER_NAME, null, TEST_ROLE_NAME);

        // Then: the history is stored with LF-only line endings so the audit
        // record is consistent regardless of client platform.
        assertThat(note.getHistory()).isEqualTo("line1\nline2\nline3");
        assertThat(note.getHistory()).doesNotContain("\r");
    }
}
