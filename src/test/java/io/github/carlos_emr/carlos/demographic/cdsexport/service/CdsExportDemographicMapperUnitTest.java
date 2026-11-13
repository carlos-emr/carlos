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
            // Neither arm is guessed: the stored postal code travels in the native record.
            assertThat(result.baseRowRepresentable()).isTrue();
            assertCarried(result, postal);
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
        for (String sex : new String[] {null, "", "T", "female"}) {
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
        // The add form's "X" (Intersex) is Administrative Gender O; the exact code is carried.
        var fields = baseline();
        fields.put("sex", "X");
        var intersex = mapper.map(row(fields));
        assertThat(intersex.administrativeGender()).isEqualTo("O");
        assertCarried(intersex, "sex");
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
        // Untrimmed or doubled whitespace: every schema reader collapses an xs:token, so the
        // collapsed name is written and the exact stored value travels in the native record.
        for (var spaced :
                Map.of(
                                " leading", "leading",
                                "trailing ", "trailing",
                                "two  spaces", "two spaces",
                                "line\nname", "line name")
                        .entrySet()) {
            var fields = baseline();
            fields.put("first_name", spaced.getKey());
            var result = mapper.map(row(fields));
            assertThat(result.names().first()).isEqualTo(spaced.getValue());
            assertCarried(result, "first_name");
        }
        for (String first : List.of("x".repeat(51), "bad\u0000name")) {
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
        // A number without an issuer is not guessed into a card: it travels in the record.
        assertCarried(mapper.map(row(fields)), "hin");
        // Updated for D11: an issuer alone (the form default) is no card; a version or expiry
        // without the number still requires review.
        fields.put("hin", "");
        fields.put("hc_type", "ON");
        fields.put("ver", "AB");
        assertThat(mapper.map(row(fields)).fields().get("hin").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
        fields = baseline();
        fields.put("email", "synthetic+tag@example.ca");
        fields.put("patient_status_date", "0000-00-00");
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isFalse();
        // A "+" tag is outside the CDS e-mail pattern: left out and carried, never a hold.
        assertCarried(result, "email");
        assertThat(result.email()).isNull();
        assertThat(result.fields().get("patient_status_date").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldOmitTheHealthCard_whenOnlyTheFormDefaultIssuerIsPresent() throws Exception {
        // D11: add-form-personal.jsp preselects the clinic province (or "OT") as hc_type, so an
        // uninsured patient, or one whose number is pending, has an issuer and no card.
        for (String issuer : List.of("ON", "OT")) {
            var fields = baseline();
            fields.put("hin", "");
            fields.put("ver", "");
            fields.put("hc_type", issuer);
            var result = mapper.map(row(fields));
            assertThat(result.baseRowRepresentable()).isTrue();
            assertThat(result.healthCard()).isNull();
            assertThat(result.fields().get("hc_type").outcome()).isEqualTo(Outcome.OPERATIONAL);
            assertThat(result.fields().get("hc_type").rule())
                    .isEqualTo("HEALTH_CARD_ISSUER_WITHOUT_A_CARD");
            assertThat(parse(xml(result)).getElementsByTagNameNS("cds", "HealthCard").getLength())
                    .isZero();
        }
    }

    @Test
    void shouldNotAssertAnAddress_whenOnlyTheFormDefaultProvinceIsPresent() throws Exception {
        // D10: both address blocks of add-form-personal.jsp preselect the clinic province (or
        // "OT"), so a patient with no address entered still has a province in each block.
        var fields = baseline();
        fields.put("province", "ON");
        fields.put("residentialProvince", "OT");
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isTrue();
        assertThat(result.addresses()).isEmpty();
        for (String column : List.of("province", "residentialProvince"))
            assertThat(result.fields().get(column).rule())
                    .isEqualTo("PROVINCE_WITHOUT_AN_ADDRESS_IS_A_FORM_DEFAULT");
        assertThat(parse(xml(result)).getElementsByTagNameNS("cds", "Address").getLength())
                .isZero();
        // Any entered component makes it an address again, and the province is mapped.
        fields.put("residentialCity", "Ottawa");
        fields.put("residentialProvince", "ON");
        var entered = mapper.map(row(fields));
        assertThat(entered.addresses()).hasSize(1);
        assertThat(entered.fields().get("residentialProvince").outcome())
                .isEqualTo(Outcome.MAPPED);
    }

    @Test
    void shouldMapTheRightHonourableTitleAndListedSpokenLanguages_toTheirSchemaCodes()
            throws Exception {
        // D12: add-form-personal.jsp stores "Right Honourable" as RT_HON (schema RtHon) and the
        // spoken-language display names of spoken_languages_codes.properties, which the
        // importer reverses with SpokenLangProperties.getLangByCode.
        var fields = baseline();
        fields.put("title", "RT_HON");
        fields.put("spoken_lang", "Arabic");
        var result = mapper.map(row(fields));
        assertThat(result.baseRowRepresentable()).isTrue();
        var document = parse(xml(result));
        assertThat(text(document, "cds_dt", "NamePrefix")).isEqualTo("RtHon");
        assertThat(text(document, "cds", "PreferredSpokenLanguage")).isEqualTo("ara");
        assertThat(
                        io.github.carlos_emr.carlos.demographic.pageUtil.SpokenLangProperties
                                .getInstance()
                                .getLangByCode("ARA"))
                .isEqualTo("Arabic");
        fields.put("spoken_lang", "Chinese");
        assertThat(mapper.map(row(fields)).spokenLanguage()).isEqualTo("chi");
        // Middle names up to the column's 100 code points are written as their space-separated
        // names, which the importer joins back with single spaces (CdsDemographicPersister).
        String middle = "Marie-Claire Anne Josephine Elisabeth Margaret Victoria";
        assertThat(middle.length()).isGreaterThan(50);
        fields.put("middleNames", middle);
        var named = mapper.map(row(fields));
        assertThat(named.fields().get("middleNames").rule())
                .isEqualTo("SPACE_SEPARATED_MIDDLE_NAMES_AS_ORDERED_PARTS");
        var parts = parse(xml(named)).getElementsByTagNameNS("cds_dt", "OtherName");
        var joined = new java.util.ArrayList<String>();
        for (int i = 0; i < parts.getLength(); i++)
            joined.add(
                    ((org.w3c.dom.Element) parts.item(i))
                            .getElementsByTagNameNS("cds_dt", "Part")
                            .item(0)
                            .getTextContent());
        assertThat(String.join(" ", joined)).isEqualTo(middle);
        assertThat(joined).hasSize(6);
        fields.put("middleNames", "Short " + "b".repeat(51));
        assertThat(mapper.map(row(fields)).fields().get("middleNames").outcome())
                .isEqualTo(Outcome.REQUIRES_REVIEW);
        fields.put("middleNames", null);
        // An unlisted name or a bare code is still not evidence of an ISO 639-2 code.
        // It is left out and the stored name travels in the native record.
        for (String unknown : List.of("Not a language", "ARA", "xyz")) {
            fields.put("spoken_lang", unknown);
            var unlisted = mapper.map(row(fields));
            assertThat(unlisted.spokenLanguage()).isNull();
            assertCarried(unlisted, "spoken_lang");
        }
    }

    @Test
    void shouldExportMovedAndFiredByMeaning_insteadOfTheirTwoLetterCodes() throws Exception {
        // Review D8: another EMR sees "Moved", not "MO"; CARLOS maps the label back on import.
        for (var status : Map.of("MO", "Moved", "FI", "Fired").entrySet()) {
            var fields = baseline();
            fields.put("patient_status", status.getKey());
            var result = mapper.map(row(fields));
            assertThat(result.enumeratedStatus()).isFalse();
            assertThat(text(parse(xml(result)), "cds", "PersonStatusAsPlainText"))
                    .isEqualTo(status.getValue());
        }
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
        // An unlisted issuer: the card travels whole in the native record, never guessed.
        var unlisted = mapper.map(row(fields));
        assertThat(unlisted.baseRowRepresentable()).isTrue();
        assertThat(unlisted.healthCard()).isNull();
        assertCarried(unlisted, "hin");
        assertCarried(unlisted, "hc_type");
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
        var letters = mapper.map(row(fields));
        assertThat(letters.baseRowRepresentable()).isTrue();
        assertCarried(letters, "postal");
    }

    /** The value travels in the native demographic NewCategory record. */
    private static void assertCarried(
            io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDemographicMappingDto
                    result,
            String column) {
        var decision = result.fields().get(column);
        assertThat(decision.outcome()).isEqualTo(Outcome.MAPPED);
        assertThat(List.of(decision.destination().split(",")))
                .contains(CdsExportDemographicSupportingMapper.DESTINATION);
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
