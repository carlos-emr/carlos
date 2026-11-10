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
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class CdsExportImmunizationRetainedAssemblerUnitTest {
    private static final String RUN = "123e4567-e89b-12d3-a456-426614174000",
            UNIT = "f".repeat(64),
            ADDRESS = RUN + ":" + UNIT,
            FIELD_UNIT = "9".repeat(64),
            FIELD_ADDRESS = RUN + ":" + FIELD_UNIT;
    @TempDir Path directory;

    record Fixture(
            CdsExportRetainedIndexDto index,
            CdsExportRetainedReceiptVerificationDto checked,
            CdsExportImmunizationFieldReconciler.Result decision,
            List<CdsExportRetainedFieldDto> fields) {}

    @Test
    void shouldVerifyChangedNotesAndAllStandardFieldsReachTheReceivingImporterWhileVendorFieldsRemain_forImmunizationRetainedAssemblerContract()
            throws Exception {
        var fixture =
                fixture(
                        1,
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"),
                        "Current café 😀\r\nnotes");
        var plan = prepare(fixture);
        var records = parse(plan.content());
        var received =
                new CdsClinicalNormalizationService(new CdsFieldParser())
                        .immunization(records.get(1));
        assertThat(received.notes()).isEqualTo("Current café 😀\r\nnotes");
        assertThat(received.name()).isEqualTo("Original café vaccine");
        assertThat(received.code()).isEqualTo("00123456");
        assertThat(received.route()).isEqualTo("IM");
        assertThat(
                        new CdsFieldParser()
                                .firstToken(
                                        records.get(1), "ResidualInfo/DataElement/Content", 1024))
                .isEqualTo("Vendor café 😀 content");
        var vendor =
                fixture.fields().stream()
                        .filter(field -> field.sourcePath().contains("ResidualInfo[1]"))
                        .map(CdsExportRetainedFieldDto::id)
                        .toList();
        assertThat(plan.retainedFields()).containsAll(vendor);
        assertThat(Collections.disjoint(plan.retainedFields(), plan.replacedFields())).isTrue();
        var accounted = new HashSet<>(plan.retainedFields());
        accounted.addAll(plan.replacedFields());
        assertThat(accounted)
                .isEqualTo(
                        fixture.fields().stream()
                                .map(CdsExportRetainedFieldDto::id)
                                .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void
            shouldVerifyImportedSecondPatientAndLaterOccurrenceAreReassembled_withoutChangingOwnershipOrDroppingFields()
                    throws Exception {
        var fixture =
                fixture(
                        2,
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"),
                        "Current notes");
        var records = parse(prepare(fixture).content());
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .immunization(records.get(1))
                                .notes())
                .isEqualTo("Current notes");
    }

    @Test
    void shouldVerifyAnExplicitNativeClearNeverReplaysTheOldSourceComment_forImmunizationRetainedAssemblerContract() throws Exception {
        var fixture = fixture(1, CdsTemporal.parse(null, null, "2024-02-29", null, "Date"), null);
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .immunization(parse(prepare(fixture).content()).get(1))
                                .notes())
                .isNull();
    }

    @Test
    void shouldVerifyPostOptionDateShiftRemainsEffectiveAndTheOriginalDateIsNotRestored_forImmunizationRetainedAssemblerContract() throws Exception {
        var fixture =
                fixture(
                        1,
                        CdsTemporal.shift(
                                CdsTemporal.parse(null, null, "2024-02-29", null, "Date"),
                                1,
                                "Date"),
                        "Current notes");
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .immunization(parse(prepare(fixture).content()).get(1))
                                .date()
                                .original())
                .isEqualTo("2024-03-01");
    }

    @Test
    void shouldVerifyAnUnqualifiedHistoricalOffsetCannotProduceAContentCallback_forImmunizationRetainedAssemblerContract() throws Exception {
        var fixture =
                fixture(
                        1,
                        CdsTemporal.parse(
                                null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"),
                        "Current notes");
        assertThatThrownBy(() -> prepare(fixture))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_MAPPING_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyMissingOrSubstitutedSourceDescriptorsFail_beforePreparingOutput() throws Exception {
        var fixture =
                fixture(
                        1,
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"),
                        "Current notes");
        var missing = new ArrayList<>(fixture.fields());
        missing.removeLast();
        assertThatThrownBy(
                        () ->
                                new CdsExportImmunizationRetainedAssembler()
                                        .prepare(
                                                27,
                                                fixture.decision(),
                                                fixture.index(),
                                                fixture.checked(),
                                                missing,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
        var first = fixture.fields().getFirst();
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
        var altered = new ArrayList<>(fixture.fields());
        altered.set(0, substituted);
        assertThatThrownBy(
                        () ->
                                new CdsExportImmunizationRetainedAssembler()
                                        .prepare(
                                                27,
                                                fixture.decision(),
                                                fixture.index(),
                                                fixture.checked(),
                                                altered,
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyChangedFieldPagesCannotBorrowUnchangedNativeClinicalRows_forImmunizationRetainedAssemblerContract() throws Exception {
        var fixture =
                fixture(
                        1,
                        CdsTemporal.parse(null, null, "2024-02-29", null, "Date"),
                        "Current notes");
        var units = new LinkedHashMap<>(fixture.checked().units());
        var previous = units.get(FIELD_ADDRESS).getFirst();
        var changed = new ArrayList<>(units.get(FIELD_ADDRESS));
        changed.set(
                0, new CheckedReference(previous.expected(), "e".repeat(64), Reason.CHANGED_ROW));
        units.put(FIELD_ADDRESS, changed);
        var unchanged = new HashSet<>(fixture.checked().unchangedUnits());
        unchanged.remove(FIELD_ADDRESS);
        var review = new HashSet<>(fixture.checked().reviewUnits());
        review.add(FIELD_ADDRESS);
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        fixture.index().sourceManifestSha256(), units, unchanged, review);
        assertThatThrownBy(
                        () ->
                                new CdsExportImmunizationRetainedAssembler()
                                        .prepare(
                                                27,
                                                fixture.decision(),
                                                fixture.index(),
                                                checked,
                                                fixture.fields(),
                                                inline(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_SOURCE_FIELD_EVIDENCE_CHANGED");
    }

    private CdsExportRetainedSectionWriter.Prepared prepare(Fixture fixture) throws Exception {
        return new CdsExportImmunizationRetainedAssembler()
                .prepare(
                        27,
                        fixture.decision(),
                        fixture.index(),
                        fixture.checked(),
                        fixture.fields(),
                        inline(),
                        () -> {});
    }

    Fixture fixture(int patientOrdinal, CdsTemporalDto date, String currentNotes) throws Exception {
        var base = new CdsExportImmunizationFieldReconcilerUnitTest().fixture(date, currentNotes);
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(field(patientOrdinal, "ImmunizationName[1]", "cds", base.source().name()));
        fields.add(field(patientOrdinal, "ImmunizationType[1]", "cds", base.source().type()));
        fields.add(field(patientOrdinal, "Manufacturer[1]", "cds", base.source().manufacturer()));
        fields.add(field(patientOrdinal, "Route[1]", "cds", base.source().route()));
        fields.add(field(patientOrdinal, "Site[1]", "cds", base.source().site()));
        fields.add(field(patientOrdinal, "Dose[1]", "cds", base.source().dose()));
        fields.add(
                field(
                        patientOrdinal,
                        "ImmunizationCode[1]/CodingSystem[1]",
                        "cds_dt",
                        base.source().codeSystem()));
        fields.add(
                field(
                        patientOrdinal,
                        "ImmunizationCode[1]/value[1]",
                        "cds_dt",
                        base.source().code()));
        String child =
                switch (date.precision()) {
                    case YEAR -> "YearOnly";
                    case MONTH -> "YearMonth";
                    case FULL_DATE -> "FullDate";
                    default -> "FullDateTime";
                };
        fields.add(field(patientOrdinal, "Date[1]/" + child + "[1]", "cds_dt", date.original()));
        fields.add(field(patientOrdinal, "RefusedFlag[1]/ynIndicatorsimple[1]", "cds_dt", "N"));
        fields.add(field(patientOrdinal, "Instructions[1]", "cds", base.source().instructions()));
        fields.add(field(patientOrdinal, "Notes[1]", "cds", base.source().notes()));
        fields.add(
                field(
                        patientOrdinal,
                        "ResidualInfo[1]/DataElement[1]/Name[1]",
                        "cds_dt",
                        "Vendor." + "x".repeat(512)));
        fields.add(
                field(
                        patientOrdinal,
                        "ResidualInfo[1]/DataElement[1]/DataType[1]",
                        "cds_dt",
                        "string"));
        fields.add(
                field(
                        patientOrdinal,
                        "ResidualInfo[1]/DataElement[1]/Content[1]",
                        "cds_dt",
                        "Vendor café 😀 content"));
        var metadata = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        for (var field : fields) metadata.put(field.id(), metadata(field));
        var tables = new LinkedHashMap<>(base.index().tables());
        tables.put("cds_import_field", metadata);
        var refs =
                fields.stream()
                        .map(
                                field ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                "cds_import_field", field.id(), "c".repeat(64)))
                        .toList();
        var references = new LinkedHashMap<>(base.index().references());
        references.put(FIELD_ADDRESS, refs);
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
                refs.stream()
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
        return new Fixture(index, checked, base.decision(), fields);
    }

    private static CdsExportRetainedFieldDto field(
            int ordinal, String relative, String namespace, String value) {
        var type = CdsWireSchema.schemaModel().element("cds", "Immunizations");
        for (String segment : relative.split("/")) {
            String name = segment.substring(0, segment.indexOf('['));
            var child =
                    type.childNames().stream()
                            .filter(candidate -> candidate.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            type = type.child(child.namespace(), child.local());
        }
        String path = "/OmdCds[1]/PatientRecord[" + ordinal + "]/Immunizations[3]/" + relative;
        return new CdsExportRetainedFieldDto(
                CdsImportKeys.hash(FIELD_UNIT, namespace, path, "1"),
                RUN,
                "d".repeat(64),
                FIELD_UNIT,
                CdsCategory.IMMUNIZATIONS,
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
        var values = new LinkedHashMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.IMPORT_FIELDS
                .columns()
                .forEach(column -> values.put(column, null));
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
                        field.category().wireName(),
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

    private static CdsExportRetainedSectionWriter.Values inline() {
        return new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError();
            }
        };
    }

    private List<CdsRecordDto> parse(CdsExportXmlComposer.Content content) throws Exception {
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
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS);
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                                xml -> new CdsExportDemographicXmlWriter().write(demographic, xml)),
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(
                                        "a".repeat(64), CdsCategory.IMMUNIZATIONS, "c".repeat(64)),
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
