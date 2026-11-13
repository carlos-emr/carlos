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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Orchestration failures must never become successful XML even when a caller catches them. */
class CdsExportImmunizationOccurrenceServiceUnitTest {
    private static final String SOURCE = "a".repeat(64), PATIENT = "b".repeat(64);
    @TempDir Path directory;

    @Test
    void shouldVerifyNativeVaccineUsesSharedServiceAndReceivingParser_withCompleteFieldReceipts()
            throws Exception {
        var f = fixture(false, null);
        var records = parse(compose(f, false));
        var value =
                new CdsClinicalNormalizationService(new CdsFieldParser())
                        .immunization(records.get(1));
        assertThat(value.type()).isEqualTo("Td");
        assertThat(value.date().original()).isEqualTo("2024-02-29");
        assertThat(f.result.nativeOnlyRows()).isEqualTo(1);
        assertThat(f.result.importedRows()).isZero();
        assertThat(f.receipts).hasSize(1);
        assertThat(f.receipts.getFirst().fields()).hasSize(16);
        verify(f.captures).authorizeSources(f.lease, f.patient, f.tables, f.control);
        verify(f.closure)
                .verifyEmbedded(
                        f.lease, f.patient, f.tables, f.source, 100000, 8L << 20, f.control);
    }

    @Test
    void shouldAccountForADeletedVaccineVersion_withoutExportingItOrHoldingThePatient()
            throws Exception {
        var f = fixture(false, null);
        var current = f.parents.getFirst();
        var fields = new TreeMap<>(current.fields());
        fields.put("id", "16");
        fields.put("deleted", "1");
        var superseded = row("preventions", fields);
        var child = new TreeMap<String, String>();
        child.put("id", "40");
        child.put("prevention_id", "16");
        child.put("keyval", "comments");
        child.put("val", "Entered on the wrong chart");
        var extension = row("preventionsExt", child);
        f.parents = List.of(superseded, current);
        f.auxiliary = List.of(extension);
        f.witnesses = List.of(witness(superseded), witness(extension), witness(current));
        f.selection = Set.of(16, 17);
        f.scopes.put("preventions", table("preventions", 2));
        f.scopes.put("preventionsExt", table("preventionsExt", 1));
        var records = parse(compose(f, false));
        assertThat(records.stream().filter(r -> r.section().equals("Immunizations"))).hasSize(1);
        assertThat(f.receipts).singleElement().satisfies(r -> assertThat(r.nativeId()).isEqualTo("17"));
        assertThat(f.result.nativeRows()).isEqualTo(1);
        verify(f.captures).authorizeSources(f.lease, f.patient, f.tables, f.control);
    }

    /** The fixture's native vaccine, restricted to program 57. */
    private static void restrict(Fixture f) {
        var fields = new TreeMap<>(f.parents.getFirst().fields());
        fields.put("restrictToProgram", "1");
        fields.put("programNo", "57");
        f.parents = List.of(row("preventions", fields));
        f.witnesses = List.of(witness(f.parents.getFirst()));
    }

    // OPEN-6: a vaccine restricted to a program is written after a live program check, carried
    // with its flag and program in the residual, and the program is checked once more after the
    // patient's vaccines are written.
    @Test
    void shouldExportARestrictedVaccine_afterLiveProgramChecksBeforeAndAfterWriting()
            throws Exception {
        var f = fixture(false, null);
        restrict(f);
        when(f.control.restrictedProgramReadable(27, CdsCategory.IMMUNIZATIONS, "57"))
                .thenReturn(true);
        var records = parse(compose(f, false));
        assertThat(records.stream().filter(r -> r.section().equals("Immunizations"))).hasSize(1);
        assertThat(f.receipts.getFirst().fields().entrySet())
                .filteredOn(entry -> entry.getKey().column().equals("restrictToProgram"))
                .singleElement()
                .satisfies(
                        entry ->
                                assertThat(entry.getValue().rule())
                                        .isEqualTo("PROGRAM_SCOPE_AUTHORIZED"));
        verify(f.control, times(2)).restrictedProgramReadable(27, CdsCategory.IMMUNIZATIONS, "57");
    }

