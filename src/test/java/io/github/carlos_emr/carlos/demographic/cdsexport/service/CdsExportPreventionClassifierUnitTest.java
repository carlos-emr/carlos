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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPreventionClassifier.Kind.*;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportPreventionClassifier.Rule.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent clinical category examples and adversarial frozen-input boundaries. No database queries. */
class CdsExportPreventionClassifierUnitTest {
    private static final String SOURCE = "a".repeat(64);
    private static final String XML =
            "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='H1N1' layout='h1n1' healthCanadaType='Inf'/><item name='PAP' layout='PAPMAM' headingName='Screenings'/><item name='Smoking' layout='history' headingName='Screenings'/><item name='custom' layout='future'/></preventions>";

    private static CdsExportPreventionClassifier classifier() throws IOException {
        return from(XML, List.of());
    }

    private static CdsExportPreventionClassifier from(
            String xml, Iterable<CdsExportCapturedTextRowDto> rows) throws IOException {
        return CdsExportPreventionClassifier.fromCaptured(
                SOURCE, xml.getBytes(StandardCharsets.UTF_8), rows);
    }

    @Test
    void shouldVerifyDistinguishesVaccinationIncludingH1n1_fromScreeningsAndHistory() throws Exception {
        var value = classifier();
        assertThat(value.classify("Td", null))
                .isEqualTo(
                        new CdsExportPreventionClassifier.Decision(
                                IMMUNIZATION, CONFIGURED_IMMUNIZATION));
        assertThat(value.classify("H1N1", "").kind()).isEqualTo(IMMUNIZATION);
        assertThat(value.classify("PAP", null).kind()).isEqualTo(NON_IMMUNIZATION);
        assertThat(value.classify("Smoking", "").kind()).isEqualTo(NON_IMMUNIZATION);
    }

    @Test
    void shouldVerifyUnknownTypesAndLayoutsAlwaysRequireReviewRatherThanSilentExclusion_forPreventionClassifierContract() throws Exception {
        var value = classifier();
        for (String type : Arrays.asList(null, "", "td", " Td", "Td ", "Unknown"))
            assertThat(value.classify(type, null))
                    .isEqualTo(
                            new CdsExportPreventionClassifier.Decision(
                                    REQUIRES_REVIEW, UNKNOWN_TYPE));
        assertThat(value.classify("custom", null).rule()).isEqualTo(UNSUPPORTED_LAYOUT);
        assertThat(value.classify("PAP", "not-in-frozen-catalogue").rule())
                .isEqualTo(UNRESOLVED_NON_IMMUNIZATION_CODE);
    }

