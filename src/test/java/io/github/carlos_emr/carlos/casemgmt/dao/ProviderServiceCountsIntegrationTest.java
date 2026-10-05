/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import java.util.Date;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.utility.EncounterUtil.EncounterType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderServiceCountsIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private CaseManagementNote encounter(String patient, String uuid, boolean archived, Date date) {
        CaseManagementNote note = createNote(patient, "FAKE export encounter", date);
        note.setUuid(uuid);
        note.setArchived(archived);
        note.setProgram_no("4174");
        note.setReporter_caisi_role("4174");
        note.setEncounter_type(EncounterType.FACE_TO_FACE_WITH_CLIENT.getOldDbValue());
        return note;
    }

    @Test
    void shouldCountOnlyCurrentNotes_whenRevisionsAreArchivedMovedOrDuplicated() {
        Date start = createDate(2003, 2, 1);
        Date end = createDate(2003, 3, 1);
        encounter("41741", "current", false, start);
        encounter("41741", "current", false, start);
        encounter("41742", "archived", false, start);
        encounter("41742", "archived", true, start);
        encounter("41743", "moved", false, start);
        encounter("41743", "moved", false, end);
        encounter("41744", null, false, start);
        encounter("41744", null, false, start);
        encounter("41745", "", false, start);
        encounter("41745", "", false, start);
        encounter("41746", "other-role", false, start).setReporter_caisi_role("4175");
        encounter("41747", "other-program", false, start).setProgram_no("4175");
        entityManager.flush();
        var counts = caseManagementNoteDAO.getDemographicEncounterCountsByProgramAndRoleId(4174, 4174, start, end);
        assertThat(counts.nonUniqueCounts.get(EncounterType.FACE_TO_FACE_WITH_CLIENT)).isEqualTo(5);
        assertThat(counts.uniqueCounts.get(EncounterType.FACE_TO_FACE_WITH_CLIENT)).isEqualTo(3);
        assertThat(counts.totalUniqueCount).isEqualTo(3);
        var agency = caseManagementNoteDAO.getDemographicEncounterCountsByProgramAndRoleId(null, 4174, start, end);
        assertThat(agency.nonUniqueCounts.get(EncounterType.FACE_TO_FACE_WITH_CLIENT)).isEqualTo(6);
        assertThat(agency.totalUniqueCount).isEqualTo(4);
    }
    @Test
    void shouldFollowGlobalUuidHistory_whenNoteMovesToAnotherPatient() {
        Date start = createDate(2003, 2, 1);
        Date end = createDate(2003, 3, 1);
        encounter("41748", "transferred-archived", false, start);
        encounter("41749", "transferred-archived", true, start);
        encounter("41750", "transferred-role", false, start);
        encounter("41751", "transferred-role", false, start).setReporter_caisi_role("4175");
        entityManager.flush();
        var counts = caseManagementNoteDAO.getDemographicEncounterCountsByProgramAndRoleId(4174, 4174, start, end);
        assertThat(counts.totalUniqueCount).isZero();
        assertThat(counts.nonUniqueCounts.get(EncounterType.FACE_TO_FACE_WITH_CLIENT)).isZero();
    }

    @Test
    void shouldUseLatestBucket_whenRevisionChangesMonthAndEncounterType() {
        Date february = createDate(2003, 2, 1);
        Date march = createDate(2003, 3, 1);
        Date april = createDate(2003, 4, 1);
        encounter("41752", "changed-bucket", false, february);
        encounter("41752", "changed-bucket", false, march)
                .setEncounter_type(EncounterType.TELEPHONE_WITH_CLIENT.getOldDbValue());
        entityManager.flush();
        var old = caseManagementNoteDAO.getDemographicEncounterCountsByProgramAndRoleId(4174, 4174, february, march);
        var current = caseManagementNoteDAO.getDemographicEncounterCountsByProgramAndRoleId(4174, 4174, march, april);
        assertThat(old.totalUniqueCount).isZero();
        assertThat(current.totalUniqueCount).isEqualTo(1);
        assertThat(current.nonUniqueCounts.get(EncounterType.FACE_TO_FACE_WITH_CLIENT)).isZero();
        assertThat(current.nonUniqueCounts.get(EncounterType.TELEPHONE_WITH_CLIENT)).isEqualTo(1);
    }

}
