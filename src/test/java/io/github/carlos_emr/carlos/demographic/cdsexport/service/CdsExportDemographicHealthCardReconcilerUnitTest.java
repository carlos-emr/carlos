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
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicHealthCardReconcilerUnitTest {
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
        source.put("ChartNumber[1]", "C27");
        source.put("Email[1]", "old@example.test");
        source.put("SIN[1]", "123456789");
        source.put("PreferredOfficialLanguage[1]", "ENG");
        source.put("PreferredSpokenLanguage[1]", "fra");
    }

    @Test
    void shouldVerifyUnchangedCardPartsPreserveTheirSourceRepresentationAndLeadingZero_forDemographicHealthCardReconcilerContract() throws Exception {
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.fields()).hasSize(4);
        assertThat(result.changes()).isEmpty();
        assertThat(text(render(result), "Number")).containsExactly("012 345 6789");
        assertThat(result.toString()).doesNotContain("0123456789", result.unitAddress());
    }

    @Test
    void shouldVerifyAVersionEditDoesNotReplaceTheOriginalNumberOrExpiry_forDemographicHealthCardReconcilerContract() throws Exception {
        fixture.currentField("ver", "CD");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        var document = render(result);
        assertThat(text(document, "Version")).containsExactly("CD");
        assertThat(text(document, "Number")).containsExactly("012 345 6789");
        assertThat(text(document, "Expirydate")).containsExactly("2028-01-02");
    }

    @Test
    void shouldVerifyLaterIssuerAndNumberEditsUseCurrentValuesAndPinnedAuthorityCodes_forDemographicHealthCardReconcilerContract() throws Exception {
        fixture.currentField("hc_type", "QC");
        fixture.currentField("hin", "0012345678");
        var document = render(reconcile());
        assertThat(text(document, "ProvinceCode")).containsExactly("CA-QC");
        assertThat(text(document, "Number")).containsExactly("0012345678");
    }

    @Test
    void shouldVerifyAnOptionalVersionCanBeCleared_forDemographicHealthCardReconcilerContract() throws Exception {
        fixture.currentField("ver", null);
        assertThat(text(render(reconcile()), "Version")).isEmpty();
    }

    @Test
    void shouldVerifyClearingTheEntireNativeCardRemovesTheWholeOriginalCard_forDemographicHealthCardReconcilerContract() throws Exception {
        for (String column : List.of("hin", "hc_type", "ver", "hc_renew_date"))
            fixture.currentField(column, null);
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("HealthCard[1]");
        assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(4);
        assertThat(text(render(result), "HealthCard")).isEmpty();
    }

    @Test
    void shouldVerifyANewCardCanBeAdded_withBothRequiredParts() throws Exception {
        source.keySet().removeIf(key -> key.startsWith("HealthCard[1]/"));
        fixture.preparedHealth(null, null, null, null);
        fixture.currentField("hin", "0012345678");
        fixture.currentField("hc_type", "BC");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        var document = render(result);
        assertThat(text(document, "Number")).containsExactly("0012345678");
        assertThat(text(document, "ProvinceCode")).containsExactly("CA-BC");
    }

    @Test
    void shouldVerifyIncompleteAndUnsupportedNativeEditsCannotReplayTheOldCard_forDemographicHealthCardReconcilerContract() throws Exception {
        for (var entry :
                List.of(
                        Map.entry("hin", ""),
                        Map.entry("hc_type", ""),
                        Map.entry("hc_type", "unknown"),
                        Map.entry("ver", "ABC"),
                        Map.entry("hin", "A".repeat(21)))) {
            setup();
            fixture.currentField(entry.getKey(), entry.getValue());
            var result = reconcile();
            assertThat(result.representable()).isFalse();
            assertThatThrownBy(result::changes)
                    .hasMessage("CDS_EXPORT_DEMOGRAPHIC_HEALTH_CARD_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldVerifyMissingPreparedSourcePartsAndMatchedExistingCardValuesRequireReview_forDemographicHealthCardReconcilerContract() throws Exception {
        source.remove("HealthCard[1]/Version[1]");
        assertThat(reconcile().representable()).isFalse();
        setup();
        fixture.oldField("hin", "different");
        assertThat(reconcile().representable()).isFalse();
    }

    @Test
    void shouldVerifyAnExpiryOnlyEditLeavesTheOtherCardPartsUntouched_forDemographicHealthCardReconcilerContract() throws Exception {
        fixture.currentField("hc_renew_date", "2029-01-02");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(text(render(result), "Expirydate")).containsExactly("2029-01-02");
    }

    @Test
    void shouldVerifyExpiredValuesPreventHealthCardDecisions_forDemographicHealthCardReconcilerContract() throws Exception {
        active = false;
        assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES");
    }

    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph() throws Exception {
        return fixture.withSource(source);
    }

    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        return graph.index().nativeParents().values().iterator().next();
    }

    private CdsExportDemographicHealthCardReconciler.Result reconcile() throws Exception {
        var graph = graph();
        return new CdsExportDemographicHealthCardReconciler()
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

    private Document render(CdsExportDemographicHealthCardReconciler.Result result)
            throws Exception {
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