    @Test
    void shouldVerifyActualBundledConfigurationKeepsMantouxScreeningAndAmbiguousInjectionsOutOfVaccinations_forPreventionClassifierContract()
            throws Exception {
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/oscar/prevention/PreventionItems.xml")) {
            assertThat(input).isNotNull();
            xml = input.readAllBytes();
        }
        var value = CdsExportPreventionClassifier.fromCaptured(SOURCE, xml, List.of());
        for (String name : List.of("Td", "H1N1", "MMR", "Inf"))
            assertThat(value.classify(name, null).kind()).as(name).isEqualTo(IMMUNIZATION);
        for (String name : List.of("OtherB", "Smoking", "PAP", "Tuberculosis"))
            assertThat(value.classify(name, null).kind()).as(name).isEqualTo(NON_IMMUNIZATION);
        // A30: the shipped catch-all "Other immunization or injection" is an untyped immunization.
        assertThat(value.classify("OtherA", null))
                .isEqualTo(
                        new CdsExportPreventionClassifier.Decision(
                                IMMUNIZATION,
                                CdsExportPreventionClassifier.Rule.CATCH_ALL_INJECTION));
    }

    @Test
    void shouldVerifyExactCvcConceptOrPicklistClassifiesGenericAndTradeVaccines_withoutClaimingCodeEquivalence()
            throws Exception {
        var generic = vaccine(1, "Frozen generic vaccine", "123456789");
        var trade = changed(vaccine(2, "Frozen trade vaccine", "987654321"), "generic", "0");
        var value = from(XML, List.of(generic, trade));
        for (String name : List.of("Frozen generic vaccine", "Frozen trade vaccine"))
            assertThat(value.classify(name, null).rule()).isEqualTo(CVC_IMMUNIZATION);
        assertThat(value.classify(null, "987654321").kind()).isEqualTo(IMMUNIZATION);
        assertThat(value.classify("unrecognized", "123456789").kind()).isEqualTo(IMMUNIZATION);
        assertThat(value.classify("frozen generic vaccine", null).kind())
                .isEqualTo(REQUIRES_REVIEW);
    }

    @Test
    void shouldVerifyOldCvcNamesRequireTheCorrespondingCapturedVaccineDefinition_forPreventionClassifierContract() throws Exception {
        var value =
                from(
                        XML,
                        List.of(
                                mapping(1, "HistoricName", "123456789"),
                                vaccine(2, "NewName", "123456789"),
                                mapping(3, "DanglingName", "456789123")));
        assertThat(value.classify("HistoricName", null).rule()).isEqualTo(CVC_IMMUNIZATION);
        assertThat(value.classify("DanglingName", null).rule()).isEqualTo(UNRESOLVED_CVC_MAPPING);
    }

    @Test
    void shouldVerifyAKnownScreeningAndVaccineBindingConflictRequiresReview_forPreventionClassifierContract() throws Exception {
        var value =
                from(
                        XML,
                        List.of(
                                vaccine(1, "PAP", "123456789"),
                                mapping(2, "Smoking", "123456789")));
        for (String name : List.of("PAP", "Smoking"))
            assertThat(value.classify(name, null).rule()).isEqualTo(CONFLICTING_CATEGORY);
        assertThat(value.classify("PAP", "123456789").kind()).isEqualTo(REQUIRES_REVIEW);
    }

    @Test
    void shouldVerifyUnknownLayoutIsNotOverridden_byACvcName() throws Exception {
        assertThat(
                        from(XML, List.of(vaccine(1, "custom", "123456789")))
                                .classify("custom", null)
                                .rule())
                .isEqualTo(UNSUPPORTED_LAYOUT);
    }

    @Test
    void shouldVerifyUnresolvedMappingsDoNotFallBack_toAnotherVaccineNameOrLayout() throws Exception {
        assertThat(from(XML, List.of(mapping(1, "Td", null))).classify("Td", null).rule())
                .isEqualTo(UNRESOLVED_CVC_MAPPING);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<other><item name='Td' layout='injection' healthCanadaType='Td'/></other>",
                "<preventions/>",
                "<preventions><item name='Td' layout='injection' healthCanadaType='Td'/><item name='Td' layout='PAPMAM'/></preventions>",
                "<preventions><item layout='injection'/></preventions>",
                "<preventions><item name='' layout='injection'/></preventions>",
                "<preventions><item name='Td'><nested/></item></preventions>",
                "<preventions xmlns='urn:foreign'><item name='Td'/></preventions>",
                "<preventions><unknown name='Td'/></preventions>",
                "<preventions>unrecognized content<item name='Td'/></preventions>",
                "<!DOCTYPE preventions [<!ENTITY x SYSTEM 'file:///must-not-read'>]><preventions><item name='&x;' layout='injection'/></preventions>",
                "<preventions><item name='Td'/></preventions><extra/>",
                "<preventions><item name='Td' layout='injection' healthCanadaType='Td' layout='PAPMAM'/></preventions>"
            })
    void shouldVerifyInvalidOrAmbiguousXmlNeverBuildsAClassifier_forPreventionClassifierContract(String xml) {
        assertThatThrownBy(() -> from(xml, List.of()))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_PREVENTION_CLASSIFICATION_INVALID");
    }

    @Test
    void shouldVerifyEnforcesByteElementAndAttributeBounds_withoutTruncating() {
        assertThatThrownBy(
                        () ->
                                CdsExportPreventionClassifier.fromCaptured(
                                        SOURCE, new byte[1_048_577], List.of()))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                from(
                                        "<preventions><item name='"
                                                + "X".repeat(256)
                                                + "'/></preventions>",
                                        List.of()))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                from(
                                        "<preventions><item name='Td' desc='"
                                                + "X".repeat(4097)
                                                + "'/></preventions>",
                                        List.of()))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                from(
                                        "<preventions><item name='Td' "
                                                + java.util.stream.IntStream.range(0, 65)
                                                        .mapToObj(i -> "a" + i + "='x' ")
                                                        .collect(
                                                                java.util.stream.Collectors
                                                                        .joining())
                                                + "/></preventions>",
                                        List.of()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifySchemaKeysBooleanStatesAndForeignCatalogueTablesAreRejected_forPreventionClassifierContract() {
        var row = vaccine(1, "Vaccine", "123456789");
        for (var invalid :
                List.of(
                        new CdsExportCapturedTextRowDto("provider", row.recordKey(), row.fields()),
                        new CdsExportCapturedTextRowDto(row.table(), "foreign", row.fields()),
                        changed(row, "id", "01"),
                        changed(row, "generic", "2"),
                        changed(row, "ispa", "true"),
                        changed(row, "picklistName", "X".repeat(256))))
            assertThatThrownBy(() -> from(XML, List.of(invalid))).isInstanceOf(IOException.class);
        var fields = new TreeMap<>(row.fields());
        fields.put("unknown", "value");
        assertThatThrownBy(
                        () ->
                                from(
                                        XML,
                                        List.of(
                                                new CdsExportCapturedTextRowDto(
                                                        row.table(), row.recordKey(), fields))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> from(XML, Arrays.asList((CdsExportCapturedTextRowDto) null)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyDuplicatePhysicalRowsAndAmbiguousOldNameMappingsAreRejected_forPreventionClassifierContract() {
        var row = vaccine(1, "Vaccine", "123456789");
        assertThatThrownBy(() -> from(XML, List.of(row, row))).isInstanceOf(IOException.class);
        assertThatThrownBy(
                        () ->
                                from(
                                        XML,
                                        List.of(
                                                mapping(1, "old", "123456789"),
                                                mapping(2, "old", "123456789"))))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyCatalogueHashIsOrderedAndBindsAllValuesIncludingNullVersusEmpty_forPreventionClassifierContract() throws Exception {
        var first = vaccine(1, "Vaccine", "123456789");
        var second = mapping(2, "old", "123456789");
        var a = from(XML, List.of(first, second));
        var b = from(XML, List.of(second, first));
        assertThat(a.catalogueSha256()).isEqualTo(b.catalogueSha256()).hasSize(64);
        assertThat(
                        from(XML, List.of(changed(first, "parentConceptId", ""), second))
                                .catalogueSha256())
                .isNotEqualTo(a.catalogueSha256());
        assertThat(
                        from(XML, List.of(changed(first, "displayName", "Changed"), second))
                                .catalogueSha256())
                .isNotEqualTo(a.catalogueSha256());
    }

    @Test
    void shouldVerifyEvidenceIdentityAndOriginalConfigurationRemainImmutableAndProtected_forPreventionClassifierContract() throws Exception {
        byte[] bytes = XML.getBytes(StandardCharsets.UTF_8);
        var value = CdsExportPreventionClassifier.fromCaptured(SOURCE, bytes, List.of());
        var digest = value.configurationSha256();
        Arrays.fill(bytes, (byte) 'X');
        assertThat(value.configurationSha256()).isEqualTo(digest);
        assertThat(value.sourceManifestSha256()).isEqualTo(SOURCE);
        assertThat(value.toString()).doesNotContain("Td", "PAP", SOURCE);
        assertThat(from(XML + "\n", List.of()).configurationSha256()).isNotEqualTo(digest);
        assertThatThrownBy(
                        () ->
                                CdsExportPreventionClassifier.fromCaptured(
                                        "invalid", XML.getBytes(StandardCharsets.UTF_8), List.of()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyBoundsEvenALazyCatalogueAndNeverSilentlyUsesItsPrefix_forPreventionClassifierContract() {
        Iterable<CdsExportCapturedTextRowDto> endless =
                () ->
                        new Iterator<>() {
                            int next = 1;

                            public boolean hasNext() {
                                return true;
                            }

                            public CdsExportCapturedTextRowDto next() {
                                return vaccine(next++, "Vaccine", "123456789");
                            }
                        };
        assertThatThrownBy(() -> from(XML, endless)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldVerifyAnInjectionLayoutDoesNotProveVaccinationButExplicitVaccineEvidenceCanResolveIt_forPreventionClassifierContract()
            throws Exception {
        String xml =
                "<preventions><item name='Tuberculosis' layout='injection' headingName='Screenings'/><item name='OtherA' layout='injection' headingName='Other'/><item name='B12' layout='injection' headingName='Other'/></preventions>";
        var value = from(xml, List.of(vaccine(1, "Verified vaccine", "123456789")));
        assertThat(value.classify("Tuberculosis", null).kind()).isEqualTo(NON_IMMUNIZATION);
        // Only the catch-all OtherA under Other is placed by name; any other injection item and an
        // OtherA filed elsewhere still need explicit vaccine evidence.
        assertThat(value.classify("OtherA", null).rule())
                .isEqualTo(CdsExportPreventionClassifier.Rule.CATCH_ALL_INJECTION);
        assertThat(value.classify("B12", null).rule()).isEqualTo(CATEGORY_NOT_PROVEN);
        var elsewhere =
                from(
                        "<preventions><item name='OtherA' layout='injection' headingName='Screenings'/><item name='Td' layout='injection' healthCanadaType='Td'/></preventions>",
                        List.of());
        assertThat(elsewhere.classify("OtherA", null).kind()).isEqualTo(NON_IMMUNIZATION);
        var unheaded =
                from(
                        "<preventions><item name='OtherA' layout='injection'/></preventions>",
                        List.of());
        assertThat(unheaded.classify("OtherA", null).rule()).isEqualTo(CATEGORY_NOT_PROVEN);
        assertThat(value.classify("OtherA", "123456789").kind()).isEqualTo(IMMUNIZATION);
        assertThat(value.classify("Tuberculosis", "123456789").rule())
                .isEqualTo(CONFLICTING_CATEGORY);
    }

    @Test
    void shouldExportAnRhImmuneGlobulinInjection_asAnImmunizationUnlessConfiguredOtherwise()
            throws Exception {
        // The Rh Immune Globulin form saves type RH, which AddPrevention2Action accepts without a
        // configured item; it was held as UNKNOWN_TYPE. A configured RH item still decides.
        var shipped = from(XML, List.of());
        assertThat(shipped.classify("RH", null))
                .isEqualTo(
                        new CdsExportPreventionClassifier.Decision(
                                IMMUNIZATION,
                                CdsExportPreventionClassifier.Rule.RH_IMMUNE_GLOBULIN));
        assertThat(shipped.classify("UNCONFIGURED", null).rule()).isEqualTo(UNKNOWN_TYPE);
        var configured =
                from(
                        "<preventions><item name='RH' layout='injection' headingName='Screenings'/></preventions>",
                        List.of());
        assertThat(configured.classify("RH", null).kind()).isEqualTo(NON_IMMUNIZATION);
    }

    @Test
    void shouldVerifyConflictingScreeningAndImmunizationDeclarationsRequireReview_forPreventionClassifierContract() throws Exception {
        var value =
                from(
                        "<preventions><item name='ambiguous' layout='injection' headingName='Screenings' healthCanadaType='Inf'/></preventions>",
                        List.of());
        assertThat(value.classify("ambiguous", null).rule()).isEqualTo(CONFLICTING_CATEGORY);
    }

    private static CdsExportCapturedTextRowDto vaccine(int id, String name, String code) {
        var fields = new TreeMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("picklistName", name);
        fields.put("snomedConceptId", code);
        fields.put("displayName", "Frozen vaccine display");
        fields.put("generic", "1");
        fields.put("ispa", "0");
        fields.put("parentConceptId", null);
        fields.put("prevalence", null);
        fields.put("versionId", "42");
        return new CdsExportCapturedTextRowDto(
                "CVCImmunization", CdsExportPatientTableSourceReader.key(id), fields);
    }

    private static CdsExportCapturedTextRowDto mapping(int id, String name, String code) {
        var fields = new TreeMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("oscarName", name);
        fields.put("cvcSnomedId", code);
        fields.put("preferCVC", "0");
        return new CdsExportCapturedTextRowDto(
                "CVCMapping", CdsExportPatientTableSourceReader.key(id), fields);
    }

    private static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new TreeMap<>(row.fields());
        fields.put(column, value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }
}
