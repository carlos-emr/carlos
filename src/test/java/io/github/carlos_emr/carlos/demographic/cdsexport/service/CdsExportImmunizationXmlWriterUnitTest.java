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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-export")
class CdsExportImmunizationXmlWriterUnitTest {
    @TempDir Path directory;
    private final CdsExportPreparedImmunizationMapper mapper =
            new CdsExportPreparedImmunizationMapper();

    @Test
    void shouldVerifyPreservesEveryStandardFieldAndRefusedFlagThroughPinnedSchemaAndReceivingImporter_forImmunizationXmlWriterContract()
            throws Exception {
        for (var profile : CdsLogicalProfile.values())
            for (boolean refused : new boolean[] {true, false}) {
                var source =
                        source(
                                CdsTemporal.parse(
                                        null, null, null, "2024-02-29T12:34:56.1200-05:00", "Date"),
                                refused);
                assertThat(roundTrip(mapper.map(source), profile)).isEqualTo(source);
            }
    }

    @Test
    void shouldVerifyPreservesEachCalendarPrecision_withoutInsertingAnInventedDay() throws Exception {
        for (var date :
                List.of(
                        CdsTemporalDto.unknown(),
                        CdsTemporal.parse("2020", null, null, null, "Date"),
                        CdsTemporal.parse(null, "2020-02", null, null, "Date"),
                        CdsTemporal.parse(null, null, "2020-02-29", null, "Date"))) {
            var source = source(date, true);
            var received = roundTrip(mapper.map(source), CdsLogicalProfile.ONTARIO_MD_5_2);
            assertThat(received.date()).isEqualTo(date);
            assertThat(received.refused()).isTrue();
        }
    }

    @Test
    void shouldVerifyPreservesSealedDateShift_insteadOfRestoringOriginalXml() throws Exception {
        var shifted =
                new CdsTemporalDto(
                        CdsTemporalDto.Precision.FULL_DATE,
                        "2020-02-29",
                        java.time.LocalDate.of(2020, 3, 1),
                        java.time.LocalDate.of(2020, 3, 1),
                        null,
                        null,
                        1);
        var received =
                roundTrip(mapper.map(source(shifted, false)), CdsLogicalProfile.ONTARIO_MD_5_2);
        assertThat(received.date().original()).isEqualTo("2020-03-01");
    }

