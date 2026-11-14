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
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
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

    // Decision 17 replaced the old "any archive row needs review" gate: CARLOS archives an
    // appointment on every status change, billing and edit, so archive rows are now read and
    // assessed as superseded versions. An archive count the delivered rows do not match is still
    // refused, and the merged-patient scan still runs before any value is read.
    @Test
    void shouldRefuseAnArchiveCountTheDeliveredVersionsDoNotMatch_afterSourceAuthentication()
            throws Exception {
        var f = fixture(false);
        f.scopes.put(
                "appointmentArchive",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                        .columns()),
                        1,
                        HASH));
        refused(f);
        f.mergeEofFailure = true;
        assertThatThrownBy(() -> compose(f, false)).hasMessage("MERGE_SOURCE_EOF");
    }

    @Test
    void shouldExportAUiBookedAppointment_afterAStatusChangeArchivedItsBookedVersion()
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
        // 26 appointment, 6 public provider, 9 status definition and 8 reason-list item fields;
        // the superseded archive version is assessed but is not an occurrence of its own.
        assertThat(fields).hasSize(49);
        assertThat(
                        fields.get(
                                        CdsExportAppointmentMapperUnitTest.itemKey(
                                                2, "label"))
                                .outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
    }

    @Test
    void shouldNotExportOrHoldADeletedAppointment_whoseLastVersionIsOnlyInTheArchive()
            throws Exception {
        var f = uiBooked();
        var deleted = new TreeMap<>(uiBooking());
        deleted.put("appointment_date", "2026-10-05");
        f.archives = List.of(f.archives.getFirst(), archive(8, "52", deleted));
        f.scopes.put(
                "appointmentArchive",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                        .columns()),
                        2,
                        HASH));
        var records = parse(compose(f, false));
        assertThat(records).hasSize(2);
        assertThat(new CdsFieldParser().firstText(records.get(1), "AppointmentDate/FullDate", 10))
                .isEqualTo("2026-11-02");
        assertThat(f.receipts).hasSize(1);
        assertThat(
                        CdsExportAppointmentVersions.dispositions(
                                        27, f.archives.get(1), Set.of("51"))
                                .values())
                .allMatch(
                        field ->
                                field.rule().equals(CdsExportAppointmentVersions.REMOVED)
                                        && field.outcome()
                                                == CdsExportFieldAssessmentDto.Outcome
                                                        .OPERATIONAL);
    }

    @Test
    void shouldRefuseAnArchiveVersionOfAnotherPatient_insteadOfDiscardingIt() throws Exception {
        var f = uiBooked();
        var foreign = new TreeMap<>(f.archives.getFirst().fields());
        foreign.put("demographic_no", "28");
        f.archives =
                List.of(
                        new CdsExportCapturedTextRowDto(
                                "appointmentArchive", f.archives.getFirst().recordKey(), foreign));
        assertThatThrownBy(() -> compose(f, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_VERSION_SCOPE_INVALID");
    }

    @Test
    void shouldRecognizeABookingMadeBeforeARename_onlyFromTheSamePatientsArchivedName()
            throws Exception {
        var renamed = new TreeMap<>(uiBooking());
        renamed.put("name", "Maiden,Synthetic");
        // Without the archived name the text is the booking's own: a labelled notes line.
        assertThat(new String(compose(uiBooked(renamed), false), java.nio.charset.StandardCharsets.UTF_8))
                .contains("Booking name: Maiden,Synthetic");
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
        // The receipt does not show the row gone from the chart: its absence is unexplained.
        var f = fixture(true);
        f.parents = List.of();
        f.receiptRows = List.of();
        f.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(
                        Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns()),
                        0,
                        HASH));
        assertThatThrownBy(() -> compose(f, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_DELETION_REQUIRES_REVIEW");
        assertThat(f.receipts).isEmpty();
    }

    /**
     * An imported appointment deleted (AppointmentDeleteRecord2Action) or cut to be pasted
     * elsewhere (AppointmentCutRecord2Action): CARLOS archived the booking and removed the row.
     */
    private Fixture deletedImported(boolean archived) throws Exception {
        var source = new CdsExportAppointmentFieldReconcilerUnitTest().fixture(true, "Current note");
        var f = fixture(source, true);
        String number = source.row().fields().get("appointment_no");
        f.parents = List.of();
        f.receiptRows = List.of();
        f.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(
                        Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns()),
                        0,
                        HASH));
        // The verified receipt: the imported row is no longer in this patient's capture.
        var units = new TreeMap<>(source.checked().units());
        for (var entry : units.entrySet())
            entry.setValue(
                    entry.getValue().stream()
                            .map(
                                    ref ->
                                            ref.expected().table().equals("appointment")
                                                    ? new CdsExportRetainedReceiptVerificationDto
                                                            .CheckedReference(
                                                            ref.expected(),
                                                            null,
                                                            CdsExportRetainedReceiptVerificationDto
                                                                    .Reason.ROW_NOT_IN_CAPTURE)
                                                    : ref)
                            .toList());
        var unchanged = new HashSet<>(source.checked().unchangedUnits());
        var review = new HashSet<>(source.checked().reviewUnits());
        for (var entry : units.entrySet())
            if (entry.getValue().stream()
                    .anyMatch(ref -> ref.expected().table().equals("appointment"))) {
                unchanged.remove(entry.getKey());
                review.add(entry.getKey());
            }
        when(f.verification.verify(
                        any(), any(), anySet(), any(), any(), anyLong(), anyLong(), anyInt(),
                        any()))
                .thenReturn(
                        new CdsExportRetainedReceiptVerificationDto(HASH, units, unchanged, review));
        if (archived) {
            var booking = new TreeMap<>(source.row().fields());
            f.archives = List.of(archive(9, number, booking));
            f.scopes.put(
                    "appointmentArchive",
                    new CdsExportCapturedTableDto(
                            Set.copyOf(
                                    CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                            .columns()),
                            1,
                            HASH));
        }
        return f;
    }

    @Test
    void shouldNotExportOrHoldAnImportedAppointment_deletedOrCutInCarlos() throws Exception {
        var f = deletedImported(true);
        var records = parse(compose(f, false));
        // Only the demographics record: the chart no longer shows the booking.
        assertThat(records).hasSize(1);
        assertThat(f.receipts).isEmpty();
        assertThat(f.result).isNotNull();
        assertThat(
                        CdsExportAppointmentVersions.dispositions(
                                        27,
                                        f.archives.getFirst(),
                                        Set.of(),
                                        Set.of(f.archives.getFirst().fields().get("appointment_no")))
                                .values())
                .allMatch(
                        field ->
                                field.rule().equals(CdsExportAppointmentVersions.REMOVED_IMPORTED)
                                        && field.outcome()
                                                == CdsExportFieldAssessmentDto.Outcome
                                                        .OPERATIONAL);
    }

    @Test
    void shouldHoldAnImportedAppointmentThatVanished_withoutCarlosArchiveCopy() throws Exception {
        var f = deletedImported(false);
        assertThatThrownBy(() -> compose(f, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_DELETION_REQUIRES_REVIEW");
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
        // A provider number with no exact provider record exports without Provider; nothing is
        // invented from a collation alias and the chart is not held.
        compose(alias, false);
        assertThat(alias.receipts).hasSize(1);
        assertThat(
                        alias.receipts.getFirst().fields().entrySet().stream()
                                .filter(field -> field.getKey().column().equals("provider_no"))
                                .map(field -> field.getValue().rule())
                                .toList())
                // This job did not select NewCategory: the number has no element and its source
                // record is not written, which is an explicit decision, not a hold.
                .containsExactly(CdsExportDemographicSupportingMapper.NOT_SELECTED_RULE);
    }

    @Test
    void shouldGiveEveryAppointmentItsSourceRecord_inTheOrderOfItsElement() throws Exception {
        // The values a screen saved that Appointments cannot hold: a slot past midnight, a
        // status code with no definition, a provider number with no record and scheduler context.
        var stored = new TreeMap<String, String>();
        stored.put("start_time", "23:30:00");
        stored.put("end_time", "00:29:00");
        stored.put("status", "ZZ");
        stored.put("provider_no", "GONE");
        stored.put("location", "Main Clinic");
        stored.put("type", "Follow-up visit");
        stored.put("notes", "n".repeat(32001));
        var f =
                fixture(
                        new CdsExportAppointmentFieldReconcilerUnitTest().nativeFixture(stored),
                        false);
        selectNewCategory(f);
        var records = parse(compose(f, false));
        var parser = new CdsFieldParser();
        assertThat(parser.firstText(records.get(1), "AppointmentTime", 64))
                .isEqualTo("23:30:00-05:00");
        assertThat(parser.firstText(records.get(1), "Duration", 8)).isEqualTo("60");
        assertThat(parser.firstText(records.get(1), "AppointmentStatus", 250)).isEqualTo("ZZ");
        assertThat(parser.firstText(records.get(1), "Provider/Name/LastName", 50)).isNull();
        assertThat(parser.firstText(records.get(1), "AppointmentNotes", 32000))
                .endsWith(CdsExportAppointmentMapper.SHORTENED_MARK)
                .hasSize(32000);
        // With NewCategory selected the carried values name the source record.
        assertThat(
                        f.receipts.getFirst().fields().entrySet().stream()
                                .filter(field -> field.getKey().column().equals("location"))
                                .map(field -> field.getValue().destination())
                                .toList())
                .containsExactly("NewCategory/ResidualInfo");
        var appointments =
                new CdsExportAppointmentOccurrenceService(
                        f.values, f.verification, f.witnesses, f.captures, f.rows, f.zones);
        var seen = new ArrayList<CdsExportAppointmentOccurrenceService.Occurrence>();
        appointments.forEachOccurrence(
                f.lease,
                f.patient,
                PATIENT,
                f.tables,
                f.source,
                file("4", "RETAINED_MANIFEST"),
                new CdsExportClinicalOccurrenceService.Bounds(
                        100000, 8L << 20, 1000, 8L << 20, 100, 1000),
                f.control,
                seen::add);
        assertThat(seen).hasSize(1);
        var occurrence = seen.getFirst();
        assertThat(occurrence.ordinal()).isEqualTo(1);
        assertThat(occurrence.identity()).isEqualTo(f.receipts.getFirst().identity());
        assertThat(occurrence.complete()).containsEntry("AppointmentNotes", "n".repeat(32001));
        var carrier =
                CdsExportAppointmentSourceMapper.map(
                        occurrence.ordinal(),
                        occurrence.row(),
                        occurrence.lookups(),
                        occurrence.complete());
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(carrier.json().replace("\\u0020", " "));
        assertThat(json.path("appointment").asLong()).isEqualTo(1);
        assertThat(json.path("fields").path("end_time").asText()).isEqualTo("00:29:00");
        assertThat(json.path("fields").path("status").asText()).isEqualTo("ZZ");
        assertThat(json.path("fields").path("provider_no").asText()).isEqualTo("GONE");
        assertThat(json.path("fields").path("location").asText()).isEqualTo("Main Clinic");
        assertThat(json.path("complete").path("AppointmentNotes").asText())
                .isEqualTo("n".repeat(32001));
        assertThat(json.path("publicProvider").isNull()).isTrue();
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(carrier.rows(), carrier.fields(), 1_000_000, () -> {});
    }

    @Test
    void shouldRestoreTheStoredSlotNotesAndSchedulerContext_whenTheCarlosImporterReadsTheExport()
            throws Exception {
        // CARLOS to CARLOS: a booking that runs past midnight, with notes longer than the CDS
        // element and scheduler values CDS has no field for. The export writes the element by
        // its documented rules and the appointment's source record; the importer proves the
        // record against the element and restores the exact stored values.
        var stored = new TreeMap<String, String>();
        stored.put("start_time", "23:30:00");
        stored.put("end_time", "00:29:00");
        stored.put("notes", "n".repeat(32500));
        stored.put("location", "Main Clinic");
        stored.put("type", "Follow-up visit");
        stored.put("resources", "Room 2");
        stored.put("urgency", "critical");
        stored.put("style", "1");
        stored.put("billing", "B");
        stored.put("bookingSource", "OSCAR");
        var f =
                fixture(
                        new CdsExportAppointmentFieldReconcilerUnitTest().nativeFixture(stored),
                        false);
        selectNewCategory(f);
        var occurrences = new ArrayList<CdsExportAppointmentOccurrenceService.Occurrence>();
        new CdsExportAppointmentOccurrenceService(
                        f.values, f.verification, f.witnesses, f.captures, f.rows, f.zones)
                .forEachOccurrence(
                        f.lease,
                        f.patient,
                        PATIENT,
                        f.tables,
                        f.source,
                        file("4", "RETAINED_MANIFEST"),
                        new CdsExportClinicalOccurrenceService.Bounds(
                                100000, 8L << 20, 1000, 8L << 20, 100, 1000),
                        f.control,
                        occurrences::add);
        assertThat(occurrences).hasSize(1);
        var occurrence = occurrences.getFirst();
        var carrier =
                CdsExportAppointmentSourceMapper.map(
                        occurrence.ordinal(),
                        occurrence.row(),
                        occurrence.lookups(),
                        occurrence.complete());
        var patient = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "U"));
        var demographic =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var selected =
                Set.of(
                        CdsCategory.DEMOGRAPHICS,
                        CdsCategory.APPOINTMENTS,
                        CdsCategory.NEW_CATEGORY);
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        PATIENT, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
                                xml -> new CdsExportDemographicXmlWriter().write(demographic, xml)),
                        new CdsExportXmlComposer.Section(
                                occurrence.identity(), occurrence.content()),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        PATIENT, CdsCategory.NEW_CATEGORY, "e".repeat(64)),
                                xml -> CdsExportAppointmentSourceMapper.write(carrier, xml)));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        PATIENT,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        8L << 20,
                        () -> {});
        // The package's own gate: the document with the source record is schema-valid.
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        ignored -> {},
                        () -> {});
        var folder = Files.createTempDirectory(directory, "transfer-");
        var received = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(folder.resolve("patient.xml"), output.toByteArray()),
                        folder.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        received::add);
        var element =
                received.stream()
                        .filter(record -> record.section().equals("Appointments"))
                        .findFirst()
                        .orElseThrow();
        var parser = new CdsFieldParser();
        // What any receiver reads: the start, 60 minutes, and notes cut at the CDS limit.
        assertThat(parser.firstText(element, "Duration", 8)).isEqualTo("60");
        assertThat(parser.firstText(element, "AppointmentNotes", 32768))
                .hasSize(32000)
                .endsWith(CdsExportAppointmentMapper.SHORTENED_MARK);
        // What a receiving CARLOS restores.
        var settings = new Properties();
        settings.setProperty("cds.import.clinicalZone", "America/Toronto");
        var providers = mock(CdsProviderResolver.class);
        when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                null, CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
        var preparation = mock(CdsNativePreparationService.class);
        var appointmentDao = mock(io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao.class);
        var demographicDao = mock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class);
        var importer =
                new CdsAppointmentPersister(
                        parser,
                        new CdsClinicalNormalizationService(parser),
                        new CdsImportConfigurationService(settings),
                        providers,
                        preparation,
                        appointmentDao,
                        demographicDao);
        var command =
                new io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand(
                        folder,
                        "owned-submission",
                        "999998",
                        null,
                        null,
                        "clinic",
                        "pcon-2024-02",
                        false,
                        false,
                        true,
                        0,
                        null,
                        false);
        importer.prepare("run", 1, "unit", element, command);
        var prepared = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(preparation)
                .store(eq("run"), eq(1L), eq("unit"), anyString(), eq(command), prepared.capture());
        var dto = (CdsAppointmentNativeDto) prepared.getValue();
        assertThat(dto.representable()).isTrue();
        assertThat(dto.date()).isEqualTo(occurrence.row().fields().get("appointment_date"));
        assertThat(dto.start()).isEqualTo("23:30");
        assertThat(dto.end()).isEqualTo("00:29");
        assertThat(dto.notes()).isEqualTo("n".repeat(32500));
        assertThat(dto.source().nativeEnd()).isTrue();
        assertThat(dto.source().completeNotes()).isTrue();
        assertThat(dto.diagnostics())
                .extracting(CdsClinicalDiagnosticDto::code)
                .contains("CDS_APPOINTMENT_SOURCE_RECORD_APPLIED")
                .doesNotContain("CDS_APPOINTMENT_CROSS_MIDNIGHT_SOURCE_ONLY");
        when(preparation.load(
                        "run", 1, "unit", element.sha256(), command, CdsAppointmentNativeDto.class))
                .thenReturn(dto);
        doAnswer(
                        call -> {
                            ((io.github.carlos_emr.carlos.commn.model.Appointment)
                                            call.getArgument(0))
                                    .setId(9);
                            return null;
                        })
                .when(appointmentDao)
                .persist(any(io.github.carlos_emr.carlos.commn.model.Appointment.class));
        when(demographicDao.getDemographicById(42))
                .thenReturn(new io.github.carlos_emr.carlos.commn.model.Demographic());
        importer.write("run", 1, "unit", 42, element, command, notice -> {});
        var written =
                org.mockito.ArgumentCaptor.forClass(
                        io.github.carlos_emr.carlos.commn.model.Appointment.class);
        verify(appointmentDao).persist(written.capture());
        var row = written.getValue();
        assertThat(row.getStartTime()).isEqualTo(java.sql.Time.valueOf("23:30:00"));
        assertThat(row.getEndTime()).isEqualTo(java.sql.Time.valueOf("00:29:00"));
        assertThat(row.getNotes()).isEqualTo(stored.get("notes"));
        assertThat(row.getLocation()).isEqualTo("Main Clinic");
        assertThat(row.getType()).isEqualTo("Follow-up visit");
        assertThat(row.getResources()).isEqualTo("Room 2");
        assertThat(row.getUrgency()).isEqualTo("critical");
        assertThat(row.getStyle()).isEqualTo("1");
        assertThat(row.getBilling()).isEqualTo("B");
        assertThat(row.getBookingSource())
                .isEqualTo(io.github.carlos_emr.carlos.commn.model.Appointment.BookingSource.OSCAR);
    }

    private static void selectNewCategory(Fixture f) {
        var old = f.lease.document();
        var command =
                new CdsExportCommand(
                        "appointment-assembly",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.APPOINTMENTS,
                                CdsCategory.NEW_CATEGORY),
                        "recipient",
                        "audit");
        f.lease =
                new CdsExportLeaseDto(
                        f.lease.runId(),
                        f.lease.epoch(),
                        f.lease.token(),
                        new CdsExportJobDocumentDto(
                                old.protocol(),
                                old.actor(),
                                command,
                                old.compatibilityDigest(),
                                old.senderNamespace(),
                                old.recipientFingerprint(),
                                old.auditPlanDigest(),
                                old.storage()));
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
        // A capture that states more appointments than it delivers is refused for what is
        // missing, never for its size.
        var short101 = fixture(false);
        short101.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(
                        Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns()),
                        101,
                        HASH));
        assertThatThrownBy(() -> compose(short101, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_OCCURRENCE_SCOPE_INVALID");
    }

    /** The UI-booked fixture with {@code count} more native appointments after its own. */
    private Fixture chart(int count) throws Exception {
        var f = uiBooked();
        var parents = new ArrayList<>(f.parents);
        var receiptRows = new ArrayList<>(f.receiptRows);
        var template = f.parents.getFirst().fields();
        for (int id = 1001; id <= 1000 + count; id++) {
            var values = new TreeMap<>(template);
            values.put("appointment_no", Integer.toString(id));
            var row =
                    new CdsExportCapturedTextRowDto(
                            "appointment", CdsExportPatientTableSourceReader.key(id), values);
            var columns = new ArrayList<CdsNativeRowDigest.Column>();
            var cells = new ArrayList<CdsNativeRowState.Cell>();
            for (var entry : values.entrySet()) {
                columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
                String text = entry.getValue();
                cells.add(
                        new CdsNativeRowState.Cell(
                                text == null,
                                text == null
                                        ? 0
                                        : text.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                                                .length,
                                text == null
                                        ? null
                                        : java.util.HexFormat.of()
                                                .formatHex(
                                                        io.github.carlos_emr.carlos.utility
                                                                .CdsImportKeys.digest()
                                                                .digest(
                                                                        text.getBytes(
                                                                                java.nio.charset
                                                                                        .StandardCharsets
                                                                                        .UTF_8)))));
            }
            var state =
                    new CdsNativeRowState("appointment", Integer.toString(id), columns, cells);
            parents.add(row);
            receiptRows.add(
                    new CdsExportNativeRowReceiptDto(
                            "appointment",
                            Integer.toString(id),
                            row.recordKey(),
                            state.digest(),
                            state));
        }
        f.parents = parents;
        f.receiptRows = receiptRows;
        f.scopes.put(
                "appointment",
                new CdsExportCapturedTableDto(
                        Set.copyOf(CdsExportPatientTableReadSupport.Plan.APPOINTMENTS.columns()),
                        parents.size(),
                        HASH));
        return f;
    }

    @Test
    void shouldEmitTheSameBytesAndReceipts_whenAChartIsReadInSmallWindows() throws Exception {
        var whole = chart(10);
        byte[] expected = compose(whole, false);
        assertThat(whole.receipts).hasSize(11);
        for (int window : new int[] {1, 2, 4, 10}) {
            var windowed = chart(10);
            windowed.bounds = windowed.bounds.withWindow(window, 8L << 20);
            assertThat(compose(windowed, false)).isEqualTo(expected);
            assertThat(windowed.receipts).isEqualTo(whole.receipts);
            assertThat(windowed.result).isEqualTo(whole.result);
            verify(windowed.witnesses, times((11 + window - 1) / window))
                    .scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any());
        }
        var byBytes = chart(10);
        byBytes.bounds = byBytes.bounds.withWindow(8192, 30_000);
        assertThat(compose(byBytes, false)).isEqualTo(expected);
        assertThat(byBytes.receipts).isEqualTo(whole.receipts);
    }

    @Test
    void shouldRefuseAMissingOrUnwitnessedAppointment_whenTheChartIsReadByWindow()
            throws Exception {
        var unwitnessed = chart(6);
        unwitnessed.bounds = unwitnessed.bounds.withWindow(2, 8L << 20);
        unwitnessed.receiptRows = new ArrayList<>(unwitnessed.receiptRows.subList(0, 6));
        assertThatThrownBy(() -> compose(unwitnessed, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_OCCURRENCE_SCOPE_INVALID");
        var missing = chart(6);
        missing.bounds = missing.bounds.withWindow(2, 8L << 20);
        missing.parents = new ArrayList<>(missing.parents.subList(0, 6));
        assertThatThrownBy(() -> compose(missing, false))
                .hasMessage("CDS_EXPORT_APPOINTMENT_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldExportTheChart_pastTheFormerRowBound() throws Exception {
        // 8,193 appointments: one more than the former cds.export.maxNativeRows.
        var f = chart(8192);
        f.bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                        CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                        1_000_000,
                        64L << 20,
                        8192,
                        131_072);
        compose(f, false);
        assertThat(f.receipts).hasSize(8193);
        assertThat(f.result.nativeRows()).isEqualTo(8193);
        assertThat(f.receipts.getLast().nativeId()).isEqualTo("9192");
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
        List<CdsExportCapturedTextRowDto> archives = List.of();
        boolean changedGeneration,
                mergeEofFailure,
                parentEofFailure,
                valuesEofFailure,
                receiptFailure,
                revokeAtReceipt;
        CdsExportClinicalOccurrenceService.Result result;
        CdsExportClinicalOccurrenceService.Bounds bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        100000, 8L << 20, 1000, 8L << 20, 100, 1000);
    }

    /**
     * The values {@code AppointmentAddRecord2Action} stores for a booking made in the add form,
     * after {@code AppointmentUpdateRecord2Action} marked the patient here (status "Q1" stands
     * for that captured status definition). The add and edit forms post neither style nor
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

    /** The version the edit archived: the same booking before the patient was marked here. */
    private static CdsExportCapturedTextRowDto archive(
            int id, String appointment, Map<String, String> booking) {
        var values = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                .columns()
                .forEach(column -> values.put(column, booking.get(column)));
        values.put("id", Integer.toString(id));
        values.put("appointment_no", appointment);
        values.put("demographic_no", "27");
        values.put("status", "t");
        values.put("lastupdateuser", "999998");
        values.put("updatedatetime", null);
        return new CdsExportCapturedTextRowDto(
                "appointmentArchive", CdsExportPatientTableSourceReader.key(id), values);
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
        f.archives = List.of(archive(7, "51", booking));
        f.scopes.put(
                "appointmentArchive",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.APPOINTMENT_ARCHIVE
                                        .columns()),
                        1,
                        HASH));
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
                            } else if ("appointmentArchive".equals(call.getArgument(3))) {
                                for (var row : f.archives)
                                    ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                            .accept(row);
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
        var bounds = f.bounds;
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
