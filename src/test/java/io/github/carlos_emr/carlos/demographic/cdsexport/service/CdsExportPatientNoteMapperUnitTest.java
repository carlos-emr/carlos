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
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportPatientNoteMapperUnitTest {
    @TempDir Path root;
    private final CdsExportPatientNoteMapper mapper = new CdsExportPatientNoteMapper();
    private static final Set<CdsCategory> DEMOGRAPHICS = Set.of(CdsCategory.DEMOGRAPHICS);

    @Test
    void preservesLiteralClosingTagsEntitiesWhitespaceAndUnicodeThroughSchemaAndReceivingImporter()
            throws Exception {
        for (String original :
                List.of(
                        "  Literal café 😀\r\nSecond line  ",
                        "Literal </unotes> inside the note",
                        "&lt;script&gt; is literal text",
                        "<unotes>nested literal</unotes>")) {
            var mapped =
                    mapper.map(27, DEMOGRAPHICS, row(DemographicCust.wrapPatientNotes(original)));
            assertThat(mapped.representable()).isTrue();
            assertThat(mapped.text()).isEqualTo(original);
            assertThat(roundTrip(mapped)).isEqualTo(original);
        }
    }

    @Test
    void preservesUnframedLegacyTextWithoutAddingXmlInterpretation() throws Exception {
        String original = "  <b>Native plain note</b> &amp; café 😀  ";
        var mapped = mapper.map(27, DEMOGRAPHICS, row(original));
        assertThat(roundTrip(mapped)).isEqualTo(original);
    }

    @Test
    void keepsUnmatchedTagTextLiteralAsTheNativeModelDoes() throws Exception {
        for (String raw : List.of("<unotes>missing close", "Only a </unotes> close", "<unotes>")) {
            var mapped = mapper.map(27, DEMOGRAPHICS, row(raw));
            assertThat(mapped.representable()).isTrue();
            assertThat(mapped.text()).isEqualTo(DemographicCust.patientNotesText(raw));
            assertThat(roundTrip(mapped)).isEqualTo(raw);
        }
    }

    @Test
    void distinguishesSqlNullEmptyAndExplicitlyClearedWrappedText() throws Exception {
        var absent = mapper.map(27, DEMOGRAPHICS, row(null));
        var empty = mapper.map(27, DEMOGRAPHICS, row(""));
        var cleared = mapper.map(27, DEMOGRAPHICS, row("<unotes></unotes>"));
        assertThat(absent.text()).isNull();
        assertThat(content(absent).presence())
                .isEqualTo(CdsExportFieldAssessmentDto.Presence.SQL_NULL);
        assertThat(roundTrip(absent)).isNull();
        assertThat(empty.text()).isEmpty();
        assertThat(content(empty).presence()).isEqualTo(CdsExportFieldAssessmentDto.Presence.EMPTY);
        assertThat(roundTrip(empty)).isEmpty();
        assertThat(cleared.text()).isEmpty();
        assertThat(content(cleared).presence())
                .isEqualTo(CdsExportFieldAssessmentDto.Presence.VALUE);
        assertThat(roundTrip(cleared)).isEmpty();
    }

    @Test
    void refusesAmbiguousFramesInsteadOfUsingTheFirstClosingTagOrDiscardingPrefixes()
            throws Exception {
        for (String raw : List.of("prefix<unotes>note</unotes>", "<unotes>note</unotes>suffix")) {
            var mapped = mapper.map(27, DEMOGRAPHICS, row(raw));
            assertThat(mapped.representable()).isFalse();
            assertThat(mapped.text()).isNull();
            assertThat(content(mapped).rule())
                    .isEqualTo("AMBIGUOUS_PATIENT_NOTE_FRAMING_REQUIRES_REVIEW");
            assertThatThrownBy(() -> compose(mapped))
                    .hasMessage("CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
        }
    }

    @Test
    void doesNotSilentlyTreatCareTeamReferencesAsNotesOrPrimaryPhysicians() throws Exception {
        for (String column : List.of("cust1", "cust2", "cust4")) {
            var original = row("Patient note");
            var fields = new LinkedHashMap<>(original.fields());
            fields.put(column, "0");
            var mapped =
                    mapper.map(
                            27,
                            DEMOGRAPHICS,
                            new CdsExportCapturedTextRowDto(
                                    original.table(), original.recordKey(), fields));
            assertThat(mapped.representable()).isFalse();
            assertThat(mapped.text()).isEqualTo("Patient note");
            assertThatThrownBy(() -> compose(mapped))
                    .hasMessage("CDS_EXPORT_PATIENT_NOTE_MAPPING_REQUIRES_REVIEW");
        }
    }

    @Test
    void delegatesSelectedAlertFieldsExplicitlyWithoutCopyingThemIntoPatientNotes()
            throws Exception {
        var original = row("Patient note");
        var fields = new LinkedHashMap<>(original.fields());
        fields.put("cust3", "PRIVATE ALERT SENTINEL");
        var full = new CdsExportCapturedTextRowDto(original.table(), original.recordKey(), fields);
        assertThatThrownBy(() -> mapper.map(27, DEMOGRAPHICS, full))
                .hasMessage("CDS_EXPORT_PATIENT_NOTE_SCOPE_INVALID");
        var mapped =
                mapper.map(
                        27,
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS),
                        full);
        assertThat(mapped.fields())
                .hasSize(5)
                .allSatisfy((key, value) -> assertThat(key.column()).isNotEqualTo("cust3"));
        assertThat(mapped.delegatedFields())
                .containsExactly(
                        new CdsExportNativeFieldKeyDto(
                                "demographiccust", original.recordKey(), "cust3"));
        assertThat(new String(compose(mapped), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("PRIVATE ALERT");
    }

    @Test
    void rejectsForeignOwnersKeysAndUndeclaredColumns() throws Exception {
        var original = row("Patient note");
        assertThatThrownBy(() -> mapper.map(28, DEMOGRAPHICS, original))
                .hasMessage("CDS_EXPORT_PATIENT_NOTE_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        DEMOGRAPHICS,
                                        new CdsExportCapturedTextRowDto(
                                                original.table(), "different", original.fields())))
                .hasMessage("CDS_EXPORT_PATIENT_NOTE_SCOPE_INVALID");
        var fields = new LinkedHashMap<>(original.fields());
        fields.put("unknown_note", "Cannot discard this");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        27,
                                        DEMOGRAPHICS,
                                        new CdsExportCapturedTextRowDto(
                                                original.table(), original.recordKey(), fields)))
                .hasMessage("CDS_EXPORT_PATIENT_NOTE_SCOPE_INVALID");
    }

    @Test
    void keepsExactCodePointCapacityAndRefusesOversizedOrIllegalValuesWithoutTruncation()
            throws Exception {
        var valid = mapper.map(27, DEMOGRAPHICS, row("😀".repeat(64000)));
        assertThat(roundTrip(valid)).isEqualTo(valid.text());
        for (String text :
                List.of(
                        "😀".repeat(64001),
                        "😀".repeat(65536),
                        "😀".repeat(65537),
                        "Invalid\u0001XML",
                        "Invalid\ud800surrogate")) {
            var mapped = mapper.map(27, DEMOGRAPHICS, row(text));
            assertThat(mapped.representable()).isFalse();
            assertThat(mapped.text()).isNull();
        }
    }

    @Test
    void rejectedNoteStillPoisonsTheDocumentWhenTheCallerCatchesItsError() throws Exception {
        var mapped = mapper.map(27, DEMOGRAPHICS, row("prefix<unotes>note</unotes>"));
        var value = new CdsExportDemographicNoteProjectionDto("a".repeat(64), base(), 1, mapped);
        assertThatThrownBy(
                        () ->
                                compose(
                                        xml -> {
                                            try {
                                                new CdsExportDemographicXmlWriter()
                                                        .writeWithPatientNote(value, xml);
                                            } catch (IOException deliberatelyCaught) {
                                                /* Deliberately swallow the first refusal to prove the composer stays poisoned. */
                                            }
                                            new CdsExportDemographicXmlWriter().write(base(), xml);
                                        }))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rejectsCrossPatientProjectionAndKeepsAbsentNativeRowsExplicit() throws Exception {
        var note = mapper.map(28, DEMOGRAPHICS, row(28, "Other patient"));
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicNoteProjectionDto(
                                        "a".repeat(64), base(), 1, note))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_NOTE_PROJECTION_INVALID");
        var absent = new CdsExportDemographicNoteProjectionDto("a".repeat(64), base(), 0, null);
        assertThat(absent.representable()).isTrue();
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicNoteProjectionDto(
                                        "a".repeat(64), base(), 1, null))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_NOTE_PROJECTION_INVALID");
    }

    @Test
    void protectsNoteDtoRenderingAndImmutableFieldEvidence() throws Exception {
        var mapped = mapper.map(27, DEMOGRAPHICS, row("PRIVATE NOTE"));
        assertThat(mapped.toString()).isEqualTo("CdsExportPatientNoteMappingDto[protected]");
        assertThatThrownBy(() -> mapped.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private CdsExportFieldAssessmentDto content(CdsExportPatientNoteMappingDto value) {
        return value.fields().entrySet().stream()
                .filter(entry -> entry.getKey().column().equals("content"))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    static CdsExportCapturedTextRowDto row(String text) {
        return row(27, text);
    }

    static CdsExportCapturedTextRowDto row(int patient, String text) {
        var values = new LinkedHashMap<String, String>();
        for (String column : List.of("cust1", "cust2", "cust4")) values.put(column, null);
        values.put("content", text);
        values.put("demographic_no", Integer.toString(patient));
        return new CdsExportCapturedTextRowDto(
                "demographiccust", CdsExportPatientTableSourceReader.key(patient), values);
    }

    private CdsExportDemographicMappingDto base() {
        var fields = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> fields.put(column, null));
        fields.putAll(
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
        return new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, fields));
    }

    private byte[] compose(CdsExportPatientNoteMappingDto note) throws Exception {
        var value = new CdsExportDemographicNoteProjectionDto("a".repeat(64), base(), 1, note);
        return compose(xml -> new CdsExportDemographicXmlWriter().writeWithPatientNote(value, xml));
    }

    private byte[] compose(CdsExportXmlComposer.Content content) throws IOException {
        var output = new ByteArrayOutputStream();
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                "a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        content);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "c".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        DEMOGRAPHICS,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        return output.toByteArray();
    }

    private String roundTrip(CdsExportPatientNoteMappingDto note) throws Exception {
        byte[] bytes = compose(note);
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes),
                        "27",
                        DEMOGRAPHICS,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        Path input = Files.write(root.resolve(UUID.randomUUID() + ".xml"), bytes);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        root.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return new CdsFieldParser().firstText(records.getFirst(), "NoteAboutPatient", 65536);
    }
}
