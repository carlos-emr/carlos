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
@Tag("unit") @Tag("cds-export")
class CdsExportClinicalOccurrenceServiceUnitTest {
    private static final String SOURCE = "b".repeat(64), PATIENT_KEY = "a".repeat(64);
    private static final CdsCategory ALLERGY = CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS, MEDICATION = CdsCategory.MEDICATIONS_AND_TREATMENTS;
    @TempDir Path directory;
    @Test void shouldAssembleEachNativeOnlyOccurrenceOnce_withItsOwnSerializationAndFieldReceipts() throws Exception {
        var f = nativeOnly(); var records = parse(compose(f, false), ALLERGY, "native");
        var received = new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(received.description()).isEqualTo("Synthetic agent"); assertThat(received.severity()).isEqualTo("MI");
        assertThat(f.receipts).hasSize(1); assertThat(f.receipts.getFirst().importedUnit()).isNull();
        assertThat(f.result.nativeOnlyRows()).isEqualTo(1); assertThat(f.result.importedRows()).isZero();
        assertThat(f.receipts.getFirst().fields()).hasSize(24); verify(f.captures).authorizeSources(f.lease, f.patient, f.tables, f.control);
    }
    @Test void shouldAssembleAnEditedImportedAllergy_andKeepItsOriginalCodeClockAndVendorFields() throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline(); current.put("reaction", "Current reaction");
        var retained = new CdsExportAllergyRetainedAssemblerUnitTest().fixture(current, false, null);
        var nativeSource = new CdsExportAllergyFieldReconcilerUnitTest().fixture(current, true, CdsExportAllergyFieldReconcilerUnitTest.source());
        var f = fixture(ALLERGY, retained.index(), retained.checked(), retained.fields(), List.of(nativeSource.row()), List.of(nativeSource.witness()));
        var records = parse(compose(f, false), ALLERGY, "imported-allergy");
        var received = new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(received.reaction()).isEqualTo("Current reaction"); assertThat(received.severity()).isEqualTo("LT"); assertThat(received.codeSystem()).isEqualTo("DIN");
        assertThat(received.recorded().original()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
        assertThat(new CdsFieldParser().firstToken(records.get(1), "ResidualInfo/DataElement/Content", 1024)).isEqualTo("Vendor café 😀 content");
        assertThat(f.result.retainedSourceFields()).isEqualTo(retained.fields().size()); assertThat(f.result.importedRows()).isEqualTo(1);
    }
    @Test void shouldAssembleAnEditedImportedMedication_andKeepUnprojectedProblemCodeAndStructuredStrength() throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline(); current.put("comment", "Current medication note");
        var retained = CdsExportMedicationRetainedAssemblerUnitTest.fixture(current);
        var nativeSource = CdsExportMedicationFieldReconcilerUnitTest.fixture(current, CdsExportMedicationFieldReconcilerUnitTest.numbers(), true);
        var f = fixture(MEDICATION, retained.index(), retained.checked(), retained.fields(), List.of(nativeSource.row()), List.of(nativeSource.witness()));
        var records = parse(compose(f, false), MEDICATION, "imported-medication");
        var received = new CdsClinicalNormalizationService(new CdsFieldParser()).medication(records.get(1));
        assertThat(received.providerNotes()).isEqualTo("Current medication note"); assertThat(received.strengthAmount()).isEqualTo("5");
        assertThat(received.strengthUnit()).isEqualTo("mg"); assertThat(received.prescriberLastName()).isEqualTo("Physician");
        assertThat(new CdsFieldParser().firstToken(records.get(1), "ProblemCode", 100)).isEqualTo("B01");
        assertThat(f.result.retainedSourceFields()).isEqualTo(retained.fields().size()); assertThat(f.receipts).hasSize(1);
    }
    @Test void shouldRefuseMissingDuplicateOrOrphanRows_insteadOfReturningACompletePatientDocument() throws Exception {
        var missing = nativeOnly(); missing.nativeRows = List.of();
        assertThatThrownBy(() -> compose(missing, false)).hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        var duplicate = nativeOnly(); duplicate.nativeRows = List.of(duplicate.nativeRows.getFirst(), duplicate.nativeRows.getFirst());
        assertThatThrownBy(() -> compose(duplicate, false)).hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
        var orphan = nativeOnly(); orphan.lookupRows = List.of(precision("1", "99"));
        assertThatThrownBy(() -> compose(orphan, false)).hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID");
    }
    @Test void shouldRejectAChangedGeneration_orAnUnselectedPrecisionDomain_beforeEmittingClinicalFacts() throws Exception {
        var changed = nativeOnly(); changed.changedGeneration = true;
        assertThatThrownBy(() -> compose(changed, false)).hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID"); assertThat(changed.receipts).isEmpty();
        var unselected = nativeOnly(); unselected.lookupRows = List.of(precision("2", "51"));
        assertThatThrownBy(() -> compose(unselected, false)).hasMessage("CDS_EXPORT_OCCURRENCE_ASSEMBLY_SCOPE_INVALID"); assertThat(unselected.receipts).isEmpty();
    }
    @Test void shouldKeepTheWholeDocumentFailed_whenFinalAuthorizationOrSourceEofFailureIsCaughtByTheCaller() throws Exception {
        var denied = nativeOnly(); doThrow(new SecurityException("ACCESS_REVOKED")).when(denied.captures).authorizeSources(denied.lease, denied.patient, denied.tables, denied.control);
        assertThatThrownBy(() -> compose(denied, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED"); assertThat(denied.receipts).hasSize(1);
        var eof = nativeOnly(); eof.finalSourceFailure = true;
        assertThatThrownBy(() -> compose(eof, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED"); assertThat(eof.receipts).hasSize(1);
    }
    @Test void shouldKeepTheDocumentFailed_whenAReceiptSinkFailureAfterEmissionIsCaught() throws Exception {
        var f = nativeOnly(); f.receiptFailure = true;
        assertThatThrownBy(() -> compose(f, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED"); assertThat(f.result).isNull();
    }
    @Test void shouldRequireReviewForUnmappedNativeValues_withoutFallingBackToRetainedOrEmptyContent() throws Exception {
        var f = nativeOnly(); var fields = new LinkedHashMap<>(f.nativeRows.getFirst().fields()); fields.put("severity_of_reaction", "3");
        f.nativeRows = List.of(new CdsExportCapturedTextRowDto("allergies", f.nativeRows.getFirst().recordKey(), fields));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA"); assertThat(f.receipts).isEmpty();
    }
    @Test void shouldRequireReviewForImportedDeletion_withoutResurrectingRetainedContent() throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        var retained = new CdsExportAllergyRetainedAssemblerUnitTest().fixture(current, false, null);
        var units = new LinkedHashMap<>(retained.checked().units()); String address = retained.decision().unitAddress();
        var original = units.get(address).getFirst(); units.put(address, List.of(new CdsExportRetainedReceiptVerificationDto.CheckedReference(original.expected(), null,
                CdsExportRetainedReceiptVerificationDto.Reason.ROW_NOT_IN_CAPTURE)));
        var unchanged = new HashSet<>(retained.checked().unchangedUnits()); unchanged.remove(address);
        var checked = new CdsExportRetainedReceiptVerificationDto(SOURCE, units, unchanged, Set.of(address));
        var f = fixture(ALLERGY, retained.index(), checked, retained.fields(), List.of(), List.of());
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_UNMAPPED_DATA"); assertThat(f.receipts).isEmpty();
    }
    @Test void shouldRequireReviewForMultipleImportedOwners_beforeDuplicatingAClinicalOccurrence() throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        var retained = new CdsExportAllergyRetainedAssemblerUnitTest().fixture(current, false, null);
        var nativeSource = new CdsExportAllergyFieldReconcilerUnitTest().fixture(current, true, CdsExportAllergyFieldReconcilerUnitTest.source());
        String key = "e".repeat(64), originalAddress = retained.decision().unitAddress(), run = originalAddress.substring(0, 36), alias = run + ":" + key;
        var tables = new LinkedHashMap<>(retained.index().tables()); var nativeUnits = new LinkedHashMap<>(tables.get("cds_import_unit"));
        var first = nativeUnits.values().iterator().next(); var fields = new LinkedHashMap<>(first.fields()); fields.put("unit_key", key);
        String id = io.github.carlos_emr.carlos.utility.CdsImportKeys.hash(run, key);
        fields.put("id", id); nativeUnits.put(id, new CdsExportCapturedTextRowDto("cds_import_unit", id, fields)); tables.put("cds_import_unit", nativeUnits);
        var applied = new HashSet<>(retained.index().appliedUnits()); applied.add(alias);
        var references = new LinkedHashMap<>(retained.index().references()); references.put(alias, references.get(originalAddress));
        var index = new CdsExportRetainedIndexDto(SOURCE, retained.index().metadataRows() + 1, 32768, tables, applied, references, retained.index().nativeParents());
        var units = new LinkedHashMap<>(retained.checked().units()); units.put(alias, units.get(originalAddress));
        var checked = new CdsExportRetainedReceiptVerificationDto(SOURCE, units, applied, Set.of());
        var f = fixture(ALLERGY, index, checked, retained.fields(), List.of(nativeSource.row()), List.of(nativeSource.witness()));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE"); assertThat(f.receipts).isEmpty(); verifyNoInteractions(f.witnesses);
    }
    @Test void shouldRefuseActiveIncomingOrOutgoingMergesBeforeOpeningRetainedOrClinicalValues() throws Exception {
        for (boolean incoming : List.of(true, false)) {
            var f = nativeOnly(); setMerge(f, merge(incoming ? 28 : 27, incoming ? 27 : 28, "0"));
            assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGED_PATIENT_SCOPE");
            assertThat(f.receipts).isEmpty(); verifyNoInteractions(f.values, f.verification, f.witnesses);
        }
    }
    @Test void shouldNotTreatDeletedMergeHistoryAsAnActiveAlias() throws Exception {
        var f = nativeOnly(); setMerge(f, merge(28, 27, "1")); parse(compose(f, false), ALLERGY, "deleted-merge");
        assertThat(f.receipts).hasSize(1);
    }
    @Test void shouldRejectUnknownMergeStatesInsteadOfAssumingTheyAreDeleted() throws Exception {
        var f = nativeOnly(); setMerge(f, merge(28, 27, "2"));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGED_PATIENT_SCOPE"); assertThat(f.receipts).isEmpty();
    }
    @Test void shouldRefuseAnAbsentFrozenMergeScopeEvenForAnOtherwiseValidNativeOccurrence() throws Exception {
        var f = nativeOnly(); f.source.capture().tables().remove("demographic_merged"); f.tables = Set.copyOf(f.source.capture().tables().keySet());
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGE_SCOPE_INVALID"); verifyNoInteractions(f.values, f.verification, f.witnesses);
    }
    @Test void shouldRejectForeignMergeRowsInsteadOfExpandingDisclosureScope() throws Exception {
        var f = nativeOnly(); setMerge(f, merge(28, 29, "0"));
        assertThatThrownBy(() -> compose(f, false)).hasMessage("CDS_EXPORT_MERGE_SCOPE_INVALID"); assertThat(f.receipts).isEmpty();
    }
    @Test void shouldKeepTheDocumentFailedWhenAMergeReviewIsCaughtByTheCaller() throws Exception {
        var f = nativeOnly(); setMerge(f, merge(28, 27, "0"));
        assertThatThrownBy(() -> compose(f, true)).hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED"); assertThat(f.receipts).isEmpty();
    }
    @Test void shouldFinishMergeAuthenticationBeforeReturningEvenAnAdverseReview() throws Exception {
        var f = nativeOnly(); setMerge(f, merge(28, 27, "0")); f.mergeReadFailure = true;
        assertThatThrownBy(() -> compose(f, false)).hasMessage("MERGE_SOURCE_EOF_REJECTED"); assertThat(f.receipts).isEmpty();
    }
    private static CdsExportCapturedTextRowDto merge(int from, int to, String deleted) {
        var fields = new LinkedHashMap<String, String>(); CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns().forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("id", "11", "demographic_no", Integer.toString(from), "merged_to", Integer.toString(to), "deleted", deleted));
        return new CdsExportCapturedTextRowDto("demographic_merged", CdsExportPatientTableSourceReader.key(11), fields);
    }
    private static void setMerge(Fixture f, CdsExportCapturedTextRowDto row) {
        f.mergeRows = List.of(row); var previous = f.source.capture().tables().get("demographic_merged");
        f.source.capture().tables().put("demographic_merged", new CdsExportCapturedTableDto(previous.columns(), 1, previous.schemaSha256()));
    }
    @Test void equalFieldCountsCannotHideAnInventedAssessmentInsteadOfTheCapturedDescription()throws Exception {
        var f=nativeOnly(); var original=new CdsExportAllergyMapper().map(27, f.nativeRows.getFirst(), List.of()); var fields=new LinkedHashMap<>(original.fields());
        var key=new CdsExportNativeFieldKeyDto("allergies", f.nativeRows.getFirst().recordKey(), "DESCRIPTION"); fields.put(new CdsExportNativeFieldKeyDto("allergies", key.recordKey(), "invented"), fields.remove(key));
        var forged=new CdsExportAllergyMappingDto(original.value(), fields);
        try (var construction=mockConstruction(CdsExportAllergyMapper.class, (mock, context)-> when(mock.map(anyInt(), any(), anyList())).thenReturn(forged))) {
            assertThatThrownBy(()-> compose(f, false)).hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID"); assertThat(f.receipts).isEmpty();
        }
    }
    private Fixture nativeOnly() throws Exception {
        var fields = new LinkedHashMap<String, String>(); CdsExportPatientTableReadSupport.Plan.ALLERGIES.columns().forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("allergyid", "51", "demographic_no", "27", "DESCRIPTION", "Synthetic agent", "TYPECODE", "0", "severity_of_reaction", "1", "archived", "0", "position", "0"));
        var row = new CdsExportCapturedTextRowDto("allergies", CdsExportPatientTableSourceReader.key(51), fields);
        var index = new CdsExportRetainedIndexDto(SOURCE, 0, 0, Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()), Set.of(), Map.of(), Map.of());
        var checked = new CdsExportRetainedReceiptVerificationDto(SOURCE, Map.of(), Set.of(), Set.of());
        return fixture(ALLERGY, index, checked, List.of(), List.of(row), List.of(new CdsExportNativeRowReceiptDto("allergies", "51", row.recordKey(), "c".repeat(64))));
    }
    private static CdsExportCapturedTextRowDto precision(String owner, String parent) {
        return new CdsExportCapturedTextRowDto("partial_date", CdsExportPatientTableSourceReader.key(11), Map.of("id", "11", "table_name", owner, "table_id", parent, "field_name", "1", "format", "YYYY"));
    }
    private static final class Fixture {
        final CdsExportRetainedValuesService values = mock(CdsExportRetainedValuesService.class);
        final CdsExportRetainedReceiptVerificationService verification = mock(CdsExportRetainedReceiptVerificationService.class);
        final CdsExportNativeRowReceiptReader witnesses = mock(CdsExportNativeRowReceiptReader.class);
        final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
        final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
        final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
        final CdsExportRunControl control = mock(CdsExportRunControl.class);
        final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
        CdsExportLeaseDto lease; CdsExportPatientCaptureDto source; Set<String> tables; CdsCategory category;
        List<CdsExportCapturedTextRowDto> nativeRows, lookupRows = List.of(), mergeRows = List.of(); boolean changedGeneration, finalSourceFailure, receiptFailure, mergeReadFailure;
        CdsExportClinicalOccurrenceService.Result result;
    }
    private Fixture fixture(CdsCategory category, CdsExportRetainedIndexDto index, CdsExportRetainedReceiptVerificationDto checked,
            List<CdsExportRetainedFieldDto> fields, List<CdsExportCapturedTextRowDto> nativeRows, List<CdsExportNativeRowReceiptDto> witnesses) throws Exception {
        var f = new Fixture(); f.category = category; f.nativeRows = List.copyOf(nativeRows);
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, category); var command = new CdsExportCommand("assembly", "999998", null, CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27), selected, "receiver", "audit");
        f.lease = new CdsExportLeaseDto(UUID.randomUUID().toString(), 1, "a".repeat(64), new CdsExportJobDocumentDto(2, new CdsExportActorDto("agent", "999998", null, null), command,
                "a".repeat(64), "sender", "c".repeat(64), "d".repeat(64), new CdsExportStorageBindingDto("primary", "primary", "e".repeat(64))));
        when(f.patient.patient()).thenReturn(27);
        String table = category == MEDICATION ? "drugs" : "allergies";
        var scopes = new LinkedHashMap<String, CdsExportCapturedTableDto>();
        scopes.put(table, new CdsExportCapturedTableDto(Set.copyOf(category == MEDICATION ? CdsExportPatientTableReadSupport.Plan.DRUGS.columns() : CdsExportPatientTableReadSupport.Plan.ALLERGIES.columns()), nativeRows.size(), "c".repeat(64)));
        scopes.put("demographic_merged", new CdsExportCapturedTableDto(Set.copyOf(CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_MERGES.columns()), 0, "c".repeat(64)));
        scopes.put("partial_date", new CdsExportCapturedTableDto(Set.copyOf(CdsExportPatientTableReadSupport.Plan.PARTIAL_DATE_ALLERGIES.columns()), 0, "c".repeat(64)));
        if (category == MEDICATION) scopes.put("provider", new CdsExportCapturedTableDto(io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto.COLUMNS, 0, "c".repeat(64)));
        for (var entry : index.tables().entrySet()) scopes.put(entry.getKey(), new CdsExportCapturedTableDto(Set.of("id"), entry.getValue().size(), "c".repeat(64)));
        f.tables = Set.copyOf(scopes.keySet()); var capture = mock(CdsExportNativeCaptureDto.class); when(capture.tables()).thenReturn(scopes);
        when(capture.values()).thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", SOURCE, 1));
        when(capture.index()).thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", SOURCE, 1));
        f.source = new CdsExportPatientCaptureDto(capture, new CdsExportStagedFileDto("e".repeat(64), "SOURCE_MANIFEST", SOURCE, 1));
        var streams = new CdsExportRetainedSectionWriter.Values() {
            @Override public Reader text(CdsExportRetainedFieldDto field) { return new StringReader(field.logicalInline()); }
            @Override public InputStream binary(CdsExportRetainedFieldDto field) { throw new AssertionError(); }
        };
        doAnswer(call -> { ((CdsExportRetainedValuesService.Work) call.getArgument(10)).use(index, fields, streams);
            if (f.finalSourceFailure) throw new IOException("FINAL_SOURCE_EOF_REJECTED"); return null;
        }).when(f.values).withValues(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), anyLong(), any(), any());
        when(f.verification.verify(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), any())).thenReturn(checked);
        when(f.witnesses.scan(any(), any(), any(), any(), anyLong(), anyLong(), any(), any())).thenAnswer(call -> {
            for (var witness : witnesses) ((CdsExportNativeRowReceiptReader.Receipts) call.getArgument(7)).accept(witness);
            return new CdsExportNativeRowReceiptsDto(SOURCE, Map.of(table, (long) witnesses.size()), witnesses.size(), 0, Set.of());
        });
        when(f.rows.forEachTable(any(), any(), any(), any(), any(), anyLong(), anyLong(), anyInt(), any(), any())).thenAnswer(call -> {
            for (var row : f.lookupRows) ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9)).accept(row);
            return f.changedGeneration ? mock(CdsExportNativeCaptureDto.class) : capture;
        });
        when(f.rows.forEach(any(), any(), any(), anyString(), any(), anyLong(), anyLong(), anyInt(), any(), any())).thenAnswer(call -> {
            boolean merge = "demographic_merged".equals(call.getArgument(3));
            for (var row : merge ? f.mergeRows : f.nativeRows) ((CdsExportCapturedTextRowsReader.Rows) call.getArgument(9)).accept(row);
            if (merge && f.mergeReadFailure) throw new IOException("MERGE_SOURCE_EOF_REJECTED"); return capture;
        }); return f;
    }
    private byte[] compose(Fixture f, boolean swallow) throws Exception {
        var patient = new LinkedHashMap<String, String>(); CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(Map.of("first_name", "Synthetic", "last_name", "Fixture", "year_of_birth", "1980", "month_of_birth", "01", "date_of_birth", "01", "patient_status", "AC", "sex", "U"));
        var demographic = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var service = new CdsExportClinicalOccurrenceService(f.values, f.verification, f.witnesses, f.captures, f.rows); var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer().composeFromSource(output, SOURCE, PATIENT_KEY, "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, f.lease.document().command().categories(), emitter -> {
            emitter.emit(new CdsExportXmlComposer.Section(new CdsExportIdentityDto(PATIENT_KEY, CdsCategory.DEMOGRAPHICS, "f".repeat(64)), xml -> new CdsExportDemographicXmlWriter().write(demographic, xml)));
            try { f.result = service.append(f.category, f.lease, f.patient, PATIENT_KEY, f.tables, f.source, new CdsExportStagedFileDto("d".repeat(64), "RETAINED_MANIFEST", SOURCE, 1),
                    new CdsExportClinicalOccurrenceService.Bounds(100000, 8L << 20, 1000, 8L << 20, 100, 1000), f.control, emitter, receipt -> {
                        if (f.receiptFailure) throw new IOException("RECEIPT_WRITE_FAILED"); f.receipts.add(receipt);
                    });
            } catch (IOException | RuntimeException failed) { if (!swallow) throw failed; }
        }, ignored -> {}, 8L << 20, () -> {}); return output.toByteArray();
    }
    private List<CdsRecordDto> parse(byte[] xml, CdsCategory category, String name) throws Exception {
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(xml), "27", Set.of(CdsCategory.DEMOGRAPHICS, category), "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> {}, () -> {});
        var records = new ArrayList<CdsRecordDto>(); var file = Files.write(directory.resolve(name + ".xml"), xml);
        new CdsXmlParser().parse(file, directory.resolve(name), CdsInputLimitsDto.defaults(), () -> false, records::add); return records;
    }
}