    @Test
    void shouldHoldARestrictedVaccine_whenNobodyExportingWorksInItsProgram() throws Exception {
        var f = fixture(false, null);
        restrict(f);
        assertThatThrownBy(() -> compose(f, false))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportReviewException.class)
                .hasMessage("CDS_EXPORT_PREVENTION_PROGRAM_SCOPE");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldRefuseThePatient_whenAGrantedProgramIsRevokedWhileItsVaccinesAreWritten()
            throws Exception {
        var f = fixture(false, null);
        restrict(f);
        when(f.control.restrictedProgramReadable(27, CdsCategory.IMMUNIZATIONS, "57"))
                .thenReturn(true, false);
        assertThatThrownBy(() -> compose(f, false))
                .isInstanceOf(SecurityException.class)
                .hasMessage("CDS_EXPORT_ACCESS_CHANGED");
        verify(f.captures, never()).authorizeSources(any(), any(), any(), any());
    }

    @Test
    void shouldStillHoldAnUnknownDeletedFlag_forReview() throws Exception {
        var f = fixture(false, null);
        var fields = new TreeMap<>(f.parents.getFirst().fields());
        fields.put("deleted", "2");
        f.parents = List.of(row("preventions", fields));
        f.witnesses = List.of(witness(f.parents.getFirst()));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void
            shouldVerifyImportedEditedNotesKeepSourceCodeDateAndVendorFieldsAndAccount_forEveryRetainedField()
                    throws Exception {
        var f = fixture(true, "Current café 😀\r\nnotes");
        var records = parse(compose(f, false));
        var value =
                new CdsClinicalNormalizationService(new CdsFieldParser())
                        .immunization(records.get(1));
        assertThat(value.notes()).isEqualTo("Current café 😀\r\nnotes");
        assertThat(value.code()).isEqualTo("00123456");
        assertThat(value.date().original()).isEqualTo("2024-02-29");
        assertThat(
                        new CdsFieldParser()
                                .firstToken(
                                        records.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
        assertThat(f.result.importedRows()).isEqualTo(1);
        assertThat(f.result.retainedSourceFields()).isEqualTo(f.retained.size());
        assertThat(f.receipts.getFirst().importedUnit()).isNotNull();
    }

    // Review finding F10: the receipt-proven OtherA row reaches this service as an immunization
    // and is written from its retained original elements, with every source field accounted.
    @Test
    void shouldReexportAnImportedCatchAllVaccine_withoutAHoldAndThroughTheReceivingParser()
            throws Exception {
        var f = catchAllFixture(null, "Current note");
        var records = parse(compose(f, false));
        var value =
                new CdsClinicalNormalizationService(new CdsFieldParser())
                        .immunization(records.get(1));
        assertThat(value.type()).isNull();
        assertThat(value.name()).isEqualTo("Original café vaccine");
        assertThat(value.notes()).isEqualTo("Current note");
        assertThat(value.code()).isEqualTo("00123456");
        assertThat(f.result.importedRows()).isEqualTo(1);
        assertThat(f.result.retainedSourceFields()).isEqualTo(f.retained.size());
        assertThat(f.receipts).singleElement().satisfies(r -> assertThat(r.importedUnit()).isNotNull());
        var typed = catchAllFixture("Chol-O", "Current note");
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .immunization(parse(compose(typed, false)).get(1))
                                .type())
                .isEqualTo("Chol-O");
    }

    @Test
    void shouldVerifyImportedClearNeverReplaysOldNotes_forImmunizationOccurrenceServiceContract()
            throws Exception {
        var f = fixture(true, null);
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .immunization(parse(compose(f, false)).get(1))
                                .notes())
                .isNull();
    }

    @Test
    void shouldVerifyPartialImportedDateUsesPhysicalPrecisionWitness_withoutReplayingItsAnchor()
            throws Exception {
        var f = fixture(true, "Current note", "2024");
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .immunization(parse(compose(f, false)).get(1))
                                .date()
                                .original())
                .isEqualTo("2024");
        assertThat(f.receipts.getFirst().fields().keySet())
                .anyMatch(key -> key.table().equals("prevention_precision"));
    }

    @Test
    void shouldVerifyUnknownNativeClinicalFieldsRequireReview_insteadOfAnEmptyOccurrence()
            throws Exception {
        var f = fixture(false, null);
        var parent = new TreeMap<>(f.parents.getFirst().fields());
        // A free-text administering clinician is carried in the residual (review F5); an
        // unknown status code still refuses the occurrence.
        parent.put("refused", "7");
        f.parents = List.of(row("preventions", parent));
        f.witnesses = List.of(witness(f.parents.getFirst()));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(f.receipts).isEmpty();
        var carried = fixture(false, null);
        var named = new TreeMap<>(carried.parents.getFirst().fields());
        named.put("provider_name", "Administering clinician");
        carried.parents = List.of(row("preventions", named));
        carried.witnesses = List.of(witness(carried.parents.getFirst()));
        assertThat(new String(compose(carried, false), StandardCharsets.UTF_8))
                .contains("CARLOS.Prevention.Source.v1", "Administering\\u0020clinician");
    }

