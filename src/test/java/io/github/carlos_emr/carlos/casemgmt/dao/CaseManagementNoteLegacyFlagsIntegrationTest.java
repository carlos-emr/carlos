/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.casemgmt.dao;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Hibernate reads of the nullable legacy archived/position columns (#5995). */
class CaseManagementNoteLegacyFlagsIntegrationTest extends CaseManagementNoteDaoBaseIntegrationTest {
    private static final String PATIENT = "95995";
    private static final String TEXT = "FAKE-5995 retained note café 中文";

    static Stream<Arguments> legacyValues() {
        return Stream.of(
                Arguments.of(null, null, false, 0),
                Arguments.of(null, -7, false, -7),
                Arguments.of(null, 31, false, 31),
                Arguments.of(false, null, false, 0),
                Arguments.of(true, null, true, 0),
                Arguments.of(false, 0, false, 0),
                Arguments.of(true, 31, true, 31),
                Arguments.of(false, Integer.MIN_VALUE, false, Integer.MIN_VALUE),
                Arguments.of(true, Integer.MAX_VALUE, true, Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @MethodSource("legacyValues")
    void shouldLoadNullableFlags_whenReadingANoteById(Boolean archived, Integer position,
                                                     boolean expectedArchived, int expectedPosition) {
        Long id = persistLegacy(archived, position);
        assertLoaded(caseManagementNoteDAO.getNote(id), id, expectedArchived, expectedPosition);
        assertRawFlags(id, archived, position);
    }

    @ParameterizedTest
    @MethodSource("legacyValues")
    void shouldLoadNullableFlags_whenReadingANoteIdList(Boolean archived, Integer position,
                                                       boolean expectedArchived, int expectedPosition) {
        Long id = persistLegacy(archived, position);
        assertThat(caseManagementNoteDAO.getNotes(List.of(id))).singleElement()
                .satisfies(note -> assertLoaded(note, id, expectedArchived, expectedPosition));
        assertRawFlags(id, archived, position);
    }

    @ParameterizedTest
    @MethodSource("legacyValues")
    void shouldLoadNullableFlags_whenReadingTheNativePatientNoteList(Boolean archived, Integer position,
                                                                    boolean expectedArchived, int expectedPosition) {
        Long id = persistLegacy(archived, position);
        assertThat(caseManagementNoteDAO.getNotesByDemographicLimit(PATIENT, 0, 10)).singleElement()
                .satisfies(note -> assertLoaded(note, id, expectedArchived, expectedPosition));
        assertRawFlags(id, archived, position);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPersistExplicitFlagChanges_whenUpdatingALegacyNullNote(boolean archived) {
        Long id = persistLegacy(null, null);
        CaseManagementNote note = caseManagementNoteDAO.getNote(id);
        assertLoaded(note, id, false, 0);
        note.setArchived(archived);
        note.setPosition(archived ? 17 : -3);
        entityManager.flush();
        entityManager.clear();
        assertLoaded(caseManagementNoteDAO.getNote(id), id, archived, archived ? 17 : -3);
        assertRawFlags(id, archived, archived ? 17 : -3);
    }

    private Long persistLegacy(Boolean archived, Integer position) {
        CaseManagementNote note = new CaseManagementNote();
        note.setDemographic_no(PATIENT);
        note.setProviderNo("999998");
        note.setNote(TEXT);
        note.setHistory("");
        note.setSigning_provider_no("");
        note.setProgram_no("0");
        note.setReporter_caisi_role("0");
        note.setReporter_program_team("");
        note.setObservation_date(new java.util.Date());
        caseManagementNoteDAO.saveNote(note);
        entityManager.flush();
        assertThat(entityManager.createNativeQuery(
                        "UPDATE casemgmt_note SET archived=:archived, position=:position WHERE note_id=:id")
                .setParameter("archived", archived).setParameter("position", position)
                .setParameter("id", note.getId()).executeUpdate()).isEqualTo(1);
        entityManager.clear();
        return note.getId();
    }

    private void assertLoaded(CaseManagementNote note, Long id, boolean archived, int position) {
        assertThat(note.getId()).isEqualTo(id);
        assertThat(note.getDemographic_no()).isEqualTo(PATIENT);
        assertThat(note.getProviderNo()).isEqualTo("999998");
        assertThat(note.getNote()).isEqualTo(TEXT);
        assertThat(note.isArchived()).isEqualTo(archived);
        assertThat(note.getPosition()).isEqualTo(position);
    }

    private void assertRawFlags(Long id, Boolean archived, Integer position) {
        entityManager.flush();
        Object[] raw = (Object[]) entityManager.createNativeQuery(
                        "SELECT archived, position FROM casemgmt_note WHERE note_id=:id")
                .setParameter("id", id).getSingleResult();
        assertThat(raw).containsExactly(archived, position);
    }
}
