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
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;

/** Native counterexamples: a valid transport or a nearby code never establishes missing clinical semantics. */
class CdsExportImmunizationMapperUnitTest {
    private final CdsExportImmunizationMapper mapper = new CdsExportImmunizationMapper();
    private Map<String, String> parent;
    private List<CdsExportCapturedTextRowDto> extras, dates;
    private CdsExportPreventionClassifier definitions;

    @BeforeEach
    void prepare() throws Exception {
        parent = new TreeMap<>();
        CdsExportPatientTableReadSupport.Plan.PREVENTIONS_FULL
                .columns()
                .forEach(column -> parent.put(column, null));
        parent.putAll(
                Map.of(
                        "id",
                        "17",
                        "demographic_no",
                        "27",
                        "prevention_type",
                        "Td",
                        "prevention_date",
                        "2024-02-29 12:34:56",
                        "deleted",
                        "0",
                        "never",
                        "0",
                        "refused",
                        "0",
                        "creator",
                        "AUDIT_ACTOR",
                        "creation_date",
                        "2025-01-01 00:00:00",
                        "lastUpdateDate",
                        "2026-01-01 00:00:00"));
        extras = new ArrayList<>();
        dates = new ArrayList<>();
        definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        "a".repeat(64),
                        "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='H1N1' layout='h1n1' healthCanadaType='Inf'/><item name='Tuberculosis' layout='injection' headingName='Screenings'/><item name='OtherA' layout='injection' healthCanadaType=''/><item name='FutureVaccine' layout='injection' healthCanadaType='COVID-19'/></preventions>"
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
    }

    private CdsExportImmunizationMappingDto map() throws Exception {
        return mapper.map(
                27,
                new CdsExportCapturedTextRowDto(
                        "preventions", CdsExportPatientTableSourceReader.key(17), parent),
                extras,
                dates,
                definitions);
    }

    private void extra(int id, String key, String value) {
        var fields = new TreeMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("prevention_id", "17");
        fields.put("keyval", key);
        fields.put("val", value);
        extras.add(
                new CdsExportCapturedTextRowDto(
                        "preventionsExt", CdsExportPatientTableSourceReader.key(id), fields));
    }

    private void date(int id, String field, String format) {
        var fields = new TreeMap<String, String>();
        fields.putAll(
                Map.of(
                        "id",
                        Integer.toString(id),
                        "table_id",
                        "17",
                        "table_name",
                        "4",
                        "field_name",
                        field));
        fields.put("format", format);
        dates.add(
                new CdsExportCapturedTextRowDto(
                        "prevention_precision", CdsExportPatientTableSourceReader.key(id), fields));
    }