    @Test
    void
            shouldVerifyMissingAndDuplicateParentCallbacksCannotProduceSuccess_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var missing = fixture(false, null);
        missing.parents = List.of();
        refused(missing);
        var duplicate = fixture(false, null);
        duplicate.parents = List.of(duplicate.parents.getFirst(), duplicate.parents.getFirst());
        refused(duplicate);
    }

    @Test
    void
            shouldVerifyMissingOrSubstitutedWitnessesCannotBorrowEarlierNativeValues_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var missing = fixture(false, null);
        missing.witnesses = List.of();
        refused(missing);
        var changed = fixture(false, null);
        var fields = new TreeMap<>(changed.parents.getFirst().fields());
        fields.put("refused", "1");
        changed.parents = List.of(row("preventions", fields));
        assertThatThrownBy(() -> compose(changed, false))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
        assertThat(changed.receipts).isEmpty();
    }

    @Test
    void
            shouldVerifyOrphanedExtensionsAndWrongPrecisionOwnerCannotBeIgnored_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        for (String table : List.of("preventionsExt", "prevention_precision")) {
            var f = fixture(false, null);
            var fields =
                    table.equals("preventionsExt")
                            ? Map.of(
                                    "id",
                                    "90",
                                    "prevention_id",
                                    "99",
                                    "keyval",
                                    "comments",
                                    "val",
                                    "Hidden")
                            : Map.of(
                                    "id",
                                    "91",
                                    "table_id",
                                    "99",
                                    "table_name",
                                    "4",
                                    "field_name",
                                    "7",
                                    "format",
                                    "YYYY");
            f.auxiliary = List.of(row(table, fields));
            refused(f);
        }
    }

    @Test
    void
            shouldVerifyMissingAuxiliaryCallbackCannotBeTreatedAsAnAbsentOriginalField_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var f = fixture(true, "Current note");
        f.auxiliary = List.of();
        refused(f);
    }

    @Test
    void
            shouldVerifyChangedGenerationAndCatalogueCannotSupplyDifferentMeaning_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var changed = fixture(false, null);
        changed.lookupGeneration = true;
        refused(changed);
        var foreign = fixture(false, null);
        when(foreign.configurations.readEmbedded(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(definitions("c".repeat(64)));
        refused(foreign);
    }

    @Test
    void
            shouldVerifyFinalCallbackRevocationOrSourceFailureCannotBeSwallowedIntoValidXml_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var denied = fixture(false, null);
        denied.revokeAtReceipt = true;
        assertThatThrownBy(() -> compose(denied, true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(denied.receipts).hasSize(1);
        var eof = fixture(false, null);
        eof.parentEofFailure = true;
        assertThatThrownBy(() -> compose(eof, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(eof.result).isNull();
    }

    @Test
    void
            shouldVerifyReceiptSinkAndRetainedScopeFailuresKeepTheWholeDocumentFailed_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var sink = fixture(false, null);
        sink.receiptFailure = true;
        assertThatThrownBy(() -> compose(sink, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        var values = fixture(false, null);
        values.valuesEofFailure = true;
        assertThatThrownBy(() -> compose(values, true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }

    @Test
    void shouldVerifyMissingImportedParentRequiresReview_insteadOfReplayingRetainedData()
            throws Exception {
        var f = fixture(true, "Current note");
        f.parents = List.of();
        f.auxiliary = List.of();
        f.witnesses = List.of();
        f.selection = Set.of();
        f.scopes.put("preventions", table("preventions", 0));
        f.scopes.put("preventionsExt", table("preventionsExt", 0));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldVerifyActiveMergeBlocks_beforeClinicalAndRetainedReads() throws Exception {
        var f = fixture(false, null);
        var fields = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES
                .columns()
                .forEach(key -> fields.put(key, null));
        fields.putAll(
                Map.of("id", "90", "demographic_no", "27", "merged_to", "28", "deleted", "0"));
        f.merges = List.of(row("demographic_merged", fields));
        f.scopes.put(
                "demographic_merged", new CdsExportCapturedTableDto(fields.keySet(), 1, SOURCE));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGED_PATIENT_SCOPE");
        verifyNoInteractions(f.values, f.closure, f.configurations, f.receiptReader);
    }

    @Test
    void shouldVerifyExplicitlyMissingMergeScopeCannotDefault_toUnmerged() throws Exception {
        var f = fixture(false, null);
        f.scopes.remove("demographic_merged");
        f.tables = Set.copyOf(f.scopes.keySet());
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGE_SCOPE_INVALID");
    }

    @Test
    void
            shouldVerifyUnaccountedRetainedSourceFieldsCannotDisappear_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var f = fixture(true, "Current note");
        var prior = f.retained.getFirst();
        var extra =
                new CdsExportRetainedFieldDto(
                        "e".repeat(64),
                        prior.runId(),
                        prior.sourcePatientKey(),
                        prior.fieldUnitKey(),
                        prior.category(),
                        prior.sourcePath(),
                        prior.occurrence(),
                        prior.namespace(),
                        prior.valueType(),
                        prior.precision(),
                        prior.originalInline(),
                        prior.logicalInline(),
                        prior.originalPayload());
        var fields = new ArrayList<>(f.retained);
        fields.add(extra);
        f.retained = fields;
        assertThatThrownBy(() -> compose(f, false)).isInstanceOf(IOException.class);
        assertThat(f.result).isNull();
    }

    @Test
    void shouldVerifyNativeAndAuxiliaryBoundsFailClosed_withoutPartialClinicalSuccess()
            throws Exception {
        var f = fixture(false, null);
        f.scopes.put("preventions", table("preventions", 101));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_RESOURCE_LIMIT");
        verifyNoInteractions(f.values, f.closure);
    }

    @Test
    void
            shouldVerifyLegacyConstructorCannotSilentlySkipANewImmunizationCategory_forImmunizationOccurrenceServiceContract()
                    throws Exception {
        var f = fixture(false, null);
        f.legacy = true;
        assertThatThrownBy(() -> compose(f, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldVerifyEqualAssessmentCountsCannotReplaceACapturedVaccineField_withAnInventedColumn()
            throws Exception {
        var f = fixture(false, null);
        var original =
                new CdsExportImmunizationMapper()
                        .map(27, f.parents.getFirst(), List.of(), List.of(), definitions(SOURCE));
        var fields = new LinkedHashMap<>(original.fields());
        var key =
                new CdsExportNativeFieldKeyDto(
                        "preventions", f.parents.getFirst().recordKey(), "prevention_type");
        fields.put(
                new CdsExportNativeFieldKeyDto("preventions", key.recordKey(), "invented"),
                fields.remove(key));
        var forged = new CdsExportImmunizationMappingDto(original.value(), fields);
        try (var construction =
                mockConstruction(
                        CdsExportImmunizationMapper.class,
                        // The service maps through the overload carrying the live program
                        // check.
                        (mock, context) ->
                                when(mock.map(
                                                anyInt(), any(), anyList(), anyList(), any(),
                                                any()))
                                        .thenReturn(forged))) {
            assertThatThrownBy(() -> compose(f, false))
                    .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
            assertThat(f.receipts).isEmpty();
        }
    }

    private void refused(Fixture f) {
        assertThatThrownBy(() -> compose(f, false))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_OCCURRENCE_SCOPE_INVALID");
        assertThat(f.result).isNull();
    }

    private static final class Fixture {
        final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
        final CdsExportRetainedReceiptVerificationService verification =
                mock(CdsExportRetainedReceiptVerificationService.class);
        final CdsExportNativeRowReceiptReader receiptReader =
                mock(CdsExportNativeRowReceiptReader.class);
        final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
        final CdsExportImmunizationCaptureValidator closure =
                mock(CdsExportImmunizationCaptureValidator.class);
        final CdsExportPreventionConfigurationService configurations =
                mock(CdsExportPreventionConfigurationService.class);
        final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
        final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
        final CdsExportRunControl control = mock(CdsExportRunControl.class);
        final Map<String, CdsExportCapturedTableDto> scopes = new TreeMap<>();
        final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
        CdsExportLeaseDto lease;
        CdsExportPatientCaptureDto source;
        Set<String> tables;
        Set<Integer> selection = Set.of(17);
        List<CdsExportCapturedTextRowDto> parents = List.of(),
                auxiliary = List.of(),
                merges = List.of();
        List<CdsExportNativeRowReceiptDto> witnesses = List.of();
        List<CdsExportRetainedFieldDto> retained = List.of();
        boolean lookupGeneration,
                parentEofFailure,
                receiptFailure,
                valuesEofFailure,
                revokeAtReceipt,
                legacy;
        CdsExportClinicalOccurrenceService.Result result;
    }

    private Fixture fixture(boolean imported, String notes) throws Exception {
        return fixture(imported, notes, null);
    }

    private Fixture fixture(boolean imported, String notes, String year) throws Exception {
        return fixture(imported, notes, year, false, null);
    }

    /** Review finding F10: an imported vaccine the importer filed as the catch-all OtherA. */
    private Fixture catchAllFixture(String type, String notes) throws Exception {
        return fixture(true, notes, null, true, type);
    }

    private Fixture fixture(
            boolean imported, String notes, String year, boolean catchAll, String type)
            throws Exception {
        var f = new Fixture();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS);
        var command =
                new CdsExportCommand(
                        "immunization-assembly",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        selected,
                        "receiver",
                        "audit");
        f.lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        SOURCE,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                SOURCE,
                                "sender",
                                SOURCE,
                                SOURCE,
                                new CdsExportStorageBindingDto("primary", "primary", SOURCE)));
        when(f.patient.patient()).thenReturn(27);
        var date =
                year == null
                        ? CdsTemporal.parse(null, null, "2024-02-29", null, "Date")
                        : CdsTemporal.parse(year, null, null, null, "Date");
        var base =
                catchAll
                        ? new CdsExportImmunizationFieldReconcilerUnitTest()
                                .catchAllFixture(date, type, notes)
                        : new CdsExportImmunizationFieldReconcilerUnitTest().fixture(date, notes);
        var saved =
                catchAll
                        ? new CdsExportImmunizationRetainedAssemblerUnitTest()
                                .catchAllFixture(2, date, type, notes)
                        : new CdsExportImmunizationRetainedAssemblerUnitTest()
                                .fixture(2, date, notes);
        CdsExportRetainedIndexDto index;
        CdsExportRetainedReceiptVerificationDto checked;
        if (imported) {
            index = saved.index();
            checked = saved.checked();
            f.retained = saved.fields();
            f.parents = List.of(base.parent());
            var auxiliary = new ArrayList<>(base.extensions());
            auxiliary.addAll(base.precision());
            f.auxiliary = auxiliary;
            f.witnesses = base.witnesses();
        } else {
            var fields = new TreeMap<>(base.parent().fields());
            fields.put("creator", null);
            f.parents = List.of(row("preventions", fields));
            f.witnesses = List.of(witness(f.parents.getFirst()));
            index =
                    new CdsExportRetainedIndexDto(
                            SOURCE,
                            0,
                            0,
                            Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                            Set.of(),
                            Map.of(),
                            Map.of());
            checked =
                    new CdsExportRetainedReceiptVerificationDto(
                            SOURCE, Map.of(), Set.of(), Set.of());
        }
        for (String name : CdsExportImmunizationCaptureValidator.REQUIRED)
            f.scopes.put(
                    name,
                    table(
                            name,
                            name.equals("preventions")
                                    ? 1
                                    : name.equals("preventionsExt")
                                            ? base.extensions().size()
                                            : name.equals("prevention_precision")
                                                    ? base.precision().size()
                                                    : 0));
        if (!imported) {
            f.scopes.put("preventionsExt", table("preventionsExt", 0));
            f.scopes.put("prevention_precision", table("prevention_precision", 0));
        }
        f.scopes.put(
                "demographic_merged",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns()),
                        0,
                        SOURCE));
        index.tables()
                .forEach(
                        (name, content) ->
                                f.scopes.put(
                                        name,
                                        new CdsExportCapturedTableDto(
                                                Set.of("id"), content.size(), SOURCE)));
        f.tables = Set.copyOf(f.scopes.keySet());
        var generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenReturn(f.scopes);
        when(generation.values()).thenReturn(file("1", "SOURCE_VALUES"));
        when(generation.index()).thenReturn(file("2", "SOURCE_INDEX"));
        f.source = new CdsExportPatientCaptureDto(generation, file("3", "SOURCE_MANIFEST"));
        var definitions =
                catchAll
                        ? CdsExportPreventionClassifier.fromCaptured(
                                SOURCE,
                                CdsExportImmunizationFieldReconcilerUnitTest.catchAllConfiguration()
                                        .getBytes(StandardCharsets.UTF_8),
                                List.of())
                        : definitions(SOURCE);
        when(f.configurations.readEmbedded(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(definitions);
        when(f.closure.verifyEmbedded(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenAnswer(
                        call ->
                                new CdsExportPreventionScopePlanner.Result(
                                        27,
                                        SOURCE,
                                        definitions.configurationSha256(),
                                        definitions.catalogueSha256(),
                                        SOURCE,
                                        f.selection,
                                        Set.of(),
                                        Map.of(),
                                        f.selection.size()));
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
                                    .use(index, f.retained, streams);
                            if (f.valuesEofFailure) throw new IOException("VALUES_FINAL_EOF");
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
        when(f.receiptReader.scan(
                        any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var witness : f.witnesses)
                                ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7))
                                        .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE,
                                    f.witnesses.stream()
                                            .collect(
                                                    java.util.stream.Collectors.groupingBy(
                                                            CdsExportNativeRowReceiptDto::table,
                                                            java.util.stream.Collectors
                                                                    .counting())),
                                    f.witnesses.size(),
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
                            return f.lookupGeneration
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
                            for (var row : merge ? f.merges : f.parents)
                                ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                        .accept(row);
                            if (!merge && f.parentEofFailure)
                                throw new IOException("PARENT_FINAL_EOF");
                            return generation;
                        });
        return f;
    }

    private byte[] compose(Fixture f, boolean swallow) throws Exception {
        var patient = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(key -> patient.put(key, null));
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
        var immunizations =
                new CdsExportImmunizationOccurrenceService(
                        f.values,
                        f.verification,
                        f.receiptReader,
                        f.captures,
                        f.closure,
                        f.configurations,
                        f.rows);
        var service =
                f.legacy
                        ? new CdsExportClinicalOccurrenceService(
                                f.values, f.verification, f.receiptReader, f.captures)
                        : new CdsExportClinicalOccurrenceService(
                                f.values,
                                f.verification,
                                f.receiptReader,
                                f.captures,
                                immunizations);
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        SOURCE,
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
                                f.result =
                                        service.append(
                                                CdsCategory.IMMUNIZATIONS,
                                                f.lease,
                                                f.patient,
                                                PATIENT,
                                                f.tables,
                                                f.source,
                                                file("4", "RETAINED_MANIFEST"),
                                                new CdsExportClinicalOccurrenceService.Bounds(
                                                        100000, 8L << 20, 1000, 8L << 20, 100,
                                                        1000),
                                                f.control,
                                                emitter,
                                                receipt -> {
                                                    if (f.receiptFailure)
                                                        throw new IOException("RECEIPT_FAILED");
                                                    f.receipts.add(receipt);
                                                    if (f.revokeAtReceipt)
                                                        doThrow(
                                                                        new SecurityException(
                                                                                "REVOKED_AFTER_RECEIPT"))
                                                                .when(f.captures)
                                                                .authorizeSources(
                                                                        any(), any(), anySet(),
                                                                        any());
                                                });
                            } catch (IOException | RuntimeException failed) {
                                if (!swallow) throw failed;
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
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var path = Files.write(directory.resolve(UUID.randomUUID() + ".xml"), xml);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        path,
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records;
    }

    private static CdsExportCapturedTableDto table(String name, long count) {
        var columns =
                switch (name) {
                    case "preventions" ->
                            Set.copyOf(
                                    CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                                            .columns());
                    case "preventionsExt" ->
                            Set.copyOf(
                                    CdsExportPatientTableReadSupport.Plan.PREVENTIONS_EXT
                                            .columns());
                    case "prevention_precision" ->
                            Set.copyOf(
                                    CdsExportPatientTableReadSupport.Plan.PREVENTION_PRECISION
                                            .columns());
                    default -> Set.of("id");
                };
        return new CdsExportCapturedTableDto(columns, count, SOURCE);
    }

    private static CdsExportPreventionClassifier definitions(String source) throws IOException {
        return CdsExportPreventionClassifier.fromCaptured(
                source,
                "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='HB' layout='injection' healthCanadaType='HB'/></preventions>"
                        .getBytes(StandardCharsets.UTF_8),
                List.of());
    }

    private static CdsExportCapturedTextRowDto row(String table, Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                table,
                CdsExportPatientTableSourceReader.key(Integer.parseInt(fields.get("id"))),
                fields);
    }

    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var field : new TreeMap<>(row.fields()).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(field.getKey(), "VARCHAR", 255, 0));
            var text = field.getValue();
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length,
                            text == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    text.getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
        }
        var state = new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), state.id(), row.recordKey(), state.digest(), state);
    }

    private static CdsExportStagedFileDto file(String member, String purpose) {
        return new CdsExportStagedFileDto(member.repeat(64), purpose, SOURCE, 1);
    }
}
