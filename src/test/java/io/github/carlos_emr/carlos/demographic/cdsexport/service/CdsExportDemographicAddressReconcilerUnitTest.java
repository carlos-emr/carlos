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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicAddressReconcilerUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source = new LinkedHashMap<>();
    private boolean active;

    @BeforeEach
    void setup() {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
        active = true;
        source.clear();
        source.put("Names[1]/LegalName[1]/@namePurpose", "L");
        source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada");
        source.put("Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV");
        source.put("Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family");
        source.put("Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC");
        source.put("DateOfBirth[1]", "1980-01-02");
        source.put("Gender[1]", "F");
        source.put("UniqueVendorIdSequence[1]", "27");
        source.put("PersonStatusCode[1]/PersonStatusAsEnum[1]", "A");
        source.put("PersonStatusDate[1]", "2024-01-02");
        source.put("HealthCard[1]/Number[1]", "012 345 6789");
        source.put("HealthCard[1]/Version[1]", "AB");
        source.put("HealthCard[1]/ProvinceCode[1]", "CA-ON");
        source.put("HealthCard[1]/Expirydate[1]", "2028-01-02");
        source.put("Address[1]/@addressType", "M");
        source.put("Address[1]/Structured[1]/Line1[1]", "123 Source");
        source.put("Address[1]/Structured[1]/City[1]", "Town");
        source.put("Address[1]/Structured[1]/CountrySubdivisionCode[1]", "CA-ON");
        source.put("Address[1]/Structured[1]/PostalZipCode[1]/PostalCode[1]", "K1A0B1");
        source.put("ChartNumber[1]", "C27");
        source.put("Email[1]", "old@example.test");
        source.put("SIN[1]", "123456789");
        source.put("PreferredOfficialLanguage[1]", "ENG");
        source.put("PreferredSpokenLanguage[1]", "fra");
    }

    @Test
    void shouldVerifyUnchangedAddressesKeepAllOriginalPartsAndSubtype_forDemographicAddressReconcilerContract() throws Exception {
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.fields()).hasSize(8);
        assertThat(result.changes()).isEmpty();
        assertThat(text(render(result), "Line1")).containsExactly("123 Source");
        assertThat(result.toString()).doesNotContain("123 Source", result.unitAddress());
    }

    @Test
    void shouldVerifyACityEditKeepsOriginalMultiLineStreetBoundaries_forDemographicAddressReconcilerContract() throws Exception {
        compoundStreet();
        fixture.currentField("city", "New town");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        var document = render(result);
        assertThat(text(document, "City")).containsExactly("New town");
        assertThat(text(document, "Line1")).containsExactly("123 Source");
        assertThat(text(document, "Line2")).containsExactly("Unit 2");
    }

    @Test
    void shouldVerifyAnEditedCompoundStreetReplacesEverySourceLineAndAnExplicitStreetClearDeletesAllProjectedLines_forDemographicAddressReconcilerContract()
            throws Exception {
        // Updated: a street edited over several source lines used to hold the chart. The edited
        // street is Line1 and the other source lines are superseded (listed as replaced).
        compoundStreet();
        fixture.currentField("address", "Later single line");
        var edited = reconcile();
        assertThat(edited.representable()).isTrue();
        assertThat(rule(edited, "address"))
                .isEqualTo("EDITED_CURRENT_ADDRESS_PARTS_SUPERSEDE_SOURCE_PARTS");
        var editedDocument = render(edited);
        assertThat(text(editedDocument, "Line1")).containsExactly("Later single line");
        assertThat(text(editedDocument, "Line2")).isEmpty();
        assertThat(text(editedDocument, "City")).containsExactly("Town");
        assertThat(plan(edited).replacedFields()).hasSize(2);
        fixture.currentField("address", null);
        var result = reconcile();
        assertThat(result.changes()).hasSize(2);
        var document = render(result);
        assertThat(text(document, "Line1")).isEmpty();
        assertThat(text(document, "Line2")).isEmpty();
        assertThat(text(document, "City")).containsExactly("Town");
    }

    @Test
    void shouldVerifyANewNativeResidentialAddressIsAppended_withItsOwnType() throws Exception {
        fixture.currentField("residentialAddress", "99 Residential");
        fixture.currentField("residentialCity", "Other town");
        fixture.currentField("residentialProvince", "BC");
        fixture.currentField("residentialPostal", "V1A1A1");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("Address[2]");
        var document = render(result);
        assertThat(text(document, "Line1")).containsExactly("123 Source", "99 Residential");
        var nodes = document.getElementsByTagNameNS("cds", "Address");
        assertThat(((org.w3c.dom.Element) nodes.item(1)).getAttribute("addressType"))
                .isEqualTo("R");
    }

    @Test
    void shouldVerifyACompletelyClearedProjectedAddressIsRemoved_forDemographicAddressReconcilerContract() throws Exception {
        for (String column : List.of("address", "city", "province", "postal"))
            fixture.currentField(column, null);
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("Address[1]");
        assertThat(text(render(result), "Address")).isEmpty();
    }

    @Test
    void shouldVerifyACountryChangeAlsoReconcilesThePostalChoice_forDemographicAddressReconcilerContract() throws Exception {
        fixture.currentField("province", "US-NY");
        fixture.currentField("postal", "01234-5678");
        var document = render(reconcile());
        assertThat(text(document, "CountrySubdivisionCode")).containsExactly("US-NY");
        assertThat(text(document, "PostalCode")).isEmpty();
        assertThat(text(document, "ZipCode")).containsExactly("01234-5678");
    }

    @Test
    void shouldVerifyAnIncompatiblePostalCodeOrMissingJurisdictionIsCarried_forDemographicAddressReconcilerContract() throws Exception {
        // Neither postal arm is guessed: the stored postal code travels in the native record and
        // the address is written without it.
        fixture.currentField("province", "US-NY");
        var foreign = reconcile();
        assertThat(foreign.representable()).isTrue();
        assertThat(text(render(foreign), "PostalCode")).isEmpty();
        assertThat(text(render(foreign), "ZipCode")).isEmpty();
        setup();
        fixture.currentField("province", null);
        assertThat(reconcile().representable()).isTrue();
    }

    @Test
    void shouldVerifyIdenticalRepeatedOriginalAddressesAreKeptAndLaterEditsRequireReview_forDemographicAddressReconcilerContract() throws Exception {
        var copy = new HashMap<>(source);
        copy.forEach(
                (path, value) -> {
                    if (path.startsWith("Address[1]/"))
                        source.put(path.replace("Address[1]", "Address[2]"), value);
                });
        assertThat(reconcile().changes()).isEmpty();
        assertThat(text(render(reconcile()), "Line1")).containsExactly("123 Source", "123 Source");
        // Updated: a later edit used to hold the chart. The current columns now replace the
        // first source address and the second is superseded; both stay in the retained source.
        fixture.currentField("city", "Later");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(rule(result, "city"))
                .isEqualTo("EDITED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS");
        var document = render(result);
        assertThat(text(document, "Address")).hasSize(1);
        assertThat(text(document, "Line1")).containsExactly("123 Source");
        assertThat(text(document, "City")).containsExactly("Later");
        assertThat(plan(result).replacedFields()).hasSize(10);
    }

    @Test
    void shouldVerifyASourceOnlyOversizedStreetSurvivesAnIndependentCityEditAndClear_forDemographicAddressReconcilerContract() throws Exception {
        source.put("Address[1]/Structured[1]/Line1[1]", "A".repeat(50));
        source.put("Address[1]/Structured[1]/Line2[1]", "B".repeat(25));
        fixture.preparedAddress(
                false, new CdsDemographicNativeDto.Address(null, "Town", "ON", "K1A0B1"));
        fixture.currentField("city", "Later");
        assertThat(text(render(reconcile()), "Line1")).containsExactly("A".repeat(50));
        for (String column : List.of("city", "province", "postal"))
            fixture.currentField(column, null);
        var document = render(reconcile());
        assertThat(text(document, "Line2")).containsExactly("B".repeat(25));
        assertThat(text(document, "City")).isEmpty();
    }

    @Test
    void shouldVerifyANewValueSupersedesAnUnprojectedOriginalStreet_forDemographicAddressReconcilerContract() throws Exception {
        // Updated: a street typed over a source-only street used to hold the chart.
        source.put("Address[1]/Structured[1]/Line1[1]", "A".repeat(50));
        source.put("Address[1]/Structured[1]/Line2[1]", "B".repeat(25));
        fixture.preparedAddress(
                false, new CdsDemographicNativeDto.Address(null, "Town", "ON", "K1A0B1"));
        fixture.currentField("address", "New");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        var document = render(result);
        assertThat(text(document, "Line1")).containsExactly("New");
        assertThat(text(document, "Line2")).isEmpty();
        assertThat(text(document, "City")).containsExactly("Town");
        // An empty value a form save writes over the source-only street is not an edit.
        fixture.currentField("address", "");
        var saved = reconcile();
        assertThat(saved.changes()).isEmpty();
        assertThat(text(render(saved), "Line2")).containsExactly("B".repeat(25));
    }

    @Test
    void shouldVerifyAFormattedStreetCanBeEditedExactly_withoutChangingItsChoice() throws Exception {
        formatted();
        fixture.currentField("address", "Later\nFormatted");
        var document = render(reconcile());
        assertThat(text(document, "Formatted")).containsExactly("Later\nFormatted");
        assertThat(text(document, "Structured")).isEmpty();
    }

    @Test
    void shouldWriteTheCurrentStructuredAddress_whenAPatientWithAnImportedFormattedAddressMoves()
            throws Exception {
        // Updated: a city entered on an imported Formatted address used to hold the chart. The
        // patient moved: the current columns are written Structured, as for a never-imported
        // chart, and the Formatted source text is superseded (listed as replaced).
        formatted();
        fixture.currentField("address", "9 New Street");
        fixture.currentField("city", "Later");
        fixture.currentField("province", "ON");
        fixture.currentField("postal", "K2B8C3");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(rule(result, "postal"))
                .isEqualTo("EDITED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS");
        var document = render(result);
        assertThat(text(document, "Formatted")).isEmpty();
        assertThat(text(document, "Line1")).containsExactly("9 New Street");
        assertThat(text(document, "City")).containsExactly("Later");
        assertThat(text(document, "CountrySubdivisionCode")).containsExactly("CA-ON");
        assertThat(text(document, "PostalCode")).containsExactly("K2B8C3");
        assertThat(plan(result).replacedFields()).hasSize(2);
    }

    @Test
    void shouldExportNoMailingAddress_whenTheUserClearsAnImportedFormattedAddress() throws Exception {
        // The forms keep their default province; with no street, city or postal code it records
        // no address, so the cleared Formatted source is superseded rather than replayed.
        formatted();
        fixture.currentField("address", "");
        fixture.currentField("province", "ON");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(rule(result, "address"))
                .isEqualTo("CLEARED_CURRENT_ADDRESS_SUPERSEDES_SOURCE_ADDRESS");
        assertThat(text(render(result), "Address")).isEmpty();
        assertThat(plan(result).replacedFields()).hasSize(2);
    }

    @Test
    void shouldReplaceBothSourceAddresses_whenAPatientImportedWithTwoMailingAddressesMoves()
            throws Exception {
        // The importer projects neither of two different mailing addresses (none is the
        // current one), so the columns start empty. A form save that writes them empty keeps
        // both; an address the user then enters replaces both.
        source.put("Address[2]/@addressType", "M");
        source.put("Address[2]/Formatted[1]", "Second\nAddress");
        fixture.preparedAddress(false, null);
        fixture.currentField("address", "");
        fixture.currentField("city", "");
        var kept = reconcile();
        assertThat(kept.representable()).isTrue();
        assertThat(kept.changes()).isEmpty();
        assertThat(rule(kept, "city"))
                .isEqualTo("UNPROJECTED_SOURCE_ADDRESS_KEPT_WITHOUT_A_CURRENT_ADDRESS");
        assertThat(text(render(kept), "Address")).hasSize(2);
        fixture.currentField("address", "9 New Street");
        fixture.currentField("city", "Later");
        var moved = reconcile();
        assertThat(moved.representable()).isTrue();
        var document = render(moved);
        assertThat(text(document, "Address")).hasSize(1);
        assertThat(text(document, "Line1")).containsExactly("9 New Street");
        assertThat(text(document, "Formatted")).isEmpty();
        assertThat(plan(moved).replacedFields()).hasSize(7);
    }

    @Test
    void shouldStillHoldTheChart_forAnAddressValueNoXmlDocumentCanCarry() throws Exception {
        formatted();
        fixture.currentField("city", "Bad\u0001Town");
        var result = reconcile();
        assertThat(result.representable()).isFalse();
        assertThat(rule(result, "city")).isEqualTo("CURRENT_NATIVE_ADDRESS_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyAnEmptyOriginalStructuredAddressCanReceiveItsFirstNativeValue_forDemographicAddressReconcilerContract() throws Exception {
        source.keySet().removeIf(path -> path.startsWith("Address[1]/Structured[1]/"));
        source.put("Address[1]/Structured[1]", "");
        fixture.preparedAddress(false, new CdsDemographicNativeDto.Address(null, null, null, null));
        fixture.currentField("city", "Added town");
        assertThat(text(render(reconcile()), "City")).containsExactly("Added town");
    }

    @Test
    void shouldVerifyAMatchedExistingAddressAndExpiredValuesCannotReplayOriginals_forDemographicAddressReconcilerContract() throws Exception {
        fixture.oldField("city", "Existing town");
        assertThat(reconcile().representable()).isFalse();
        active = false;
        assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES");
    }

    private static String rule(
            CdsExportDemographicAddressReconciler.Result result, String column) {
        return result.fields().entrySet().stream()
                .filter(entry -> entry.getKey().column().equals(column))
                .findFirst()
                .orElseThrow()
                .getValue()
                .rule();
    }

    private CdsExportRetainedSectionWriter.Prepared plan(
            CdsExportDemographicAddressReconciler.Result result) throws Exception {
        return new CdsExportRetainedSectionWriter()
                .prepareChanges(graph().fields(), values(), result.changes(), () -> {});
    }

    private void compoundStreet() {
        source.put("Address[1]/Structured[1]/Line2[1]", "Unit 2");
        fixture.preparedAddress(
                false,
                new CdsDemographicNativeDto.Address("123 Source\nUnit 2", "Town", "ON", "K1A0B1"));
    }

    private void formatted() {
        source.keySet().removeIf(path -> path.startsWith("Address[1]/Structured[1]"));
        source.put("Address[1]/Formatted[1]", "Original\nFormatted");
        fixture.preparedAddress(
                false,
                new CdsDemographicNativeDto.Address("Original\nFormatted", null, null, null));
    }

    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph() throws Exception {
        return fixture.withSource(source);
    }

    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        return graph.index().nativeParents().values().iterator().next();
    }

    private CdsExportDemographicAddressReconciler.Result reconcile() throws Exception {
        var graph = graph();
        return new CdsExportDemographicAddressReconciler()
                .reconcile(
                        27,
                        address(graph),
                        graph.index(),
                        graph.checked(),
                        graph.fields(),
                        graph.current(),
                        graph.witness(),
                        values(),
                        () -> {});
    }

    private CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public void requireActive() throws IOException {
                if (!active) throw new IOException("EXPIRED_VALUES");
            }

            public Reader text(CdsExportRetainedFieldDto field) throws IOException {
                requireActive();
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError("No binary scalar");
            }
        };
    }

    private Document render(CdsExportDemographicAddressReconciler.Result result) throws Exception {
        var graph = graph();
        var plan =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(graph.fields(), values(), result.changes(), () -> {});
        var bytes = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS);
        String patient = "a".repeat(64), version = "2026.08.0-test";
        var section =
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(patient, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        plan.content());
        new CdsExportXmlComposer()
                .compose(
                        bytes,
                        "c".repeat(64),
                        patient,
                        version,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        List.of(section).iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(bytes.toByteArray()),
                        "27",
                        selected,
                        version,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes.toByteArray()));
    }

    private static List<String> text(Document document, String local) {
        var nodes = document.getElementsByTagNameNS("*", local);
        var values = new ArrayList<String>();
        for (int i = 0; i < nodes.getLength(); i++) values.add(nodes.item(i).getTextContent());
        return values;
    }
}
