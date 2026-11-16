/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises legacy lock values through real Hibernate hydration on the shipped MariaDB schema.
 * H2's generated BOOLEAN column cannot establish compatibility with nullable CHAR(1).
 * @since 2026-11-03
 */
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_SCHEMA", matches = "flyway")
class CaseManagementNoteLockMariaDbIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private static final String PATIENT = "94703";
    private static final String TEXT = "FAKE-4703 owned note café 中文";

    @Test
    void shouldUseNullableCharacterLockColumn_onTheShippedSchema() {
        Object[] column = (Object[]) entityManager.createNativeQuery("""
                SELECT DATA_TYPE, IS_NULLABLE FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='casemgmt_note' AND COLUMN_NAME='locked'
                """).getSingleResult();
        assertThat(column).containsExactly("char", "YES");
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,false", "0,false", "1,true"}, nullValues = "NULL")
    void shouldHydrateLockWithoutChangingClinicalRows_whenLoadingNoteIds(String lock, boolean expected) {
        Long id = persistWithLock(lock);
        List<?> before = clinicalRows();
        List<CaseManagementNote> notes = caseManagementNoteDAO.getNotes(List.of(id));
        assertThat(notes).hasSize(1);
        assertNote(notes.getFirst(), id, expected);
        assertUnchanged(before);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,false", "0,false", "1,true"}, nullValues = "NULL")
    void shouldHydrateLockWithoutChangingClinicalRows_whenLoadingNativeNoteList(String lock, boolean expected) {
        Long id = persistWithLock(lock);
        List<?> before = clinicalRows();
        List<CaseManagementNote> notes = caseManagementNoteDAO.getNotesByDemographicLimit(PATIENT, 0, 10);
        assertThat(notes).hasSize(1);
        assertNote(notes.getFirst(), id, expected);
        assertUnchanged(before);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,false", "0,false", "1,true"}, nullValues = "NULL")
    void shouldHydrateLockWithoutChangingClinicalRows_whenProjectingNoteList(String lock, boolean expected) {
        Long id = persistWithLock(lock);
        List<?> before = clinicalRows();
        var notes = caseManagementNoteDAO.findNoteDTOsByDemographicNo(PATIENT);
        assertThat(notes).hasSize(1);
        assertThat(notes.getFirst().getId()).isEqualTo(id);
        assertThat(notes.getFirst().isLocked()).isEqualTo(expected);
        assertUnchanged(before);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL,false", "0,true", "1,false"}, nullValues = "NULL")
    void shouldPreserveUnlockedQueryRestrictions_whenReadingLegacyLockStates(String lock, boolean included) {
        Long id = persistWithLock(lock);
        List<?> before = clinicalRows();
        var notes = caseManagementNoteDAO.getNotesByDemographicSince(PATIENT, new Date(0));
        assertThat(notes).extracting(CaseManagementNote::getId)
                .containsExactlyElementsOf(included ? List.of(id) : List.of());
        assertUnchanged(before);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPersistExplicitLockChanges_whenUpdatingLegacyNullNote(boolean locked) {
        Long id = persistWithLock(null);
        CaseManagementNote note = caseManagementNoteDAO.getNoteForUpdate(id);
        assertNote(note, id, false);
        note.setLocked(true);
        entityManager.flush();
        assertThat(rawLock(id)).isEqualTo("1");
        note.setLocked(locked);
        entityManager.flush();
        entityManager.clear();
        assertNote(caseManagementNoteDAO.getNote(id), id, locked);
        assertThat(rawLock(id)).isEqualTo(locked ? "1" : "0");
    }

    private Long persistWithLock(String lock) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(PATIENT);
        note.setNote(TEXT);
        note.setProviderNo("999998");
        note.setSigning_provider_no("");
        note.setProgram_no("0");
        note.setReporter_caisi_role("");
        note.setReporter_program_team("");
        note.setHistory("");
        note.setObservation_date(new Date());
        caseManagementNoteDAO.saveNote(note);
        entityManager.flush();
        // Change exactly one column of an ordinarily persisted note, then force a database read.
        assertThat(entityManager.createNativeQuery("UPDATE casemgmt_note SET locked=:lock WHERE note_id=:id")
                .setParameter("lock", lock).setParameter("id", note.getId()).executeUpdate()).isEqualTo(1);
        entityManager.clear();
        return note.getId();
    }

    private void assertNote(CaseManagementNote note, Long id, boolean locked) {
        assertThat(note.getId()).isEqualTo(id);
        assertThat(note.getDemographic_no()).isEqualTo(PATIENT);
        assertThat(note.getNote()).isEqualTo(TEXT);
        assertThat(note.isLocked()).isEqualTo(locked);
    }

    private List<?> clinicalRows() {
        return entityManager.createNativeQuery("SELECT * FROM casemgmt_note WHERE demographic_no=:patient ORDER BY note_id")
                .setParameter("patient", PATIENT).getResultList();
    }

    private String rawLock(Long id) {
        return entityManager.createNativeQuery("SELECT locked FROM casemgmt_note WHERE note_id=:id")
                .setParameter("id", id).getSingleResult().toString();
    }

    private void assertUnchanged(List<?> before) {
        entityManager.flush();
        assertThat(clinicalRows()).usingRecursiveComparison().isEqualTo(before);
    }
}
