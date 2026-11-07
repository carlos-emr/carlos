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
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import java.util.Collection;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real issue joins must retain independent notes before chart-role filtering or CPP printing.
 *
 * @since 2026-10-25
 */
class CaseManagementIssueNoteOrderingIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private static final int PATIENT = 39525;
    private static final long OBSERVED = 1_770_112_800_000L;

    @ParameterizedTest
    @ValueSource(strings = {"MedHistory", "OMeds"})
    void shouldKeepDistinctNotes_whenObservationDatesMatch(String code) {
        CaseManagementIssue issue = issue(PATIENT, code);
        CaseManagementNote visible = linked(issue, UUID.randomUUID().toString(), OBSERVED, OBSERVED,
                "FAKE visible shared-program CPP café 中文");
        visible.setReporter_caisi_role("1");
        visible.setProgram_no("8");
        CaseManagementNote hidden = linked(issue, UUID.randomUUID().toString(), OBSERVED, OBSERVED + 1_000,
                "FAKE other reporter-role CPP");
        hidden.setReporter_caisi_role("2");
        hidden.setProgram_no("7");
        entityManager.flush();
        entityManager.clear();

        Collection<CaseManagementNote> result = find(code);

        assertThat(result).extracting(CaseManagementNote::getId).containsExactly(visible.getId(), hidden.getId());
        assertThat(result).extracting(CaseManagementNote::getNote)
                .containsExactly("FAKE visible shared-program CPP café 中文", "FAKE other reporter-role CPP");
    }

    @Test
    void shouldChooseNewestUpdatePerUuid_thenOrderByObservationDateAndId() {
        CaseManagementIssue issue = issue(PATIENT, "MedHistory");
        String revised = UUID.randomUUID().toString();
        CaseManagementNote current = linked(issue, revised, OBSERVED, OBSERVED + 5_000, "FAKE current revision");
        CaseManagementNote obsolete = linked(issue, revised, OBSERVED - 10_000, OBSERVED + 4_000,
                "FAKE higher-id obsolete revision");
        CaseManagementNote later = linked(issue, UUID.randomUUID().toString(), OBSERVED + 10_000, OBSERVED,
                "FAKE later observation");
        CaseManagementNote tied = linked(issue, UUID.randomUUID().toString(), OBSERVED, OBSERVED,
                "FAKE independent tied observation");
        CaseManagementNote earlier = linked(issue, UUID.randomUUID().toString(), OBSERVED - 20_000, OBSERVED,
                "FAKE earlier observation");
        entityManager.clear();

        Collection<CaseManagementNote> result = find("MedHistory");

        assertThat(obsolete.getId()).isGreaterThan(current.getId());
        assertThat(result).extracting(CaseManagementNote::getId)
                .containsExactly(earlier.getId(), current.getId(), tied.getId(), later.getId())
                .doesNotContain(obsolete.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "null", "blank"})
    void shouldChooseHighestRevisionId_whenUpdateTimestampsMatch(String uuidKind) {
        CaseManagementIssue issue = issue(PATIENT, "MedHistory");
        String uuid = "blank".equals(uuidKind) ? "" : UUID.randomUUID().toString();
        CaseManagementNote older = linked(issue, uuid, OBSERVED + 1_000, OBSERVED, "FAKE older tied revision");
        CaseManagementNote current = linked(issue, uuid, OBSERVED - 1_000, OBSERVED, "FAKE current tied revision");
        if ("null".equals(uuidKind)) {
            entityManager.createNativeQuery("UPDATE casemgmt_note SET uuid=NULL WHERE note_id IN (?1,?2)")
                    .setParameter(1, older.getId()).setParameter(2, current.getId()).executeUpdate();
        }
        entityManager.clear();

        Collection<CaseManagementNote> result = find("MedHistory");

        assertThat(current.getId()).isGreaterThan(older.getId());
        assertThat(result).extracting(CaseManagementNote::getId).containsExactly(current.getId());
        assertThat(result).extracting(CaseManagementNote::getNote).containsExactly("FAKE current tied revision");
    }

    @Test
    void shouldPreserveLegacyUuidGrouping_whenUuidIsNullOrBlank() {
        CaseManagementIssue issue = issue(PATIENT, "MedHistory");
        CaseManagementNote nullOld = linked(issue, UUID.randomUUID().toString(), OBSERVED, OBSERVED, "FAKE null old");
        CaseManagementNote nullNew = linked(issue, UUID.randomUUID().toString(), OBSERVED + 1_000,
                OBSERVED + 1_000, "FAKE null current");
        linked(issue, "", OBSERVED + 2_000, OBSERVED, "FAKE blank old");
        CaseManagementNote blankNew = linked(issue, "", OBSERVED + 3_000, OBSERVED + 1_000, "FAKE blank current");
        CaseManagementNote independent = linked(issue, UUID.randomUUID().toString(), OBSERVED + 4_000,
                OBSERVED, "FAKE independent UUID");
        entityManager.createNativeQuery("UPDATE casemgmt_note SET uuid=NULL WHERE note_id IN (?1,?2)")
                .setParameter(1, nullOld.getId()).setParameter(2, nullNew.getId()).executeUpdate();
        entityManager.clear();

        Collection<CaseManagementNote> result = find("MedHistory");

        assertThat(result).extracting(CaseManagementNote::getId)
                .containsExactly(nullNew.getId(), blankNew.getId(), independent.getId());
        assertThat(result).extracting(CaseManagementNote::getUuid).containsExactly(null, "", independent.getUuid());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetainEveryIssueJoinOnce_whenIssueCodeFilterIsAbsent(boolean emptyArray) {
        CaseManagementIssue history = issue(PATIENT, "MedHistory");
        CaseManagementIssue medication = issue(PATIENT, "OMeds");
        CaseManagementNote first = linked(history, UUID.randomUUID().toString(), OBSERVED, OBSERVED,
                "FAKE note linked to two categories");
        first.getIssues().add(medication);
        entityManager.flush();
        CaseManagementNote second = linked(medication, UUID.randomUUID().toString(), OBSERVED, OBSERVED,
                "FAKE independent other medication");
        linked(issue(PATIENT + 1, "MedHistory"), UUID.randomUUID().toString(), OBSERVED, OBSERVED,
                "FAKE other patient");
        entityManager.clear();

        Collection<CaseManagementNote> result = caseManagementNoteDAO.findNotesByDemographicAndIssueCode(
                PATIENT, emptyArray ? new String[0] : null);

        assertThat(result).extracting(CaseManagementNote::getId).containsExactly(first.getId(), second.getId());
    }

    @Test
    void shouldReturnEmpty_whenNoIssueMatches() {
        linked(issue(PATIENT, "MedHistory"), UUID.randomUUID().toString(), OBSERVED, OBSERVED, "FAKE linked note");
        assertThat(find("OMeds")).isEmpty();
        assertThat(caseManagementNoteDAO.findNotesByDemographicAndIssueCode(PATIENT + 1,
                new String[]{"MedHistory"})).isEmpty();
    }

    @Test
    void shouldKeepMissingObservationDate_whenALegacyRowHasNoSortKey() {
        CaseManagementNote note = linked(issue(PATIENT, "MedHistory"), UUID.randomUUID().toString(),
                OBSERVED, OBSERVED, "FAKE missing date");
        entityManager.createNativeQuery("UPDATE casemgmt_note SET observation_date=NULL WHERE note_id=?1")
                .setParameter(1, note.getId()).executeUpdate();
        entityManager.clear();
        assertThat(find("MedHistory")).singleElement().satisfies(stored -> {
            assertThat(stored.getId()).isEqualTo(note.getId());
            assertThat(stored.getObservation_date()).isNull();
        });
    }

    private CaseManagementIssue issue(int patient, String code) {
        Issue issue = createIssue(code, "FAKE tied-date CPP issue");
        return createCaseManagementIssue(Integer.toString(patient), issue);
    }

    private CaseManagementNote linked(CaseManagementIssue issue, String uuid, long observed, long updated, String body) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(issue.getDemographic_no().toString());
        note.setProviderNo("999998");
        note.setUuid(uuid);
        note.setNote(body);
        note.setHistory(body);
        note.setObservation_date(new Date(observed));
        note.setUpdate_date(new Date(updated));
        note.setSigned(true);
        note.setArchived(false);
        note.setLocked(false);
        note.getIssues().add(issue);
        caseManagementNoteDAO.saveNote(note);
        return note;
    }

    private Collection<CaseManagementNote> find(String code) {
        return caseManagementNoteDAO.findNotesByDemographicAndIssueCode(PATIENT, new String[]{code});
    }
}
