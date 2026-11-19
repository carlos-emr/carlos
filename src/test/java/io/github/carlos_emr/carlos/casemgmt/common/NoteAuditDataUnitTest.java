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
package io.github.carlos_emr.carlos.casemgmt.common;

import java.util.Set;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramAccessDAO;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramProviderDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteLinkDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.RoleProgramAccessDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * What a chart note's audit row carries in {@code log.data}: enough to tell the revision apart,
 * nothing a clinician typed.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("casemgmt")
@Tag("security")
@DisplayName("Chart note audit data")
class NoteAuditDataUnitTest extends CarlosUnitTestBase {

    private static final String NOTE_TEXT = "Jane Roe reports chest pain";
    private static final String ISSUE_TEXT = "Hypertension";

    @BeforeEach
    void registerNoteCollaborators() {
        // CaseManagementNote's field initializers look these up when a note is constructed.
        registerMock(CaseManagementNoteLinkDAO.class, mock(CaseManagementNoteLinkDAO.class));
        registerMock(ProgramProviderDAO.class, mock(ProgramProviderDAO.class));
        registerMock(ProgramAccessDAO.class, mock(ProgramAccessDAO.class));
        registerMock(RoleProgramAccessDAO.class, mock(RoleProgramAccessDAO.class));
    }

    private static CaseManagementNote note() {
        CaseManagementNote note = new CaseManagementNote();
        note.setId(501L);
        note.setUuid("0b4c6f6e-7f1d-4c58-9a0e-2f1f5f2a9d11");
        note.setNote(NOTE_TEXT);
        note.setHistory(NOTE_TEXT);
        Issue issue = new Issue();
        issue.setDescription(ISSUE_TEXT);
        CaseManagementIssue assigned = new CaseManagementIssue();
        assigned.setIssue(issue);
        note.setIssues(Set.of(assigned));
        return note;
    }

    @Test
    void shouldDescribeTheRevision_withoutTheNoteOrIssueText() {
        CaseManagementNote note = note();
        note.setSigned(true);

        String data = NoteAuditData.describe(note);

        assertThat(data).isEqualTo("uuid=0b4c6f6e-7f1d-4c58-9a0e-2f1f5f2a9d11; signed=true; issues=1");
        assertThat(data).doesNotContain("Jane Roe").doesNotContain("chest pain").doesNotContain(ISSUE_TEXT);
        // The text the audit row used to carry.
        assertThat(note.getAuditString()).contains(NOTE_TEXT).contains(ISSUE_TEXT);
    }

    @Test
    void shouldStillDescribeTheRevision_whenTheNoteHasNoThreadIdOrIssues() {
        CaseManagementNote note = new CaseManagementNote();
        note.setNote(NOTE_TEXT);
        note.setIssues(null);

        assertThat(NoteAuditData.describe(note)).isEqualTo("signed=false; issues=0");
    }

    @Test
    void shouldLeaveOutAThreadId_whenItIsNotAnIdentifier() {
        CaseManagementNote note = note();
        // uuid is generated, but the column is plain text: anything else is not copied.
        note.setUuid(NOTE_TEXT);

        assertThat(NoteAuditData.describe(note)).isEqualTo("signed=false; issues=1");
    }

    @Test
    void shouldReturnNoData_forNoNote() {
        assertThat(NoteAuditData.describe(null)).isNull();
    }
}
