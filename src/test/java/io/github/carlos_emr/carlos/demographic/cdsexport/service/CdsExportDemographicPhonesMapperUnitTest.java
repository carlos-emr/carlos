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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicPhonesMapperUnitTest {
    @TempDir Path directory;
    private final CdsExportDemographicPhonesMapper mapper = new CdsExportDemographicPhonesMapper();

    @Test
    void shouldVerifyMapsBothExtensionsAndMobile_withoutCombiningOrReformattingNativeValues()
            throws Exception {
        var result =
                map(
                        List.of(
                                property(1, "hPhoneExt", "0012"),
                                property(2, "wPhoneExt", "AB-5"),
                                property(3, "demo_cell", "+1 613-555-0103")));
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(result.phones())
                .containsExactly(
                        new CdsExportDemographicMappingDto.Phone("R", "613-555-0101", "0012"),
                        new CdsExportDemographicMappingDto.Phone("W", "613-555-0102", "AB-5"),
                        new CdsExportDemographicMappingDto.Phone("C", "+1 613-555-0103", null));
        assertThat(result.relatedFields()).hasSize(24);
        assertThat(result.relatedFields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
    }

    @Test
    void
            shouldVerifyActualSchemaAndReceivingParserPreserveEachTypeNumberAndExtension_forDemographicPhonesMapperContract()
                    throws Exception {
        var result =
                map(
                        List.of(
                                property(1, "hPhoneExt", "0012"),
                                property(2, "wPhoneExt", "5"),
                                property(3, "demo_cell", "+1 613-555-0103")));
        var record = parse(result);
        var parser = new CdsFieldParser();
        var values = new LinkedHashMap<String, String>();
        parser.forEachField(
                record,
                field -> {
                    if (field.path().startsWith("PhoneNumber["))
                        try {
                            values.put(field.path(), parser.readLogicalText(record, field, 64));
                        } catch (IOException error) {
                            throw new UncheckedIOException(error);
                        }
                });
        assertThat(values)
                .containsEntry("PhoneNumber[1]/@phoneNumberType", "R")
                .containsEntry("PhoneNumber[1]/phoneNumber[1]", "613-555-0101")
                .containsEntry("PhoneNumber[1]/extension[1]", "0012")
                .containsEntry("PhoneNumber[2]/@phoneNumberType", "W")
                .containsEntry("PhoneNumber[2]/extension[1]", "5")
                .containsEntry("PhoneNumber[3]/@phoneNumberType", "C")
                .containsEntry("PhoneNumber[3]/phoneNumber[1]", "+1 613-555-0103");
    }

    @Test
    void shouldPreserveNativePresence_withoutEmittingAnEmptyPhoneExtension() throws Exception {
        var absent = map(List.of(property(1, "hPhoneExt", null)));
        var empty = map(List.of(property(1, "hPhoneExt", "")));
        assertThat(absent.phones().getFirst().extension()).isNull();
        assertThat(empty.phones().getFirst().extension()).isNull();
        assertThat(value(absent, 1).presence()).isEqualTo(Presence.SQL_NULL);
        assertThat(value(empty, 1).presence()).isEqualTo(Presence.EMPTY);
        parse(absent);
        parse(empty);
    }

    @Test
    void shouldDecideTheRowTheChartShows_andCarryAnotherRowOfARepeatedPhoneKey()
            throws Exception {
        // A table adopted without the one-row-per-key index (V1.0.54) can hold two rows of one
        // phone key. The chart shows the later row (DemographicExtDao.getLatestDemographicExt):
        // it gives the extension, and the other row travels in the native record.
        var later = changed(property(2, "hPhoneExt", "34"), "date_time", "2024-02-01 00:00:00");
        var result = map(List.of(property(1, "hPhoneExt", "12"), later));
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(result.phones().getFirst().extension()).isEqualTo("34");
        assertThat(result.relatedFields()).hasSize(16);
        assertCarriedRow(result, 1);
        assertThat(value(result, 2).destination()).isEqualTo("PhoneNumber[R]/extension");
        parse(result);
        // Equal times: the higher id is the row shown; equal values change nothing.
        var tied = map(List.of(property(1, "hPhoneExt", "12"), property(2, "hPhoneExt", "12")));
        assertThat(tied.baseRowRepresentable()).isTrue();
        assertThat(tied.phones().getFirst().extension()).isEqualTo("12");
        assertCarriedRow(tied, 1);
        assertThat(value(tied, 2).destination()).isEqualTo("PhoneNumber[R]/extension");
        parse(tied);
    }

    @Test
    void shouldCarryHiddenAndKeylessRows_insteadOfHoldingTheChart() throws Exception {
        // Only the REST API writes the hidden flag and an extra without a key; nothing in CARLOS
        // reads the flag. Such a row travels whole in the native record and never holds.
        for (var row :
                List.of(
                        changed(property(1, "hPhoneExt", "12"), "hidden", "1"),
                        changed(property(1, "hPhoneExt", "12"), "hidden", null))) {
            var result = map(List.of(row));
            assertThat(result.baseRowRepresentable()).isTrue();
            assertThat(result.phones().getFirst().extension()).isNull();
            assertThat(result.relatedFields()).hasSize(8);
            assertCarriedRow(result, 1);
            var hidden = field(result, 1, "hidden");
            assertThat(hidden.outcome()).isEqualTo(Outcome.MAPPED);
            assertThat(hidden.destination())
                    .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
            assertThat(hidden.rule()).isEqualTo(CdsExportDemographicPhonesMapper.HIDDEN_PROPERTY);
            parse(result);
        }
        for (String key : new String[] {null, ""}) {
            var result = map(List.of(property(1, key, "Additional value")));
            assertThat(result.baseRowRepresentable()).isTrue();
            assertThat(value(result, 1).destination())
                    .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
            assertThat(field(result, 1, "key_val").outcome()).isEqualTo(Outcome.ABSENT);
        }
        // A visible property with a key and no Demographics element travels in the native
        // demographic NewCategory record instead of holding the chart.
        var carried = map(List.of(property(1, "unknownPhonePurpose", "Additional value")));
        assertThat(carried.baseRowRepresentable()).isTrue();
        assertThat(value(carried, 1).destination())
                .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        var hidden =
                map(
                        List.of(
                                property(1, "demo_cell", "613-555-0000"),
                                changed(property(2, "demo_cell", "Older mobile"), "hidden", "1")));
        assertThat(hidden.baseRowRepresentable()).isTrue();
        assertThat(hidden.phones())
                .extracting(CdsExportDemographicMappingDto.Phone::number)
                .contains("613-555-0000");
        assertCarriedRow(hidden, 2);
    }

    private static CdsExportFieldAssessmentDto field(
            CdsExportDemographicMappingDto value, long id, String column) {
        return value.relatedFields()
                .get(
                        new CdsExportNativeFieldKeyDto(
                                "demographicExt", CdsExportPatientTableSourceReader.key(id), column));
    }

    /** The row's key and value travel in the native demographic record. */
    private static void assertCarriedRow(CdsExportDemographicMappingDto value, long id) {
        for (String column : List.of("key_val", "value")) {
            var decision = field(value, id, column);
            assertThat(decision.outcome()).isEqualTo(Outcome.MAPPED);
            assertThat(decision.destination())
                    .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        }
    }

    @Test
    void shouldAccountForTheRowsAMasterRecordSaveWrites_andCarryAPopulatedUnsupportedValue()
            throws Exception {
        // D5: DemographicUpdate2Action saves one demographicExt row per key the form lists, with a
        // NULL or empty value for each blank field, and edit-form-clinical.jsp posts
        // rxInteractionWarningLevel "0" ("Not specified") for a patient without a level.
        var saved = new ArrayList<CdsExportCapturedTextRowDto>();
        long id = 0;
        for (String key :
                List.of(
                        "aboriginal",
                        "ethnicity",
                        "area",
                        "given_consent",
                        "primaryEMR",
                        "usSigned",
                        "privacyConsent",
                        "informedConsent",
                        "paper_chart_archived_date",
                        "HasPrimaryCarePhysician",
                        "EmploymentStatus"))
            saved.add(property(++id, key, null));
        for (String key :
                List.of("cytolNum", "phoneComment", "paper_chart_archived", "paper_chart_archived_program", "PHU"))
            saved.add(property(++id, key, ""));
        saved.add(property(++id, "rxInteractionWarningLevel", "0"));
        var result = map(saved);
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(result.relatedFields().values())
                .noneMatch(field -> field.outcome() == Outcome.REQUIRES_REVIEW);
        assertThat(
                        result.relatedFields()
                                .get(
                                        new CdsExportNativeFieldKeyDto(
                                                "demographicExt",
                                                CdsExportPatientTableSourceReader.key(1),
                                                "key_val"))
                                .rule())
                .isEqualTo("PROPERTY_KEY_OF_AN_EMPTY_EXTENSION");
        assertThat(value(result, id).rule()).isEqualTo("UI_PLACEHOLDER_NOT_A_PATIENT_FACT");
        // A value somebody chose has no Demographics element and is carried, never held.
        for (var chosen :
                List.of(
                        property(1, "aboriginal", "Yes"),
                        property(1, "rxInteractionWarningLevel", "3"),
                        property(1, "cytolNum", "12345"))) {
            var mapped = map(List.of(chosen));
            assertThat(mapped.baseRowRepresentable()).isTrue();
            assertThat(value(mapped, 1))
                    .isEqualTo(
                            new CdsExportFieldAssessmentDto(
                                    Presence.VALUE,
                                    Outcome.MAPPED,
                                    CdsExportDemographicSupportingMapper.DESTINATION,
                                    CdsExportDemographicSupportingMapper.CARRIED_RULE));
        }
    }

    @Test
    void
            shouldVerifyOrphanedNonemptyExtensionsCannotCreateAPhoneNumber_forDemographicPhonesMapperContract()
                    throws Exception {
        var raw = raw();
        var values = new TreeMap<>(raw.fields());
        values.put("phone", null);
        var current = new CdsExportDemographicRowDto(27, values);
        var base = new CdsExportDemographicMapper().map(current);
        var result =
                mapper.map(
                        base,
                        current,
                        List.of(property(1, "hPhoneExt", "12")),
                        () -> {});
        // The extension left after its number was cleared is carried, never a phone of its own.
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(value(result, 1).destination())
                .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
        assertThat(result.phones())
                .extracting(CdsExportDemographicMappingDto.Phone::type)
                .containsExactly("W");
        for (String missing : Arrays.asList(null, ""))
            assertThat(
                            mapper.map(
                                            base,
                                            current,
                                            List.of(property(1, "hPhoneExt", missing)),
                                            () -> {})
                                    .baseRowRepresentable())
                    .isTrue();
    }

    @Test
    void shouldVerifyClearedMobileRowsStayAbsentAndAreNotRecovered_fromAnotherProperty()
            throws Exception {
        for (String missing : Arrays.asList(null, "")) {
            var result = map(List.of(property(1, "demo_cell", missing)));
            assertThat(result.baseRowRepresentable()).isTrue();
            assertThat(result.phones()).hasSize(2);
            assertThat(value(result, 1).outcome()).isEqualTo(Outcome.ABSENT);
        }
    }

    @Test
    void
            shouldVerifyWireBoundsCountUnicodeCodePointsAndDoNotTruncate_forDemographicPhonesMapperContract()
                    throws Exception {
        var result =
                map(
                        List.of(
                                property(1, "hPhoneExt", "😀".repeat(5)),
                                property(2, "demo_cell", "😀".repeat(25))));
        assertThat(result.baseRowRepresentable()).isTrue();
        parse(result);
        // A value the CDS phone token cannot hold is carried exactly, never truncated or held.
        for (var row :
                List.of(
                        property(1, "hPhoneExt", "😀".repeat(6)),
                        property(1, "demo_cell", "😀".repeat(26)),
                        property(1, "hPhoneExt", "12  3"),
                        property(1, "demo_cell", " leading"),
                        property(1, "hPhoneExt", "1\t2"),
                        property(1, "demo_cell", "bad\u0001XML"))) {
            var mapped = map(List.of(row));
            assertThat(mapped.baseRowRepresentable()).isTrue();
            assertThat(value(mapped, 1).destination())
                    .isEqualTo(CdsExportDemographicSupportingMapper.DESTINATION);
            assertThat(mapped.phones()).noneMatch(phone -> row.fields().get("value").equals(phone.extension()) || row.fields().get("value").equals(phone.number()));
        }
        // A character no XML document can hold is carried too, as its JSON escape.
        assertThat(
                        map(List.of(property(1, "demo_cell", "bad\ud800surrogate")))
                                .baseRowRepresentable())
                .isTrue();
    }

    @Test
    void
            shouldVerifyCrossPatientRowsKeysAndUnknownColumnsCannotBorrowAnotherBaseProjection_forDemographicPhonesMapperContract()
                    throws Exception {
        var row = property(1, "hPhoneExt", "12");
        assertThatThrownBy(() -> map(List.of(changed(row, "demographic_no", "28"))))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(
                                                new CdsExportCapturedTextRowDto(
                                                        row.table(), "1", row.fields()))))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID");
        var values = new TreeMap<>(row.fields());
        values.put("unexpected", "value");
        assertThatThrownBy(
                        () ->
                                map(
                                        List.of(
                                                new CdsExportCapturedTextRowDto(
                                                        row.table(), row.recordKey(), values))))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID");
        assertThatThrownBy(() -> map(List.of(row, row)))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_SCOPE_INVALID");
    }

    @Test
    void shouldMapEveryProperty_whenAChartHasMoreThan8192OfThem() throws Exception {
        // Before: the 8,193rd demographicExt row stopped the whole job
        // (CDS_EXPORT_DEMOGRAPHIC_PHONE_RESOURCE_LIMIT).
        var current = raw();
        var base = new CdsExportDemographicMapper().map(current);
        var rows = new java.util.ArrayList<CdsExportCapturedTextRowDto>();
        rows.add(property(1, "hPhoneExt", "12"));
        for (int id = 2; id <= 8193; id++) rows.add(property(id, "clinicNote" + id, "value " + id));
        var mapped = mapper.map(base, current, rows, () -> {});
        assertThat(mapped.phones()).anySatisfy(phone -> assertThat(phone.extension()).isEqualTo("12"));
        assertThat(mapped.relatedFields().keySet().stream().map(key -> key.recordKey()).distinct())
                .hasSize(8193);
    }

    @Test
    void shouldMapPhones_pastTheFormerRowAndByteBoundsAndStillHonourCancellation() throws Exception {
        var current = raw();
        var base = new CdsExportDemographicMapper().map(current);
        var rows = List.of(property(1, "hPhoneExt", "12"), property(2, "wPhoneExt", "5"));
        assertThat(mapper.map(base, current, rows, () -> {})).isNotNull();
        assertThat(mapper.map(base, current, rows, () -> {})).isNotNull();
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        base,
                                        current,
                                        rows,
                                        () -> {
                                            throw new IllegalStateException("CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
        assertThat(base.phones()).allSatisfy(phone -> assertThat(phone.extension()).isNull());
        assertThat(base.relatedFields()).isEmpty();
    }

    @Test
    void
            shouldVerifyAnUnrelatedPropertyReviewDoesNotEraseAnOtherwiseValidPrimaryPhysician_forDemographicPhonesMapperContract()
                    throws Exception {
        var nativeRow = raw();
        var values = new TreeMap<>(nativeRow.fields());
        values.put("provider_no", "A");
        nativeRow = new CdsExportDemographicRowDto(27, values);
        var provider = CdsExportEnrolmentHistoryUnitTest.provider("A");
        var base = new CdsExportDemographicMapper().map(nativeRow, provider);
        var result =
                mapper.map(
                        base,
                        nativeRow,
                        List.of(property(1, "unmapped", "Extra\uFFFF")),
                        () -> {});
        assertThat(result.primaryPhysician()).isEqualTo(base.primaryPhysician());
        // The property is carried in the native demographic record; it holds nothing.
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(result.relatedFields()).hasSize(14);
    }

    @Test
    void
            shouldVerifyALaterPrimaryPhysicianJoinPreservesPhoneValuesAndTheirIndependentFieldEvidence_forDemographicPhonesMapperContract()
                    throws Exception {
        var raw = raw();
        var fields = new TreeMap<>(raw.fields());
        fields.put("provider_no", "A");
        raw = new CdsExportDemographicRowDto(27, fields);
        var provider =
                new CdsExportPublicProviderMapper()
                        .map(
                                CdsExportEnrolmentHistoryUnitTest.provider("A"),
                                CdsExportPublicProviderMapper.Target.PRIMARY_PHYSICIAN);
        var base =
                new CdsExportDemographicMapper()
                        .map(raw, CdsExportEnrolmentHistoryUnitTest.provider("A"));
        var mapped =
                mapper.map(
                        base,
                        raw,
                        List.of(
                                property(1, "hPhoneExt", "12"),
                                property(2, "demo_cell", "613-555-0103")),
                        () -> {});
        var joined =
                mapped.withPrimaryPhysician(
                        provider.value(), provider.fields(), mapped.fields().get("provider_no"));
        assertThat(joined.baseRowRepresentable()).isTrue();
        assertThat(joined.phones()).isEqualTo(mapped.phones());
        assertThat(joined.relatedFields()).isEqualTo(mapped.relatedFields()).hasSize(22);
        parse(joined);
    }

    @Test
    void
            shouldVerifyPhoneDtosCannotBypassTheWriterLexicalGate_forDemographicPhonesMapperContract() {
        for (String number :
                List.of(
                        "x".repeat(26),
                        " leading",
                        "trailing ",
                        "two  spaces",
                        "line\nnumber",
                        "bad\u0001XML"))
            assertThatThrownBy(() -> new CdsExportDemographicMappingDto.Phone("R", number))
                    .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_INVALID");
        assertThatThrownBy(() -> new CdsExportDemographicMappingDto.Phone("X", "123"))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_INVALID");
        assertThatThrownBy(() -> new CdsExportDemographicMappingDto.Phone(null, "123"))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_INVALID");
    }

    static CdsExportDemographicRowDto raw() {
        var fields = new TreeMap<String, String>();
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
                        "sex",
                        "U",
                        "patient_status",
                        "AC",
                        "phone",
                        "613-555-0101",
                        "phone2",
                        "613-555-0102"));
        return new CdsExportDemographicRowDto(27, fields);
    }

    static CdsExportCapturedTextRowDto property(long id, String key, String value) {
        var fields = new TreeMap<String, String>();
        CdsExportPatientTableReadSupport.Plan.DEMOGRAPHIC_EXT
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.putAll(
                Map.of(
                        "id",
                        Long.toString(id),
                        "demographic_no",
                        "27",
                        "provider_no",
                        "A",
                        "hidden",
                        "0",
                        "date_time",
                        "2024-01-01 12:34:56"));
        fields.put("key_val", key);
        fields.put("dashboard_single_value_key", key);
        fields.put("value", value);
        return new CdsExportCapturedTextRowDto(
                "demographicExt", CdsExportPatientTableSourceReader.key(id), fields);
    }

    private static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new TreeMap<>(row.fields());
        fields.put(column, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    private static CdsExportFieldAssessmentDto value(
            CdsExportDemographicMappingDto value, long id) {
        return value.relatedFields()
                .get(
                        new CdsExportNativeFieldKeyDto(
                                "demographicExt",
                                CdsExportPatientTableSourceReader.key(id),
                                "value"));
    }

    private CdsExportDemographicMappingDto map(List<CdsExportCapturedTextRowDto> rows)
            throws Exception {
        var current = raw();
        return mapper.map(
                new CdsExportDemographicMapper().map(current),
                current,
                rows,
                () -> {});
    }

    private CdsRecordDto parse(CdsExportDemographicMappingDto value) throws Exception {
        String hash = "a".repeat(64);
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(hash, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().write(value, xml));
        var wire = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        wire,
                        hash,
                        hash,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(wire.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        ignored -> {},
                        () -> {});
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(
                                directory.resolve(UUID.randomUUID() + ".xml"), wire.toByteArray()),
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records.getFirst();
    }
}
