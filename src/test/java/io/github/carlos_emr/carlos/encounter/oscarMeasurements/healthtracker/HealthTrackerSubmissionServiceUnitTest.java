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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker;

import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.SecRole;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.EntryOutcome;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.ValidationFailure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HealthTrackerSubmissionService}.
 *
 * <p>The behaviour that matters here is partial success: the Health Tracker saves
 * every valid row even when a sibling row is rejected, and only writes a progress
 * note for rows the clinician opted in.
 */
@Tag("unit")
@Tag("fast")
@Tag("measurement")
class HealthTrackerSubmissionServiceUnitTest {

    private HealthTrackerSubmissionParser parser;
    private HealthTrackerMeasurementPersister persister;
    private CaseManagementManager caseManagementManager;
    private SecRoleDao secRoleDao;
    private HealthTrackerSubmissionService service;
    private MeasurementFlowSheet flowSheet;

    @BeforeEach
    void setUp() {
        parser = mock(HealthTrackerSubmissionParser.class);
        persister = mock(HealthTrackerMeasurementPersister.class);
        caseManagementManager = mock(CaseManagementManager.class);
        secRoleDao = mock(SecRoleDao.class);
        flowSheet = mock(MeasurementFlowSheet.class);

        // SecRole has no id setter (the column is generated), so the role the
        // note's reporter_caisi_role comes from is stubbed rather than built.
        SecRole doctor = mock(SecRole.class);
        lenient().when(doctor.getId()).thenReturn(7);
        lenient().when(secRoleDao.findByName("doctor")).thenReturn(doctor);

        service = new HealthTrackerSubmissionService(
                parser, persister, new HealthTrackerNoteComposer(), caseManagementManager, secRoleDao);
    }

    @Test
    @DisplayName("should count only the entries that were actually written")
    void shouldCountPersistedEntries_whenSomeAreDuplicates() {
        HealthTrackerEntry fresh = entry("Weightkg", "82.5", false);
        HealthTrackerEntry duplicate = entry("Heightcm", "180", false);
        givenParsed(fresh, duplicate);
        when(persister.persist(eqEntry(fresh), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));
        when(persister.persist(eqEntry(duplicate), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(false, List.of()));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.persistedCount()).isEqualTo(1);
        assertThat(result.hasRejections()).isFalse();
    }

    @Test
    @DisplayName("should save valid entries and still report the rejected ones")
    void shouldSaveValidEntries_whenOneEntryRejected() {
        HealthTrackerEntry good = entry("Weightkg", "82.5", false);
        HealthTrackerEntry bad = entry("Heightcm", "nonsense", false);
        givenParsed(good, bad);
        when(persister.persist(eqEntry(good), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));
        when(persister.persist(eqEntry(bad), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(false,
                        List.of(new ValidationFailure("errors.invalid", new String[]{"Heightcm"}))));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.persistedCount()).isEqualTo(1);
        assertThat(result.hasRejections()).isTrue();
        assertThat(result.rejected()).containsExactly("Heightcm: nonsense");
        assertThat(result.failures()).extracting(ValidationFailure::messageKey).containsExactly("errors.invalid");
    }

