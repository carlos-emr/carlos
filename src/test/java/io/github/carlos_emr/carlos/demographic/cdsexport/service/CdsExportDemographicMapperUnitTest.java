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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto.Presence;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicMapperUnitTest {
    private static final String VERSION = "2026.08.0-test";
    private final CdsExportDemographicMapper mapper = new CdsExportDemographicMapper();

    @Test
    void shouldVerifyMissingJurisdictionCannotGuessThePostalCodeChoice_forEitherAddress() {
        for (String prefix : java.util.List.of("mailing", "residential")) {
            var fields = baseline();
            String postal = prefix.equals("mailing") ? "postal" : "residentialPostal";
            fields.put(postal, "12345");
            var result = mapper.map(row(fields));
            assertThat(result.baseRowRepresentable()).isFalse();
            assertThat(result.fields().get(postal).rule())
                    .isEqualTo("POSTAL_JURISDICTION_REQUIRES_REVIEW");
            assertThat(result.addresses())
                    .allSatisfy(address -> assertThat(address.postalCode()).isNull());
        }
    }

    @Test
    void shouldWriteActualPinnedSchemaXml_andPreserveNativeIdentityAndContactValues()
            throws Exception {
        var fields = baseline();
        fields.putAll(
                Map.of(
                        "title",
                        "DR",
                        "middleNames",
                        "Marie",
                        "chart_no",
                        "00027",
                        "hin",
                        "0123456789",
                        "hc_type",
                        "ON",
                        "ver",
                        "AB",
                        "hc_renew_date",
                        "2031-02-28"));
        fields.putAll(
                Map.of(
                        "address",
                        "27 rue École",
                        "city",
                        "Ottawa",
                        "province",
                        "ON",
                        "postal",
                        "K1A 0B1",
                        "residentialAddress",
                        "18 Main St",
                        "residentialCity",
                        "Gatineau",
                        "residentialProvince",
                        "QC",
                        "residentialPostal",
                        "J8X3Y8"));
        fields.putAll(
                Map.of(
                        "phone",
                        "613-555-0101",
                        "phone2",
                        "613-555-0102",
                        "official_lang",
                        "French",
                        "spoken_lang",
                        "French",
                        "email",
                        "synthetic@example.ca",
                        "sin",
                        "012345678",
                        "patient_status_date",
                        "2026-11-05"));
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(59);
        byte[] bytes = xml(result);
        var validation =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(bytes),
                                "27",
                                Set.of(CdsCategory.DEMOGRAPHICS),
                                VERSION,
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                CdsExportXmlLimitsDto.defaults(),
                                ignored -> {},
                                () -> {});
        assertThat(validation.occurrences()).containsEntry(CdsCategory.DEMOGRAPHICS, 1L);
        Document document = parse(bytes);
        assertThat(text(document, "cds_dt", "Part")).isEqualTo("Élodie😀");
        assertThat(text(document, "cds", "DateOfBirth")).isEqualTo("2000-02-29");
        assertThat(text(document, "cds_dt", "NamePrefix")).isEqualTo("Dr");
        assertThat(text(document, "cds_dt", "Number")).isEqualTo("0123456789");
        assertThat(text(document, "cds_dt", "ProvinceCode")).isEqualTo("CA-ON");
        assertThat(text(document, "cds_dt", "Line1")).isEqualTo("27 rue École");
        assertThat(text(document, "cds_dt", "PostalCode")).isEqualTo("K1A0B1");
        assertThat(text(document, "cds", "PreferredOfficialLanguage")).isEqualTo("FRE");
        assertThat(text(document, "cds", "PreferredSpokenLanguage")).isEqualTo("fre");
        assertThat(text(document, "cds", "PersonStatusAsEnum")).isEqualTo("A");
        assertThat(text(document, "cds", "SIN")).isEqualTo("012345678");
        assertThat(document.getElementsByTagNameNS("cds", "Address").getLength()).isEqualTo(2);
        assertThat(document.getElementsByTagNameNS("cds", "PhoneNumber").getLength()).isEqualTo(2);
        assertThat(result.fields().get("postal").rule())
                .isEqualTo("CANADIAN_POSTAL_DISPLAY_SEPARATOR");
        assertThat(result.toString()).doesNotContain("Élodie", "0123456789");
    }

    @Test
    void shouldBlockIncompleteInvalidAndPlaceholderBirthDates_withoutInventingADay() {
        for (String day : new String[] {null, "", "00", "30", "unknown"}) {
            var fields = baseline();
            fields.put("date_of_birth", day);
            var result = mapper.map(row(fields));
            assertThat(result.baseRowRepresentable()).isFalse();
            assertThat(result.birthDate()).isNull();
            assertThat(result.fields().get("date_of_birth").outcome())
                    .isEqualTo(Outcome.REQUIRES_REVIEW);
            assertThatThrownBy(() -> xml(result))
                    .hasMessage("CDS_EXPORT_DEMOGRAPHIC_MAPPING_REQUIRES_REVIEW");
        }
        var fields = baseline();
        fields.put("year_of_birth", "1900");
        assertThat(mapper.map(row(fields)).birthDate()).isNull();
    }

    @Test
    void shouldBlockUnknownAdministrativeGender_andNeverDefaultItFromIdentityOrPronouns() {
        for (String sex : new String[] {null, "", "T", "X", "female"}) {
            var fields = baseline();
            fields.put("sex", sex);
            fields.put("gender", "Woman");
            fields.put("pronoun", "she/her");
            var result = mapper.map(row(fields));
            assertThat(result.administrativeGender()).isNull();
            assertThat(result.fields().get("sex").outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
            assertThat(result.fields().get("gender").outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
            assertThat(result.fields().get("pronoun").outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
        }
    }

    @Test
    void shouldAccountForEveryPopulatedAdditionalField_withoutMovingItIntoPatientNotes() {
        var fields = baseline();
        fields.putAll(
                Map.of(
                        "alias",
                        "Name used",
                        "citizenship",
                        "Canadian",
                        "children",
                        "2",
                        "sourceOfIncome",
                        "Employment",
                        "provider_no",
                        "000027",
                        "roster_status",
                        "RO",
                        "anonymous",
                        "0",
                        "consentToUseEmailForCare",
                        "1"));
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isFalse();
        for (String column :
                Set.of(
                        "alias",
                        "citizenship",
                        "children",
                        "sourceOfIncome",
                        "provider_no",
                        "roster_status",
                        "anonymous",
                        "consentToUseEmailForCare")) {
            assertThat(result.fields().get(column).outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
        }
        assertThatThrownBy(() -> xml(result))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_MAPPING_REQUIRES_REVIEW");
    }

    @Test
    void shouldDistinguishSqlNullAndEmpty_fromMappedAndOperationalFields() {
        var fields = baseline();
        fields.put("middleNames", "");
        fields.put("alias", null);
        fields.put("lastUpdateUser", "000027");
        var result = mapper.map(row(fields));
        assertThat(result.fields().get("middleNames").presence()).isEqualTo(Presence.EMPTY);
        assertThat(result.fields().get("alias").presence()).isEqualTo(Presence.SQL_NULL);
        assertThat(result.fields().get("lastUpdateUser").outcome()).isEqualTo(Outcome.OPERATIONAL);
        assertThat(result.fields().get("first_name").outcome()).isEqualTo(Outcome.MAPPED);
        assertThatThrownBy(() -> result.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldBlockTokenNormalizationAndCapacityChanges_insteadOfTrimmingOrSplittingNames() {
        for (String first :
                List.of(
                        " leading",
                        "trailing ",
                        "two  spaces",
                        "line\nname",
                        "x".repeat(51),
                        "bad\u0000name")) {
            var fields = baseline();
            fields.put("first_name", first);
            var result = mapper.map(row(fields));
            assertThat(result.baseRowRepresentable()).isFalse();
            assertThat(result.names().first()).isNull();
        }
        var fields = baseline();
        fields.put("middleNames", "a".repeat(51));
        assertThat(mapper.map(row(fields)).fields().get("middleNames").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldCountUnicodeCodePoints_withoutTruncatingSupplementaryCharacters() throws Exception {
        var fields = baseline();
        fields.put("first_name", "😀".repeat(50));
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(text(parse(xml(result)), "cds_dt", "Part")).isEqualTo("😀".repeat(50));
    }

    @Test
    void shouldRequireHealthCardNumberAndIssuer_andRejectZeroDatesAndUnrepresentableEmail() {
        var fields = baseline();
        fields.put("hin", "0123456789");
        assertThat(mapper.map(row(fields)).fields().get("hc_type").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
        fields.put("hin", "");
        fields.put("hc_type", "ON");
        assertThat(mapper.map(row(fields)).fields().get("hin").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
        fields = baseline();
        fields.put("email", "synthetic+tag@example.ca");
        fields.put("patient_status_date", "0000-00-00");
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isFalse();
        assertThat(result.fields().get("email").outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
        assertThat(result.fields().get("patient_status_date").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldKeepCustomPatientStatusAsPlainText_withoutAssumingItMeansActive() throws Exception {
        var fields = baseline();
        fields.put("patient_status", "Transferred & pending");
        var result = mapper.map(row(fields));
        assertThat(result.enumeratedStatus()).isFalse();
        assertThat(text(parse(xml(result)), "cds", "PersonStatusAsPlainText"))
                .isEqualTo("Transferred & pending");
    }

    @Test
    void shouldPreservePinnedUsAndExplicitUnknownHealthAuthorities_withoutInventingAKnownIdentity()
            throws Exception {
        for (String code : List.of("US-NY", "-50", "-70", "-90")) {
            var fields = baseline();
            fields.put("hin", "0123456789");
            fields.put("hc_type", code);
            var result = mapper.map(row(fields));
            assertThat(result.baseRowRepresentable()).isTrue();
            assertThat(text(parse(xml(result)), "cds_dt", "ProvinceCode")).isEqualTo(code);
        }
        var fields = baseline();
        fields.put("hin", "0123456789");
        fields.put("hc_type", "NY");
        assertThat(mapper.map(row(fields)).baseRowRepresentable()).isFalse();
    }

    @Test
    void shouldChooseTheZipCodeArm_forAnExplicitUsAddress() throws Exception {
        var fields = baseline();
        fields.put("address", "27 Synthetic St");
        fields.put("province", "US-NY");
        fields.put("postal", "12345-6789");
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isTrue();
        Document document = parse(xml(result));
        assertThat(text(document, "cds_dt", "ZipCode")).isEqualTo("12345-6789");
        assertThat(document.getElementsByTagNameNS("cds_dt", "PostalCode").getLength()).isZero();
        assertThat(result.fields().get("postal").destination()).endsWith("/PostalZipCode/ZipCode");
        fields.put("postal", "ABC123");
        assertThat(mapper.map(row(fields)).baseRowRepresentable()).isFalse();
    }

    private static Map<String, String> baseline() {
        var result = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> result.put(column, null));
        result.putAll(
                Map.of(
                        "first_name",
                        "Élodie😀",
                        "last_name",
                        "D’Arcy",
                        "year_of_birth",
                        "2000",
                        "month_of_birth",
                        "2",
                        "date_of_birth",
                        "29",
                        "sex",
                        "F",
                        "patient_status",
                        "AC"));
        return result;
    }

    private static CdsExportDemographicRowDto row(Map<String, String> values) {
        return new CdsExportDemographicRowDto(27, values);
    }

    private static byte[] xml(CdsExportDemographicMappingDto patient) throws Exception {
        var output = new ByteArrayOutputStream();
        var identity =
                new CdsExportIdentityDto("a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64));
        var section =
                new CdsExportXmlComposer.Section(
                        identity,
                        writer -> new CdsExportDemographicXmlWriter().write(patient, writer));
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "c".repeat(64),
                        "a".repeat(64),
                        VERSION,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        List.of(section).iterator(),
                        ignored -> {},
                        1_048_576,
                        () -> {});
        byte[] bytes = output.toByteArray();
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes),
                        "27",
                        Set.of(CdsCategory.DEMOGRAPHICS),
                        VERSION,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        return bytes;
    }

    private static Document parse(byte[] xml) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultNSInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static String text(Document document, String namespace, String name) {
        return document.getElementsByTagNameNS(namespace, name).item(0).getTextContent();
    }
}
