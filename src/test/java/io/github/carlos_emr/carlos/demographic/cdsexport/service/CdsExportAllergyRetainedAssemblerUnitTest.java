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
class CdsExportAllergyRetainedAssemblerUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            UNIT = "a".repeat(64),
            ADDRESS = RUN + ":" + UNIT,
            FIELD_UNIT = "f".repeat(64),
            FIELD_ADDRESS = RUN + ":" + FIELD_UNIT;
    @TempDir Path directory;

    record Fixture(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportAllergyFieldReconciler.Result decision,
            List<CdsExportRetainedFieldDto> fields) {}

    @Test
    void
            shouldEmitTheReconciledReactionCodeAndClock_andPreserveUnknownExtensionsAndTheirFieldIdentities()
                    throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        current.put("reaction", "Current café 😀");
        var f = fixture(current, true, "false");
        var plan = prepare(f);
        var received = parse(plan.content(), "reconciled");
        var parser = new CdsFieldParser();
        var allergy = new CdsClinicalNormalizationService(parser).allergy(received.get(1));
        assertThat(allergy.reaction()).isEqualTo("Current café 😀");
        assertThat(allergy.severity()).isEqualTo("LT");
        assertThat(allergy.codeSystem()).isEqualTo("DIN");
        assertThat(allergy.recorded().original()).isEqualTo("2024-02-29T12:34:56.1200-05:00");
        assertThat(allergy.notes()).isEqualTo("Source-only provider note");
        assertThat(parser.firstToken(received.get(1), "ResidualInfo/DataElement/Name", 1024))
                .isEqualTo("Vendor." + "x".repeat(512));
        assertThat(parser.firstToken(received.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
        var unknown =
                f.fields.stream()
                        .filter(field -> field.sourcePath().contains("/DataElement[1]/"))
                        .map(CdsExportRetainedFieldDto::id)
                        .toList();
        assertThat(plan.retainedFields()).containsAll(unknown);
        assertThat(Collections.disjoint(plan.retainedFields(), plan.replacedFields())).isTrue();
        var all = new HashSet<>(plan.retainedFields());
        all.addAll(plan.replacedFields());
        assertThat(all)
                .isEqualTo(
                        f.fields.stream()
                                .map(CdsExportRetainedFieldDto::id)
                                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void shouldAppendNewTypedMarkersWithoutReplacingVendorData_andKeepAnExplicitNativeClearCleared()
            throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        current.put("reaction", null);
        current.put("age_of_onset", "0012");
        var f = fixture(current, false, null);
        var records = parse(prepare(f).content(), "added-markers");
        var allergy =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(allergy.reaction()).isNull();
        assertThat(allergy.ageOfOnset()).isEqualTo("0012");
        assertThat(allergy.archived()).isFalse();
        assertThat(
                        new CdsFieldParser()
                                .firstToken(
                                        records.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
    }

    @Test
    void
            shouldUpdateOnlyTheKnownArchiveMarkerContent_andRetainItsOriginalNameTypeAndNeighboringVendorFields()
                    throws Exception {
        var current = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        current.put("archived", "1");
        var f = fixture(current, true, "false");
        var plan = prepare(f);
        var records = parse(plan.content(), "archive-update");
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .allergy(records.get(1))
                                .archived())
                .isTrue();
        var markerName =
                f.fields.stream()
                        .filter(field -> field.sourcePath().endsWith("/DataElement[2]/Name[1]"))
                        .findFirst()
                        .orElseThrow();
        var markerType =
                f.fields.stream()
                        .filter(field -> field.sourcePath().endsWith("/DataElement[2]/DataType[1]"))
                        .findFirst()
                        .orElseThrow();
        var markerContent =
                f.fields.stream()
                        .filter(field -> field.sourcePath().endsWith("/DataElement[2]/Content[1]"))
                        .findFirst()
                        .orElseThrow();
        assertThat(plan.retainedFields()).contains(markerName.id(), markerType.id());
        assertThat(plan.replacedFields()).contains(markerContent.id());
        assertThat(
                        new CdsFieldParser()
                                .firstToken(
                                        records.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
    }

    @Test
    void
            shouldRefuseAStoredMarkerThatContradictsItsHistoricalPreparation_insteadOfOverwritingItWithAnImportedDefault()
                    throws Exception {
        var f = fixture(CdsExportAllergyFieldReconcilerUnitTest.baseline(), true, "true");
        assertThatThrownBy(() -> prepare(f))
                .hasMessage("CDS_EXPORT_ALLERGY_EXTENSION_PROJECTION_CONFLICT");
    }

    @Test
    void shouldRefuseMissingOrSubstitutedSourceDescriptors_beforeProducingAContentCallback()
            throws Exception {
        var f = fixture(CdsExportAllergyFieldReconcilerUnitTest.baseline(), false, null);
        var absent = new ArrayList<>(f.fields);
        absent.removeLast();
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision,
                                                f.index,
                                                f.checked,
                                                absent,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_ALLERGY_RETAINED_ASSEMBLY_SCOPE_INVALID");
        var first = f.fields.getFirst();
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
        var changed = new ArrayList<>(f.fields);
        changed.set(0, substituted);
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision,
                                                f.index,
                                                f.checked,
                                                changed,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_ALLERGY_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldRequireUnchangedPublishedSourceFieldUnits_andRefuseAnyPendingRelatedFieldPage()
            throws Exception {
        var f = fixture(CdsExportAllergyFieldReconcilerUnitTest.baseline(), false, null);
        var original = f.checked.units().get(FIELD_ADDRESS).getFirst();
        var changed = new LinkedHashMap<>(f.checked.units());
        changed.put(
                FIELD_ADDRESS,
                List.of(
                        new CheckedReference(
                                original.expected(), "e".repeat(64), Reason.CHANGED_ROW)));
        var changedChecked =
                new CdsExportRetainedReceiptVerificationDto(
                        f.checked.sourceManifestSha256(),
                        changed,
                        Set.of(ADDRESS),
                        Set.of(FIELD_ADDRESS));
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision,
                                                f.index,
                                                changedChecked,
                                                f.fields,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_ALLERGY_SOURCE_FIELD_EVIDENCE_CHANGED");
        var pendingTables = new LinkedHashMap<>(f.index.tables());
        var pendingFields = new LinkedHashMap<>(pendingTables.get("cds_import_field"));
        var first = pendingFields.values().iterator().next();
        var pendingValues = new LinkedHashMap<>(first.fields());
        pendingValues.put("unit_key", "d".repeat(64));
        pendingFields.put(
                first.recordKey(),
                new CdsExportCapturedTextRowDto(
                        "cds_import_field", first.recordKey(), pendingValues));
        pendingTables.put("cds_import_field", pendingFields);
        var pending =
                new CdsExportRetainedIndexDto(
                        f.index.sourceManifestSha256(),
                        f.index.metadataRows(),
                        f.index.metadataBytes(),
                        pendingTables,
                        f.index.appliedUnits(),
                        f.index.references(),
                        f.index.nativeParents());
        assertThatThrownBy(
                        () ->
                                new CdsExportAllergyRetainedAssembler()
                                        .prepare(
                                                27,
                                                f.decision,
                                                pending,
                                                f.checked,
                                                f.fields,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_ALLERGY_SOURCE_FIELD_EVIDENCE_CHANGED");
    }

    @Test
    void shouldKeepASendersStandardAtcCode_whenItsCodedAgentIsUnchanged() throws Exception {
        // F9: a third-party file coded the agent with the standard Code (CodeType ATC). It must
        // not be moved into the CARLOS residual on re-export.
        var original = CdsExportAllergyFieldReconcilerUnitTest.source();
        var source =
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto(
                        original.description(),
                        original.property(),
                        "B01AB01",
                        "ATC",
                        original.reactionType(),
                        original.reaction(),
                        original.severity(),
                        original.lifeStage(),
                        original.notes(),
                        original.start(),
                        original.recorded(),
                        false,
                        null,
                        null,
                        false);
        var previous = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        previous.put("regional_identifier", "B01AB01");
        var current = new java.util.LinkedHashMap<>(previous);
        current.put("reaction", "Current reaction");
        var fields = standardFields("ATC", "B01AB01");
        var records = parse(prepare(fixture(current, previous, source, fields)).content(), "atc");
        var allergy =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(records.get(1));
        assertThat(allergy.codeSystem()).isEqualTo("ATC");
        assertThat(allergy.code()).isEqualTo("B01AB01");
        assertThat(allergy.atcResidual()).isFalse();
        assertThat(allergy.reaction()).isEqualTo("Current reaction");
    }

    @Test
    void shouldReplaceTheSeverityCarrierWithTheStandardSeverity_whenSevereIsChangedInCarlos()
            throws Exception {
        var original = CdsExportAllergyFieldReconcilerUnitTest.source();
        var source =
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto(
                        original.description(),
                        original.property(),
                        original.code(),
                        original.codeSystem(),
                        original.reactionType(),
                        original.reaction(),
                        null,
                        original.lifeStage(),
                        original.notes(),
                        original.start(),
                        original.recorded(),
                        false,
                        null,
                        null,
                        false,
                        false,
                        "3");
        var previous = CdsExportAllergyFieldReconcilerUnitTest.baseline();
        var fields = standardFields("DIN", "00123456");
        fields.add(
                field(
                        "ResidualInfo[1]/DataElement[2]/Name[1]",
                        "cds_dt",
                        CdsNativeExtensionContract.ALLERGY_SEVERITY));
        fields.add(field("ResidualInfo[1]/DataElement[2]/DataType[1]", "cds_dt", "string"));
        fields.add(field("ResidualInfo[1]/DataElement[2]/Content[1]", "cds_dt", "Severe"));
        var unchanged =
                parse(prepare(fixture(previous, previous, source, fields)).content(), "severe");
        var kept =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(unchanged.get(1));
        assertThat(kept.nativeSeverity()).isEqualTo("3");
        assertThat(kept.severity()).isNull();
        var current = new java.util.LinkedHashMap<>(previous);
        current.put("severity_of_reaction", "2");
        var changed =
                parse(prepare(fixture(current, previous, source, fields)).content(), "moderate");
        var moderate =
                new CdsClinicalNormalizationService(new CdsFieldParser()).allergy(changed.get(1));
        assertThat(moderate.severity()).isEqualTo("MO");
        assertThat(moderate.nativeSeverity()).isNull();
    }

    private static ArrayList<CdsExportRetainedFieldDto> standardFields(String type, String code) {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field("OffendingAgentDescription[1]", "cds", "Original agent"));
        fields.add(field("Reaction[1]", "cds", "Original reaction"));
        fields.add(field("Code[1]/CodeType[1]", "cds_dt", type));
        fields.add(field("Code[1]/CodeValue[1]", "cds_dt", code));
        fields.add(
                field(
                        "RecordedDate[1]/FullDateTime[1]",
                        "cds_dt",
                        "2024-02-29T12:34:56.1200-05:00"));
        fields.add(field("Notes[1]", "cds", "Source-only provider note"));
        fields.add(field("ResidualInfo[1]/DataElement[1]/Name[1]", "cds_dt", "Vendor.other"));
        fields.add(field("ResidualInfo[1]/DataElement[1]/DataType[1]", "cds_dt", "string"));
        fields.add(field("ResidualInfo[1]/DataElement[1]/Content[1]", "cds_dt", "Vendor content"));
        return fields;
    }

    private CdsExportRetainedSectionWriter.Prepared prepare(Fixture f) throws Exception {
        return new CdsExportAllergyRetainedAssembler()
                .prepare(27, f.decision, f.index, f.checked, f.fields, inline(), () -> {});
    }

    Fixture fixture(Map<String, String> current, boolean archive, String archiveValue)
            throws Exception {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field("OffendingAgentDescription[1]", "cds", "Original agent"));
        fields.add(field("Reaction[1]", "cds", "Original reaction"));
        fields.add(field("Code[1]/CodeType[1]", "cds_dt", "DIN"));
        fields.add(field("Code[1]/CodeValue[1]", "cds_dt", "00123456"));
        fields.add(
                field(
                        "RecordedDate[1]/FullDateTime[1]",
                        "cds_dt",
                        "2024-02-29T12:34:56.1200-05:00"));
        fields.add(field("Notes[1]", "cds", "Source-only provider note"));
        fields.add(
                field(
                        "ResidualInfo[1]/DataElement[1]/Name[1]",
                        "cds_dt",
                        "Vendor." + "x".repeat(512)));
        fields.add(field("ResidualInfo[1]/DataElement[1]/DataType[1]", "cds_dt", "string"));
        fields.add(
                field(
                        "ResidualInfo[1]/DataElement[1]/Content[1]",
                        "cds_dt",
                        "Vendor café 😀 content"));
        if (archive) {
            fields.add(
                    field(
                            "ResidualInfo[1]/DataElement[2]/Name[1]",
                            "cds_dt",
                            " \t" + CdsNativeExtensionContract.ALLERGY_ARCHIVED + "\n "));
            fields.add(field("ResidualInfo[1]/DataElement[2]/DataType[1]", "cds_dt", " boolean\t"));
            fields.add(field("ResidualInfo[1]/DataElement[2]/Content[1]", "cds_dt", archiveValue));
        }
        return fixture(
                current,
                CdsExportAllergyFieldReconcilerUnitTest.baseline(),
                CdsExportAllergyFieldReconcilerUnitTest.source(),
                fields);
    }

    Fixture fixture(
            Map<String, String> current,
            Map<String, String> previous,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAllergyDto source,
            List<CdsExportRetainedFieldDto> fields)
            throws Exception {
        var base =
                new CdsExportAllergyFieldReconcilerUnitTest()
                        .fixture(current, true, source, previous);
        var decision =
                new CdsExportAllergyFieldReconciler()
                        .reconcile(
                                27,
                                base.index(),
                                ADDRESS,
                                base.checked(),
                                base.row(),
                                base.witness(),
                                List.of());
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
        var checkedUnits = new LinkedHashMap<>(base.checked().units());
        checkedUnits.put(
                FIELD_ADDRESS,
                sourceRefs.stream()
                        .map(ref -> new CheckedReference(ref, ref.digest(), Reason.UNCHANGED_ROW))
                        .toList());
        var unchanged = new HashSet<>(base.checked().unchangedUnits());
        unchanged.add(FIELD_ADDRESS);
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        index.sourceManifestSha256(),
                        checkedUnits,
                        unchanged,
                        base.checked().reviewUnits());
        return new Fixture(index, checked, decision, fields);
    }

    private static CdsExportRetainedFieldDto field(
            String relative, String namespace, String value) {
        var type = CdsWireSchema.schemaModel().element("cds", "AllergiesAndAdverseReactions");
        for (String part : relative.split("/")) {
            String name = part.substring(0, part.indexOf('['));
            var child =
                    type.childNames().stream()
                            .filter(n -> n.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            type = type.child(child.namespace(), child.local());
        }
        String path = "/OmdCds[1]/PatientRecord[1]/AllergiesAndAdverseReactions[1]/" + relative;
        return new CdsExportRetainedFieldDto(
                CdsImportKeys.hash(FIELD_UNIT, namespace, path, "1"),
                RUN,
                "d".repeat(64),
                FIELD_UNIT,
                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                path,
                1,
                namespace,
                "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                null,
                value,
                value,
                null);
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
        return new CdsExportCapturedTextRowDto("cds_import_field", f.id(), values);
    }

    private static CdsExportRetainedSectionWriter.Values inline() {
        return new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto f) {
                return new StringReader(f.logicalInline());
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
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
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
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
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
