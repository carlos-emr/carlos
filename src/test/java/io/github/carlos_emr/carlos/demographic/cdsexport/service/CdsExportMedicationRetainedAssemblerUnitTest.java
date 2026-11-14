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
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportMedicationRetainedAssemblerUnitTest {
    private static final String RUN = CdsExportMedicationFieldReconcilerUnitTest.RUN,
            UNIT = CdsExportMedicationFieldReconcilerUnitTest.UNIT,
            ADDRESS = CdsExportMedicationFieldReconcilerUnitTest.ADDRESS,
            FIELD_UNIT = "f".repeat(64),
            FIELD_ADDRESS = RUN + ":" + FIELD_UNIT;
    @TempDir Path directory;

    record Fixture(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportMedicationFieldReconciler.Result decision,
            List<CdsExportRetainedFieldDto> fields) {}

    @Test
    void
            shouldPreserveUnprojectedProblemCodeAndVendorData_alongsideCurrentNotesAndVerifiedSourceStrength()
                    throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline();
        current.put("comment", "Current café 😀 notes");
        var fixture = fixture(current);
        var plan = prepare(fixture);
        var records = parse(plan.content(), "reconciled");
        var parser = new CdsFieldParser();
        var medication = new CdsClinicalNormalizationService(parser).medication(records.get(1));
        assertThat(medication.providerNotes()).isEqualTo("Current café 😀 notes");
        assertThat(medication.din()).isEqualTo("00123456");
        assertThat(medication.strengthAmount()).isEqualTo("5");
        assertThat(medication.strengthUnit()).isEqualTo("mg");
        assertThat(medication.written().original()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
        assertThat(parser.firstToken(records.get(1), "ProblemCode", 100)).isEqualTo("B01");
        assertThat(parser.firstToken(records.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
        var retained =
                fixture.fields().stream()
                        .filter(
                                field ->
                                        field.sourcePath().contains("/ResidualInfo[")
                                                || field.sourcePath().contains("/ProblemCode["))
                        .map(CdsExportRetainedFieldDto::id)
                        .toList();
        assertThat(plan.retainedFields()).containsAll(retained);
        var all = new HashSet<>(plan.retainedFields());
        assertThat(Collections.disjoint(all, plan.replacedFields())).isTrue();
        all.addAll(plan.replacedFields());
        assertThat(all)
                .isEqualTo(
                        fixture.fields().stream()
                                .map(CdsExportRetainedFieldDto::id)
                                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldPreserveUnchangedIntegerLexicalValues_whileReplacingOnlyCurrentNotes()
            throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline();
        current.put("comment", "Current provider notes");
        var f = fixture(current);
        var plan = prepare(f);
        var record = parse(plan.content(), "lexical").get(1);
        var parser = new CdsFieldParser();
        assertThat(parser.firstToken(record, "NumberOfRefills", 100)).isEqualTo("02");
        assertThat(parser.firstToken(record, "RefillQuantity", 100)).isEqualTo("012");
        assertThat(parser.firstToken(record, "Notes", 100)).isEqualTo("Current provider notes");
        assertThat(plan.replacedFields())
                .containsExactly(
                        f.fields().stream()
                                .filter(field -> field.sourcePath().endsWith("/Notes[1]"))
                                .map(CdsExportRetainedFieldDto::id)
                                .findFirst()
                                .orElseThrow());
    }

    @Test
    void shouldStreamTheCompleteSourceOnlyStrength_afterVerifyingItsUnchangedNativePlaceholder()
            throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.sourceOnlyStrengthBaseline();
        current.put("comment", "Current provider notes");
        var f = fixture(current, directory.resolve("complete-strength.txt"));
        var plan = prepare(f);
        var record = parse(plan.content(), "large-strength").get(1);
        assertThat(new CdsFieldParser().firstToken(record, "Strength/UnitOfMeasure", 50000))
                .isEqualTo("µg".repeat(20000));
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .medication(record)
                                .strengthSourceOnly())
                .isTrue();
        assertThat(plan.retainedFields())
                .containsAll(
                        f.fields().stream()
                                .filter(field -> field.sourcePath().contains("/Strength["))
                                .map(CdsExportRetainedFieldDto::id)
                                .toList());
    }

    @Test
    void shouldRefuseAMissingRetainedStrengthCarrier_evenWhenItsPlaceholderAndRemainingPagesVerify()
            throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.sourceOnlyStrengthBaseline();
        var f = fixture(current, directory.resolve("missing-strength.txt"), true);
        assertThat(f.decision().mapping().nativeRowRepresentable()).isTrue();
        assertThatThrownBy(() -> prepare(f))
                .hasMessage("CDS_EXPORT_MEDICATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldKeepAnExplicitNativeNoteClear_withoutDroppingUnprojectedStandardFields()
            throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline();
        current.put("comment", null);
        var records = parse(prepare(fixture(current)).content(), "clear");
        var parser = new CdsFieldParser();
        assertThat(
                        new CdsClinicalNormalizationService(parser)
                                .medication(records.get(1))
                                .providerNotes())
                .isNull();
        assertThat(parser.firstToken(records.get(1), "ProblemCode", 100)).isEqualTo("B01");
    }

    @Test
    void shouldRefuseMissingOrSubstitutedSourceDescriptors_beforeProducingAContentCallback()
            throws Exception {
        var f = fixture(CdsExportMedicationFieldReconcilerUnitTest.baseline());
        var absent = new ArrayList<>(f.fields());
        absent.removeLast();
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision(),
                                                f.index(),
                                                f.checked(),
                                                absent,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
        var first = f.fields().getFirst();
        var substituted =
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
                        "substituted",
                        first.originalPayload());
        var changed = new ArrayList<>(f.fields());
        changed.set(0, substituted);
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision(),
                                                f.index(),
                                                f.checked(),
                                                changed,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldRequireUnchangedAppliedSourceFieldUnits_beforeReusingProblemCodeOrVendorData()
            throws Exception {
        var f = fixture(CdsExportMedicationFieldReconcilerUnitTest.baseline());
        var reference = f.checked().units().get(FIELD_ADDRESS).getFirst();
        var units = new LinkedHashMap<>(f.checked().units());
        units.put(
                FIELD_ADDRESS,
                List.of(
                        new CheckedReference(
                                reference.expected(), "e".repeat(64), Reason.CHANGED_ROW)));
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        f.checked().sourceManifestSha256(),
                        units,
                        Set.of(ADDRESS),
                        Set.of(FIELD_ADDRESS));
        assertThatThrownBy(
                        () ->
                                new CdsExportMedicationRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision(),
                                                f.index(),
                                                checked,
                                                f.fields(),
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_MEDICATION_SOURCE_FIELD_EVIDENCE_CHANGED");
    }

    @Test
    void shouldKeepTheOriginalProblemCode_andAddReasonsRecordedInCarlosBesideTheVendorResidual()
            throws Exception {
        // P3: the importer writes no drugReason, so a reason on an imported medication was added
        // in CARLOS. The sender's ProblemCode stays the only ProblemCode; the reason is added.
        var f = fixture(CdsExportMedicationFieldReconcilerUnitTest.baseline());
        var reasons = reasons();
        var assembly =
                new CdsExportMedicationRetainedAssembler()
                        .prepare(
                                27,
                                f.decision(),
                                reasons,
                                f.index(),
                                f.checked(),
                                f.fields(),
                                inline(),
                                () -> {});
        assertThat(assembly.problemCodeWritten()).isFalse();
        var record = parse(assembly.prepared().content(), "kept-problem-code").get(1);
        var parser = new CdsFieldParser();
        assertThat(parser.firstToken(record, "ProblemCode", 100)).isEqualTo("B01");
        var residualNames = new ArrayList<String>();
        var problemCodes = new ArrayList<String>();
        parser.forEachField(
                record,
                field -> {
                    if (field.path().startsWith("ResidualInfo[1]/DataElement[")
                            && field.path().endsWith("/Name[1]")) residualNames.add(field.path());
                    if (field.path().startsWith("ProblemCode[")) problemCodes.add(field.path());
                });
        assertThat(residualNames)
                .containsExactly(
                        "ResidualInfo[1]/DataElement[1]/Name[1]",
                        "ResidualInfo[1]/DataElement[2]/Name[1]");
        assertThat(problemCodes).containsExactly("ProblemCode[1]");
        assertThat(
                        Files.readString(
                                directory.resolve("kept-problem-code.xml"),
                                java.nio.charset.StandardCharsets.UTF_8))
                .contains("Vendor.custom", "CARLOS.Medication.Reasons.v1", "\"code\":\"250\"");
        // Every source field stays as it was; the reason was added, nothing was replaced.
        assertThat(assembly.prepared().replacedFields()).isEmpty();
        assertThat(assembly.prepared().retainedFields())
                .containsExactlyInAnyOrderElementsOf(
                        f.fields().stream().map(CdsExportRetainedFieldDto::id).toList());
        assertThat(reasons.fields(assembly.problemCodeWritten()).values())
                .noneMatch(field -> field.destination().equals("ProblemCode"));
    }

    @Test
    void shouldAddTheIndicationAsProblemCode_whenTheImportedMedicationHasNone() throws Exception {
        var f = fixture(CdsExportMedicationFieldReconcilerUnitTest.baseline(), null, false, false);
        var reasons = reasons();
        var assembly =
                new CdsExportMedicationRetainedAssembler()
                        .prepare(
                                27,
                                f.decision(),
                                reasons,
                                f.index(),
                                f.checked(),
                                f.fields(),
                                inline(),
                                () -> {});
        assertThat(assembly.problemCodeWritten()).isTrue();
        var record = parse(assembly.prepared().content(), "added-problem-code").get(1);
        var parser = new CdsFieldParser();
        assertThat(parser.firstToken(record, "ProblemCode", 100)).isEqualTo("250");
        assertThat(parser.firstToken(record, "ResidualInfo/DataElement/Name", 100))
                .isEqualTo("CARLOS.Medication.Reasons.v1");
        assertThat(assembly.prepared().replacedFields()).isEmpty();
        assertThat(reasons.fields(true).values())
                .anyMatch(field -> field.destination().equals("ProblemCode"));
        // Without reasons the occurrence is assembled exactly as before.
        var plain = prepare(f);
        assertThat(parser.firstToken(parse(plain.content(), "plain").get(1), "ProblemCode", 100))
                .isNull();
    }

    @Test
    void shouldAddTheArchiveStateAndCurrentRow_whenAnImportedPrescriptionIsDeletedInCarlos()
            throws Exception {
        var current = CdsExportMedicationFieldReconcilerUnitTest.baseline();
        current.put("archived", "1");
        current.put("archived_reason", "deleted");
        current.put("archived_date", "2026-01-02 03:04:05");
        var f = fixture(current);
        var plan = prepare(f);
        var record = parse(plan.content(), "archived").get(1);
        var parser = new CdsFieldParser();
        // The vendor's own residual is untouched and both CARLOS carriers follow it.
        var names = new ArrayList<String>();
        var contents = new ArrayList<String>();
        parser.forEachField(
                record,
                field -> {
                    try {
                        if (CdsFieldParser.unindexed(field.path())
                                .equals("ResidualInfo/DataElement/Name"))
                            names.add(parser.readToken(record, field, 128));
                        if (CdsFieldParser.unindexed(field.path())
                                .equals("ResidualInfo/DataElement/Content"))
                            contents.add(parser.readToken(record, field, 1 << 20));
                    } catch (java.io.IOException invalid) {
                        throw new java.io.UncheckedIOException(invalid);
                    }
                });
        assertThat(names)
                .hasSize(3)
                .endsWith(
                        CdsNativeExtensionContract.MEDICATION_ARCHIVED,
                        CdsNativeExtensionContract.MEDICATION_CURRENT_SOURCE);
        assertThat(contents.get(0)).isEqualTo("Vendor café 😀 content");
        assertThat(contents.get(1)).isEqualTo("true");
        var row =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(contents.get(2))
                        .get("fields");
        assertThat(row.get("archived_reason").asText()).isEqualTo("deleted");
        assertThat(row.get("archived_date").asText()).isEqualTo("2026-01-02 03:04:05");
        assertThat(row.get("special").asText())
                .isEqualTo("Original café drug 5 mg\n 0.5-1 tablet PO BID");
        // A receiving CARLOS reads a prescription that is no longer current.
        var medication = new CdsClinicalNormalizationService(parser).medication(record);
        assertThat(medication.prescriptionStatus()).isEqualTo("Nullified");
        assertThat(medication.past()).isTrue();
        assertThat(medication.din()).isEqualTo("00123456");
        assertThat(parser.firstToken(record, "ProblemCode", 100)).isEqualTo("B01");
        // Only the status elements were replaced; every other source field is retained.
        assertThat(plan.replacedFields())
                .containsExactlyInAnyOrderElementsOf(
                        f.fields().stream()
                                .filter(
                                        field ->
                                                field.sourcePath().contains("/PrescriptionStatus[")
                                                        || field.sourcePath()
                                                                .contains("/PastMedications["))
                                .map(CdsExportRetainedFieldDto::id)
                                .toList());
    }

    private static CdsExportMedicationReasonMapper.Result reasons() throws Exception {
        var drug =
                CdsExportMedicationMapperUnitTest.medication(
                        CdsExportMedicationFieldReconcilerUnitTest.baseline());
        return new CdsExportMedicationReasonMapper()
                .map(
                        27,
                        drug,
                        List.of(
                                CdsExportMedicationReasonMapperUnitTest.row(
                                        CdsExportMedicationReasonMapperUnitTest.reason(
                                                5, "icd9", "250", "1"))));
    }

    private static CdsExportRetainedSectionWriter.Prepared prepare(Fixture f) throws Exception {
        return new CdsExportMedicationRetainedAssembler()
                .prepare(27, f.decision(), f.index(), f.checked(), f.fields(), inline(), () -> {});
    }

    static Fixture fixture(Map<String, String> current) throws Exception {
        return fixture(current, null);
    }

    static Fixture fixture(Map<String, String> current, Path strengthPayload) throws Exception {
        return fixture(current, strengthPayload, false);
    }

    static Fixture fixture(Map<String, String> current, Path strengthPayload, boolean omitStrength)
            throws Exception {
        return fixture(current, strengthPayload, omitStrength, true);
    }

    /** {@code sourceExtras} false: the sender wrote no ProblemCode and no residual data. */
    static Fixture fixture(
            Map<String, String> current,
            Path strengthPayload,
            boolean omitStrength,
            boolean sourceExtras)
            throws Exception {
        boolean sourceOnlyStrength = strengthPayload != null;
        var base =
                CdsExportMedicationFieldReconcilerUnitTest.fixture(
                        current,
                        CdsExportMedicationFieldReconcilerUnitTest.numbers(),
                        true,
                        sourceOnlyStrength
                                ? CdsExportMedicationFieldReconcilerUnitTest.sourceOnlyStrength()
                                : CdsExportMedicationFieldReconcilerUnitTest.source(),
                        sourceOnlyStrength
                                ? CdsExportMedicationFieldReconcilerUnitTest
                                        .sourceOnlyStrengthBaseline()
                                : CdsExportMedicationFieldReconcilerUnitTest.baseline());
        var decision = CdsExportMedicationFieldReconcilerUnitTest.reconcile(base);
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field("DrugName[1]", "cds", "Original café drug"));
        fields.add(field("DrugIdentificationNumber[1]", "cds", "00123456"));
        if (!omitStrength) {
            fields.add(field("Strength[1]/Amount[1]", "cds_dt", "5"));
            fields.add(
                    sourceOnlyStrength
                            ? payloadField(
                                    "Strength[1]/UnitOfMeasure[1]",
                                    "cds_dt",
                                    "µg".repeat(20000),
                                    strengthPayload)
                            : field("Strength[1]/UnitOfMeasure[1]", "cds_dt", "mg"));
        }
        fields.add(field("NumberOfRefills[1]", "cds", "02"));
        fields.add(field("RefillQuantity[1]", "cds", "012"));
        fields.add(field("Dosage[1]", "cds", "0.5-1"));
        fields.add(field("Notes[1]", "cds", "Original provider notes"));
        fields.add(
                field(
                        "PrescriptionWrittenDate[1]/FullDateTime[1]",
                        "cds_dt",
                        "2024-02-29T12:34:56.1200-05:00"));
        fields.add(field("DrugDescription[1]", "cds", "Source description"));
        fields.add(field("PrescriptionIdentifier[1]", "cds", "rx-source"));
        if (sourceExtras) {
            fields.add(field("ProblemCode[1]", "cds", "B01"));
            fields.add(
                    field("ResidualInfo[1]/DataElement[1]/Name[1]", "cds_dt", "Vendor.custom"));
            fields.add(field("ResidualInfo[1]/DataElement[1]/DataType[1]", "cds_dt", "string"));
            fields.add(
                    field(
                            "ResidualInfo[1]/DataElement[1]/Content[1]",
                            "cds_dt",
                            "Vendor café 😀 content"));
        }
        var metadata = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        for (var field : fields) metadata.put(field.id(), metadata(field));
        var tables = new LinkedHashMap<>(base.index().tables());
        tables.put("cds_import_field", metadata);
        var sourceRefs =
                fields.stream()
                        .map(
                                field ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                "cds_import_field", field.id(), "c".repeat(64)))
                        .toList();
        var references = new LinkedHashMap<>(base.index().references());
        references.put(FIELD_ADDRESS, sourceRefs);
        var index =
                new CdsExportRetainedIndexDto(
                        base.index().sourceManifestSha256(),
                        base.index().metadataRows() + fields.size(),
                        16384,
                        tables,
                        Set.of(ADDRESS, FIELD_ADDRESS),
                        references,
                        fields.stream()
                                .collect(
                                        java.util.stream.Collectors.toMap(
                                                CdsExportRetainedFieldDto::id, field -> ADDRESS)));
        var units = new LinkedHashMap<>(base.checked().units());
        units.put(
                FIELD_ADDRESS,
                sourceRefs.stream()
                        .map(ref -> new CheckedReference(ref, ref.digest(), Reason.UNCHANGED_ROW))
                        .toList());
        var unchanged = new HashSet<>(base.checked().unchangedUnits());
        unchanged.add(FIELD_ADDRESS);
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        index.sourceManifestSha256(),
                        units,
                        unchanged,
                        base.checked().reviewUnits());
        return new Fixture(index, checked, decision, fields);
    }

    private static CdsExportRetainedFieldDto field(
            String relative, String namespace, String value) {
        var type = CdsWireSchema.schemaModel().element("cds", "MedicationsAndTreatments");
        for (String part : relative.split("/")) {
            String name = part.substring(0, part.indexOf('['));
            var child =
                    type.childNames().stream()
                            .filter(n -> n.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            type = type.child(child.namespace(), child.local());
        }
        String path = "/OmdCds[1]/PatientRecord[1]/MedicationsAndTreatments[1]/" + relative;
        return new CdsExportRetainedFieldDto(
                CdsImportKeys.hash(FIELD_UNIT, namespace, path, "1"),
                RUN,
                "d".repeat(64),
                FIELD_UNIT,
                CdsCategory.MEDICATIONS_AND_TREATMENTS,
                path,
                1,
                namespace,
                "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                null,
                value,
                value,
                null);
    }

    private static CdsExportRetainedFieldDto payloadField(
            String relative, String namespace, String value, Path path) throws Exception {
        var descriptor = field(relative, namespace, "");
        Files.writeString(path, value, java.nio.charset.StandardCharsets.UTF_8);
        byte[] bytes = Files.readAllBytes(path);
        String digest =
                HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        return new CdsExportRetainedFieldDto(
                descriptor.id(),
                descriptor.runId(),
                descriptor.sourcePatientKey(),
                descriptor.fieldUnitKey(),
                descriptor.category(),
                descriptor.sourcePath(),
                descriptor.occurrence(),
                descriptor.namespace(),
                descriptor.valueType(),
                descriptor.precision(),
                null,
                null,
                new CdsExportRetainedFieldDto.Payload(
                        path.toAbsolutePath().normalize().toString(), digest, 0, bytes.length));
    }

    private static CdsExportCapturedTextRowDto metadata(CdsExportRetainedFieldDto f) {
        var values = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS
                .columns()
                .forEach(c -> values.put(c, null));
        values.putAll(
                Map.of(
                        "id",
                        f.id(),
                        "run_id",
                        f.runId(),
                        "patient_key",
                        f.sourcePatientKey(),
                        "unit_key",
                        f.fieldUnitKey(),
                        "demographic_no",
                        "27",
                        "category",
                        f.category().wireName(),
                        "source_path",
                        f.sourcePath(),
                        "occurrence",
                        "1",
                        "source_namespace",
                        f.namespace(),
                        "value_type",
                        f.valueType()));
        values.put("original_value", f.originalInline());
        values.put("normalized_value", f.logicalInline());
        values.put("payload_offset", "0");
        values.put("payload_length", "0");
        if (f.originalPayload() != null) {
            values.put("payload_location", f.originalPayload().location());
            values.put("payload_digest", f.originalPayload().sha256());
            values.put("payload_offset", Long.toString(f.originalPayload().offset()));
            values.put("payload_length", Long.toString(f.originalPayload().bytes()));
        }
        return new CdsExportCapturedTextRowDto("cds_import_field", f.id(), values);
    }

    private static CdsExportRetainedSectionWriter.Values inline() {
        return new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto f) throws IOException {
                return f.originalPayload() == null
                        ? new StringReader(f.logicalInline())
                        : Files.newBufferedReader(
                                Path.of(f.originalPayload().location()),
                                java.nio.charset.StandardCharsets.UTF_8);
            }

            public InputStream binary(CdsExportRetainedFieldDto f) {
                throw new AssertionError();
            }
        };
    }

    private List<CdsRecordDto> parse(CdsExportXmlComposer.Content content, String name)
            throws Exception {
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(c -> patient.put(c, null));
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
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.MEDICATIONS_AND_TREATMENTS);
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                                xml ->
                                        new CdsExportDemographicXmlWriter()
                                                .write(demographics, xml)),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64),
                                        CdsCategory.MEDICATIONS_AND_TREATMENTS,
                                        "c".repeat(64)),
                                content));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
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
        var file = Files.write(directory.resolve(name + ".xml"), output.toByteArray());
        var records = new ArrayList<CdsRecordDto>();
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
