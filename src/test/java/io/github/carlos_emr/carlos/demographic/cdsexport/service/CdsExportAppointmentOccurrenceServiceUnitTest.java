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
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Shared facade, explicit-zone, receipt/field closure and sticky-failure integration. */
class CdsExportAppointmentOccurrenceServiceUnitTest {
    private static final String HASH = "a".repeat(64), PATIENT = "b".repeat(64);
    @TempDir Path directory;

    @Test
    void shouldVerifyNativeAppointmentUsesExactCurrentDisplayLookupsAndReceivingParser_forAppointmentOccurrenceServiceContract() throws Exception {
        var f = fixture(false);
        var records = parse(compose(f, false));
        var parser = new CdsFieldParser();
        assertThat(parser.firstText(records.get(1), "AppointmentNotes", 32000))
                .isEqualTo("Current note");
        assertThat(parser.firstText(records.get(1), "Provider/Name/FirstName", 50))
                .isEqualTo("Zoë😀");
        assertThat(f.result.nativeOnlyRows()).isEqualTo(1);
        assertThat(f.result.importedRows()).isZero();
        assertThat(f.receipts).hasSize(1);
        assertThat(f.receipts.getFirst().fields()).hasSize(32);
    }

    @Test
    void shouldVerifyImportedAppointmentRetainsOriginalClinicianAndEffectiveDate_withCurrentNotes()
            throws Exception {
        var f = fixture(true);
        var records = parse(compose(f, false));
        var parser = new CdsFieldParser();
        assertThat(parser.firstText(records.get(1), "Provider/Name/FirstName", 50))
                .isEqualTo("Original");
        assertThat(parser.firstText(records.get(1), "AppointmentDate/FullDate", 10))
                .isEqualTo("2024-03-01");
        assertThat(parser.firstText(records.get(1), "AppointmentNotes", 32000))
                .isEqualTo("Current note");
        assertThat(f.result.importedRows()).isEqualTo(1);
        assertThat(f.result.retainedSourceFields()).isEqualTo(f.fields.size());
    }