    @Test
    @DisplayName("should not write a progress note when nothing was flagged for it")
    void shouldNotWriteNote_whenNoEntryFlagged() {
        HealthTrackerEntry entry = entry("Weightkg", "82.5", false);
        givenParsed(entry);
        when(persister.persist(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.noteText()).isEmpty();
        verify(caseManagementManager, never()).saveNoteSimple(any(CaseManagementNote.class));
    }

    @Test
    @DisplayName("should write a signed progress note for the flagged entries")
    void shouldWriteNote_whenEntryFlagged() {
        HealthTrackerEntry entry = entry("Weightkg", "82.5", true);
        givenParsed(entry);
        when(persister.persist(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.noteText()).contains("Weightkg: 82.5");

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(caseManagementManager).saveNoteSimple(saved.capture());
        CaseManagementNote note = saved.getValue();
        assertThat(note.getDemographic_no()).isEqualTo("111");
        assertThat(note.getProviderNo()).isEqualTo("999998");
        assertThat(note.getProgram_no()).isEqualTo("10");
        assertThat(note.isSigned()).isTrue();
        assertThat(note.getReporter_caisi_role()).isEqualTo("7");
        assertThat(note.getAppointmentNo()).isEqualTo(42);
    }

    @Test
    @DisplayName("should not note an entry that was suppressed as a duplicate")
    void shouldNotWriteNote_whenEntrySuppressedAsDuplicate() {
        // A refresh or a re-submit re-sends a value that is already in the chart.
        // It is valid, so it is not reported as an error -- but nothing was written,
        // and filing a second signed note for it would add clinical content with no
        // measurement behind it.
        HealthTrackerEntry entry = entry("Weightkg", "82.5", true);
        givenParsed(entry);
        when(persister.persist(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(false, List.of()));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.persistedCount()).isZero();
        assertThat(result.hasRejections()).isFalse();
        assertThat(result.noteText()).isEmpty();
        verify(caseManagementManager, never()).saveNoteSimple(any(CaseManagementNote.class));
    }

    @Test
    @DisplayName("should note only the newly written entry when a sibling is a duplicate")
    void shouldNoteOnlyPersistedEntry_whenSiblingIsDuplicate() {
        HealthTrackerEntry fresh = entry("Weightkg", "82.5", true);
        HealthTrackerEntry duplicate = entry("Heightcm", "180", true);
        givenParsed(fresh, duplicate);
        when(persister.persist(eqEntry(fresh), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));
        when(persister.persist(eqEntry(duplicate), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(false, List.of()));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.persistedCount()).isEqualTo(1);
        assertThat(result.noteText()).contains("Weightkg: 82.5");
        assertThat(result.noteText()).doesNotContain("Heightcm");
    }

    @Test
    @DisplayName("should not reject a rejected entry's value into the note")
    void shouldExcludeRejectedEntry_fromNote() {
        HealthTrackerEntry bad = entry("Heightcm", "nonsense", true);
        givenParsed(bad);
        when(persister.persist(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(false,
                        List.of(new ValidationFailure("errors.invalid", new String[]{"Heightcm"}))));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.noteText()).isEmpty();
        verify(caseManagementManager, never()).saveNoteSimple(any(CaseManagementNote.class));
    }

    @Test
    @DisplayName("should still report the save when filing the progress note fails")
    void shouldReportSave_whenNoteFilingFails() {
        HealthTrackerEntry entry = entry("Weightkg", "82.5", true);
        givenParsed(entry);
        when(persister.persist(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));
        doThrow(new IllegalStateException("note store offline"))
                .when(caseManagementManager).saveNoteSimple(any(CaseManagementNote.class));

        HealthTrackerSubmissionResult result = submit();

        assertThat(result.persistedCount()).isEqualTo(1);
        assertThat(result.hasRejections()).isFalse();
    }

    @Test
    @DisplayName("should fall back to role 0 when the stock doctor role is missing")
    void shouldFallBackToZeroRole_whenDoctorRoleMissing() {
        when(secRoleDao.findByName("doctor")).thenReturn(null);
        HealthTrackerEntry entry = entry("Weightkg", "82.5", true);
        givenParsed(entry);
        when(persister.persist(any(), anyInt(), anyString(), anyInt()))
                .thenReturn(new EntryOutcome(true, List.of()));

        submit();

        ArgumentCaptor<CaseManagementNote> saved = ArgumentCaptor.forClass(CaseManagementNote.class);
        verify(caseManagementManager).saveNoteSimple(saved.capture());
        assertThat(saved.getValue().getReporter_caisi_role()).isEqualTo("0");
    }

    private HealthTrackerSubmissionResult submit() {
        return service.submit(flowSheet, Map.<String, String>of()::get, 111, "999998", 42, "10", "2026-09-20");
    }

    private void givenParsed(HealthTrackerEntry... entries) {
        when(parser.parse(any(), any(), anyString())).thenReturn(List.of(entries));
    }

    private static HealthTrackerEntry eqEntry(HealthTrackerEntry entry) {
        return org.mockito.ArgumentMatchers.eq(entry);
    }

    private static HealthTrackerEntry entry(String fieldName, String value, boolean addToNote) {
        return new HealthTrackerEntry("WT", fieldName, fieldName, "", value, "", "2026-09-01", addToNote);
    }
}
