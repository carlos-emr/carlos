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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportCareOccurrenceServiceUnitTest {
    private static final String PATH = "/OmdCds[1]/PatientRecord[1]/CareElements[1]/Weight[1]";
    private Map<String, String> observation;
    private List<CdsExportCapturedTextRowDto> extensions;
    private CdsCareElementDto prepared;

    @BeforeEach
    void setup() {
        observation =
                new HashMap<>(
                        Map.of(
                                "id",
                                "7",
                                "demographicNo",
                                "27",
                                "type",
                                "WT",
                                "dataField",
                                "75",
                                "measuringInstruction",
                                "in kg",
                                "comments",
                                "",
                                "dateObserved",
                                "2020-02-28 05:00:00.0",
                                "appointmentNo",
                                "0"));
        prepared =
                new CdsCareElementDto(
                        "Weight",
                        "WT",
                        "75",
                        "kg",
                        null,
                        CdsTemporal.parse(null, null, "2020-02-28", null, "Date"),
                        false,
                        false,
                        null,
                        List.of());
        extensions = new ArrayList<>();
        extension("cdsCareElement", "Weight");
        extension("date_precision", "FULL_DATE");
        extension("cdsSourceDate", "2020-02-28");
        extension("cdsSourcePath", PATH);
        extension("unit", "kg");
    }

    private void extension(String key, String value) {
        var fields = new HashMap<String, String>();
        fields.put("id", Integer.toString(extensions.size() + 20));
        fields.put("measurement_id", "7");
        fields.put("keyval", key);
        fields.put("val", value);
        extensions.add(
                new CdsExportCapturedTextRowDto(
                        "measurementsExt",
                        CdsExportPatientTableSourceReader.key(extensions.size() + 20),
                        fields));
    }

    private void bind() throws IOException {
        CdsExportCareOccurrenceService.bind(
                27,
                new CdsExportCapturedTextRowDto(
                        "measurements", CdsExportPatientTableSourceReader.key(7), observation),
                extensions,
                prepared,
                PATH);
    }

    @Test
    void shouldRequireTheWholeSealedObservation_andAllTemporalAndUnitMetadata() {
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"demographicNo", "type", "dataField", "measuringInstruction", "comments"})
    void shouldRefuseChangedNativeFields_insteadOfReplayingSource(String column) {
        observation.put(column, "Changed");
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseMissingNativeDate_forCareOccurrenceServiceContract() {
        observation.put("dateObserved", null);
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseAddedAppointmentContext_forCareOccurrenceServiceContract() {
        observation.put("appointmentNo", "91");
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseMissingPrecisionMetadata_forCareOccurrenceServiceContract() {
        extensions.removeIf(row -> row.fields().get("keyval").equals("date_precision"));
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseUnknownExtraMetadata_forCareOccurrenceServiceContract() {
        extension("unmappedClinicalContext", "Additional context");
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseDuplicateMetadata_forCareOccurrenceServiceContract() {
        extension("unit", "kg");
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseNullDuplicateMetadata_beforeAnOtherwiseCorrectValue() {
        var last = extensions.removeLast();
        extension("unit", null);
        extensions.add(last);
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseForeignObservationExtension_forCareOccurrenceServiceContract() {
        var row = extensions.getFirst();
        var f = new HashMap<>(row.fields());
        f.put("measurement_id", "8");
        extensions.set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), f));
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseChangedSourcePathAssociation_forCareOccurrenceServiceContract() {
        extensions.removeIf(row -> row.fields().get("keyval").equals("cdsSourcePath"));
        extension("cdsSourcePath", PATH.replace("Weight[1]", "Weight[2]"));
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldKeepEntryProviderAsLocalProvenance_withoutInventingASourceAuthor() {
        observation.put("providerNo", "999998");
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @Test
    void shouldRequireOriginalRecordedShift_whenPresentInPreparation() {
        prepared =
                new CdsCareElementDto(
                        "Weight",
                        "WT",
                        "75",
                        "kg",
                        null,
                        CdsTemporal.shift(prepared.date(), 2, "Date"),
                        false,
                        false,
                        null,
                        List.of());
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        extension("cdsShiftDays", "2");
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    /**
     * Issue #6200 (CARE-5). Before, a chart with more than 8,192 observations or 64 MiB of
     * captured measurement rows was held with CDS_EXPORT_NATIVE_CARE_PATIENT_LIMIT_EXCEEDED; a
     * long-standing chart entered through the flowsheets reached it. It is now read in windows.
     */
    @Nested
    class LongStandingCharts {
        private CdsExportCareCaptureFixture fixture;

        @BeforeEach
        void chart() throws Exception {
            fixture =
                    new CdsExportCareCaptureFixture(
                            EnumSet.of(
                                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                            .DEMOGRAPHICS,
                                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                            .CARE_ELEMENTS,
                                    io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory
                                            .NEW_CATEGORY));
            fixture.add(CdsExportCareCaptureFixture.vocabulary(1, "WT"));
            // 9,000 weights (one window holds 8,192), ids of one to five digits so the decimal
            // order differs from the numeric one, and 250 deleted versions with the extensions
            // their deletion left behind.
            for (long id = 1; id <= 9_000; id++)
                fixture.add(CdsExportCareCaptureFixture.measurement(id, "WT", Long.toString(60 + id % 40)));
            for (long i = 0; i < 250; i++) {
                fixture.add(CdsExportCareCaptureFixture.deleted(90_000 + i, 50_000 + i));
                fixture.add(
                        CdsExportCareCaptureFixture.extension(
                                80_000 + i, 50_000 + i, "comment", "entered on the wrong chart"));
            }
            for (long id = 1; id <= 100; id++)
                fixture.add(
                        CdsExportCareCaptureFixture.extension(
                                70_000 + id, id * 90, "comment", "after exercise"));
        }

        @Test
        void shouldPlanEveryObservation_exactlyAsAWholeChartReadPlansIt() throws Exception {
            var index =
                    new CdsExportRetainedIndexDto(
                            CdsExportCareCaptureFixture.HASH,
                            0,
                            0,
                            Map.of(),
                            Set.of(),
                            Map.of(),
                            Map.of());
            var windowed = new ArrayList<CdsExportNativeCarePlanner.Occurrence>();
            fixture.service()
                    .forEachNativeSource(
                            fixture.lease,
                            fixture.patient,
                            fixture.requiredTables(),
                            fixture.source(),
                            index,
                            CdsExportCareCaptureFixture.bounds(8192),
                            fixture.control,
                            windowed::add);
            assertThat(fixture.rowScans).isGreaterThan(2);
            // The single plan the export made of the whole chart before windows existed.
            var whole = new TreeMap<String, CdsExportCapturedTextRowDto>();
            for (var table : fixture.tables.values())
                for (var row : table.values())
                    whole.put(row.table() + ":" + CdsExportCapturedCareRowsReader.id(row), row);
            var plan = new CdsExportNativeCarePlanner().plan(index, whole, () -> {});
            assertThat(windowed.stream().filter(o -> !o.deleted()).toList())
                    .isEqualTo(plan.stream().filter(o -> !o.deleted()).toList());
            assertThat(windowed.stream().filter(o -> o.deleted()).toList())
                    .containsExactlyInAnyOrderElementsOf(
                            plan.stream().filter(o -> o.deleted()).toList());
            assertThat(windowed.stream().filter(o -> !o.deleted())).hasSize(9_000);
        }

        @Test
        void shouldExportEveryMeasurement_ofAChartLargerThanOneWindow() throws Exception {
            org.mockito.Mockito.doAnswer(
                            call -> {
                                CdsExportRetainedValuesService.Work work = call.getArgument(10);
                                work.use(
                                        new CdsExportRetainedIndexDto(
                                                CdsExportCareCaptureFixture.HASH,
                                                0,
                                                0,
                                                Map.of(),
                                                Set.of(),
                                                Map.of(),
                                                Map.of()),
                                        List.of(),
                                        org.mockito.Mockito.mock(
                                                CdsExportRetainedSectionWriter.Values.class));
                                return null;
                            })
                    .when(fixture.values)
                    .withValues(
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anySet(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.anyInt(),
                            org.mockito.ArgumentMatchers.anyLong(),
                            org.mockito.ArgumentMatchers.any(),
                            org.mockito.ArgumentMatchers.any());
            var composed =
                    fixture.compose(fixture.service(), CdsExportCareCaptureFixture.bounds(8192));
            assertThat(composed.result().nativeRows()).isEqualTo(9_000);
            assertThat(composed.result().nativeOnlyRows()).isEqualTo(9_000);
            assertThat(
                            composed.xml()
                                    .occurrences()
                                    .get(
                                            io.github.carlos_emr.carlos.demographic.cds.contract
                                                    .CdsCategory.CARE_ELEMENTS))
                    .isEqualTo(9_000L);
            // Emitted in the decimal order of the ids, as a whole-chart read emitted them.
            var ids = composed.receipts().stream().map(r -> r.nativeId()).toList();
            assertThat(ids).hasSize(9_000).isSortedAccordingTo(Comparator.naturalOrder());
            assertThat(fixture.rowScans).isGreaterThan(2);
            assertThat(fixture.witnessScans).isEqualTo(2);
            // Each receipt's audit lines (its header and grouped field decisions) stay well
            // inside the audit allowance each occurrence brings (auditLimit).
            var json = com.fasterxml.jackson.databind.json.JsonMapper.builder().build();
            for (var receipt : composed.receipts()) {
                long bytes =
                        json.writeValueAsBytes(
                                        Map.of(
                                                "identity", receipt.identity(),
                                                "table", receipt.table(),
                                                "nativeId", receipt.nativeId(),
                                                "retainedFields", List.of(),
                                                "replacedFields", List.of()))
                                .length;
                for (var line :
                        CdsExportPatientDocumentService.fieldDecisions(
                                receipt.identity(),
                                receipt.fields(),
                                org.mockito.Mockito.mock(
                                        io.github.carlos_emr.carlos.demographic.cdsexport.engine
                                                .CdsExportRunControl.class)))
                    bytes += json.writeValueAsBytes(line).length + 1;
                assertThat(bytes)
                        .isLessThan(CdsExportPatientDocumentService.AUDIT_BYTES_PER_OCCURRENCE / 4);
            }
        }
    }

    @Test
    void shouldNameADeletedImportedObservation_insteadOfAGenericUnmappedReview() {
        // Before: deleting an imported care measurement (the only way to correct one) held the
        // patient as generic unmapped data. Its retained source would present the deleted value
        // as current, so the patient is still held, now with a code that says why.
        var refs =
                List.of(
                        new CdsExportRetainedIndexDto.Reference("measurements", "7", "a".repeat(64)),
                        new CdsExportRetainedIndexDto.Reference(
                                "measurementsExt", "20", "b".repeat(64)));
        var current = new HashMap<String, CdsExportCapturedTextRowDto>();
        assertThat(CdsExportCareOccurrenceService.importedObservationDeleted(refs, current))
                .isFalse();
        var deleted = new HashMap<String, String>();
        deleted.put("id", "40");
        deleted.put("originalId", "7");
        deleted.put("demographicNo", "27");
        current.put(
                "measurementsDeleted:40",
                new CdsExportCapturedTextRowDto(
                        "measurementsDeleted", CdsExportPatientTableSourceReader.key(40), deleted));
        assertThat(CdsExportCareOccurrenceService.importedObservationDeleted(refs, current))
                .isTrue();
        current.put(
                "measurements:7",
                new CdsExportCapturedTextRowDto(
                        "measurements",
                        CdsExportPatientTableSourceReader.key(7),
                        new HashMap<>(observation)));
        assertThat(CdsExportCareOccurrenceService.importedObservationDeleted(refs, current))
                .isFalse();
        assertThat(CdsExportCareOccurrenceService.importedObservationDeleted(null, current))
                .isFalse();
    }
}