    @Test
    void shouldVerifyMissingCapturedZoneCannotReadRetainedOrClinicalValuesOrUseTheJvmDefault_forAppointmentOccurrenceServiceContract()
            throws Exception {
        var f = fixture(false);
        f.scopes.remove("clinical_timezone");
        f.tables = Set.copyOf(f.scopes.keySet());
        assertThatThrownBy(() -> compose(f, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_OCCURRENCE_SCOPE_INVALID");
        verifyNoInteractions(f.rows, f.values, f.witnesses);
    }

    @Test
    void shouldVerifyForeignCapturedZoneGenerationCannotBeRelabelledAsTheCurrentSnapshot_forAppointmentOccurrenceServiceContract() throws Exception {
        var f = fixture(false);
        when(f.zones.read(
                        any(),
                        any(),
                        anySet(),
                        any(CdsExportPatientCaptureDto.class),
                        anyLong(),
                        anyLong(),
                        any()))
                .thenReturn(
                        new CdsExportClinicalZoneService.Bound(
                                "c".repeat(64), ZoneId.of("America/Toronto"), "fixture"));
        refused(f);
    }

    @Test
    void shouldVerifyMissingAndDuplicateRowsCannotBorrowAValidNativeReceipt_forAppointmentOccurrenceServiceContract() throws Exception {
        var missing = fixture(false);
        missing.parents = List.of();
        refused(missing);
        var duplicate = fixture(false);
        duplicate.parents = List.of(duplicate.parents.getFirst(), duplicate.parents.getFirst());
        refused(duplicate);
    }

    @Test
    void shouldVerifyChangedLookupGenerationAndMissingWitnessesCannotReturnAnOccurrence_forAppointmentOccurrenceServiceContract() throws Exception {
        var changed = fixture(false);
        changed.changedGeneration = true;
        refused(changed);
        var missing = fixture(false);
        missing.receiptRows = List.of();
        refused(missing);
    }

    @Test
    void shouldVerifyArchiveHistoryNeedsReview_afterSourceAuthenticationInsteadOfBeingDropped()
            throws Exception {
        var f = fixture(false);
        f.scopes.put("appointmentArchive", new CdsExportCapturedTableDto(Set.of("id"), 1, HASH));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        verifyNoInteractions(f.values);
        f.mergeEofFailure = true;
        assertThatThrownBy(() -> compose(f, false)).hasMessage("MERGE_SOURCE_EOF");
    }

    @Test
    void shouldExportAUiBookedAppointment_withItsScheduleReasonLabelAndBookingContext()
            throws Exception {
        var f = uiBooked();
        var records = parse(compose(f, false));
        var parser = new CdsFieldParser();
        assertThat(records).hasSize(2);
        assertThat(parser.firstText(records.get(1), "AppointmentPurpose", 250))
                .isEqualTo("Follow-Up Cough for 3 days");
        assertThat(parser.firstText(records.get(1), "AppointmentStatus", 250))
                .isEqualTo("Exact booked Ω");
        assertThat(parser.firstText(records.get(1), "Duration", 10)).isEqualTo("15");
        assertThat(f.result.nativeOnlyRows()).isEqualTo(1);
        assertThat(f.receipts).hasSize(1);
        var fields = f.receipts.getFirst().fields();
        assertThat(fields.values())
                .noneMatch(
                        field ->
                                field.outcome()
                                        == CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
        // 26 appointment, 6 public provider, 9 status definition and 8 reason-list item fields.
        assertThat(fields).hasSize(49);
        assertThat(
                        fields.get(
                                        CdsExportAppointmentMapperUnitTest.itemKey(
                                                2, "label"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
    }

    @Test
    void shouldRecognizeABookingMadeBeforeARename_onlyFromTheSamePatientsArchivedName()
            throws Exception {
        var renamed = new TreeMap<>(uiBooking());
        renamed.put("name", "Maiden,Synthetic");
        var held = uiBooked(renamed);
        assertThatThrownBy(() -> compose(held, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_REQUIRES_REVIEW");
        var f = uiBooked(renamed);
        f.auxiliary = new ArrayList<>(f.auxiliary);
        f.auxiliary.add(
                CdsExportAppointmentMapperUnitTest.demographicArchive(
                        5, 27, "Maiden", "Synthetic"));
        f.scopes.put(
                "demographicArchive",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_ARCHIVE
                                        .columns()),
                        1,
                        HASH));
        assertThat(parse(compose(f, false))).hasSize(2);
    }

    @Test
    void shouldVerifyFinalReceiptRevocationAndParentEofCannotBeSwallowedIntoValidXml_forAppointmentOccurrenceServiceContract() throws Exception {
        var revoked = fixture(false);
        revoked.revokeAtReceipt = true;
        assertThatThrownBy(() -> compose(revoked, true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(revoked.receipts).hasSize(1);
        var eof = fixture(false);
        eof.parentEofFailure = true;
        assertThatThrownBy(() -> compose(eof, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(eof.result).isNull();
    }

    @Test
    void shouldVerifyReceiptSinkAndRetainedValuesFinalFailuresKeepTheWholeDocumentFailed_forAppointmentOccurrenceServiceContract() throws Exception {
        var sink = fixture(false);
        sink.receiptFailure = true;
        assertThatThrownBy(() -> compose(sink, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        var values = fixture(false);
        values.valuesEofFailure = true;
        assertThatThrownBy(() -> compose(values, true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }

    @Test
    void shouldVerifyAMissingImportedOccurrenceNeedsReview_insteadOfReplayingOriginalXml() throws Exception {
        var f = fixture(true);
        f.parents = List.of();
        f.receiptRows = List.of();
        f.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(
                        Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns()),
                        0,
                        HASH));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldVerifyMissingDemographicAndCollationAliasesCannotSupplyAnInventedProviderOrName_forAppointmentOccurrenceServiceContract()
            throws Exception {
        var missing = fixture(false);
        missing.auxiliary =
                missing.auxiliary.stream()
                        .filter(row -> !row.table().equals("demographic"))
                        .toList();
        refused(missing);
        var alias = fixture(false);
        alias.auxiliary =
                alias.auxiliary.stream().filter(row -> !row.table().equals("provider")).toList();
        alias.scopes.put(
                "provider",
                new CdsExportCapturedTableDto(
                        io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS,
                        0,
                        HASH));
        assertThatThrownBy(() -> compose(alias, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldVerifyUnaccountedRetainedFieldsAndNativeBoundsCannotBecomeCompletePatientResults_forAppointmentOccurrenceServiceContract()
            throws Exception {
        var f = fixture(true);
        var original = f.fields.getFirst();
        var more = new ArrayList<>(f.fields);
        more.add(
                new CdsExportRetainedFieldDto(
                        "d".repeat(64),
                        original.runId(),
                        original.sourcePatientKey(),
                        original.fieldUnitKey(),
                        original.category(),
                        original.sourcePath(),
                        original.occurrence(),
                        original.namespace(),
                        original.valueType(),
                        original.precision(),
                        original.originalInline(),
                        original.logicalInline(),
                        original.originalPayload()));
        f.fields = more;
        refused(f);
        var limit = fixture(false);
        limit.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(
                        Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns()),
                        101,
                        HASH));
        assertThatThrownBy(() -> compose(limit, false)).hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
        verifyNoInteractions(limit.rows, limit.values);
    }

    private void refused(Fixture f) {
        assertThatThrownBy(() -> compose(f, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_OCCURRENCE_SCOPE_INVALID");
        assertThat(f.result).isNull();
    }

    private static final class Fixture {
        final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
        final CdsExportRetainedReceiptVerificationService verification =
                mock(CdsExportRetainedReceiptVerificationService.class);
        final CdsExportNativeRowReceiptReader witnesses =
                mock(CdsExportNativeRowReceiptReader.class);
        final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
        final CdsExportClinicalZoneService zones = mock(CdsExportClinicalZoneService.class);
        final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
        final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
        final CdsExportRunControl control = mock(CdsExportRunControl.class);
        final Map<String, CdsExportCapturedTableDto> scopes = new TreeMap<>();
        final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
        CdsExportLeaseDto lease;
        CdsExportPatientCaptureDto source;
        Set<String> tables;
        List<CdsExportCapturedTextRowDto> parents, auxiliary;
        List<CdsExportNativeRowReceiptDto> receiptRows;
        List<CdsExportRetainedFieldDto> fields;
        boolean changedGeneration,
                mergeEofFailure,
                parentEofFailure,
                valuesEofFailure,
                receiptFailure,
                revokeAtReceipt;
        CdsExportClinicalOccurrenceService.Result result;
    }

    /**
     * The values {@code AppointmentAddRecord2Action} stores for a booking made in the add form,
     * after {@code AppointmentUpdateRecord2Action} marked the patient here (status "Q1" stands
     * for that captured status definition; the archive row that edit wrote is left out here). The add and edit forms post neither style nor
     * billing (both stored NULL); a recurring booking or an older row carries values, so this
     * booking has both to prove they no longer hold the chart.
     */
    private static Map<String, String> uiBooking() {
        var values = new TreeMap<String, String>();
        values.put("appointment_date", "2026-11-02");
        values.put("start_time", "09:00:00");
        values.put("end_time", "09:14:00");
        values.put("provider_no", "0é27");
        values.put("name", "Fixture,Synthetic");
        values.put("reason", "Cough for 3 days");
        values.put("reasonCode", "2");
        values.put("notes", "");
        values.put("location", "Main Clinic");
        values.put("type", "Follow-up visit");
        values.put("style", "0");
        values.put("billing", "B");
        values.put("urgency", "critical");
        values.put("resources", "");
        values.put("remarks", "");
        values.put("status", "Q1");
        values.put("program_id", "0");
        values.put("creator", "999998");
        values.put("lastupdateuser", "999998");
        values.put("createdatetime", "2026-11-01 16:20:00");
        values.put("updatedatetime", "2026-11-02 09:03:00");
        return values;
    }

    private Fixture uiBooked() throws Exception {
        return uiBooked(uiBooking());
    }

    private Fixture uiBooked(Map<String, String> booking) throws Exception {
        var f =
                fixture(
                        new CdsExportAppointmentFieldReconcilerUnitTest().nativeFixture(booking),
                        false);
        var item = CdsExportAppointmentMapperUnitTest.reasonItem("2", "Follow-Up");
        f.auxiliary = new ArrayList<>(f.auxiliary);
        f.auxiliary.add(item);
        f.scopes.put(
                "LookupListItem", new CdsExportCapturedTableDto(item.fields().keySet(), 1, HASH));
        return f;
    }

    private Fixture fixture(boolean imported) throws Exception {
        return fixture(
                new CdsExportAppointmentFieldReconcilerUnitTest().fixture(imported, "Current note"),
                imported);
    }

    private Fixture fixture(
            CdsExportAppointmentFieldReconcilerUnitTest.Fixture source, boolean imported)
            throws Exception {
        var f = new Fixture();
        f.parents = List.of(source.row());
        f.receiptRows = List.of(source.witness());
        f.fields = imported ? source.fields() : List.of();
        var index =
                imported
                        ? source.index()
                        : new CdsExportRetainedIndexDto(
                                HASH,
                                0,
                                0,
                                Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                                Set.of(),
                                Map.of(),
                                Map.of());
        var checked =
                imported
                        ? source.checked()
                        : new CdsExportRetainedReceiptVerificationDto(
                                HASH, Map.of(), Set.of(), Set.of());
        var lookups = new ArrayList<CdsExportCapturedTextRowDto>();
        lookups.add(source.demographic());
        lookups.addAll(source.lookups().providers().values());
        source.lookups().statuses().values().forEach(lookups::addAll);
        f.auxiliary = lookups;
        var command =
                new CdsExportCommand(
                        "appointment-assembly",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.APPOINTMENTS),
                        "recipient",
                        "audit");
        f.lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        HASH,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                HASH,
                                "sender",
                                HASH,
                                HASH,
                                new CdsExportStorageBindingDto("primary", "primary", HASH)));
        when(f.patient.patient()).thenReturn(27);
        f.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(source.row().fields().keySet(), 1, HASH));
        f.scopes.put(
                "appointmentArchive",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                        .columns()),
                        0,
                        HASH));
        f.scopes.put(
                "demographic_merged",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns()),
                        0,
                        HASH));
        for (String table :
                List.of(
                        "provider",
                        "appointment_status",
                        "demographic",
                        "LookupListItem",
                        "demographicArchive")) {
            var matches = lookups.stream().filter(row -> row.table().equals(table)).toList();
            f.scopes.put(
                    table,
                    new CdsExportCapturedTableDto(
                            matches.isEmpty() ? Set.of("id") : matches.getFirst().fields().keySet(),
                            matches.size(),
                            HASH));
        }
        for (String table :
                List.of(
                        "cds_import_unit",
                        "cds_import_field",
                        "cds_import_preparation",
                        "cds_import_dependency",
                        "cds_import_patient",
                        "cds_import_run"))
            f.scopes.put(
                    table,
                    new CdsExportCapturedTableDto(
                            Set.of("id"),
                            index.tables().getOrDefault(table, Map.of()).size(),
                            HASH));
        f.scopes.put(
                "clinical_timezone",
                new CdsExportCapturedTableDto(Set.of("sha256", "tzdbVersion", "zoneId"), 1, HASH));
        f.tables = Set.copyOf(f.scopes.keySet());
        var generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenReturn(f.scopes);
        when(generation.values()).thenReturn(file("1", "SOURCE_VALUES"));
        when(generation.index()).thenReturn(file("2", "SOURCE_INDEX"));
        f.source = new CdsExportPatientCaptureDto(generation, file("3", "SOURCE_MANIFEST"));
        when(f.zones.read(
                        any(),
                        any(),
                        anySet(),
                        any(CdsExportPatientCaptureDto.class),
                        anyLong(),
                        anyLong(),
                        any()))
                .thenReturn(
                        new CdsExportClinicalZoneService.Bound(
                                HASH, ZoneId.of("America/Toronto"), "fixture"));
        var streams =
                new CdsExportRetainedSectionWriter.Values() {
                    public Reader text(CdsExportRetainedFieldDto field) {
                        return new StringReader(field.logicalInline());
                    }

                    public InputStream binary(CdsExportRetainedFieldDto field) {
                        throw new AssertionError();
                    }
                };
        doAnswer(
                        call -> {
                            ((CdsExportRetainedValuesService.Work) call.getArgument(10))
                                    .use(index, f.fields, streams);
                            if (f.valuesEofFailure) throw new IOException("RETAINED_FINAL_EOF");
                            return null;
                        })
                .when(f.values)
                .withValues(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
        when(f.verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(checked);
        when(f.witnesses.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var witness : f.receiptRows)
                                ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7))
                                        .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    HASH,
                                    Map.of("appointment", (long) f.receiptRows.size()),
                                    f.receiptRows.size(),
                                    0,
                                    Set.of());
                        });
        when(f.rows.forEachTable(
                        any(), any(), anySet(), anySet(), any(), anyLong(), anyLong(), anyInt(),
                        any(), any()))
                .thenAnswer(
                        call -> {
                            for (var row : f.auxiliary)
                                ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                        .accept(row);
                            return f.changedGeneration
                                    ? mock(CdsExportNativeCaptureDto.class)
                                    : generation;
                        });
        when(f.rows.forEach(
                        any(),
                        any(),
                        anySet(),
                        anyString(),
                        any(),
                        anyLong(),
                        anyLong(),
                        anyInt(),
                        any(),
                        any()))
                .thenAnswer(
                        call -> {
                            boolean merge = "demographic_merged".equals(call.getArgument(3));
                            if (merge) {
                                if (f.mergeEofFailure) throw new IOException("MERGE_SOURCE_EOF");
                            } else {
                                for (var row : f.parents)
                                    ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                            .accept(row);
                                if (f.parentEofFailure) throw new IOException("PARENT_FINAL_EOF");
                            }
                            return generation;
                        });
        return f;
    }

    private byte[] compose(Fixture f, boolean swallow) throws Exception {
        var patient = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(
                Map.of(
                        "first_name",
                        "Synthetic",
                        "last_name",
                        "Fixture",
                        "year_of_birth",
                        "1980",
                        "month_of_birth",
                        "01",
                        "date_of_birth",
                        "01",
                        "patient_status",
                        "AC",
                        "sex",
                        "U"));
        var demographic =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var appointments =
                new CdsExportAppointmentOccurrenceService(
                        f.values, f.verification, f.witnesses, f.captures, f.rows, f.zones);
        var service =
                new CdsExportClinicalOccurrenceService(
                        f.values,
                        f.verification,
                        f.witnesses,
                        f.captures,
                        mock(CdsExportImmunizationOccurrenceService.class),
                        appointments);
        var output = new ByteArrayOutputStream();
        var bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        100000, 8L << 20, 1000, 8L << 20, 100, 1000);
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        HASH,
                        PATIENT,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        f.lease.document().command().categories(),
                        emitter -> {
                            emitter.emit(
                                    new CdsExportXmlComposer.Section(
                                            new CdsExportIdentityDto(
                                                    PATIENT,
                                                    CdsCategory.DEMOGRAPHICS,
                                                    "f".repeat(64)),
                                            xml ->
                                                    new CdsExportDemographicXmlWriter()
                                                            .write(demographic, xml)));
                            try {
                                CdsExportClinicalOccurrenceService.Receipts sink =
                                        receipt -> {
                                            if (f.receiptFailure)
                                                throw new IOException("RECEIPT_WRITE_FAILED");
                                            f.receipts.add(receipt);
                                            if (f.revokeAtReceipt)
                                                doThrow(
                                                                new SecurityException(
                                                                        "REVOKED_AFTER_RECEIPT"))
                                                        .when(f.captures)
                                                        .authorizeSources(
                                                                any(), any(), anySet(), any());
                                        };
                                f.result =
                                        service.append(
                                                CdsCategory.APPOINTMENTS,
                                                f.lease,
                                                f.patient,
                                                PATIENT,
                                                f.tables,
                                                f.source,
                                                file("4", "RETAINED_MANIFEST"),
                                                bounds,
                                                f.control,
                                                emitter,
                                                sink);
                            } catch (IOException | RuntimeException failure) {
                                if (!swallow) throw failure;
                            }
                        },
                        ignored -> {},
                        8L << 20,
                        () -> {});
        return output.toByteArray();
    }

    private List<CdsRecordDto> parse(byte[] xml) throws Exception {
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(xml),
                        "27",
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.APPOINTMENTS),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var input = Files.write(directory.resolve(UUID.randomUUID() + ".xml"), xml);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records;
    }

    private static CdsExportStagedFileDto file(String name, String purpose) {
        return new CdsExportStagedFileDto(name.repeat(64), purpose, HASH, 1);
    }
}