    @Test
    void shouldVerifyPreservesExactEmptyOptionalTextAndUnicode_withoutTrimming() throws Exception {
        var value =
                new CdsExportImmunizationDto(
                        " Physician's café 😀 entry ",
                        null,
                        "",
                        "",
                        "",
                        "",
                        "",
                        null,
                        null,
                        new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null),
                        false,
                        "",
                        " Leading café 😀\r\nTrailing ");
        var received = roundTrip(value, CdsLogicalProfile.ONTARIO_MD_5_2);
        assertThat(received.name()).isEqualTo(value.name());
        assertThat(received.notes()).isEqualTo(value.notes());
        assertThat(received.manufacturer()).isEmpty();
        assertThat(received.instructions()).isEmpty();
    }

    @Test
    void shouldVerifyRefusesUnlistedAndNormalizedTypeAliases_beforeWritingAnyStandardValue() throws Exception {
        for (String type : List.of("H1N1", "COVID-19", "Td ", " TD", "UNKNOWN_TYPE"))
            assertThatThrownBy(
                            () -> compose(replace("type", type), CdsLogicalProfile.ONTARIO_MD_5_2))
                    .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyTheDictionary32000BoundaryIsEnforcedBelowTheBroaderWireFacet_forImmunizationXmlWriterContract() throws Exception {
        assertThat(
                        roundTrip(
                                        replace("notes", "😀".repeat(32000)),
                                        CdsLogicalProfile.ONTARIO_MD_5_2)
                                .notes()
                                .codePointCount(0, 64000))
                .isEqualTo(32000);
        assertThatThrownBy(
                        () ->
                                compose(
                                        replace("notes", "😀".repeat(32001)),
                                        CdsLogicalProfile.ONTARIO_MD_5_2))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyRefusesMissingBlankOrOverlongRequiredName_forImmunizationXmlWriterContract() throws Exception {
        for (String name : Arrays.asList(null, "", "  ", "😀".repeat(121))) {
            var value = replace("name", name);
            assertThatThrownBy(() -> compose(value, CdsLogicalProfile.ONTARIO_MD_5_2))
                    .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldVerifyRefusesOversizedOptionalFieldsAndIllegalXml_withoutTruncation() throws Exception {
        for (String field :
                List.of("manufacturer", "lot", "route", "site", "dose", "instructions", "notes")) {
            int limit = field.equals("notes") ? 32000 : field.equals("instructions") ? 250 : 120;
            for (String text : List.of("😀".repeat(limit + 1), "invalid\u0001value"))
                assertThatThrownBy(
                                () ->
                                        compose(
                                                replace(field, text),
                                                CdsLogicalProfile.ONTARIO_MD_5_2))
                        .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldVerifyAcceptsCodePointLimit_withoutTreatingSurrogatePairsAsTwoCharacters() throws Exception {
        var value = replace("name", "😀".repeat(120));
        assertThat(roundTrip(value, CdsLogicalProfile.ONTARIO_MD_5_2).name())
                .isEqualTo(value.name());
    }

    @Test
    void shouldVerifyRefusesUnverifiedCodingSystemsOrInvalidDin_insteadOfPaddingOrInventingAuthority()
            throws Exception {
        for (String din : List.of("1234567", "123456789", "abcdefgh", "１２３４５６７８", " 00123456"))
            assertThatThrownBy(
                            () -> compose(replace("code", din), CdsLogicalProfile.ONTARIO_MD_5_2))
                    .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                compose(
                                        replace("codeSystem", "SNOMED CT"),
                                        CdsLogicalProfile.ONTARIO_MD_5_2))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_VALUES_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyRejectsIncompleteCodePairsAtConstruction_forImmunizationXmlWriterContract() {
        assertThatThrownBy(() -> replace("code", null))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_CODE_INCOMPLETE");
        assertThatThrownBy(() -> replace("codeSystem", null))
                .hasMessage("CDS_EXPORT_IMMUNIZATION_CODE_INCOMPLETE");
    }

    @Test
    void shouldVerifyUnknownImmunizationTypeMustFailThePinnedSchema_forImmunizationXmlWriterContract() throws Exception {
        assertThatThrownBy(
                        () ->
                                roundTrip(
                                        replace("type", "Invented vaccine type"),
                                        CdsLogicalProfile.ONTARIO_MD_5_2))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyCaughtInvalidEventStillPoisonsTheWholeDocument_forImmunizationXmlWriterContract() throws Exception {
        var good = mapper.map(source(CdsTemporalDto.unknown(), false));
        var bad = replace("name", "");
        assertThatThrownBy(
                        () ->
                                compose(
                                        xml -> {
                                            try {
                                                new CdsExportImmunizationXmlWriter()
                                                        .write(bad, xml);
                                            } catch (IOException deliberatelyCaught) {
                                                /* Deliberately swallow the first refusal to prove the composer stays poisoned. */
                                            }
                                            new CdsExportImmunizationXmlWriter().write(good, xml);
                                        },
                                        CdsLogicalProfile.ONTARIO_MD_5_2))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyProtectedDtoDoesNotRenderClinicalValues_forImmunizationXmlWriterContract() throws Exception {
        assertThat(mapper.map(source(CdsTemporalDto.unknown(), true)).toString())
                .isEqualTo("CdsExportImmunizationDto[protected]");
    }

    private CdsImmunizationDto source(CdsTemporalDto date, boolean refused) {
        return new CdsImmunizationDto(
                "Synthetic café 😀 vaccine",
                "MMR",
                "Manufacturer café",
                "00A123",
                "Intramuscular",
                "Left arm",
                "0.5 mL",
                "00123456",
                "DIN",
                refused,
                "Series 1 of 2",
                "Exact original café 😀\r\nProvider note",
                date);
    }

    private CdsExportImmunizationDto replace(String field, String text) throws Exception {
        var source = mapper.map(source(CdsTemporalDto.unknown(), false));
        return new CdsExportImmunizationDto(
                field.equals("name") ? text : source.name(),
                field.equals("type") ? text : source.type(),
                field.equals("manufacturer") ? text : source.manufacturer(),
                field.equals("lot") ? text : source.lot(),
                field.equals("route") ? text : source.route(),
                field.equals("site") ? text : source.site(),
                field.equals("dose") ? text : source.dose(),
                field.equals("code") ? text : source.code(),
                field.equals("codeSystem") ? text : source.codeSystem(),
                source.date(),
                source.refused(),
                field.equals("instructions") ? text : source.instructions(),
                field.equals("notes") ? text : source.notes());
    }

    private byte[] compose(CdsExportImmunizationDto value, CdsLogicalProfile profile)
            throws Exception {
        return compose(xml -> new CdsExportImmunizationXmlWriter().write(value, xml), profile);
    }

    private byte[] compose(CdsExportXmlComposer.Content content, CdsLogicalProfile profile)
            throws Exception {
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
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
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
                                        "a".repeat(64), CdsCategory.IMMUNIZATIONS, "c".repeat(64)),
                                content));
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        "a".repeat(64),
                        "2026.08.0-test",
                        profile,
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS),
                        sections.iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        return output.toByteArray();
    }

    private CdsImmunizationDto roundTrip(CdsExportImmunizationDto value, CdsLogicalProfile profile)
            throws Exception {
        byte[] bytes = compose(value, profile);
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS);
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes),
                        "27",
                        selected,
                        "2026.08.0-test",
                        profile,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        Path input = Files.write(directory.resolve(UUID.randomUUID() + ".xml"), bytes);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        directory.resolve(UUID.randomUUID().toString()),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return new CdsClinicalNormalizationService(new CdsFieldParser())
                .immunization(records.get(1));
    }
}
