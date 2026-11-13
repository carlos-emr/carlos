/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * <p>This software is published under the GPL GNU General Public License. This program is free
 * software; you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * <p>You should have received a copy of the GNU General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * <p>CARLOS EMR Project https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exact projection/current edits and actual receiving parser; native-driver qualification is
 * separate.
 */
class CdsExportAppointmentFieldReconcilerUnitTest {
    private static final String HASH = "a".repeat(64),
            RUN = "123e4567-e89b-12d3-a456-426614174000",
            KEY = "f".repeat(64),
            ADDRESS = RUN + ":" + KEY,
            PAGE = "e".repeat(64),
            PAGE_ADDRESS = RUN + ":" + PAGE;
    @TempDir Path directory;
    private Map<String, String> old, current;
    private CdsAppointmentNativeDto prepared;
    private String originalPurpose = "Original purpose",
            originalNotes = "Original café 😀\r\nnotes";
    private List<CdsExportRetainedFieldDto> fields;
    private CdsExportRetainedIndexDto index;
    private CdsExportRetainedReceiptVerificationDto checked;
    private CdsExportNativeRowReceiptDto witness;
    private boolean baseline = true, foreignNamespace;
    private CdsExportCapturedTextRowDto demographic;
    private CdsExportAppointmentLookupsDto lookups;

    @BeforeEach
    void setup() {
        old = new TreeMap<>();
        CdsExportPatientTableReadSupport.Plan.APPOINTMENTS
                .columns()
                .forEach(name -> old.put(name, null));
        old.putAll(
                Map.of(
                        "appointment_no",
                        "51",
                        "demographic_no",
                        "27",
                        "appointment_date",
                        "2024-03-01",
                        "start_time",
                        "09:00:00",
                        "end_time",
                        "10:00:00",
                        "provider_no",
                        "0é27",
                        "name",
                        "Fixture,Synthetic",
                        "program_id",
                        "0",
                        "status",
                        ""));
        old.put("imported_status", "Original imported status");
        old.put("reason", originalPurpose);
        old.put("notes", originalNotes);
        old.put("creator", "ORIGINAL_ACTOR");
        old.put("lastupdateuser", "ORIGINAL_ACTOR");
        old.put("createdatetime", "2024-02-29 12:34:56");
        current = new TreeMap<>(old);
        prepared =
                new CdsAppointmentNativeDto(
                        "2024-03-01",
                        "09:00",
                        "10:00",
                        "Original imported status",
                        originalPurpose,
                        originalNotes,
                        "0é27",
                        true,
                        List.of());
        var values = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(name -> values.put(name, null));
        values.putAll(
                Map.of("demographic_no", "27", "first_name", "Synthetic", "last_name", "Fixture"));
        demographic = new CdsExportCapturedTextRowDto("demographic", "0000000027", values);
        lookups = CdsExportAppointmentMapperUnitTest.lookups();
    }

