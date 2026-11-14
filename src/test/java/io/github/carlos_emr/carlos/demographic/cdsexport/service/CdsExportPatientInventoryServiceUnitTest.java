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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportPatientInventoryServiceUnitTest {
    @TempDir Path root;

    private CdsExportPatientPipelineFixture fixture(CdsCategory... selected) throws Exception {
        var categories = EnumSet.of(CdsCategory.DEMOGRAPHICS);
        categories.addAll(List.of(selected));
        return new CdsExportPatientPipelineFixture(root, categories);
    }

    private CdsExportPatientInventoryService.Inventory capture(
            CdsExportPatientPipelineFixture fixture) throws IOException {
        return fixture.inventories.capture(
                fixture.lease,
                fixture.patient,
                "c".repeat(64),
                fixture.tables,
                fixture.source,
                fixture.bounds,
                fixture.control);
    }

    @Test
    void shouldCaptureExpectedNativeOccurrences_withoutInvokingMappersOrSerialization()
            throws Exception {
        var f = fixture(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "11", "11", CdsExportPatientPipelineFixture.HASH));
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "12", "12", CdsExportPatientPipelineFixture.HASH));
        var result = capture(f);
        assertThat(result.identities())
                .hasSize(3)
                .contains(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "allergies", "11")));
        verifyNoInteractions(f.demographics, f.clinical);
        assertThat(result.toString()).isEqualTo("CdsExportPatientInventory[protected]");
        assertThatThrownBy(() -> result.identities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldKeepDistinctRetainedOccurrences_withEqualClinicalValues() throws Exception {
        var f = fixture(CdsCategory.NEW_CATEGORY);
        f.unit(CdsCategory.NEW_CATEGORY, "d".repeat(64), List.of());
        f.unit(CdsCategory.NEW_CATEGORY, "e".repeat(64), List.of());
        assertThat(capture(f).identities()).hasSize(3);
    }

    @Test
    void shouldExcludeUnappliedRetainedUnits_withoutCreatingGhostOccurrences() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.unit(CdsCategory.REPORTS, "d".repeat(64), List.of());
        f.applied.clear();
        assertThat(capture(f).identities()).hasSize(1);
    }

    @Test
    void shouldRefuseAnUnclassifiedNativeNote_insteadOfOmittingIt() throws Exception {
        var f = fixture(CdsCategory.CLINICAL_NOTES);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "casemgmt_note", "11", "11", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldClassifyHistoricalNotes_fromTheirAppliedSourceUnit() throws Exception {
        var f = fixture(CdsCategory.CLINICAL_NOTES);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "casemgmt_note", "11", "11", CdsExportPatientPipelineFixture.HASH));
        f.unit(
                CdsCategory.CLINICAL_NOTES,
                "d".repeat(64),
                List.of(
                        new CdsExportRetainedIndexDto.Reference(
                                "casemgmt_note", "11", CdsExportPatientPipelineFixture.HASH)));
        when(f.historyInventory.plan(any(), any(), anySet(), any(), any(), any(), any()))
                .thenReturn(
                        List.of(
                                new CdsExportHistoryOccurrenceService.Occurrence(
                                        CdsCategory.CLINICAL_NOTES, "retained", 11)));
        assertThat(capture(f).identities()).hasSize(2);
    }

    @Test
    void shouldRefuseANoteAssigned_toCompetingSourceUnits() throws Exception {
        var f = fixture(CdsCategory.CLINICAL_NOTES);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "casemgmt_note", "11", "11", CdsExportPatientPipelineFixture.HASH));
        var refs =
                List.of(
                        new CdsExportRetainedIndexDto.Reference(
                                "casemgmt_note", "11", CdsExportPatientPipelineFixture.HASH));
        f.unit(CdsCategory.CLINICAL_NOTES, "d".repeat(64), refs);
        f.unit(CdsCategory.CLINICAL_NOTES, "e".repeat(64), refs);
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAnUnexpectedCategory_insteadOfLeakingItIntoTheInventory() throws Exception {
        var f = fixture();
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "allergies", "11", "11", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldRefuseDuplicateNativeIdentities_evenWhenWitnessesMatch() throws Exception {
        var f = fixture();
        f.nativeRows.add(f.nativeRows.getFirst());
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    private static CdsExportNativeRowReceiptDto screening(String table, int id) {
        return screening(table, id, "0");
    }

    /** A witness whose authenticated state carries the native deleted flag. */
    private static CdsExportNativeRowReceiptDto screening(String table, int id, String deleted) {
        var columns =
                List.of(
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column("deleted", "VARCHAR", 255, 0),
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column("id", "VARCHAR", 255, 0));
        var cells = new ArrayList<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState.Cell>();
        for (String value : Arrays.asList(deleted, Integer.toString(id)))
            cells.add(
                    new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState.Cell(
                            value == null,
                            value == null ? 0 : value.length(),
                            value == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    value.getBytes(
                                                                            java.nio.charset
                                                                                    .StandardCharsets
                                                                                    .UTF_8)))));
        var state =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        table, Integer.toString(id), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                table,
                Integer.toString(id),
                CdsExportPatientTableSourceReader.key(id),
                state.digest(),
                state);
    }

    /** A registry or episode witness whose authenticated state carries the native status. */
    private static CdsExportNativeRowReceiptDto problem(String table, int id, String status) {
        var columns =
                List.of(
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column("status", "VARCHAR", 25, 0));
        var cells =
                List.of(
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState
                                .Cell(
                                status == null,
                                status == null ? 0 : status.length(),
                                status == null
                                        ? null
                                        : HexFormat.of()
                                                .formatHex(
                                                        CdsImportKeys.digest()
                                                                .digest(
                                                                        status.getBytes(
                                                                                java.nio.charset
                                                                                        .StandardCharsets
                                                                                        .UTF_8)))));
        var state =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        table, Integer.toString(id), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                table,
                Integer.toString(id),
                CdsExportPatientTableSourceReader.key(id),
                state.digest(),
                state);
    }

    private static CdsExportIdentityDto problemIdentity(String table, int id) {
        return new CdsExportIdentityDto(
                "c".repeat(64),
                CdsCategory.PROBLEM_LIST,
                CdsImportKeys.hash("carlos-native-occurrence-v1", table, Integer.toString(id)));
    }

    // Before: dxresearch and Episode were REQUIRE_EMPTY, so a chart with a Disease Registry entry
    // or an episode was held. Now each current one is one expected ProblemList item; a deleted one
    // is accounted by its occurrence service and holds no one.
    @Test
    void shouldExpectOneProblemPerCurrentRegistryEntryAndEpisode_forPatientInventory()
            throws Exception {
        var f = fixture(CdsExportNoteSourceReader.CATEGORIES.toArray(CdsCategory[]::new));
        f.nativeRows.add(problem("Episode", 6, "Current"));
        f.nativeRows.add(problem("Episode", 10, "Deleted"));
        f.nativeRows.add(problem("dxresearch", 5, "A"));
        f.nativeRows.add(problem("dxresearch", 7, "C"));
        f.nativeRows.add(problem("dxresearch", 8, "D"));
        // An unknown status stays expected; its occurrence service holds the patient.
        f.nativeRows.add(problem("dxresearch", 9, null));
        // Start-date precision rows belong to their entry's occurrence.
        f.nativeRows.add(problem("dxresearch_precision", 11, null));
        var problems =
                capture(f).identities().stream()
                        .filter(identity -> identity.category() == CdsCategory.PROBLEM_LIST)
                        .toList();
        assertThat(problems)
                .containsExactlyInAnyOrder(
                        problemIdentity("Episode", 6),
                        problemIdentity("dxresearch", 5),
                        problemIdentity("dxresearch", 7),
                        problemIdentity("dxresearch", 9));
    }

    @Test
    void shouldRefuseCapturedRegistryEntries_whenProblemListWasNotRequested() throws Exception {
        var f = fixture(CdsCategory.IMMUNIZATIONS);
        f.nativeRows.add(problem("dxresearch", 5, "A"));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldRefuseARegistryWitness_withoutTheStateThatShowsItsStatus() throws Exception {
        var f = fixture(CdsExportNoteSourceReader.CATEGORIES.toArray(CdsCategory[]::new));
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "dxresearch",
                        "5",
                        CdsExportPatientTableSourceReader.key(5),
                        CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f))
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_WITNESS_REQUIRED");
    }

    @Test
    void shouldLeaveSupersededPreventionVersions_outOfTheExpectedOccurrences() throws Exception {
        var categories = new ArrayList<>(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.IMMUNIZATIONS);
        var f = fixture(categories.toArray(CdsCategory[]::new));
        f.nativeRows.add(screening("preventions", 28, "1"));
        f.nativeRows.add(screening("preventions", 29, null));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 30, "1"));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 31, "0"));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 32, "2"));
        var identities = capture(f).identities();
        assertThat(identities)
                .contains(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.IMMUNIZATIONS,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "29")),
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.PAST_HEALTH,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "31")),
                        // An unknown flag stays expected; its occurrence service holds it.
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.PAST_HEALTH,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "32")));
        assertThat(identities).hasSize(4);
    }

    @Test
    void shouldRefuseAPreventionWitness_withoutTheStateThatShowsItsVersion() throws Exception {
        var f = fixture(CdsCategory.IMMUNIZATIONS);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "preventions", "28", "28", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f))
                .hasMessage("CDS_EXPORT_PREVENTION_VERSION_WITNESS_REQUIRED");
    }

    @Test
    void shouldGiveEveryCapturedScreeningExactlyOnePastHealthIdentity_forPatientInventory()
            throws Exception {
        var f = fixture(CdsExportNoteSourceReader.CATEGORIES.toArray(CdsCategory[]::new));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 31));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 32));
        // Extension and precision witnesses belong to their parent's occurrence.
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENING_EXTENSIONS, 41));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENING_PRECISION, 51));
        var screenings =
                capture(f).identities().stream()
                        .filter(identity -> identity.category() == CdsCategory.PAST_HEALTH)
                        .toList();
        assertThat(screenings)
                .containsExactlyInAnyOrder(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.PAST_HEALTH,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "31")),
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.PAST_HEALTH,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "32")));
    }

    @Test
    void shouldRefuseADuplicatedScreeningWitness_insteadOfCountingItOnce() throws Exception {
        var f = fixture(CdsExportNoteSourceReader.CATEGORIES.toArray(CdsCategory[]::new));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 31));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 31));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldRefuseCapturedScreenings_whenPastHealthWasNotRequested() throws Exception {
        var f = fixture(CdsCategory.IMMUNIZATIONS);
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 31));
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldKeepAScreeningAndAVaccine_inTheirOwnCategories() throws Exception {
        var categories = new ArrayList<>(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.IMMUNIZATIONS);
        var f = fixture(categories.toArray(CdsCategory[]::new));
        f.nativeRows.add(screening("preventions", 30));
        f.nativeRows.add(screening(CdsExportPreventionSources.SCREENINGS, 31));
        var identities = capture(f).identities();
        assertThat(identities)
                .contains(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.IMMUNIZATIONS,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "30")),
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.PAST_HEALTH,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "preventions", "31")));
        assertThat(identities).hasSize(3);
    }

    @Test
    void shouldRequireTheCurrentDemographic_forPatientInventoryServiceContract() throws Exception {
        var f = fixture();
        f.nativeRows.clear();
        assertThatThrownBy(() -> capture(f)).hasMessage("CDS_EXPORT_PATIENT_INVENTORY_INVALID");
    }

    @Test
    void shouldNotReturnInventory_afterFinalAuthorityRevocation() throws Exception {
        var f = fixture();
        doThrow(new SecurityException("REVOKED")).when(f.control).authorizePatient(27);
        assertThatThrownBy(() -> capture(f)).hasMessage("REVOKED");
    }

    @Test
    void shouldInventoryEveryNativeLabObservationRepetition_andItsSeparateSourceContext()
            throws Exception {
        var f = fixture(CdsCategory.LABORATORY_RESULTS, CdsCategory.NEW_CATEGORY);
        var source =
                CdsExportNativeLaboratoryMapperUnitTest.source(
                        CdsExportNativeLaboratoryMapperUnitTest.message());
        when(f.laboratoryInventory.nativeSources(
                        any(), any(), anySet(), any(), any(), any(), any()))
                .thenReturn(List.of(source));
        var result = capture(f);
        assertThat(result.identities()).hasSize(6);
        assertThat(
                        result.identities().stream()
                                .filter(i -> i.category() == CdsCategory.LABORATORY_RESULTS))
                .hasSize(4);
        assertThat(
                        result.identities().stream()
                                .filter(i -> i.category() == CdsCategory.NEW_CATEGORY))
                .hasSize(1);
    }

    @Test
    void shouldExpectAReportsOccurrence_forEachEmbeddedLabDocument() throws Exception {
        var f =
                fixture(
                        CdsCategory.LABORATORY_RESULTS,
                        CdsCategory.NEW_CATEGORY,
                        CdsCategory.REPORTS);
        var source =
                CdsExportNativeLaboratoryMapperUnitTest.source(
                        CdsExportNativeLaboratoryMapperUnitTest.message()
                                .replace("|NM|2345-7^Glucose^LN||5.4|", "|ED|PDF^Report^L||JVBERi0xLjQK|"));
        when(f.laboratoryInventory.nativeSources(
                        any(), any(), anySet(), any(), any(), any(), any()))
                .thenReturn(List.of(source));
        var result = capture(f);
        assertThat(
                        result.identities().stream()
                                .filter(i -> i.category() == CdsCategory.LABORATORY_RESULTS))
                .hasSize(3);
        assertThat(result.identities().stream().filter(i -> i.category() == CdsCategory.REPORTS))
                .containsExactly(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.REPORTS,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "hl7TextMessage", "91:1:1:0")));
        // Before: a job that left Reports out held the chart (CDS_EXPORT_UNMAPPED_DATA). The
        // chart is exported and the document is named as not exported.
        var withoutReports = fixture(CdsCategory.LABORATORY_RESULTS, CdsCategory.NEW_CATEGORY);
        when(withoutReports.laboratoryInventory.nativeSources(
                        any(), any(), anySet(), any(), any(), any(), any()))
                .thenReturn(List.of(source));
        var unselected = capture(withoutReports);
        assertThat(unselected.identities().stream().filter(i -> i.category() == CdsCategory.REPORTS))
                .isEmpty();
        assertThat(
                        unselected
                                .notSelected()
                                .get(
                                        CdsExportPatientInventoryService
                                                .LABORATORY_DOCUMENT_NOT_EXPORTED_LABEL)
                                .get("hl7TextMessage"))
                .containsExactly("91:1:1:0");
        assertThat(unselected.notExported())
                .containsEntry(
                        CdsExportPatientInventoryService.LABORATORY_DOCUMENT_NOT_EXPORTED_LABEL, 1L);
    }

    @Test
    void shouldExpectOneSourceRecordPerAppointment_whenNewCategoryIsSelected() throws Exception {
        var f = fixture(CdsCategory.APPOINTMENTS, CdsCategory.NEW_CATEGORY);
        for (int id : List.of(77, 78))
            f.nativeRows.add(
                    new CdsExportNativeRowReceiptDto(
                            "appointment",
                            Integer.toString(id),
                            CdsExportPatientTableSourceReader.key(id),
                            "f".repeat(64)));
        var inventory = capture(f);
        // Each appointment: its Appointments element and its CARLOS.Appointment.Source.v1 record.
        assertThat(inventory.identities()).hasSize(5);
        for (var category : List.of(CdsCategory.APPOINTMENTS, CdsCategory.NEW_CATEGORY))
            assertThat(inventory.identities())
                    .contains(
                            new CdsExportIdentityDto(
                                    "c".repeat(64),
                                    category,
                                    CdsImportKeys.hash(
                                            "carlos-native-occurrence-v1", "appointment", "78")));
        assertThat(inventory.notExported()).isEmpty();
    }

    @Test
    void shouldCountAppointmentSourceRecordsAsNotExported_whenNewCategoryIsNotSelected()
            throws Exception {
        var f = fixture(CdsCategory.APPOINTMENTS);
        for (int id : List.of(77, 78))
            f.nativeRows.add(
                    new CdsExportNativeRowReceiptDto(
                            "appointment",
                            Integer.toString(id),
                            CdsExportPatientTableSourceReader.key(id),
                            "f".repeat(64)));
        var inventory = capture(f);
        // The chart is exported, not held: only the Appointments elements are expected and the
        // ReadMe is told how many source records the selection left out.
        assertThat(inventory.identities()).hasSize(3);
        assertThat(inventory.notExported())
                .containsExactly(
                        Map.entry(
                                "Appointment source records"
                                        + " (NO_CDS_ELEMENT_NEW_CATEGORY_NOT_SELECTED)",
                                2L));
    }

    @Test
    void shouldNotExpectTheRetainedFields_ofAnImportedAppointmentDeletedInCarlos()
            throws Exception {
        for (boolean deleted : List.of(true, false)) {
            var f = fixture(CdsCategory.APPOINTMENTS);
            String key = "e".repeat(64);
            f.unit(
                    CdsCategory.APPOINTMENTS,
                    key,
                    List.of(new CdsExportRetainedIndexDto.Reference("appointment", "77", "f".repeat(64))));
            if (!deleted)
                f.nativeRows.add(
                        new CdsExportNativeRowReceiptDto(
                                "appointment",
                                "77",
                                CdsExportPatientTableSourceReader.key(77),
                                "f".repeat(64)));
            var values =
                    new TreeMap<>(
                            CdsExportRetainedFieldMapperUnitTest.row(1, "Follow-up", "Follow-up", null)
                                    .fields());
            values.put("category", "Appointments");
            values.put("source_path", "/OmdCds[1]/PatientRecord[1]/Appointments[1]/AppointmentPurpose[1]");
            values.put("value_type", "{cds_dt}text250");
            values.put(
                    "id",
                    CdsImportKeys.hash(
                            values.get("unit_key"),
                            values.get("source_namespace"),
                            values.get("source_path"),
                            values.get("occurrence")));
            var field = new CdsExportCapturedTextRowDto("cds_import_field", values.get("id"), values);
            f.metadata.computeIfAbsent("cds_import_field", ignored -> new HashMap<>())
                    .put(values.get("id"), field);
            f.parents.put(values.get("id"), f.applied.iterator().next());
            var result = capture(f);
            if (deleted) assertThat(result.retainedFields()).isEmpty();
            else assertThat(result.retainedFields()).containsExactly(values.get("id"));
        }
    }

    @Test
    void shouldNotExpectTheRetainedFields_ofAnImportedVaccineRemovedInCarlos() throws Exception {
        // The imported vaccine row 77 is superseded in every case. Removed: no version records it
        // as previousId, so its retained fields are accounted as removed by the vaccine assembly.
        // Edited: version 78 records previousId 77 and carries the fields as replaced.
        for (boolean edited : List.of(false, true)) {
            var f = fixture(CdsCategory.IMMUNIZATIONS);
            String key = "e".repeat(64);
            f.unit(
                    CdsCategory.IMMUNIZATIONS,
                    key,
                    List.of(new CdsExportRetainedIndexDto.Reference("preventions", "77", "f".repeat(64))));
            f.nativeRows.add(screening("preventions", 77, "1"));
            if (edited) {
                f.nativeRows.add(screening("preventions", 78, "0"));
                f.nativeRows.add(
                        textWitness(
                                "preventionsExt",
                                90,
                                Map.of("id", "90", "prevention_id", "78", "keyval", "previousId", "val", "77")));
            }
            var values =
                    new TreeMap<>(
                            CdsExportRetainedFieldMapperUnitTest.row(1, "Td", "Td", null).fields());
            values.put("category", "Immunizations");
            values.put("source_path", "/OmdCds[1]/PatientRecord[1]/Immunizations[1]/ImmunizationName[1]");
            values.put("value_type", "{cds_dt}text120");
            values.put(
                    "id",
                    CdsImportKeys.hash(
                            values.get("unit_key"),
                            values.get("source_namespace"),
                            values.get("source_path"),
                            values.get("occurrence")));
            var field = new CdsExportCapturedTextRowDto("cds_import_field", values.get("id"), values);
            f.metadata.computeIfAbsent("cds_import_field", ignored -> new HashMap<>())
                    .put(values.get("id"), field);
            f.parents.put(values.get("id"), f.applied.iterator().next());
            var result = capture(f);
            if (edited) {
                assertThat(result.retainedFields()).containsExactly(values.get("id"));
                assertThat(result.identities())
                        .contains(
                                new CdsExportIdentityDto(
                                        "c".repeat(64),
                                        CdsCategory.IMMUNIZATIONS,
                                        CdsImportKeys.hash(
                                                "carlos-native-occurrence-v1", "preventions", "78")));
            } else assertThat(result.retainedFields()).isEmpty();
        }
    }

    /** A witness whose authenticated state carries exactly the given text columns. */
    private static CdsExportNativeRowReceiptDto textWitness(
            String table, int id, Map<String, String> values) {
        var columns =
                new ArrayList<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest.Column>();
        var cells =
                new ArrayList<io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(values).entrySet()) {
            columns.add(
                    new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                            .Column(entry.getKey(), "VARCHAR", 255, 0));
            byte[] bytes = entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            cells.add(
                    new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState.Cell(
                            false,
                            bytes.length,
                            HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes))));
        }
        var state =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        table, Integer.toString(id), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                table,
                Integer.toString(id),
                CdsExportPatientTableSourceReader.key(id),
                state.digest(),
                state);
    }

    @Test
    void shouldExpectANewCategoryRecord_forCarlosCommentsOnAnImportedLabResult() throws Exception {
        String result = "d".repeat(64);
        var commented =
                CdsExportLaboratoryReviewerMappingUnitTest.route(
                        14, "101", "A", "2026-07-10 16:45:00", "Discussed with patient");
        for (var selected :
                List.of(
                        List.of(CdsCategory.LABORATORY_RESULTS, CdsCategory.NEW_CATEGORY),
                        List.of(CdsCategory.LABORATORY_RESULTS))) {
            var f = fixture(selected.toArray(CdsCategory[]::new));
            f.unit(
                    CdsCategory.LABORATORY_RESULTS,
                    "e".repeat(64),
                    List.of(
                            new CdsExportRetainedIndexDto.Reference(
                                    "cds_laboratory_result", result, "f".repeat(64)),
                            new CdsExportRetainedIndexDto.Reference(
                                    "hl7TextMessage", "91", "f".repeat(64))));
            when(f.laboratoryInventory.routes(any(), any(), anySet(), any(), any(), any()))
                    .thenReturn(Map.of("91", List.of(commented)));
            if (selected.contains(CdsCategory.NEW_CATEGORY))
                assertThat(capture(f).identities())
                        .contains(
                                new CdsExportIdentityDto(
                                        "c".repeat(64),
                                        CdsCategory.NEW_CATEGORY,
                                        CdsExportLaboratoryOccurrenceService.importedCommentsId(
                                                result)));
            // Without NewCategory the comment's record is not written: the chart is still
            // exported and the result is named as not exported, never held.
            else {
                var inventory = capture(f);
                assertThat(inventory.identities())
                        .noneMatch(identity -> identity.category() == CdsCategory.NEW_CATEGORY);
                assertThat(inventory.notExported())
                        .containsExactly(
                                Map.entry(
                                        CdsExportPatientInventoryService
                                                .LABORATORY_COMMENTS_NOT_EXPORTED_LABEL,
                                        1L));
                assertThat(inventory.notSelected())
                        .containsEntry(
                                CdsExportPatientInventoryService
                                        .LABORATORY_COMMENTS_NOT_EXPORTED_LABEL,
                                new TreeMap<>(
                                        Map.of("cds_laboratory_result", new TreeSet<>(Set.of(result)))));
            }
        }
    }

    @Test
    void shouldExportNativeLabResultsWithoutTheirSourceRecord_whenNewCategoryIsNotSelected()
            throws Exception {
        // Was a hold (CDS_EXPORT_UNMAPPED_DATA): the operator's category choice held the chart.
        var f = fixture(CdsCategory.LABORATORY_RESULTS);
        var source =
                CdsExportNativeLaboratoryMapperUnitTest.source(
                        CdsExportNativeLaboratoryMapperUnitTest.message());
        when(f.laboratoryInventory.nativeSources(
                        any(), any(), anySet(), any(), any(), any(), any()))
                .thenReturn(List.of(source));
        var inventory = capture(f);
        assertThat(inventory.identities())
                .noneMatch(identity -> identity.category() == CdsCategory.NEW_CATEGORY)
                .filteredOn(identity -> identity.category() == CdsCategory.LABORATORY_RESULTS)
                .hasSize(source.parsed().observations().size());
        assertThat(inventory.notExported())
                .containsExactly(
                        Map.entry(
                                "Laboratory report source records"
                                        + " (NO_CDS_ELEMENT_NEW_CATEGORY_NOT_SELECTED)",
                                1L));
    }

    @Test
    void shouldCountPrintedPrescriptionsAsNotExported_whenNewCategoryIsNotSelected()
            throws Exception {
        // Was CDS_EXPORT_PRESCRIPTION_SUPPORTING_CATEGORY_REQUIRED.
        for (var selected :
                List.of(
                        List.of(CdsCategory.MEDICATIONS_AND_TREATMENTS, CdsCategory.NEW_CATEGORY),
                        List.of(CdsCategory.MEDICATIONS_AND_TREATMENTS))) {
            var f = fixture(selected.toArray(CdsCategory[]::new));
            var fields = new TreeMap<String, String>();
            io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan
                    .PRESCRIPTIONS
                    .columns()
                    .forEach(column -> fields.put(column, null));
            fields.put("script_no", "501");
            fields.put("demographic_no", "27");
            var script =
                    new CdsExportPrescriptionOccurrenceService.Source(
                            new CdsExportCapturedTextRowDto(
                                    "prescription",
                                    CdsExportPatientTableSourceReader.key(501),
                                    fields),
                            null);
            when(f.prescriptionInventory.sources(any(), any(), anySet(), any(), any(), any()))
                    .thenReturn(List.of(script));
            var inventory = capture(f);
            var identity =
                    new CdsExportIdentityDto(
                            "c".repeat(64),
                            CdsCategory.NEW_CATEGORY,
                            CdsImportKeys.hash(
                                    "carlos-native-occurrence-v1", "prescription", "501"));
            if (selected.contains(CdsCategory.NEW_CATEGORY)) {
                assertThat(inventory.identities()).contains(identity);
                assertThat(inventory.notExported()).isEmpty();
            } else {
                assertThat(inventory.identities()).doesNotContain(identity);
                assertThat(inventory.notExported())
                        .containsExactly(
                                Map.entry(
                                        CdsExportPrescriptionOccurrenceService.NOT_EXPORTED_LABEL,
                                        1L));
                assertThat(inventory.notSelected())
                        .containsEntry(
                                CdsExportPrescriptionOccurrenceService.NOT_EXPORTED_LABEL,
                                new TreeMap<>(Map.of("prescription", new TreeSet<>(Set.of("501")))));
            }
        }
    }

    @Test
    void shouldInventoryNativeCareAndItsSupportingContext_withoutAnImportedParent()
            throws Exception {
        var f = fixture(CdsCategory.CARE_ELEMENTS, CdsCategory.NEW_CATEGORY);
        var observation = CdsExportNativeCareMapperUnitTest.observation("WT", "70", "kg");
        careSources(
                f,
                new CdsExportNativeCarePlanner.Occurrence(
                        "measurements", "91", "Weight", observation, List.of(), List.of()));
        var inventory = capture(f);
        assertThat(inventory.identities()).hasSize(3);
        assertThat(
                        inventory.identities().stream()
                                .filter(i -> i.category() == CdsCategory.CARE_ELEMENTS))
                .hasSize(1);
        assertThat(
                        inventory.identities().stream()
                                .filter(i -> i.category() == CdsCategory.NEW_CATEGORY))
                .hasSize(1);
    }

    @Test
    void shouldExportMeasurementsWithoutTheirSourceRecords_whenNewCategoryIsNotSelected()
            throws Exception {
        // Was a hold (CDS_EXPORT_UNMAPPED_DATA). A measurement with a CareElements element is
        // still exported; one with no element is named as not exported. Neither holds the chart.
        var f = fixture(CdsCategory.CARE_ELEMENTS);
        var observation = CdsExportNativeCareMapperUnitTest.observation("WT", "70", "kg");
        careSources(
                f,
                new CdsExportNativeCarePlanner.Occurrence(
                        "measurements", "91", "Weight", observation, List.of(), List.of()),
                new CdsExportNativeCarePlanner.Occurrence(
                        "measurements", "92", null, observation, List.of(), List.of()));
        var inventory = capture(f);
        assertThat(inventory.identities())
                .noneMatch(identity -> identity.category() == CdsCategory.NEW_CATEGORY)
                .filteredOn(identity -> identity.category() == CdsCategory.CARE_ELEMENTS)
                .hasSize(1);
        assertThat(inventory.notExported())
                .containsExactly(
                        Map.entry(
                                CdsExportPatientInventoryService.MEASUREMENT_NOT_EXPORTED_LABEL,
                                1L),
                        Map.entry(
                                CdsExportPatientInventoryService
                                        .MEASUREMENT_WITHOUT_ELEMENT_NOT_EXPORTED_LABEL,
                                1L));
    }

    @Test
    void shouldHoldOnlyThatChart_whenItsMeasurementsPassThePatientOccurrenceCapacity()
            throws Exception {
        // The fixture's occurrence capacity is 1,000 (maxIndexRows); every weight is a
        // CareElements and a NewCategory occurrence. Before, the inventory stopped the whole
        // job with RESOURCE_LIMIT; the care capture had held the chart much earlier, at 8,192.
        var f = fixture(CdsCategory.CARE_ELEMENTS, CdsCategory.NEW_CATEGORY);
        var weights = new ArrayList<CdsExportNativeCarePlanner.Occurrence>();
        for (int id = 1; id <= 499; id++)
            weights.add(
                    new CdsExportNativeCarePlanner.Occurrence(
                            "measurements",
                            Integer.toString(id),
                            "Weight",
                            CdsExportNativeCareMapperUnitTest.observation("WT", "70", "kg"),
                            List.of(),
                            List.of()));
        careSources(f, weights.toArray(CdsExportNativeCarePlanner.Occurrence[]::new));
        assertThat(capture(f).identities()).hasSize(999);
        weights.add(
                new CdsExportNativeCarePlanner.Occurrence(
                        "measurements",
                        "500",
                        "Weight",
                        CdsExportNativeCareMapperUnitTest.observation("WT", "70", "kg"),
                        List.of(),
                        List.of()));
        careSources(f, weights.toArray(CdsExportNativeCarePlanner.Occurrence[]::new));
        assertThatThrownBy(() -> capture(f))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_CARE_PATIENT_LIMIT_EXCEEDED");
    }

    /** The care service streams its occurrences to the inventory, window by window. */
    private static void careSources(
            CdsExportPatientPipelineFixture f,
            CdsExportNativeCarePlanner.Occurrence... occurrences)
            throws IOException {
        org.mockito.Mockito.doAnswer(
                        call -> {
                            CdsExportCareOccurrenceService.NativeSources sink =
                                    call.getArgument(7);
                            for (var occurrence : occurrences) sink.accept(occurrence);
                            return null;
                        })
                .when(f.careInventory)
                .forEachNativeSource(any(), any(), anySet(), any(), any(), any(), any(), any());
    }

    @Test
    void
            shouldInventoryNativeReportsWithoutImportMetadata_andAvoidDuplicatingImportedRepresentations()
                    throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        // A document witness now carries its state: the inventory reads its status to leave a
        // soft-deleted document out (review R6).
        f.nativeRows.add(documentWitness("document", "91", "A"));
        assertThat(capture(f).identities()).hasSize(2);
        f.unit(
                CdsCategory.REPORTS,
                "d".repeat(64),
                List.of(
                        new CdsExportRetainedIndexDto.Reference(
                                "document", "91", CdsExportPatientPipelineFixture.HASH)));
        assertThat(capture(f).identities()).hasSize(2);
        assertThat(
                        capture(f).identities().stream()
                                .filter(i -> i.category() == CdsCategory.REPORTS))
                .hasSize(1);
    }

    @Test
    void shouldLeaveSoftDeletedDocumentsAndDeletedLinks_outOfTheExpectedReports() throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.nativeRows.add(documentWitness("document", "91", "A"));
        f.nativeRows.add(documentWitness("ctl_document", "27_91", "A"));
        f.nativeRows.add(documentWitness("document", "92", "D"));
        f.nativeRows.add(documentWitness("ctl_document", "27_92", "A"));
        f.nativeRows.add(documentWitness("document", "93", "A"));
        f.nativeRows.add(documentWitness("ctl_document", "27_93", "D"));
        f.nativeRows.add(documentWitness("document", "94", "H"));
        f.nativeRows.add(documentWitness("ctl_document", "27_94", null));
        var reports =
                capture(f).identities().stream()
                        .filter(identity -> identity.category() == CdsCategory.REPORTS)
                        .toList();
        assertThat(reports)
                .containsExactlyInAnyOrder(
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.REPORTS,
                                CdsImportKeys.hash("carlos-native-occurrence-v1", "document", "91")),
                        new CdsExportIdentityDto(
                                "c".repeat(64),
                                CdsCategory.REPORTS,
                                CdsImportKeys.hash(
                                        "carlos-native-occurrence-v1", "document", "94")));
    }

    @Test
    void shouldRefuseADocumentWitness_withoutTheStateThatShowsWhetherItWasDeleted()
            throws Exception {
        var f = fixture(CdsCategory.REPORTS);
        f.nativeRows.add(
                new CdsExportNativeRowReceiptDto(
                        "document", "91", "91", CdsExportPatientPipelineFixture.HASH));
        assertThatThrownBy(() -> capture(f))
                .hasMessage("CDS_EXPORT_DOCUMENT_STATUS_WITNESS_REQUIRED");
    }

    /** A document or patient-link witness whose authenticated state carries its status. */
    private static CdsExportNativeRowReceiptDto documentWitness(
            String table, String id, String status) {
        var columns =
                List.of(
                        new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowDigest
                                .Column("status", "VARCHAR", 1, 0));
        var cell =
                status == null
                        ? new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState
                                .Cell(true, 0, null)
                        : new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState
                                .Cell(
                                false,
                                1,
                                HexFormat.of()
                                        .formatHex(
                                                CdsImportKeys.digest()
                                                        .digest(
                                                                status.getBytes(
                                                                        java.nio.charset
                                                                                .StandardCharsets
                                                                                .UTF_8))));
        var state =
                new io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeRowState(
                        table, id, columns, List.of(cell));
        return new CdsExportNativeRowReceiptDto(
                table,
                id,
                CdsExportPatientTableSourceReader.key(
                        Integer.parseInt(id.substring(id.indexOf('_') + 1))),
                state.digest(),
                state);
    }
}
