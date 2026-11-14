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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Service joins and sticky source failure proof; source encryption/physical authorization run in their owning suites. */
@Tag("unit")
@Tag("cds-export")
class CdsExportClinicalOccurrenceServiceUnitTest {
    private static final String SOURCE = "b".repeat(64), PATIENT_KEY = "a".repeat(64);
    private static final CdsCategory ALLERGY = CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
            MEDICATION = CdsCategory.MEDICATIONS_AND_TREATMENTS;
    @TempDir Path directory;

    @Test
    void shouldDispatchHistoryThroughTheSharedService_inTheSameSourceFailureScope()
            throws Exception {
        var history = mock(CdsExportHistoryOccurrenceService.class);
        var captures = mock(CdsExportNativeCaptureService.class);
        var values = mock(CdsExportRetainedValuesService.class);
        var receipts = mock(CdsExportNativeRowReceiptReader.class);
        var verification = mock(CdsExportRetainedReceiptVerificationService.class);
        var service =
                new CdsExportClinicalOccurrenceService(
                        values,
                        verification,
                        receipts,
                        captures,
                        mock(CdsExportImmunizationOccurrenceService.class),
                        mock(CdsExportAppointmentOccurrenceService.class),
                        history);
        var lease = mock(CdsExportLeaseDto.class);
        var patient = mock(CdsExportCohortService.Patient.class);
        var source = mock(CdsExportPatientCaptureDto.class);
        var payload = mock(CdsExportStagedFileDto.class);
        var control = mock(CdsExportRunControl.class);
        var xml = mock(CdsExportXmlComposer.SectionEmitter.class);
        var output = mock(CdsExportClinicalOccurrenceService.Receipts.class);
        var bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        1000, 1L << 20, 100, 1L << 20, 100, 100);
        doAnswer(
                        call -> {
                            call.<CdsExportXmlComposer.Sections>getArgument(0).write(xml);
                            return null;
                        })
                .when(xml)
                .source(any());
        var result =
                new CdsExportClinicalOccurrenceService.Result(
                        SOURCE, CdsCategory.FAMILY_HISTORY, 1, 1, 0, 2);
        when(history.append(
                        CdsCategory.FAMILY_HISTORY,
                        lease,
                        patient,
                        PATIENT_KEY,
                        Set.of("casemgmt_note"),
                        source,
                        payload,
                        bounds,
                        control,
                        xml,
                        output))
                .thenReturn(result);
        assertThat(
                        service.append(
                                CdsCategory.FAMILY_HISTORY,
                                lease,
                                patient,
                                PATIENT_KEY,
                                Set.of("casemgmt_note"),
                                source,
                                payload,
                                bounds,
                                control,
                                xml,
                                output))
                .isEqualTo(result);
        verifyNoInteractions(values, receipts, verification, captures);
        verify(xml).source(any());
    }

    @Test
    void shouldDispatchLaboratoriesThroughTheSharedService_withTheSameSourceFailureBoundary()
            throws Exception {
        var laboratory = mock(CdsExportLaboratoryOccurrenceService.class);
        var captures = mock(CdsExportNativeCaptureService.class);
        var values = mock(CdsExportRetainedValuesService.class);
        var receipts = mock(CdsExportNativeRowReceiptReader.class);
        var verification = mock(CdsExportRetainedReceiptVerificationService.class);
        var service =
                new CdsExportClinicalOccurrenceService(
                        values,
                        verification,
                        receipts,
                        captures,
                        mock(CdsExportImmunizationOccurrenceService.class),
                        mock(CdsExportAppointmentOccurrenceService.class),
                        mock(CdsExportHistoryOccurrenceService.class),
                        laboratory);
        var lease = mock(CdsExportLeaseDto.class);
        var patient = mock(CdsExportCohortService.Patient.class);
        var source = mock(CdsExportPatientCaptureDto.class);
        var payload = mock(CdsExportStagedFileDto.class);
        var control = mock(CdsExportRunControl.class);
        var xml = mock(CdsExportXmlComposer.SectionEmitter.class);
        var output = mock(CdsExportClinicalOccurrenceService.Receipts.class);
        var bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        1000, 1L << 20, 100, 1L << 20, 100, 100);
        doAnswer(
                        call -> {
                            call.<CdsExportXmlComposer.Sections>getArgument(0).write(xml);
                            return null;
                        })
                .when(xml)
                .source(any());
        var result =
                new CdsExportClinicalOccurrenceService.Result(
                        SOURCE, CdsCategory.LABORATORY_RESULTS, 1, 1, 0, 3);
        when(laboratory.append(
                        lease,
                        patient,
                        PATIENT_KEY,
                        Set.of("cds_laboratory_result"),
                        source,
                        payload,
                        bounds,
                        control,
                        xml,
                        output))
                .thenReturn(result);
        assertThat(
                        service.append(
                                CdsCategory.LABORATORY_RESULTS,
                                lease,
                                patient,
                                PATIENT_KEY,
                                Set.of("cds_laboratory_result"),
                                source,
                                payload,
                                bounds,
                                control,
                                xml,
                                output))
                .isEqualTo(result);
        verifyNoInteractions(values, receipts, verification, captures);
        verify(xml).source(any());
    }

    @Test
    void shouldAssembleEachNativeOnlyOccurrenceOnce_withItsOwnSerializationAndFieldReceipts()
            throws Exception {
        var f = nativeOnly();
        var records = parse(compose(f, false), ALLERGY, "native");
        var received =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(received.description()).isEqualTo("Synthetic agent");
        assertThat(received.severity()).isEqualTo("MI");
        assertThat(f.receipts).hasSize(1);
        assertThat(f.receipts.getFirst().importedUnit()).isNull();
        assertThat(f.result.nativeOnlyRows()).isEqualTo(1);
        assertThat(f.result.importedRows()).isZero();
        assertThat(f.receipts.getFirst().fields()).hasSize(24);
        verify(f.captures).authorizeSources(f.lease, f.patient, f.tables, f.control);
    }

    @Test
    void shouldAssembleAnEditedImportedAllergy_andKeepItsOriginalCodeClockAndVendorFields()
            throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        current.put("reaction", "Current reaction");
        var retained =
                new CdsExportAllergyRetainedAssemblerUnitTest().fixture(current, false, null);
        var nativeSource =
                new CdsExportAllergyFieldReconcilerUnitTest()
                        .fixture(current, true, CdsExportAllergyFieldReconcilerUnitTest.source());
        var f =
                fixture(
                        ALLERGY,
                        retained.index(),
                        retained.checked(),
                        retained.fields(),
                        List.of(nativeSource.row()),
                        List.of(nativeSource.witness()));
        var records = parse(compose(f, false), ALLERGY, "imported-allergy");
        var received =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(received.reaction()).isEqualTo("Current reaction");
        assertThat(received.severity()).isEqualTo("LT");
        assertThat(received.codeSystem()).isEqualTo("DIN");
        assertThat(received.recorded().original()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
        assertThat(
                        new CdsFieldParser()
                                .firstToken(
                                        records.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
        assertThat(f.result.retainedSourceFields()).isEqualTo(retained.fields().size());
        assertThat(f.result.importedRows()).isEqualTo(1);
    }

    @Test
    void
            shouldAssembleAnEditedImportedMedication_andKeepUnprojectedProblemCodeAndStructuredStrength()
                    throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline();
        current.put("comment", "Current medication note");
        var retained = CdsExportMedicationRetainedAssemblerUnitTest.fixture(current);
        var nativeSource =
                CdsExportMedicationFieldReconcilerUnitTest.fixture(
                        current, CdsExportMedicationFieldReconcilerUnitTest.numbers(), true);
        var f =
                fixture(
                        MEDICATION,
                        retained.index(),
                        retained.checked(),
                        retained.fields(),
                        List.of(nativeSource.row()),
                        List.of(nativeSource.witness()));
        var records = parse(compose(f, false), MEDICATION, "imported-medication");
        var received =
                new CdsClinicalNormalizationService(new CdsFieldParser())
                        .medication(records.get(1));
        assertThat(received.providerNotes()).isEqualTo("Current medication note");
        assertThat(received.strengthAmount()).isEqualTo("5");
        assertThat(received.strengthUnit()).isEqualTo("mg");
        assertThat(received.prescriberLastName()).isEqualTo("Physician");
        assertThat(new CdsFieldParser().firstToken(records.get(1), "ProblemCode", 100))
                .isEqualTo("B01");
        assertThat(f.result.retainedSourceFields()).isEqualTo(retained.fields().size());
        assertThat(f.receipts).hasSize(1);
    }

    @Test
    void shouldExportANativeMedicationsIndication_asProblemCodeWithAReceiptForEveryReasonField()
            throws Exception {
        var f = nativeMedication();
        var archived = CdsExportMedicationReasonMapperUnitTest.reason(4, "icd9", "401", "1");
        archived.put("archivedFlag", "1");
        archived.put("archivedReason", "Wrong drug");
        f.lookupRows =
                List.of(
                        CdsExportMedicationReasonMapperUnitTest.row(
                                CdsExportMedicationReasonMapperUnitTest.reason(
                                        3, "icd9", "250", "0")),
                        CdsExportMedicationReasonMapperUnitTest.row(archived));
        var records = parse(compose(f, false), MEDICATION, "native-medication-reason");
        assertThat(new CdsFieldParser().firstToken(records.get(1), "ProblemCode", 100))
                .isEqualTo("250");
        assertThat(f.receipts).hasSize(1);
        var reasonFields =
                f.receipts.getFirst().fields().entrySet().stream()
                        .filter(entry -> entry.getKey().table().equals("drugReason"))
                        .toList();
        assertThat(reasonFields).hasSize(22);
        assertThat(reasonFields)
                .anySatisfy(
                        entry -> {
                            assertThat(entry.getKey().column()).isEqualTo("code");
                            assertThat(entry.getValue().destination()).isEqualTo("ProblemCode");
                        });
    }

    @Test
    void shouldAddAReasonRecordedAfterTheImport_withoutReplacingTheOriginalProblemCode()
            throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline();
        var retained = CdsExportMedicationRetainedAssemblerUnitTest.fixture(current);
        var nativeSource =
                CdsExportMedicationFieldReconcilerUnitTest.fixture(
                        current, CdsExportMedicationFieldReconcilerUnitTest.numbers(), true);
        var f =
                fixture(
                        MEDICATION,
                        retained.index(),
                        retained.checked(),
                        retained.fields(),
                        List.of(nativeSource.row()),
                        List.of(nativeSource.witness()));
        f.lookupRows =
                List.of(
                        CdsExportMedicationReasonMapperUnitTest.row(
                                CdsExportMedicationReasonMapperUnitTest.reason(
                                        3, "icd9", "250", "1")));
        byte[] xml = compose(f, false);
        var records = parse(xml, MEDICATION, "imported-medication-reason");
        assertThat(new CdsFieldParser().firstToken(records.get(1), "ProblemCode", 100))
                .isEqualTo("B01");
        assertThat(new String(xml, java.nio.charset.StandardCharsets.UTF_8))
                .contains("CARLOS.Medication.Reasons.v1", "Vendor café 😀 content");
        assertThat(f.result.retainedSourceFields()).isEqualTo(retained.fields().size());
        assertThat(f.receipts.getFirst().fields().keySet())
                .filteredOn(field -> field.table().equals("drugReason"))
                .hasSize(11);
    }

    @Test
    void shouldRefuseAReasonOfNoExportedPrescription_orAMedicationCaptureWithoutReasons()
            throws Exception {
        var orphan = nativeMedication();
        var foreign = CdsExportMedicationReasonMapperUnitTest.reason(3, "icd9", "250", "1");
        foreign.put("drugId", "52");
        orphan.lookupRows = List.of(CdsExportMedicationReasonMapperUnitTest.row(foreign));
        assertThatThrownBy(() -> compose(orphan, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        var withoutReasons = nativeMedication();
        withoutReasons.source.capture().tables().remove("drugReason");
        withoutReasons.tables = Set.copyOf(withoutReasons.source.capture().tables().keySet());
        assertThatThrownBy(() -> compose(withoutReasons, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        assertThat(withoutReasons.receipts).isEmpty();
    }

    private Fixture nativeMedication() throws Exception {
        var row =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportNativeMedicationMapperUnitTest.source());
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        return fixture(
                MEDICATION,
                index,
                new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(), Set.of(), Set.of()),
                List.of(),
                List.of(row),
                List.of(new CdsExportNativeRowReceiptDto("drugs", "51", row.recordKey(), "c".repeat(64))));
    }

    @Test
    void shouldRefuseMissingDuplicateOrOrphanRows_insteadOfReturningACompletePatientDocument()
            throws Exception {
        var missing = nativeOnly();
        missing.nativeRows = List.of();
        assertThatThrownBy(() -> compose(missing, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        var duplicate = nativeOnly();
        duplicate.nativeRows =
                List.of(duplicate.nativeRows.getFirst(), duplicate.nativeRows.getFirst());
        assertThatThrownBy(() -> compose(duplicate, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        var orphan = nativeOnly();
        orphan.lookupRows = List.of(precision("1", "99"));
        assertThatThrownBy(() -> compose(orphan, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldRejectAChangedGeneration_orAnUnselectedPrecisionDomainBeforeEmittingClinicalFacts()
            throws Exception {
        var changed = nativeOnly();
        changed.changedGeneration = true;
        assertThatThrownBy(() -> compose(changed, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        assertThat(changed.receipts).isEmpty();
        var unselected = nativeOnly();
        unselected.lookupRows = List.of(precision("2", "51"));
        assertThatThrownBy(() -> compose(unselected, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        assertThat(unselected.receipts).isEmpty();
    }

    @Test
    void
            shouldKeepTheWholeDocumentFailed_whenFinalAuthorizationOrSourceEofFailureIsCaughtByTheCaller()
                    throws Exception {
        var denied = nativeOnly();
        doThrow(new SecurityException("ACCESS_REVOKED"))
                .when(denied.captures)
                .authorizeSources(denied.lease, denied.patient, denied.tables, denied.control);
        assertThatThrownBy(() -> compose(denied, true))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(denied.receipts).hasSize(1);
        var eof = nativeOnly();
        eof.finalSourceFailure = true;
        assertThatThrownBy(() -> compose(eof, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(eof.receipts).hasSize(1);
    }

    @Test
    void shouldKeepTheDocumentFailed_whenAReceiptSinkFailureAfterEmissionIsCaught()
            throws Exception {
        var f = nativeOnly();
        f.receiptFailure = true;
        assertThatThrownBy(() -> compose(f, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(f.result).isNull();
    }

    @Test
    void shouldRequireReviewForUnmappedNativeValues_withoutFallingBackToRetainedOrEmptyContent()
            throws Exception {
        var f = nativeOnly();
        var fields = new LinkedHashMap<>(f.nativeRows.getFirst().fields());
        // Native "Severe", an unlisted drug/non-drug flag and text XML cannot carry now travel in
        // residuals; an archive flag that is neither 0 nor 1 (no screen writes one) still has no
        // representation and must hold the patient.
        fields.put("archived", "2");
        f.nativeRows =
                List.of(
                        new CdsExportCapturedTextRowDto(
                                "allergies", f.nativeRows.getFirst().recordKey(), fields));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldRequireReviewForImportedDeletion_withoutResurrectingRetainedContent()
            throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        var retained =
                new CdsExportAllergyRetainedAssemblerUnitTest().fixture(current, false, null);
        var units = new LinkedHashMap<>(retained.checked().units());
        String address = retained.decision().unitAddress();
        var original = units.get(address).getFirst();
        units.put(
                address,
                List.of(
                        new CdsExportRetainedReceiptVerificationDto.CheckedReference(
                                original.expected(),
                                null,
                                CdsExportRetainedReceiptVerificationDto.Reason
                                        .ROW_NOT_IN_CAPTURE)));
        var unchanged = new HashSet<>(retained.checked().unchangedUnits());
        unchanged.remove(address);
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        SOURCE, units, unchanged, Set.of(address));
        var f =
                fixture(
                        ALLERGY,
                        retained.index(),
                        checked,
                        retained.fields(),
                        List.of(),
                        List.of());
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldRequireReviewForMultipleImportedOwners_beforeDuplicatingAClinicalOccurrence()
            throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        var retained =
                new CdsExportAllergyRetainedAssemblerUnitTest().fixture(current, false, null);
        var nativeSource =
                new CdsExportAllergyFieldReconcilerUnitTest()
                        .fixture(current, true, CdsExportAllergyFieldReconcilerUnitTest.source());
        String key = "e".repeat(64),
                originalAddress = retained.decision().unitAddress(),
                run = originalAddress.substring(0, 36),
                alias = run + ":" + key;
        var tables = new LinkedHashMap<>(retained.index().tables());
        var nativeUnits = new LinkedHashMap<>(tables.get("cds_import_unit"));
        var first = nativeUnits.values().iterator().next();
        var fields = new LinkedHashMap<>(first.fields());
        fields.put("unit_key", key);
        String id = io.github.carlos_emr.carlos.utility.CdsImportKeys.hash(run, key);
        fields.put("id", id);
        nativeUnits.put(id, new CdsExportCapturedTextRowDto("cds_import_unit", id, fields));
        tables.put("cds_import_unit", nativeUnits);
        var applied = new HashSet<>(retained.index().appliedUnits());
        applied.add(alias);
        var references = new LinkedHashMap<>(retained.index().references());
        references.put(alias, references.get(originalAddress));
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        retained.index().metadataRows() + 1,
                        32768,
                        tables,
                        applied,
                        references,
                        retained.index().nativeParents());
        var units = new LinkedHashMap<>(retained.checked().units());
        units.put(alias, units.get(originalAddress));
        var checked = new CdsExportRetainedReceiptVerificationDto(SOURCE, units, applied, Set.of());
        var f =
                fixture(
                        ALLERGY,
                        index,
                        checked,
                        retained.fields(),
                        List.of(nativeSource.row()),
                        List.of(nativeSource.witness()));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        assertThat(f.receipts).isEmpty();
        verifyNoInteractions(f.witnesses);
    }

    @Test
    void shouldRefuseActiveIncomingOrOutgoingMerges_beforeOpeningRetainedOrClinicalValues()
            throws Exception {
        for (boolean incoming : List.of(true, false)) {
            var f = nativeOnly();
            setMerge(f, merge(incoming ? 28 : 27, incoming ? 27 : 28, "0"));
            assertThatThrownBy(() -> compose(f, false))
                    .hasMessage("CDS_EXPORT_MERGED_PATIENT_SCOPE");
            assertThat(f.receipts).isEmpty();
            verifyNoInteractions(f.values, f.verification, f.witnesses);
        }
    }

    @Test
    void shouldNotTreatDeletedMergeHistoryAsAnActiveAlias_forClinicalOccurrenceServiceContract() throws Exception {
        var f = nativeOnly();
        setMerge(f, merge(28, 27, "1"));
        parse(compose(f, false), ALLERGY, "deleted-merge");
        assertThat(f.receipts).hasSize(1);
    }

    @Test
    void shouldRejectUnknownMergeStates_insteadOfAssumingTheyAreDeleted() throws Exception {
        var f = nativeOnly();
        setMerge(f, merge(28, 27, "2"));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGED_PATIENT_SCOPE");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldRefuseAnAbsentFrozenMergeScopeEven_forAnOtherwiseValidNativeOccurrence()
            throws Exception {
        var f = nativeOnly();
        f.source.capture().tables().remove("demographic_merged");
        f.tables = Set.copyOf(f.source.capture().tables().keySet());
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGE_SCOPE_INVALID");
        verifyNoInteractions(f.values, f.verification, f.witnesses);
    }

    @Test
    void shouldRejectForeignMergeRows_insteadOfExpandingDisclosureScope() throws Exception {
        var f = nativeOnly();
        setMerge(f, merge(28, 29, "0"));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGE_SCOPE_INVALID");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldKeepTheDocumentFailed_whenAMergeReviewIsCaughtByTheCaller() throws Exception {
        var f = nativeOnly();
        setMerge(f, merge(28, 27, "0"));
        assertThatThrownBy(() -> compose(f, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        assertThat(f.receipts).isEmpty();
    }

    @Test
    void shouldFinishMergeAuthentication_beforeReturningEvenAnAdverseReview() throws Exception {
        var f = nativeOnly();
        setMerge(f, merge(28, 27, "0"));
        f.mergeReadFailure = true;
        assertThatThrownBy(() -> compose(f, false)).hasMessage("MERGE_SOURCE_EOF_REJECTED");
        assertThat(f.receipts).isEmpty();
    }

    private static CdsExportCapturedTextRowDto merge(int from, int to, String deleted) {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id",
                        "11",
                        "demographic_no",
                        Integer.toString(from),
                        "merged_to",
                        Integer.toString(to),
                        "deleted",
                        deleted));
        return new CdsExportCapturedTextRowDto(
                "demographic_merged", CdsExportPatientTableSourceReader.key(11), fields);
    }

    private static void setMerge(Fixture f, CdsExportCapturedTextRowDto row) {
        f.mergeRows = List.of(row);
        var previous = f.source.capture().tables().get("demographic_merged");
        f.source
                .capture()
                .tables()
                .put(
                        "demographic_merged",
                        new CdsExportCapturedTableDto(
                                previous.columns(), 1, previous.schemaSha256()));
    }

    @Test
    void shouldVerifyEqualFieldCountsCannotHideAnInventedAssessment_insteadOfTheCapturedDescription()
            throws Exception {
        var f = nativeOnly();
        var original = new CdsExportAllergyMapper().map(27, f.nativeRows.getFirst(), List.of());
        var fields = new LinkedHashMap<>(original.fields());
        var key =
                new CdsExportNativeFieldKeyDto(
                        "allergies", f.nativeRows.getFirst().recordKey(), "DESCRIPTION");
        fields.put(
                new CdsExportNativeFieldKeyDto("allergies", key.recordKey(), "invented"),
                fields.remove(key));
        var forged = new CdsExportAllergyMappingDto(original.value(), fields);
        try (var construction =
                mockConstruction(
                        CdsExportAllergyMapper.class,
                        (mock, context) ->
                                when(mock.map(anyInt(), any(), anyList())).thenReturn(forged))) {
            assertThatThrownBy(() -> compose(f, false))
                    .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
            assertThat(f.receipts).isEmpty();
        }
    }

    private Fixture nativeOnly() throws Exception {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.ALLERGIES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "allergyid",
                        "51",
                        "demographic_no",
                        "27",
                        "DESCRIPTION",
                        "Synthetic agent",
                        "TYPECODE",
                        "0",
                        "severity_of_reaction",
                        "1",
                        "archived",
                        "0",
                        "position",
                        "0"));
        var row =
                new CdsExportCapturedTextRowDto(
                        "allergies", CdsExportPatientTableSourceReader.key(51), fields);
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        var checked =
                new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(), Set.of(), Set.of());
        return fixture(
                ALLERGY,
                index,
                checked,
                List.of(),
                List.of(row),
                List.of(
                        new CdsExportNativeRowReceiptDto(
                                "allergies", "51", row.recordKey(), "c".repeat(64))));
    }

    private static CdsExportCapturedTextRowDto precision(String owner, String parent) {
        return new CdsExportCapturedTextRowDto(
                "partial_date",
                CdsExportPatientTableSourceReader.key(11),
                Map.of(
                        "id",
                        "11",
                        "table_name",
                        owner,
                        "table_id",
                        parent,
                        "field_name",
                        "1",
                        "format",
                        "YYYY"));
    }

    private static CdsExportCapturedTextRowDto allergy(int id) {
        var fields = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.ALLERGIES
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "allergyid",
                        Integer.toString(id),
                        "demographic_no",
                        "27",
                        "DESCRIPTION",
                        "Synthetic agent " + id,
                        "TYPECODE",
                        "0",
                        "severity_of_reaction",
                        Integer.toString(1 + id % 3),
                        "archived",
                        "0",
                        "position",
                        "0"));
        return new CdsExportCapturedTextRowDto(
                "allergies", CdsExportPatientTableSourceReader.key(id), fields);
    }

    private static CdsExportCapturedTextRowDto allergyPrecision(int id, int parent, String format) {
        return new CdsExportCapturedTextRowDto(
                "partial_date",
                CdsExportPatientTableSourceReader.key(id),
                Map.of(
                        "id",
                        Integer.toString(id),
                        "table_name",
                        "1",
                        "table_id",
                        Integer.toString(parent),
                        "field_name",
                        "1",
                        "format",
                        format));
    }

    /** A chart of {@code count} native allergies; every third one has a precision row. */
    private Fixture allergies(int count) throws Exception {
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var witnesses = new ArrayList<CdsExportNativeRowReceiptDto>();
        var lookups = new ArrayList<CdsExportCapturedTextRowDto>();
        for (int id = 1001; id <= 1000 + count; id++) {
            var row = allergy(id);
            rows.add(row);
            witnesses.add(
                    new CdsExportNativeRowReceiptDto(
                            "allergies", Integer.toString(id), row.recordKey(), "c".repeat(64)));
            if (id % 3 == 0) lookups.add(allergyPrecision(500_000 + id, id, "YYYY"));
        }
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        Set.of(),
                        Map.of(),
                        Map.of());
        var f =
                fixture(
                        ALLERGY,
                        index,
                        new CdsExportRetainedReceiptVerificationDto(
                                SOURCE, Map.of(), Set.of(), Set.of()),
                        List.of(),
                        rows,
                        witnesses);
        f.lookupRows = lookups;
        return f;
    }

    @Test
    void shouldEmitTheSameBytesAndReceipts_whenAChartIsReadInSmallWindows() throws Exception {
        var whole = allergies(11);
        byte[] expected = compose(whole, false);
        verify(whole.witnesses, times(1))
                .scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
        for (int window : new int[] {1, 2, 5, 10}) {
            var windowed = allergies(11);
            windowed.bounds = windowed.bounds.withWindow(window, 8L << 20);
            assertThat(compose(windowed, false)).isEqualTo(expected);
            assertThat(windowed.receipts).isEqualTo(whole.receipts);
            assertThat(windowed.result).isEqualTo(whole.result);
            // More rows than one window holds: no attempt at the whole chart, one witness pass
            // for each window.
            verify(windowed.witnesses, times((11 + window - 1) / window))
                    .scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
        }
        // A byte limit smaller than the chart's witnesses (1,024 bytes each) windows it too.
        var byBytes = allergies(11);
        byBytes.bounds = byBytes.bounds.withWindow(8192, 3 * 1024L);
        assertThat(compose(byBytes, false)).isEqualTo(expected);
        assertThat(byBytes.receipts).isEqualTo(whole.receipts);
        assertThat(parse(expected, ALLERGY, "windows")).hasSize(12);
    }

    @Test
    void shouldEmitTheSameMedications_whenTheirReasonsAndPrecisionAreReadByWindow()
            throws Exception {
        java.util.function.Function<Integer, Fixture> chart =
                window -> {
                    try {
                        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
                        var witnesses = new ArrayList<CdsExportNativeRowReceiptDto>();
                        var lookups = new ArrayList<CdsExportCapturedTextRowDto>();
                        for (int id = 51; id <= 57; id++) {
                            var fields =
                                    new LinkedHashMap<>(
                                            CdsExportNativeMedicationMapperUnitTest.source());
                            fields.put("drugid", Integer.toString(id));
                            var row =
                                    new CdsExportCapturedTextRowDto(
                                            "drugs",
                                            CdsExportPatientTableSourceReader.key(id),
                                            fields);
                            rows.add(row);
                            witnesses.add(
                                    new CdsExportNativeRowReceiptDto(
                                            "drugs",
                                            Integer.toString(id),
                                            row.recordKey(),
                                            "c".repeat(64)));
                            var reason =
                                    CdsExportMedicationReasonMapperUnitTest.reason(
                                            300 + id, "icd9", "250", "0");
                            reason.put("drugId", Integer.toString(id));
                            if (id % 2 == 1)
                                lookups.add(CdsExportMedicationReasonMapperUnitTest.row(reason));
                        }
                        var index =
                                new CdsExportRetainedIndexDto(
                                        SOURCE,
                                        0,
                                        0,
                                        Map.of(
                                                "cds_import_unit",
                                                Map.of(),
                                                "cds_import_field",
                                                Map.of()),
                                        Set.of(),
                                        Map.of(),
                                        Map.of());
                        var f =
                                fixture(
                                        MEDICATION,
                                        index,
                                        new CdsExportRetainedReceiptVerificationDto(
                                                SOURCE, Map.of(), Set.of(), Set.of()),
                                        List.of(),
                                        rows,
                                        witnesses);
                        f.lookupRows = lookups;
                        if (window != null) f.bounds = f.bounds.withWindow(window, 8L << 20);
                        return f;
                    } catch (Exception failed) {
                        throw new IllegalStateException(failed);
                    }
                };
        var whole = chart.apply(null);
        byte[] expected = compose(whole, false);
        assertThat(whole.receipts).hasSize(7);
        for (int window : new int[] {1, 3}) {
            var windowed = chart.apply(window);
            assertThat(compose(windowed, false)).isEqualTo(expected);
            assertThat(windowed.receipts).isEqualTo(whole.receipts);
        }
        // A reason whose prescription is in no window is refused, as it was when read whole.
        var orphan = chart.apply(2);
        var foreign = CdsExportMedicationReasonMapperUnitTest.reason(999, "icd9", "250", "1");
        foreign.put("drugId", "99");
        var rows = new ArrayList<>(orphan.lookupRows);
        rows.add(CdsExportMedicationReasonMapperUnitTest.row(foreign));
        orphan.lookupRows = rows;
        assertThatThrownBy(() -> compose(orphan, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseMissingDuplicateOrOrphanRows_whenTheChartIsReadByWindow() throws Exception {
        var duplicate = allergies(6);
        duplicate.bounds = duplicate.bounds.withWindow(2, 8L << 20);
        var rows = new ArrayList<>(duplicate.nativeRows);
        rows.set(4, rows.get(1));
        duplicate.nativeRows = rows;
        assertThatThrownBy(() -> compose(duplicate, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        var orphan = allergies(6);
        orphan.bounds = orphan.bounds.withWindow(2, 8L << 20);
        var lookups = new ArrayList<>(orphan.lookupRows);
        lookups.add(allergyPrecision(777_777, 99, "YYYY"));
        orphan.lookupRows = lookups;
        assertThatThrownBy(() -> compose(orphan, false))
                .hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldExportTheChart_pastTheFormerRowAndLookupBounds() throws Exception {
        // 8,193 allergies: one more than the former cds.export.maxNativeRows, under the
        // installed window. Each has 16 precision rows: 131,088 lookups, more than the former
        // cds.export.maxLookupRows (131,072).
        var f = allergies(8193);
        var lookups = new ArrayList<CdsExportCapturedTextRowDto>();
        int next = 2_000_000;
        for (var row : f.nativeRows)
            for (int repeat = 0; repeat < 16; repeat++)
                lookups.add(
                        allergyPrecision(
                                next++, Integer.parseInt(row.fields().get("allergyid")), "YYYY"));
        f.lookupRows = lookups;
        f.documentBytes = 512L << 20;
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
        assertThat(f.receipts.stream().map(receipt -> receipt.nativeId()).distinct()).hasSize(8193);
        assertThat(f.receipts.getFirst().nativeId()).isEqualTo("1001");
        assertThat(f.receipts.getLast().nativeId()).isEqualTo("9193");
    }

    @Test
    void shouldExportTheChart_pastTheFormerIndexBytes() throws Exception {
        // The former cds.export.maxIndexBytes was 64 MiB of witnesses at 1,024 bytes and 256 for
        // each column. A witness of 70 columns counts 18,944 bytes, so 3,543 rows pass it.
        var f = allergies(3543);
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (int column = 0; column < 70; column++) {
            columns.add(new CdsNativeRowDigest.Column("c" + column, "VARCHAR", 255, 0));
            cells.add(new CdsNativeRowState.Cell(true, 0, null));
        }
        var wide = new ArrayList<CdsExportNativeRowReceiptDto>();
        for (var row : f.nativeRows) {
            String id = row.fields().get("allergyid");
            var state = new CdsNativeRowState("allergies", id, columns, cells);
            wide.add(
                    new CdsExportNativeRowReceiptDto(
                            "allergies", id, row.recordKey(), state.digest(), state));
        }
        doAnswer(
                        call -> {
                            for (var witness : wide)
                                ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7))
                                        .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE, Map.of("allergies", (long) wide.size()), wide.size(), 0,
                                    Set.of());
                        })
                .when(f.witnesses)
                .scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
        f.bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                        CdsExportClinicalOccurrenceService.Bounds.UNLIMITED,
                        1_000_000,
                        64L << 20,
                        8192,
                        131_072);
        compose(f, false);
        assertThat(f.receipts).hasSize(3543);
        // The whole chart did not fit 64 MiB, so it was read in two windows after that attempt.
        verify(f.witnesses, times(3))
                .scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any());
    }

    @Test
    void shouldHoldOneWindowOfLookups_whileAChartOfManyRowsIsEmitted() throws Exception {
        // A regression to loading the whole chart would keep every lookup row reachable while
        // rows are emitted. Each lookup pass hands out fresh rows; while a window's rows are
        // emitted, only that window's lookups may still be reachable.
        int count = 600, window = 30;
        var f = allergies(count);
        f.bounds = f.bounds.withWindow(window, 8L << 20);
        var handed = new ArrayList<java.lang.ref.WeakReference<CdsExportCapturedTextRowDto>>();
        f.lookupSource =
                () -> {
                    handed.clear();
                    var fresh = new ArrayList<CdsExportCapturedTextRowDto>();
                    for (int id = 1001; id <= 1000 + count; id++) {
                        var row = allergyPrecision(500_000 + id, id, "YYYY");
                        fresh.add(row);
                        handed.add(new java.lang.ref.WeakReference<>(row));
                    }
                    return fresh;
                };
        long[] most = {0};
        int[] rows = {0};
        f.beforeRow =
                () -> {
                    // Sampled inside a pass over the main table, when the lookup pass's own
                    // list is gone: once per emitting pass is enough.
                    if (handed.isEmpty() || rows[0]++ % count != count / 2) return;
                    System.gc();
                    most[0] =
                            Math.max(
                                    most[0],
                                    handed.stream().filter(row -> row.get() != null).count());
                };
        compose(f, false);
        assertThat(f.receipts).hasSize(count);
        assertThat(most[0]).isPositive().isLessThanOrEqualTo(window);
    }

    private static final class Fixture {
        final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
        final CdsExportRetainedReceiptVerificationService verification =
                mock(CdsExportRetainedReceiptVerificationService.class);
        final CdsExportNativeRowReceiptReader witnesses =
                mock(CdsExportNativeRowReceiptReader.class);
        final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
        final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
        final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
        final CdsExportRunControl control = mock(CdsExportRunControl.class);
        final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
        CdsExportLeaseDto lease;
        CdsExportPatientCaptureDto source;
        Set<String> tables;
        CdsCategory category;
        List<CdsExportCapturedTextRowDto> nativeRows, lookupRows = List.of(), mergeRows = List.of();
        boolean changedGeneration, finalSourceFailure, receiptFailure, mergeReadFailure;
        CdsExportClinicalOccurrenceService.Result result;
        CdsExportClinicalOccurrenceService.Bounds bounds =
                new CdsExportClinicalOccurrenceService.Bounds(
                        100000, 8L << 20, 1000, 8L << 20, 100, 1000);
        // Each lookup scan may hand out fresh rows, so a test can see which ones stay reachable.
        java.util.function.Supplier<List<CdsExportCapturedTextRowDto>> lookupSource;
        long documentBytes = 8L << 20;
        Runnable beforeRow = () -> {};
    }

    private Fixture fixture(
            CdsCategory category,
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> fields,
            List<CdsExportCapturedTextRowDto> nativeRows,
            List<CdsExportNativeRowReceiptDto> witnesses)
            throws Exception {
        var f = new Fixture();
        f.category = category;
        f.nativeRows = List.copyOf(nativeRows);
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, category);
        var command =
                new CdsExportCommand(
                        "assembly",
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
                        "a".repeat(64),
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                "a".repeat(64),
                                "sender",
                                "c".repeat(64),
                                "d".repeat(64),
                                new CdsExportStorageBindingDto(
                                        "primary", "primary", "e".repeat(64))));
        when(f.patient.patient()).thenReturn(27);
        String table = category == MEDICATION ? "drugs" : "allergies";
        var scopes = new LinkedHashMap<String, CdsExportCapturedTableDto>();
        scopes.put(
                table,
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                category == MEDICATION
                                        ? CdsExportPatientTableReadSupport.Plan.DRUGS.columns()
                                        : CdsExportPatientTableReadSupport.Plan.ALLERGIES
                                                .columns()),
                        nativeRows.size(),
                        "c".repeat(64)));
        scopes.put(
                "demographic_merged",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns()),
                        0,
                        "c".repeat(64)));
        scopes.put(
                "partial_date",
                new CdsExportCapturedTableDto(
                        Set.copyOf(
                                CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES
                                        .columns()),
                        0,
                        "c".repeat(64)));
        if (category == MEDICATION) {
            scopes.put(
                    "provider",
                    new CdsExportCapturedTableDto(
                            io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS,
                            0,
                            "c".repeat(64)));
            scopes.put(
                    "drugReason",
                    new CdsExportCapturedTableDto(
                            Set.copyOf(CdsExportPatientTableReadSupport.Plan.DRUG_REASONS.columns()),
                            0,
                            "c".repeat(64)));
        }
        for (var entry : index.tables().entrySet())
            scopes.put(
                    entry.getKey(),
                    new CdsExportCapturedTableDto(
                            Set.of("id"), entry.getValue().size(), "c".repeat(64)));
        f.tables = Set.copyOf(scopes.keySet());
        var capture = mock(CdsExportNativeCaptureDto.class);
        when(capture.tables()).thenReturn(scopes);
        when(capture.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", SOURCE, 1));
        when(capture.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", SOURCE, 1));
        f.source =
                new CdsExportPatientCaptureDto(
                        capture,
                        new CdsExportStagedFileDto("e".repeat(64), "SOURCE_MANIFEST", SOURCE, 1));
        var streams =
                new CdsExportRetainedSectionWriter.Values() {
                    @Override
                    public Reader text(CdsExportRetainedFieldDto field) {
                        return new StringReader(field.logicalInline());
                    }

                    @Override
                    public InputStream binary(CdsExportRetainedFieldDto field) {
                        throw new AssertionError();
                    }
                };
        doAnswer(
                        call -> {
                            ((CdsExportRetainedValuesService.Work) call.getArgument(10))
                                    .use(index, fields, streams);
                            if (f.finalSourceFailure)
                                throw new IOException("FINAL_SOURCE_EOF_REJECTED");
                            return null;
                        })
                .when(f.values)
                .withValues(
                        any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(),
                        anyLong(), any(), any());
        when(f.verification.verify(
                        any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), any()))
                .thenReturn(checked);
        when(f.witnesses.scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var witness : witnesses)
                                ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7))
                                        .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE,
                                    Map.of(table, (long) witnesses.size()),
                                    witnesses.size(),
                                    0,
                                    Set.of());
                        });
        when(f.rows.forEachTable(
                        any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), any(),
                        any()))
                .thenAnswer(
                        call -> {
                            for (var row :
                                    f.lookupSource == null ? f.lookupRows : f.lookupSource.get())
                                ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                        .accept(row);
                            return f.changedGeneration
                                    ? mock(CdsExportNativeCaptureDto.class)
                                    : capture;
                        });
        when(f.rows.forEach(
                        any(),
                        any(),
                        any(),
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
                            for (var row : merge ? f.mergeRows : f.nativeRows) {
                                if (!merge) f.beforeRow.run();
                                ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9))
                                        .accept(row);
                            }
                            if (merge && f.mergeReadFailure)
                                throw new IOException("MERGE_SOURCE_EOF_REJECTED");
                            return capture;
                        });
        return f;
    }

    private byte[] compose(Fixture f, boolean swallow) throws Exception {
        var patient = new LinkedHashMap<String, String>();
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
        var service =
                new CdsExportClinicalOccurrenceService(
                        f.values, f.verification, f.witnesses, f.captures, f.rows);
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        SOURCE,
                        PATIENT_KEY,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        f.lease.document().command().categories(),
                        emitter -> {
                            emitter.emit(
                                    new CdsExportXmlComposer.Section(
                                            new CdsExportIdentityDto(
                                                    PATIENT_KEY,
                                                    CdsCategory.DEMOGRAPHICS,
                                                    "f".repeat(64)),
                                            xml ->
                                                    new CdsExportDemographicXmlWriter()
                                                            .write(demographic, xml)));
                            try {
                                f.result =
                                        service.append(
                                                f.category,
                                                f.lease,
                                                f.patient,
                                                PATIENT_KEY,
                                                f.tables,
                                                f.source,
                                                new CdsExportStagedFileDto(
                                                        "d".repeat(64),
                                                        "RETAINED_MANIFEST",
                                                        SOURCE,
                                                        1),
                                                f.bounds,
                                                f.control,
                                                emitter,
                                                receipt -> {
                                                    if (f.receiptFailure)
                                                        throw new IOException(
                                                                "RECEIPT_WRITE_FAILED");
                                                    f.receipts.add(receipt);
                                                });
                            } catch (IOException | RuntimeException failed) {
                                if (!swallow) throw failed;
                            }
                        },
                        ignored -> {},
                        f.documentBytes,
                        () -> {});
        return output.toByteArray();
    }

    private List<CdsRecordDto> parse(byte[] xml, CdsCategory category, String name)
            throws Exception {
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(xml),
                        "27",
                        Set.of(CdsCategory.DEMOGRAPHICS, category),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var records = new ArrayList<CdsRecordDto>();
        var file = Files.write(directory.resolve(name + ".xml"), xml);
        new CdsXmlParser()
                .parse(
                        file,
                        directory.resolve(name),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records;
    }
}