    @Test
    void shouldUseOnlyAnExactlyMatchedLegacyReleaseReceipt_withoutBackfillingItsBaseline()
            throws Exception {
        baseline = false;
        var result = reconcile();
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().notes()).isEqualTo(originalNotes);
        var unit = index.tables().get("cds_import_unit").get(CdsImportKeys.hash(RUN, KEY));
        assertThat(unit.fields().get("native_baseline")).isNull();
    }

    @Test
    void shouldRejectAnEditedLegacyRow_whenNoRecordedFieldBaselineCanProveThePriorValues() {
        baseline = false;
        current.put("notes", "An edit cannot be backfilled as an original value");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void shouldRejectAPopulatedLocationReference_whenDerivingAnOldUnchangedReceipt() {
        baseline = false;
        current.put("locationCode", "17");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void shouldRejectAnUnknownLegacyShape_insteadOfDroppingAnotherCapturedColumn() {
        baseline = false;
        old.remove("billing");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void shouldReconcileTheReleaseReceipt_withoutInventingADevelopLocationColumn()
            throws Exception {
        var result = reconcile();
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().notes()).isEqualTo(originalNotes);
        assertThat(result.preparedPaths()).contains("AppointmentNotes", "AppointmentPurpose");
        assertThat(result.mapping().fields().keySet())
                .noneMatch(key -> key.column().equals("locationCode"));
    }

    @Test
    void shouldPreserveCurrentEdits_withTheRecordedReleaseColumnSet() throws Exception {
        current.put("notes", "Edited after the schema upgrade");
        var result = reconcile();
        assertThat(result.mapping().value().notes()).isEqualTo("Edited after the schema upgrade");
        assertThat(result.mapping().value().purpose()).isEqualTo(originalPurpose);
    }

    @Test
    void shouldRequireReviewForAPopulatedAddedLocationReference_insteadOfIgnoringIt() {
        current.put("locationCode", "17");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void shouldRefuseAMissingBaselineColumn_insteadOfAssumingItIsStorageMetadata() {
        old.remove("billing");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_FIELD_CODEC_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyUnchangedImportedStatusPurposeNotesAndOriginalRecordedProviderRemainExact_forAppointmentFieldReconcilerContract()
            throws Exception {
        var result = reconcile();
        assertThat(result.mapping().nativeRowRepresentable()).isTrue();
        assertThat(result.mapping().value().status()).isEqualTo(prepared.status());
        assertThat(result.mapping().value().notes()).isEqualTo(originalNotes);
        assertThat(result.mapping().value().provider().firstName()).isEqualTo("Original");
        assertThat(result.mapping().value().provider().ohipNumber()).isEqualTo("654321");
        assertThat(result.preparedPaths())
                .contains(
                        "Provider", "AppointmentStatus", "AppointmentNotes", "AppointmentPurpose");
    }

    @Test
    void shouldVerifyEditedOrClearedNotesNeverReplayTheOriginalSourceValue_forAppointmentFieldReconcilerContract() throws Exception {
        for (String notes : Arrays.asList("Current café 😀\r\nnotes", null, "")) {
            current.put("notes", notes);
            var result = reconcile();
            assertThat(result.mapping().value().notes()).isEqualTo(notes);
            assertThat(result.currentPaths()).contains("AppointmentNotes");
        }
    }

    @Test
    void shouldVerifyANativePurposeEditPreservesTheOtherOriginalFields_forAppointmentFieldReconcilerContract() throws Exception {
        current.put("reason", "Current purpose");
        var result = reconcile();
        assertThat(result.mapping().value().purpose()).isEqualTo("Current purpose");
        assertThat(result.mapping().value().notes()).isEqualTo(originalNotes);
        assertThat(result.currentPaths()).containsExactly("AppointmentPurpose");
    }

    @Test
    void shouldVerifyAnExplicitImportedStatusClearIsNotReplaced_withOldPreparedStatus() throws Exception {
        current.put("imported_status", null);
        assertThat(reconcile().mapping().value().status()).isNull();
        current.put("imported_status", "");
        assertThat(reconcile().mapping().value().status()).isEmpty();
    }

    @Test
    void shouldVerifyCurrentNativeStatusDefinitionWinsOnly_whenItsSeparateOldImportedLabelIsUnchanged()
            throws Exception {
        current.put("status", "Q1");
        assertThat(reconcile().mapping().value().status()).isEqualTo("Exact booked Ω");
        current.put("imported_status", "Another independently edited status");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void shouldVerifyUnknownCurrentStatusNeverFallsBack_toAnOldImportedLabel() throws Exception {
        current.put("status", "UNRESOLVED");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void shouldVerifyChangedAndClearedProviderAssociationsDoNotResurrectTheOldSourceClinician_forAppointmentFieldReconcilerContract()
            throws Exception {
        current.put("provider_no", "");
        assertThat(reconcile().mapping().value().provider()).isNull();
        current.put("provider_no", "new");
        var provider = new TreeMap<>(CdsExportAppointmentMapperUnitTest.provider().fields());
        provider.put("provider_no", "new");
        provider.put("first_name", "Current");
        var rows = new HashMap<>(lookups.providers());
        rows.put(
                "new",
                new CdsExportCapturedTextRowDto(
                        "provider", CdsExportPublicProviderSourceReader.key("new"), provider));
        lookups = new CdsExportAppointmentLookupsDto(HASH, rows, lookups.statuses(), lookups.reasonCodes(), lookups.earlierPatientDisplays());
        assertThat(reconcile().mapping().value().provider().firstName()).isEqualTo("Current");
    }

    @Test
    void shouldVerifyTheLongPurposeSourceViewMarkerIsReplaced_withItsVerifiedCompleteSourceValue()
            throws Exception {
        originalPurpose = "P".repeat(250);
        String marker = "CDS: complete appointment purpose is available in CDS source fields.";
        old.put("reason", marker);
        current.put("reason", marker);
        prepared =
                new CdsAppointmentNativeDto(
                        prepared.date(),
                        prepared.start(),
                        prepared.end(),
                        prepared.status(),
                        marker,
                        prepared.notes(),
                        prepared.providerNo(),
                        true,
                        List.of(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_APPOINTMENT_PURPOSE_SOURCE_VIEW",
                                        "AppointmentPurpose")));
        assertThat(reconcile().mapping().value().purpose()).isEqualTo(originalPurpose);
    }

    @Test
    void shouldVerifyAWrongPreparationProjectionCannotClaimUnchangedNativeValues_forAppointmentFieldReconcilerContract() {
        old.put("notes", "Different baseline notes");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_PREPARED_PROJECTION_NOT_PROVEN");
    }

    @Test
    void shouldVerifyMissingHistoricalFieldBaselineAllowsOnlyActuallyUnchangedQualifiedLegacyRows_forAppointmentFieldReconcilerContract()
            throws Exception {
        baseline = false;
        assertThat(reconcile().mapping().value()).isNotNull();
        current.put("notes", "Edited");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_FIELD_BASELINE_REQUIRED");
    }

    @Test
    void shouldVerifyCurrentRowsCannotBorrowWitnesses_fromBeforeTheirMutation() throws Exception {
        build();
        current.put("notes", "Later mutation");
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_APPOINTMENT_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void shouldVerifyPatientNameMismatchProgramAffiliationAndUnmappedNativeValuesNeedReview_forAppointmentFieldReconcilerContract() throws Exception {
        current.put("name", "Another patient");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_PATIENT_DISPLAY_REQUIRES_REVIEW");
        current.put("name", old.get("name"));
        current.put("program_id", "1");
        assertThat(reconcile().mapping().value()).isNull();
        current.put("program_id", "0");
        // The booking site (location) is now operational scheduler context; remarks, which only
        // the SOAP schedule service and legacy data fill, is still an unmapped value.
        current.put("location", "Additional appointment location");
        assertThat(reconcile().mapping().value()).isNotNull();
        current.put("remarks", "Additional appointment remark");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void shouldRequireReview_whenAReasonLabelIsChosenForAnUneditedImportedPurpose()
            throws Exception {
        // Imports never set reasonCode. Editing the booking in CARLOS submits the reason list's
        // selected item; its label would show before the preserved imported purpose.
        current.put("reasonCode", "2");
        lookups = withReasonItem(CdsExportAppointmentMapperUnitTest.reasonItem("2", "Follow-Up"));
        assertThatThrownBy(this::reconcile)
                .hasMessage(
                        "CDS_EXPORT_APPOINTMENT_REASON_LABEL_ON_PRESERVED_PURPOSE_REQUIRES_REVIEW");
        // The list's blank first item, the form default, adds nothing to the display.
        lookups = withReasonItem(CdsExportAppointmentMapperUnitTest.reasonItem("2", ""));
        assertThat(reconcile().mapping().value().purpose()).isEqualTo(originalPurpose);
    }

    private CdsExportAppointmentLookupsDto withReasonItem(CdsExportCapturedTextRowDto item) {
        return new CdsExportAppointmentLookupsDto(
                HASH,
                lookups.providers(),
                lookups.statuses(),
                Map.of(item.fields().get("id"), List.of(item)),
                Set.of());
    }

    @Test
    void shouldVerifyChangedSourceFieldPagesCannotBeReplaced_withOtherwiseCorrectNativeWitnesses()
            throws Exception {
        build();
        var unchanged = new HashSet<>(checked.unchangedUnits());
        unchanged.remove(PAGE_ADDRESS);
        var review = new HashSet<>(checked.reviewUnits());
        review.add(PAGE_ADDRESS);
        var units = new TreeMap<>(checked.units());
        var prior = units.get(PAGE_ADDRESS).getFirst();
        var changed = new ArrayList<>(units.get(PAGE_ADDRESS));
        changed.set(0, new CheckedReference(prior.expected(), "d".repeat(64), Reason.CHANGED_ROW));
        units.put(PAGE_ADDRESS, changed);
        checked = new CdsExportRetainedReceiptVerificationDto(HASH, units, unchanged, review);
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_APPOINTMENT_SOURCE_FIELD_EVIDENCE_CHANGED");
    }

    @Test
    void shouldVerifyASecondAppliedUnitCannotClaimTheSameAppointment_withoutAliasReview() throws Exception {
        build();
        String alias = RUN + ":" + "d".repeat(64);
        var refs = new TreeMap<>(index.references());
        refs.put(alias, refs.get(ADDRESS));
        var applied = new HashSet<>(index.appliedUnits());
        applied.add(alias);
        index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        index.metadataRows(),
                        index.metadataBytes(),
                        index.tables(),
                        applied,
                        refs,
                        index.nativeParents());
        var checks = new TreeMap<>(checked.units());
        checks.put(alias, checks.get(ADDRESS));
        var unchanged = new HashSet<>(checked.unchangedUnits());
        unchanged.add(alias);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, checks, unchanged, checked.reviewUnits());
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_APPOINTMENT_ALIAS_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyReceivingImporterSeesCurrentNotesOriginalProviderAndPostOptionDateExactlyOnce_forAppointmentFieldReconcilerContract()
            throws Exception {
        current.put("notes", "Current receiving note");
        var result = reconcile();
        var plan =
                new CdsExportAppointmentRetainedAssembler()
                        .prepare(27, result, index, checked, fields, values(), () -> {});
        var records = parse(plan.content());
        var parser = new CdsFieldParser();
        assertThat(parser.firstText(records.get(1), "AppointmentNotes", 32000))
                .isEqualTo("Current receiving note");
        assertThat(parser.firstText(records.get(1), "AppointmentDate/FullDate", 10))
                .isEqualTo("2024-03-01");
        assertThat(parser.firstText(records.get(1), "Provider/Name/FirstName", 50))
                .isEqualTo("Original");
        assertThat(parser.firstText(records.get(1), "AppointmentTime", 64))
                .isEqualTo("09:00:00-05:00");
        var accounted = new HashSet<>(plan.retainedFields());
        accounted.addAll(plan.replacedFields());
        assertThat(accounted)
                .isEqualTo(
                        fields.stream()
                                .map(CdsExportRetainedFieldDto::id)
                                .collect(java.util.stream.Collectors.toSet()));
        assertThat(Collections.disjoint(plan.retainedFields(), plan.replacedFields())).isTrue();
    }

    @Test
    void shouldVerifyClearedNativeNotesAreAbsent_fromTheReassembledReceivingRecord() throws Exception {
        current.put("notes", null);
        var result = reconcile();
        var content =
                new CdsExportAppointmentRetainedAssembler()
                        .prepare(27, result, index, checked, fields, values(), () -> {})
                        .content();
        assertThat(new CdsFieldParser().firstText(parse(content).get(1), "AppointmentNotes", 32000))
                .isNull();
    }

    @Test
    void shouldVerifyCurrentDateAndClockEditsReplaceOldSourceTiming_withoutAnotherImportShift()
            throws Exception {
        current.put("appointment_date", "2025-04-02");
        current.put("start_time", "11:15:30");
        current.put("end_time", "12:15:30");
        var result = reconcile();
        assertThat(result.mapping().value().timing().date().lexical()).isEqualTo("2025-04-02");
        assertThat(result.mapping().value().timing().time()).isEqualTo("11:15:30");
        var content =
                new CdsExportAppointmentRetainedAssembler()
                        .prepare(27, result, index, checked, fields, values(), () -> {})
                        .content();
        var record = parse(content).get(1);
        var parser = new CdsFieldParser();
        assertThat(parser.firstText(record, "AppointmentDate/FullDate", 10))
                .isEqualTo("2025-04-02");
        assertThat(parser.firstText(record, "AppointmentTime", 64)).isEqualTo("11:15:30-04:00");
    }

    @Test
    void shouldVerifyOvernightAndAmbiguousLocalTimingAreNotReconstructed_fromOldFields() throws Exception {
        current.put("start_time", "23:30:00");
        current.put("end_time", "00:30:00");
        assertThat(reconcile().mapping().value()).isNull();
        current.put("appointment_date", "2024-11-03");
        current.put("start_time", "01:15:00");
        current.put("end_time", "01:45:00");
        assertThat(reconcile().mapping().value()).isNull();
    }

    @Test
    void shouldVerifyRetainedPurposeAndNotesMustMatchTheClaimedPreparationRatherThanBeDiscarded_forAppointmentFieldReconcilerContract()
            throws Exception {
        originalPurpose = "Another untouched original purpose";
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_ORIGINAL_PURPOSE_REQUIRED");
        originalPurpose = "Original purpose";
        originalNotes = "Another untouched original note";
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_ORIGINAL_NOTES_REQUIRED");
    }

    @Test
    void shouldVerifyRetainedValuesOverTheDictionaryBoundRequireReview_insteadOfTruncation() {
        originalNotes = "x".repeat(32001);
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_ORIGINAL_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifySourceOnlyPreparationCannotBeRelabelledAsAnAppliedCalendarProjection_forAppointmentFieldReconcilerContract() {
        prepared =
                new CdsAppointmentNativeDto(
                        prepared.date(),
                        prepared.start(),
                        prepared.end(),
                        prepared.status(),
                        prepared.purpose(),
                        prepared.notes(),
                        prepared.providerNo(),
                        false,
                        List.of());
        assertThatThrownBy(this::reconcile).hasMessage("CDS_EXPORT_RETAINED_PREPARATION_INVALID");
    }

    @Test
    void shouldVerifySubstitutedOriginalDescriptorsCannotBorrowUnchangedFieldPageReceipts_forAppointmentFieldReconcilerContract() throws Exception {
        build();
        var first = fields.getFirst();
        var changed = new ArrayList<>(fields);
        changed.set(
                0,
                new CdsExportRetainedFieldDto(
                        first.id(),
                        first.runId(),
                        first.sourcePatientKey(),
                        first.fieldUnitKey(),
                        first.category(),
                        first.sourcePath(),
                        first.occurrence(),
                        first.namespace(),
                        first.valueType(),
                        first.precision(),
                        first.originalInline(),
                        "Substituted source text",
                        first.originalPayload()));
        fields = changed;
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_APPOINTMENT_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyAnUnrelatedNamespaceCannotBorrowAStandardProviderPath_forAppointmentFieldReconcilerContract() {
        foreignNamespace = true;
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_APPOINTMENT_ORIGINAL_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyActualReceivingAppointmentPreparationPreservesTheInstant_inAnotherClinicalZone()
            throws Exception {
        current.put("notes", "Current cross-zone note");
        var decision = reconcile();
        var content =
                new CdsExportAppointmentRetainedAssembler()
                        .prepare(27, decision, index, checked, fields, values(), () -> {})
                        .content();
        var record = parse(content).get(1);
        var configuration = org.mockito.Mockito.mock(CdsImportConfigurationService.class);
        org.mockito.Mockito.when(configuration.value("cds.import.clinicalZone", ""))
                .thenReturn("America/Vancouver");
        var providers = org.mockito.Mockito.mock(CdsProviderResolver.class);
        org.mockito.Mockito.when(
                        providers.resolve(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                "028", CdsProviderMatchDto.Outcome.MATCHED_IDENTIFIERS));
        var preparation = org.mockito.Mockito.mock(CdsNativePreparationService.class);
        var parser = new CdsFieldParser();
        var service =
                new CdsAppointmentPersister(
                        parser,
                        new CdsClinicalNormalizationService(parser),
                        configuration,
                        providers,
                        preparation,
                        org.mockito.Mockito.mock(
                                io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao.class),
                        org.mockito.Mockito.mock(
                                io.github.carlos_emr.carlos.commn.dao.DemographicDao.class));
        var command =
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand
                                .class);
        service.prepare(RUN, 1, KEY, record, command);
        var captured = org.mockito.ArgumentCaptor.forClass(CdsAppointmentNativeDto.class);
        org.mockito.Mockito.verify(preparation)
                .store(
                        org.mockito.ArgumentMatchers.eq(RUN),
                        org.mockito.ArgumentMatchers.eq(1L),
                        org.mockito.ArgumentMatchers.eq(KEY),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq(command),
                        captured.capture());
        assertThat(captured.getValue().representable()).isTrue();
        assertThat(captured.getValue().date()).isEqualTo("2024-03-01");
        assertThat(captured.getValue().start()).isEqualTo("06:00");
        assertThat(captured.getValue().end()).isEqualTo("07:00");
        assertThat(captured.getValue().notes()).isEqualTo("Current cross-zone note");
    }

    record Fixture(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportCapturedTextRowDto row,
            CdsExportNativeRowReceiptDto witness,
            List<CdsExportRetainedFieldDto> fields,
            CdsExportCapturedTextRowDto demographic,
            CdsExportAppointmentLookupsDto lookups) {}

    Fixture fixture(boolean imported, String notes) throws Exception {
        setup();
        current.put("notes", notes);
        if (!imported) {
            current.put("imported_status", null);
            old.put("imported_status", null);
        }
        build();
        return new Fixture(
                index,
                checked,
                row(current),
                witness,
                fields,
                demographic,
                new CdsExportAppointmentLookupsDto(HASH, lookups.providers(), lookups.statuses(), lookups.reasonCodes(), lookups.earlierPatientDisplays()));
    }

    /** A native (never imported) appointment whose current and witnessed values are {@code values}. */
    Fixture nativeFixture(Map<String, String> values) throws Exception {
        setup();
        current.put("imported_status", null);
        old.put("imported_status", null);
        current.putAll(values);
        old.putAll(values);
        build();
        return new Fixture(
                index,
                checked,
                row(current),
                witness,
                fields,
                demographic,
                new CdsExportAppointmentLookupsDto(
                        HASH,
                        lookups.providers(),
                        lookups.statuses(),
                        lookups.reasonCodes(),
                        lookups.earlierPatientDisplays()));
    }

    private CdsExportAppointmentFieldReconciler.Result reconcile() throws Exception {
        build();
        return invoke();
    }

    private CdsExportAppointmentFieldReconciler.Result invoke() throws Exception {
        return new CdsExportAppointmentFieldReconciler()
                .reconcile(
                        27,
                        index,
                        ADDRESS,
                        checked,
                        row(current),
                        witness,
                        new CdsExportAppointmentLookupsDto(
                                HASH, lookups.providers(), lookups.statuses(), lookups.reasonCodes(), lookups.earlierPatientDisplays()),
                        demographic,
                        ZoneId.of("America/Toronto"),
                        fields,
                        values(),
                        () -> {});
    }

    private void build() throws Exception {
        var previous = state(old);
        var now = state(current);
        witness =
                new CdsExportNativeRowReceiptDto(
                        "appointment", "51", row(current).recordKey(), now.digest(), now);
        var ref = new CdsExportRetainedIndexDto.Reference("appointment", "51", previous.digest());
        var json = new ObjectMapper();
        String refs = json.writeValueAsString(Map.of("protocol", 2, "refs", List.of(ref))),
                target = hash(refs),
                payload = json.writeValueAsString(prepared),
                id = CdsImportKeys.hash(RUN, KEY),
                source = "c".repeat(64),
                options = "d".repeat(64),
                ruleset = "b".repeat(64);
        var unit =
                new TreeMap<String, String>(
                        Map.of(
                                "id",
                                id,
                                "run_id",
                                RUN,
                                "unit_key",
                                KEY,
                                "category",
                                "Appointments",
                                "state",
                                "COMMITTED",
                                "source_digest",
                                source,
                                "target_references",
                                refs,
                                "target_digest",
                                target));
        unit.put(
                "native_baseline",
                baseline ? CdsNativeBaselineCodec.encode(refs, target, List.of(previous)) : null);
        var preparation =
                Map.of(
                        "id",
                        id,
                        "run_id",
                        RUN,
                        "unit_key",
                        KEY,
                        "source_digest",
                        source,
                        "options_digest",
                        options,
                        "ruleset_digest",
                        ruleset,
                        "payload_type",
                        CdsAppointmentNativeDto.class.getName(),
                        "payload",
                        payload,
                        "digest",
                        CdsPreparationSeal.digest(
                                CdsAppointmentNativeDto.class.getName(),
                                source,
                                options,
                                ruleset,
                                payload));
        fields =
                List.of(
                        field("AppointmentTime[1]", "cds", "09:00:00-05:00"),
                        field("Duration[1]", "cds", "60"),
                        field("AppointmentStatus[1]", "cds", prepared.status()),
                        field("AppointmentDate[1]/FullDate[1]", "cds_dt", "2024-02-29"),
                        field(
                                "Provider[1]/Name[1]/FirstName[1]",
                                foreignNamespace ? "urn:owned:unmapped" : "cds_dt",
                                "Original"),
                        field("Provider[1]/Name[1]/LastName[1]", "cds_dt", "Physician"),
                        field("Provider[1]/OHIPPhysicianId[1]", "cds", "654321"),
                        field("AppointmentPurpose[1]", "cds", originalPurpose),
                        field("AppointmentNotes[1]", "cds", originalNotes));
        var metadata = new TreeMap<String, CdsExportCapturedTextRowDto>();
        fields.forEach(field -> metadata.put(field.id(), metadata(field)));
        var sourceRefs =
                fields.stream()
                        .map(
                                field ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                "cds_import_field", field.id(), "e".repeat(64)))
                        .toList();
        var tables = new TreeMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        tables.put(
                "cds_import_unit",
                Map.of(id, new CdsExportCapturedTextRowDto("cds_import_unit", id, unit)));
        tables.put(
                "cds_import_preparation",
                Map.of(
                        id,
                        new CdsExportCapturedTextRowDto(
                                "cds_import_preparation", id, preparation)));
        tables.put(
                "cds_import_run",
                Map.of(
                        RUN,
                        new CdsExportCapturedTextRowDto(
                                "cds_import_run",
                                RUN,
                                Map.of(
                                        "id",
                                        RUN,
                                        "options_digest",
                                        options,
                                        "ruleset_digest",
                                        ruleset))));
        tables.put("cds_import_field", metadata);
        index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        3 + fields.size(),
                        32768,
                        tables,
                        Set.of(ADDRESS, PAGE_ADDRESS),
                        Map.of(ADDRESS, List.of(ref), PAGE_ADDRESS, sourceRefs),
                        fields.stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                CdsExportRetainedFieldDto::id, field -> ADDRESS)));
        boolean same = previous.digest().equals(now.digest());
        var comparisons =
                Map.of(
                        ADDRESS,
                        List.of(
                                new CheckedReference(
                                        ref,
                                        now.digest(),
                                        same
                                                ? Reason.UNCHANGED_ROW
                                                : CdsNativeRowComparison.compare(previous, now)
                                                                .schemaChanged()
                                                        ? Reason.RECEIPT_FIELD_CODEC_UNQUALIFIED
                                                        : Reason.CHANGED_ROW,
                                        CdsNativeRowComparison.compare(previous, now))),
                        PAGE_ADDRESS,
                        sourceRefs.stream()
                                .map(
                                        value ->
                                                new CheckedReference(
                                                        value,
                                                        value.digest(),
                                                        Reason.UNCHANGED_ROW))
                                .toList());
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH,
                        comparisons,
                        same ? Set.of(ADDRESS, PAGE_ADDRESS) : Set.of(PAGE_ADDRESS),
                        same ? Set.of() : Set.of(ADDRESS));
    }

    private static CdsExportRetainedFieldDto field(
            String relative, String namespace, String value) {
        var type = CdsWireSchema.schemaModel().element("cds", "Appointments");
        for (String segment : relative.split("/")) {
            String name = segment.substring(0, segment.indexOf('['));
            var child =
                    type.childNames().stream()
                            .filter(candidate -> candidate.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            type = type.child(child.namespace(), child.local());
        }
        String path = "/OmdCds[1]/PatientRecord[2]/Appointments[3]/" + relative;
        return new CdsExportRetainedFieldDto(
                CdsImportKeys.hash(PAGE, namespace, path, "1"),
                RUN,
                "c".repeat(64),
                PAGE,
                CdsCategory.APPOINTMENTS,
                path,
                1,
                namespace,
                "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                null,
                value,
                value,
                null);
    }

    private static CdsExportCapturedTextRowDto metadata(CdsExportRetainedFieldDto field) {
        var values = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS
                .columns()
                .forEach(name -> values.put(name, null));
        values.putAll(
                Map.of(
                        "id",
                        field.id(),
                        "run_id",
                        field.runId(),
                        "patient_key",
                        field.sourcePatientKey(),
                        "unit_key",
                        field.fieldUnitKey(),
                        "demographic_no",
                        "27",
                        "category",
                        "Appointments",
                        "source_path",
                        field.sourcePath(),
                        "occurrence",
                        "1",
                        "source_namespace",
                        field.namespace(),
                        "value_type",
                        field.valueType()));
        values.put("original_value", field.originalInline());
        values.put("normalized_value", field.logicalInline());
        values.put("payload_offset", "0");
        values.put("payload_length", "0");
        return new CdsExportCapturedTextRowDto("cds_import_field", field.id(), values);
    }

    private static CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError();
            }
        };
    }

    private static CdsExportCapturedTextRowDto row(Map<String, String> values) {
        return new CdsExportCapturedTextRowDto(
                "appointment", CdsExportPatientTableSourceReader.key(51), values);
    }

    private static CdsNativeRowState state(Map<String, String> values) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : new TreeMap<>(values).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            String text = entry.getValue();
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length,
                            text == null ? null : hash(text)));
        }
        return new CdsNativeRowState("appointment", "51", columns, cells);
    }

    private static String hash(String text) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private List<CdsRecordDto> parse(CdsExportXmlComposer.Content appointment) throws Exception {
        var nativePatient = new TreeMap<>(demographic.fields());
        nativePatient.remove("demographic_no");
        nativePatient.putAll(
                Map.of(
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
        var patient =
                new CdsExportDemographicMapper()
                        .map(new CdsExportDemographicRowDto(27, nativePatient));
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.APPOINTMENTS);
        var parts =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        HASH, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                                xml -> new CdsExportDemographicXmlWriter().write(patient, xml)),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        HASH, CdsCategory.APPOINTMENTS, "c".repeat(64)),
                                appointment));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        HASH,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        parts.iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var input =
                Files.write(directory.resolve(UUID.randomUUID() + ".xml"), output.toByteArray());
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
}
