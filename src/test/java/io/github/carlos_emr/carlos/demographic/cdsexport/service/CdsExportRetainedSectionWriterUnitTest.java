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
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedSectionWriterUnitTest {
    @TempDir Path directory;
    private static final String RUN = "a3b89e4d-b623-4f61-a66d-e8446ef5cba4",
            PATIENT = "a".repeat(64),
            CAPTURE = "b".repeat(64);
    private static final String VERSION = "2026.08.0-test";
    private final CdsExportRetainedSectionWriter writer = new CdsExportRetainedSectionWriter();

    @Test
    void shouldRoundTripEveryPinnedChoiceFixture_inSchemaOrderWithEveryScalarAndAttribute()
            throws Exception {
        for (int fixture = 1; fixture <= 4; fixture++) {
            Path input = Path.of("src/test/resources/cds-spec/choices-0" + fixture + ".xml");
            var records = parse(input, "source-" + fixture);
            var sections = new ArrayList<CdsExportXmlComposer.Section>();
            var selected = EnumSet.noneOf(CdsCategory.class);
            var expected = scalars(records);
            for (var record : records) {
                var captured = capture(record);
                Collections.shuffle(captured.fields, new Random(27));
                CdsCategory category = CdsCategory.fromWireName(record.section());
                selected.add(category);
                sections.add(
                        section(category, writer.prepare(captured.fields, captured, () -> {})));
            }
            byte[] output = compose(sections, selected);
            // All-choice fixtures deliberately combine FilePath and inline TextContent,
            // or label Media as Text. Rebuilding their structure must not qualify those
            // semantic inconsistencies. The receiving parser independently validates XSD.
            assertThatThrownBy(
                            () ->
                                    new CdsExportXmlValidator()
                                            .validate(
                                                    new ByteArrayInputStream(output),
                                                    "SSSS",
                                                    selected,
                                                    VERSION,
                                                    CdsLogicalProfile.ONTARIO_MD_5_2,
                                                    CdsExportXmlLimitsDto.defaults(),
                                                    ignored -> {},
                                                    () -> {}))
                    .hasMessage("CDS_EXPORT_REPORT_FORMAT_MISMATCH");
            Path file = Files.write(directory.resolve("rebuilt-" + fixture + ".xml"), output);
            assertThat(scalars(parse(file, "receiving-" + fixture)))
                    .containsExactlyInAnyOrderEntriesOf(expected);
        }
    }

    @Test
    void shouldKeepRepeatedOccurrencesAndEmptyComplexNodes_andUseEffectiveText() throws Exception {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[1]/Name[1]",
                        "cds_dt",
                        "first"));
        fields.add(
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[1]/DataType[1]",
                        "cds_dt",
                        "String"));
        fields.add(
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[1]/Content[1]",
                        "cds_dt",
                        "original"));
        fields.add(
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[2]/Name[1]",
                        "cds_dt",
                        "second"));
        fields.add(
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[2]/DataType[1]",
                        "cds_dt",
                        "String"));
        fields.add(
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[2]/Content[1]",
                        "cds_dt",
                        "second value"));
        var corrected = fields.get(2);
        fields.set(
                2,
                copy(corrected, corrected.sourcePath(), corrected.namespace(), "reviewed café 😀"));
        var output =
                compose(
                        List.of(
                                demographics(),
                                section(
                                        CdsCategory.PERSONAL_HISTORY,
                                        writer.prepare(fields, inline(), () -> {}))),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.PERSONAL_HISTORY));
        var records = parse(Files.write(directory.resolve("effective.xml"), output), "effective");
        assertThat(scalars(records).values())
                .contains("reviewed café 😀", "second value")
                .doesNotContain("original");
        var empty = field("PersonalHistory[1]", "cds", "");
        compose(
                List.of(
                        demographics(),
                        section(
                                CdsCategory.PERSONAL_HISTORY,
                                writer.prepare(List.of(empty), inline(), () -> {}))),
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.PERSONAL_HISTORY));
    }

    @Test
    void shouldRefuseConflictingPathsNamespacesTypesAndMissingRequiredChildren_beforeReadingValues()
            throws Exception {
        var name =
                field(
                        "PersonalHistory[1]/ResidualInfo[1]/DataElement[1]/Name[1]",
                        "cds_dt",
                        "label");
        var attribute = field("Demographics[1]/PhoneNumber[1]/@phoneNumberType", "", "R");
        for (var fields :
                List.of(
                        List.of(name, name),
                        List.of(name),
                        List.of(copy(name, name.sourcePath(), "cds", null)),
                        List.of(
                                copy(
                                        name,
                                        name.sourcePath()
                                                .replace("DataElement[1]", "DataElement[2]"),
                                        name.namespace(),
                                        null)),
                        List.of(
                                copy(
                                        name,
                                        name.sourcePath()
                                                .replace(
                                                        "PersonalHistory[1]", "PersonalHistory[2]"),
                                        name.namespace(),
                                        null),
                                name),
                        List.of(
                                name,
                                copy(
                                        name,
                                        name.sourcePath().replace("Name[1]", "Unknown[1]"),
                                        name.namespace(),
                                        null)),
                        List.of(attribute))) {
            assertThatThrownBy(() -> writer.prepare(fields, inline(), () -> {}))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void shouldInvalidateTheDocumentWhenSourceAcquisitionOrCloseFails_evenIfTheMapperCatchesIt()
            throws Exception {
        var note = field("PersonalHistory[1]", "cds", "");
        for (boolean acquisition : List.of(true, false)) {
            var values =
                    new CdsExportRetainedSectionWriter.Values() {
                        @Override
                        public Reader text(CdsExportRetainedFieldDto field) throws IOException {
                            if (acquisition) throw new IOException("SOURCE_AUTHENTICATION_FAILED");
                            return new StringReader("") {
                                @Override
                                public void close() {
                                    throw new IllegalStateException(
                                            "SOURCE_CLOSE_AUTHENTICATION_FAILED");
                                }
                            };
                        }

                        @Override
                        public InputStream binary(CdsExportRetainedFieldDto field) {
                            throw new AssertionError();
                        }
                    };
            var content = writer.prepare(List.of(note), values, () -> {});
            var bad =
                    section(
                            CdsCategory.PERSONAL_HISTORY,
                            xml -> {
                                try {
                                    content.write(xml);
                                } catch (IOException | IllegalStateException expected) {
                                }
                            });
            assertThatThrownBy(
                            () ->
                                    compose(
                                            List.of(demographics(), bad),
                                            Set.of(
                                                    CdsCategory.DEMOGRAPHICS,
                                                    CdsCategory.PERSONAL_HISTORY)))
                    .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
        }
    }

    @Test
    void shouldReviewStructuralAttributes_insteadOfReplayingUnknownNamespaceBindings()
            throws Exception {
        var original = field("PersonalHistory[1]", "cds", "");
        var structural =
                new CdsExportRetainedFieldDto(
                        original.id(),
                        RUN,
                        PATIENT,
                        original.fieldUnitKey(),
                        original.category(),
                        original.sourcePath() + "/@type",
                        1,
                        "http://www.w3.org/2001/XMLSchema-instance",
                        "ATTRIBUTE",
                        null,
                        "source:opaque",
                        null,
                        null);
        assertThatThrownBy(() -> writer.prepare(List.of(structural, original), inline(), () -> {}))
                .hasMessage("CDS_EXPORT_RETAINED_STRUCTURAL_ATTRIBUTE_REQUIRES_REVIEW");
    }

    @Test
    void
            shouldReplaceOneQualifiedValue_withoutLosingUnknownResidualsNotesOrTheirOriginalIdentities()
                    throws Exception {
        var fields = changedAllergyFields();
        var reaction =
                fields.stream()
                        .filter(f -> f.sourcePath().endsWith("/Reaction[1]"))
                        .findFirst()
                        .orElseThrow();
        var plan =
                writer.prepareChanges(
                        fields,
                        inline(),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "Reaction[1]",
                                        Set.of(reaction.id()),
                                        xml -> xml.text("Current café 😀"))),
                        () -> {});
        assertThat(plan.replacedFields()).containsExactly(reaction.id());
        assertThat(plan.retainedFields()).hasSize(fields.size() - 1);
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var output =
                compose(
                        List.of(
                                demographics(),
                                section(
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        plan.content())),
                        selected);
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output),
                        "27",
                        selected,
                        VERSION,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var records = parse(Files.write(directory.resolve("changed.xml"), output), "changed");
        var values = scalars(records);
        assertThat(values.values())
                .contains(
                        "Current café 😀",
                        "Vendor.One",
                        "Vendor.Two",
                        "vendor-😀-one",
                        "vendor-😀-two",
                        " Notes café 😀\r\n ")
                .doesNotContain("Original reaction");
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .allergy(records.get(1))
                                .reaction())
                .isEqualTo("Current café 😀");
    }

    @Test
    void shouldAddAnAbsentOptionalField_andKeepAnExplicitlyRemovedValueRemoved() throws Exception {
        var fields = changedAllergyFields();
        var reaction =
                fields.stream()
                        .filter(f -> f.sourcePath().endsWith("/Reaction[1]"))
                        .findFirst()
                        .orElseThrow();
        var removed =
                writer.prepareChanges(
                        fields,
                        inline(),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "Reaction[1]", Set.of(reaction.id()), null)),
                        () -> {});
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var output =
                compose(
                        List.of(
                                demographics(),
                                section(
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        removed.content())),
                        selected);
        var records = parse(Files.write(directory.resolve("removed.xml"), output), "removed");
        assertThat(
                        new CdsClinicalNormalizationService(new CdsFieldParser())
                                .allergy(records.get(1))
                                .reaction())
                .isNull();
        var absent = fields.stream().filter(f -> !f.id().equals(reaction.id())).toList();
        var added =
                writer.prepareChanges(
                        absent,
                        inline(),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "Reaction[1]",
                                        Set.of(),
                                        xml -> xml.text("Added from current native evidence"))),
                        () -> {});
        assertThat(added.replacedFields()).isEmpty();
        assertThat(added.retainedFields()).hasSize(absent.size());
        var addedXml =
                compose(
                        List.of(
                                demographics(),
                                section(
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        added.content())),
                        selected);
        assertThat(
                        scalars(
                                        parse(
                                                Files.write(
                                                        directory.resolve("added.xml"), addedXml),
                                                "added"))
                                .values())
                .contains("Added from current native evidence", "vendor-😀-one", "vendor-😀-two");
    }

    @Test
    void
            shouldRequireEveryPriorFieldIdentity_forAChangedSubtree_andRejectOverlapsMissingDeletesAndOrdinalGaps()
                    throws Exception {
        var fields = changedAllergyFields();
        var prior =
                fields.stream()
                        .filter(f -> f.sourcePath().contains("/ResidualInfo[1]/DataElement[1]/"))
                        .map(CdsExportRetainedFieldDto::id)
                        .collect(java.util.stream.Collectors.toSet());
        for (var changes :
                List.of(
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "ResidualInfo[1]/DataElement[1]", Set.of(), null)),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "ResidualInfo[1]/DataElement[4]",
                                        Set.of(),
                                        xml -> xml.text("gap"))),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "Code[1]", Set.of(), null)),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "ResidualInfo[1]/DataElement[1]", prior, null),
                                new CdsExportRetainedSectionWriter.Change(
                                        "ResidualInfo[1]", Set.of(), null)))) {
            assertThatThrownBy(() -> writer.prepareChanges(fields, inline(), changes, () -> {}))
                    .hasMessage("CDS_EXPORT_RETAINED_STRUCTURE_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldKeepRemainingRepeatedValuesInOrder_afterAnExplicitSubtreeDeletion()
            throws Exception {
        var fields = changedAllergyFields();
        var prior =
                fields.stream()
                        .filter(f -> f.sourcePath().contains("/DataElement[1]/"))
                        .map(CdsExportRetainedFieldDto::id)
                        .collect(java.util.stream.Collectors.toSet());
        var plan =
                writer.prepareChanges(
                        fields,
                        inline(),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "ResidualInfo[1]/DataElement[1]", prior, null)),
                        () -> {});
        var output =
                compose(
                        List.of(
                                demographics(),
                                section(
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        plan.content())),
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS));
        var values =
                scalars(
                        parse(
                                Files.write(directory.resolve("repeated-delete.xml"), output),
                                "repeated-delete"));
        assertThat(values.values())
                .contains("Vendor.Two", "vendor-😀-two")
                .doesNotContain("Vendor.One", "vendor-😀-one");
        assertThat(
                        values.keySet().stream()
                                .filter(k -> k.contains("/ResidualInfo[1]/DataElement["))
                                .toList())
                .allMatch(k -> k.contains("/DataElement[1]/"));
    }

    @Test
    void shouldApplyAddsAndDeletesInOriginalCoordinates_evenWhenTheChangesArriveInAnotherOrder()
            throws Exception {
        var fields = changedAllergyFields();
        var removed =
                fields.stream()
                        .filter(f -> f.sourcePath().contains("/DataElement[2]/"))
                        .map(CdsExportRetainedFieldDto::id)
                        .collect(java.util.stream.Collectors.toSet());
        var fourth =
                new CdsExportRetainedSectionWriter.Change(
                        "ResidualInfo[1]/DataElement[4]",
                        Set.of(),
                        xml -> {
                            xml.element("cds_dt", "Name", "Native.Four");
                            xml.element("cds_dt", "DataType", "string");
                            xml.element("cds_dt", "Content", "four");
                        });
        var third =
                new CdsExportRetainedSectionWriter.Change(
                        "ResidualInfo[1]/DataElement[3]",
                        Set.of(),
                        xml -> {
                            xml.element("cds_dt", "Name", "Native.Three");
                            xml.element("cds_dt", "DataType", "string");
                            xml.element("cds_dt", "Content", "three");
                        });
        var plan =
                writer.prepareChanges(
                        fields,
                        inline(),
                        List.of(
                                fourth,
                                new CdsExportRetainedSectionWriter.Change(
                                        "ResidualInfo[1]/DataElement[2]", removed, null),
                                third),
                        () -> {});
        var output =
                compose(
                        List.of(
                                demographics(),
                                section(
                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                        plan.content())),
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS));
        var values =
                scalars(
                        parse(
                                Files.write(directory.resolve("original-coordinates.xml"), output),
                                "original-coordinates"));
        assertThat(values.values())
                .contains("Vendor.One", "Native.Three", "Native.Four")
                .doesNotContain("Vendor.Two");
        assertThat(
                        values.entrySet().stream()
                                .filter(e -> e.getKey().contains("/Name[1]"))
                                .map(Map.Entry::getValue)
                                .toList())
                .containsSubsequence("Vendor.One", "Native.Three", "Native.Four");
    }

    @Test
    void
            shouldFenceReplacementFragmentsInsideTheirElement_evenIfTheCallbackCatchesAnAttemptToCloseItsParent()
                    throws Exception {
        var fields = changedAllergyFields();
        var reaction =
                fields.stream()
                        .filter(f -> f.sourcePath().endsWith("/Reaction[1]"))
                        .findFirst()
                        .orElseThrow();
        var plan =
                writer.prepareChanges(
                        fields,
                        inline(),
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "Reaction[1]",
                                        Set.of(reaction.id()),
                                        xml -> {
                                            try {
                                                xml.end();
                                            } catch (IOException expected) {
                                            }
                                        })),
                        () -> {});
        assertThatThrownBy(
                        () ->
                                compose(
                                        List.of(
                                                demographics(),
                                                section(
                                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                                        plan.content())),
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS)))
                .hasMessage("CDS_EXPORT_SECTION_WRITE_FAILED");
    }

    @Test
    void shouldRequireTheAuthenticatedValueScope_evenWhenAllOriginalFieldsWereExplicitlyReplaced()
            throws Exception {
        var description =
                field(
                        "AllergiesAndAdverseReactions[1]/OffendingAgentDescription[1]",
                        "cds",
                        "Original");
        var values =
                new CdsExportRetainedSectionWriter.Values() {
                    @Override
                    public void requireActive() throws IOException {
                        throw new IOException("EXPIRED_SCOPE");
                    }

                    @Override
                    public Reader text(CdsExportRetainedFieldDto field) {
                        throw new AssertionError();
                    }

                    @Override
                    public InputStream binary(CdsExportRetainedFieldDto field) {
                        throw new AssertionError();
                    }
                };
        var plan =
                writer.prepareChanges(
                        List.of(description),
                        values,
                        List.of(
                                new CdsExportRetainedSectionWriter.Change(
                                        "OffendingAgentDescription[1]",
                                        Set.of(description.id()),
                                        xml -> xml.text("New"))),
                        () -> {});
        assertThatThrownBy(
                        () ->
                                compose(
                                        List.of(
                                                demographics(),
                                                section(
                                                        CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                                        plan.content())),
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS)))
                .hasMessage("EXPIRED_SCOPE");
    }

    private List<CdsExportRetainedFieldDto> changedAllergyFields() {
        var fields = new ArrayList<CdsExportRetainedFieldDto>();
        fields.add(
                field(
                        "AllergiesAndAdverseReactions[1]/OffendingAgentDescription[1]",
                        "cds",
                        "Synthetic agent"));
        fields.add(
                field("AllergiesAndAdverseReactions[1]/Reaction[1]", "cds", "Original reaction"));
        fields.add(field("AllergiesAndAdverseReactions[1]/Notes[1]", "cds", " Notes café 😀\r\n "));
        for (int n = 1; n <= 2; n++) {
            String root = "AllergiesAndAdverseReactions[1]/ResidualInfo[1]/DataElement[" + n + "]";
            fields.add(field(root + "/Name[1]", "cds_dt", n == 1 ? "Vendor.One" : "Vendor.Two"));
            fields.add(field(root + "/DataType[1]", "cds_dt", "string"));
            fields.add(
                    field(
                            root + "/Content[1]",
                            "cds_dt",
                            n == 1 ? "vendor-😀-one" : "vendor-😀-two"));
        }
        return fields;
    }

    private List<CdsRecordDto> parse(Path file, String name) throws Exception {
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

    private Capture capture(CdsRecordDto record) throws IOException {
        var captured = new Capture();
        var parser = new CdsFieldParser();
        parser.forEachField(
                record,
                scalar -> {
                    try {
                        String id = String.format("%064x", captured.fields.size() + 1);
                        byte[] bytes;
                        try (var input = parser.openValue(record, scalar)) {
                            bytes = input.readAllBytes();
                        }
                        boolean binary = scalar.kind().equals("BINARY");
                        String original = binary ? null : new String(bytes, StandardCharsets.UTF_8);
                        var payload =
                                binary
                                        ? new CdsExportRetainedFieldDto.Payload(
                                                "/frozen/owned-value",
                                                scalar.sha256(),
                                                0,
                                                bytes.length)
                                        : null;
                        var field =
                                new CdsExportRetainedFieldDto(
                                        id,
                                        RUN,
                                        PATIENT,
                                        "c".repeat(64),
                                        CdsCategory.fromWireName(record.section()),
                                        record.sourceXPath()
                                                + (scalar.path().isEmpty()
                                                        ? ""
                                                        : "/" + scalar.path()),
                                        record.ordinal(),
                                        scalar.namespace(),
                                        scalar.schemaType(),
                                        null,
                                        original,
                                        binary
                                                ? null
                                                : parser.readLogicalText(record, scalar, 65_536),
                                        payload);
                        captured.fields.add(field);
                        captured.bytes.put(id, bytes);
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                });
        return captured;
    }

    private Map<String, String> scalars(List<CdsRecordDto> records) throws IOException {
        var output = new LinkedHashMap<String, String>();
        var parser = new CdsFieldParser();
        for (var record : records)
            parser.forEachField(
                    record,
                    scalar -> {
                        try {
                            String value;
                            if (scalar.kind().equals("BINARY")) {
                                try (var input = parser.openValue(record, scalar)) {
                                    value =
                                            Base64.getEncoder()
                                                    .encodeToString(input.readAllBytes());
                                }
                            } else value = parser.readLogicalText(record, scalar, 65_536);
                            output.put(
                                    record.sourceXPath()
                                            + "/"
                                            + scalar.path()
                                            + "#"
                                            + scalar.namespace()
                                            + "#"
                                            + scalar.schemaType(),
                                    value);
                        } catch (IOException failed) {
                            throw new UncheckedIOException(failed);
                        }
                    });
        return output;
    }

    private CdsExportRetainedFieldDto field(String relative, String namespace, String value) {
        String[] parts = relative.split("/");
        var category = CdsCategory.fromWireName(parts[0].substring(0, parts[0].indexOf('[')));
        var type = CdsWireSchema.schemaModel().element("cds", category.wireName());
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("@")) type = type.attribute(namespace, part.substring(1));
            else {
                String name = part.substring(0, part.indexOf('['));
                var child =
                        type.childNames().stream()
                                .filter(n -> n.local().equals(name))
                                .findFirst()
                                .orElseThrow();
                type = type.child(child.namespace(), child.local());
            }
        }
        String id = HexFormat.of().formatHex(digest(relative));
        return new CdsExportRetainedFieldDto(
                id,
                RUN,
                PATIENT,
                "c".repeat(64),
                category,
                "/OmdCds[1]/PatientRecord[1]/" + relative,
                1,
                namespace,
                "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                null,
                value,
                value,
                null);
    }

    private static byte[] digest(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static CdsExportRetainedFieldDto copy(
            CdsExportRetainedFieldDto value, String path, String namespace, String effective) {
        return new CdsExportRetainedFieldDto(
                value.id(),
                value.runId(),
                value.sourcePatientKey(),
                value.fieldUnitKey(),
                value.category(),
                path,
                value.occurrence(),
                namespace,
                value.valueType(),
                value.precision(),
                value.originalInline(),
                effective,
                value.originalPayload());
    }

    private static CdsExportRetainedSectionWriter.Values inline() {
        return new CdsExportRetainedSectionWriter.Values() {
            @Override
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            @Override
            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError();
            }
        };
    }

    private static CdsExportXmlComposer.Section section(
            CdsCategory category, CdsExportXmlComposer.Content content) {
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(PATIENT, category, "d".repeat(64)), content);
    }

    private static CdsExportXmlComposer.Section demographics() throws Exception {
        var mapping =
                new CdsExportDemographicMapper()
                        .map(CdsExportDemographicProviderUnitTest.demographic(null));
        return section(
                CdsCategory.DEMOGRAPHICS,
                xml -> new CdsExportDemographicXmlWriter().write(mapping, xml));
    }

    private static byte[] compose(
            List<CdsExportXmlComposer.Section> sections, Set<CdsCategory> selected)
            throws IOException {
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        CAPTURE,
                        PATIENT,
                        VERSION,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        16L << 20,
                        () -> {});
        return output.toByteArray();
    }

    private static final class Capture implements CdsExportRetainedSectionWriter.Values {
        private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
        private final Map<String, byte[]> bytes = new HashMap<>();

        @Override
        public Reader text(CdsExportRetainedFieldDto field) {
            return new StringReader(
                    field.logicalInline() == null ? field.originalInline() : field.logicalInline());
        }

        @Override
        public InputStream binary(CdsExportRetainedFieldDto field) {
            return new ByteArrayInputStream(bytes.get(field.id()));
        }
    }
}