    @Test
    void shouldVerifyMapsExactNativeEventClock_insteadOfCreationOrImportClocks() throws Exception {
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(16);
        assertThat(result.value().name()).isEqualTo("Td");
        assertThat(result.value().type()).isEqualTo("Td");
        assertThat(result.value().date())
                .isEqualTo(
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE_TIME, "2024-02-29T12:34:56"));
        assertThat(result.value().refused()).isFalse();
    }

    @Test
    void shouldVerifyAllNativeStandardExtensionsMap_withoutTrimmingOrChangingNullAndEmpty() throws Exception {
        extra(30, "name", " Brand café 😀 ");
        extra(31, "manufacture", "");
        extra(32, "lot", null);
        extra(33, "route", "IM");
        extra(34, "location", " Left arm ");
        extra(35, "dose", "0.50 mL");
        extra(36, "instructions", "Exact\r\nInstructions");
        extra(37, "comments", "Exact café 😀\r\nNotes ");
        extra(38, "din", "00123456");
        var result = map();
        assertThat(result.nativeRowRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(52);
        var value = result.value();
        assertThat(value.name()).isEqualTo(" Brand café 😀 ");
        assertThat(value.manufacturer()).isEmpty();
        assertThat(value.lot()).isNull();
        assertThat(value.route()).isEqualTo("IM");
        assertThat(value.site()).isEqualTo(" Left arm ");
        assertThat(value.dose()).isEqualTo("0.50 mL");
        assertThat(value.instructions()).isEqualTo("Exact\r\nInstructions");
        assertThat(value.notes()).isEqualTo("Exact café 😀\r\nNotes ");
        assertThat(value.code()).isEqualTo("00123456");
        assertThat(value.codeSystem()).isEqualTo("DIN");
    }

    @Test
    void shouldVerifyExplicitRefusalRemainsTrue_forImmunizationMapperContract() throws Exception {
        parent.put("refused", "1");
        assertThat(map().value().refused()).isTrue();
    }

    @Test
    void shouldVerifyIneligibleAndCompletedExternallyAreNeverCollapsed_toNotRefused() throws Exception {
        for (String flag : Arrays.asList("2", "3", "x", null, "")) {
            parent.put("refused", flag);
            assertThat(map().value()).isNull();
            assertThat(decision(map(), "preventions", "refused").rule())
                    .isEqualTo("INELIGIBLE_EXTERNAL_OR_UNKNOWN_STATUS_NEEDS_RECONCILIATION");
        }
    }

    @Test
    void shouldVerifyArchivedAndUnknownArchiveStatesCannotBecomeActiveEvents_forImmunizationMapperContract() throws Exception {
        for (String flag : Arrays.asList("1", "x", null, "")) {
            parent.put("deleted", flag);
            assertThat(map().value()).isNull();
        }
    }

    @Test
    void shouldVerifyDuplicateExtensionKeysRetainEveryRowAndNeverUseFirstOrLast_forImmunizationMapperContract() throws Exception {
        extra(30, "comments", "First");
        extra(31, "comments", "Second");
        var result = map();
        assertThat(result.value()).isNull();
        assertThat(result.fields()).hasSize(24);
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getKey().table().equals("preventionsExt")
                                                        && entry.getKey().column().equals("val")))
                .allSatisfy(
                        entry ->
                                assertThat(entry.getValue().outcome())
                                        .isEqualTo(
                                                CdsExportFieldAssessmentDto.Outcome
                                                        .REQUIRES_REVIEW));
    }

    @Test
    void shouldVerifyEvenEqualDuplicateCommentsRemainSeparateUnresolvedSourceRows_forImmunizationMapperContract() throws Exception {
        extra(30, "comments", "Same");
        extra(31, "comments", "Same");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyConflictingLegacyAndImportedNamesCannotSilentlyChooseOne_forImmunizationMapperContract() throws Exception {
        extra(30, "name", "Current Name");
        extra(31, "cdsName", "Old imported name");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyConflictingNativeTypeAndImportedTypeRequiresFieldReconciliation_forImmunizationMapperContract() throws Exception {
        extra(30, "cdsType", "HB");
        assertThat(map().value()).isNull();
        assertThat(decision(map(), "preventions", "prevention_type").rule())
                .isEqualTo("CONFLICTING_NATIVE_IMMUNIZATION_TYPE");
    }

    @Test
    void shouldVerifyExplicitImportedDinNeedsItsPairedAuthority_forImmunizationMapperContract() throws Exception {
        extra(30, "cdsCode", "00123456");
        assertThat(map().value()).isNull();
        extra(31, "cdsCodeSystem", "DIN");
        assertThat(map().value().code()).isEqualTo("00123456");
    }

    @Test
    void shouldVerifyASnomedCodeOrEightDigitsCannotInventDinAuthority_forImmunizationMapperContract() throws Exception {
        extra(30, "cdsCode", "00123456");
        extra(31, "cdsCodeSystem", "SNOMED");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyANativeSnomedIdNeverTurnsIntoStandardDin_forImmunizationMapperContract() throws Exception {
        parent.put("snomedId", "00123456");
        assertThat(map().value()).isNull();
        assertThat(decision(map(), "preventions", "snomedId").outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldVerifyCompetingDinFieldsCannotChooseOneEven_whenBothAreEightDigits() throws Exception {
        extra(30, "din", "00123456");
        extra(31, "cdsCode", "00987654");
        extra(32, "cdsCodeSystem", "DIN");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyConfiguredH1n1UsesItsExplicitWireType_insteadOfAnUnlistedName() throws Exception {
        parent.put("prevention_type", "H1N1");
        assertThat(map().value().type()).isEqualTo("Inf");
    }

    @Test
    void shouldVerifyAFutureConfiguredVaccineDoesNotExtendTheOldWireEnumeration_forImmunizationMapperContract() throws Exception {
        parent.put("prevention_type", "FutureVaccine");
        assertThat(map().value()).isNull();
        assertThat(decision(map(), "preventions", "prevention_type").rule())
                .isEqualTo("NO_PINNED_WIRE_TYPE_EQUIVALENCE");
    }

    @Test
    void shouldVerifyMantouxAndAmbiguousOtherInjectionsNeverBecomeVaccinationEvents_forImmunizationMapperContract() throws Exception {
        for (String type : List.of("Tuberculosis", "OtherA")) {
            parent.put("prevention_type", type);
            extra(30, "name", "Clinical name");
            assertThat(map().value()).isNull();
            extras.clear();
        }
    }

    @Test
    void shouldVerifyYearAndMonthMarkersNeverInventAClinicalMonthDayOrClock_forImmunizationMapperContract() throws Exception {
        date(30, "7", "YYYY");
        assertThat(map().value().date())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR, "2024"));
        dates.clear();
        date(30, "7", "YYYY-MM");
        assertThat(map().value().date())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.YEAR_MONTH, "2024-02"));
    }

    @Test
    void shouldVerifyUnknownDateStaysAbsentAndCannotUseCreationOrModificationDates_forImmunizationMapperContract() throws Exception {
        parent.put("prevention_date", null);
        assertThat(map().value().date().precision()).isEqualTo(CdsExportDateDto.Precision.ABSENT);
        date(30, "7", "YYYY");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyDuplicateOrUnsupportedEventPrecisionRemainsReview_forImmunizationMapperContract() throws Exception {
        date(30, "7", "YYYY");
        date(31, "7", "YYYY");
        assertThat(map().value()).isNull();
        dates.clear();
        date(30, "7", "YYYY-MM-DD");
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyNextDateAndUnknownPrecisionFieldsCannotBeMergedIntoEventDate_forImmunizationMapperContract() throws Exception {
        for (String field : List.of("8", "999")) {
            date(30, field, "YYYY");
            assertThat(map().value()).isNull();
            dates.clear();
        }
    }

    @Test
    void shouldVerifyUnknownAndStaleImportedExtensionsCannotBeIgnoredOrOverrideCurrentEvent_forImmunizationMapperContract() throws Exception {
        for (String key : List.of("customClinicalValue", "cdsDateOriginal", "cdsDatePrecision")) {
            extra(30, key, "2020");
            assertThat(map().value()).isNull();
            extras.clear();
        }
    }

    @Test
    void shouldVerifyAdministeringCliniciansDueDatesReminderSuppressionAndProgramAffiliationRemainExplicit_forImmunizationMapperContract()
            throws Exception {
        for (String column :
                List.of("provider_no", "provider_name", "next_date", "never", "programNo")) {
            String prior =
                    parent.put(column, column.equals("never") ? "1" : "Recorded native value");
            assertThat(map().value()).isNull();
            parent.put(column, prior);
        }
    }

    @Test
    void shouldVerifyExactDictionaryNoteLimitCountsCodePointsAndNeverTruncates_forImmunizationMapperContract() throws Exception {
        extra(30, "comments", "😀".repeat(32000));
        assertThat(map().value().notes().codePointCount(0, map().value().notes().length()))
                .isEqualTo(32000);
        extras.clear();
        extra(30, "comments", "😀".repeat(32001));
        assertThat(map().value()).isNull();
    }

    @Test
    void shouldVerifyForeignParentsChildrenPrecisionAndDuplicatePhysicalKeysAreScopeErrors_forImmunizationMapperContract() {
        parent.put("demographic_no", "28");
        assertThatThrownBy(this::map).hasMessage("CDS_EXPORT_IMMUNIZATION_MAPPING_SCOPE_INVALID");
        parent.put("demographic_no", "27");
        extra(30, "comments", "One");
        extras.add(extras.getFirst());
        assertThatThrownBy(this::map).hasMessage("CDS_EXPORT_IMMUNIZATION_MAPPING_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyNullAndEmptyNativeExtensionValuesHaveDifferentFieldPresence_forImmunizationMapperContract() throws Exception {
        extra(30, "lot", null);
        extra(31, "manufacture", "");
        var result = map();
        assertThat(result.value().manufacturer()).isEmpty();
        assertThat(result.value().lot()).isNull();
        assertThat(
                        result.fields().entrySet().stream()
                                .filter(
                                        entry ->
                                                entry.getKey().table().equals("preventionsExt")
                                                        && entry.getKey().column().equals("val"))
                                .map(entry -> entry.getValue().presence())
                                .toList())
                .containsExactlyInAnyOrder(
                        CdsExportFieldAssessmentDto.Presence.SQL_NULL,
                        CdsExportFieldAssessmentDto.Presence.EMPTY);
    }

    @Test
    void shouldVerifyWireVocabularyExactlyMatchesTheHashPinnedOriginalSchema_forImmunizationMapperContract() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var document =
                factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(CdsWireSchema.datatypeBytes()));
        var nodes =
                document.getElementsByTagNameNS(
                        javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "simpleType");
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var element = (org.w3c.dom.Element) nodes.item(i);
            if (!element.getAttribute("name").equals("immunizationType")) continue;
            var enumerations =
                    element.getElementsByTagNameNS(
                            javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI, "enumeration");
            for (int j = 0; j < enumerations.getLength(); j++)
                actual.add(((org.w3c.dom.Element) enumerations.item(j)).getAttribute("value"));
        }
        assertThat(CdsExportImmunizationVocabulary.TYPES)
                .containsExactlyInAnyOrderElementsOf(actual);
        assertThat(actual).doesNotContain("H1N1", "COVID-19");
    }

    @Test
    void shouldVerifyProtectedIntermediateObjectsDoNotPrintClinicalNamesOrValues_forImmunizationMapperContract() throws Exception {
        extra(30, "name", "PRIVATE NAME");
        assertThat(map().toString()).isEqualTo("CdsExportImmunizationMappingDto[protected]");
        assertThat(definitions.definition("Td").orElseThrow().toString())
                .isEqualTo("CdsExportPreventionClassifier.Definition[protected]");
    }

    private static CdsExportFieldAssessmentDto decision(
            CdsExportImmunizationMappingDto result, String table, String column) {
        return result.fields().entrySet().stream()
                .filter(
                        entry ->
                                entry.getKey().table().equals(table)
                                        && entry.getKey().column().equals(column))
                .findFirst()
                .orElseThrow()
                .getValue();
    }
}
