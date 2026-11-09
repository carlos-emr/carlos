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
class CdsExportDemographicOptionalDateReconcilerUnitTest {
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
        source.put("HealthCard[1]/Number[1]", "0123456789");
        source.put("HealthCard[1]/ProvinceCode[1]", "CA-ON");
        source.put("HealthCard[1]/Expirydate[1]", "2028-01-02");
        source.put("ChartNumber[1]", "C27");
        source.put("Email[1]", "old@example.test");
        source.put("SIN[1]", "123456789");
        source.put("PreferredOfficialLanguage[1]", "ENG");
        source.put("PreferredSpokenLanguage[1]", "fra");
    }

    @Test
    void unchangedDatesRetainExactSourceOffsets() throws Exception {
        source.put("PersonStatusDate[1]", "2024-01-02-05:00");
        source.put("HealthCard[1]/Expirydate[1]", "2028-01-02Z");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.fields()).hasSize(2);
        assertThat(result.changes()).isEmpty();
        assertThat(text(render(result), "PersonStatusDate")).containsExactly("2024-01-02-05:00");
    }

    @Test
    void preparedSyntheticDatesAreEmittedWithoutRestoringUnshiftedOriginals() throws Exception {
        fixture.preparedOptionalDate("patient_status_date", "2025-01-02");
        fixture.preparedOptionalDate("hc_renew_date", "2029-01-02");
        var result = reconcile();
        assertThat(result.changes()).hasSize(2);
        var document = render(result);
        assertThat(text(document, "PersonStatusDate")).containsExactly("2025-01-02");
        assertThat(text(document, "Expirydate")).containsExactly("2029-01-02");
    }

    @Test
    void aNativeEditIsNotShiftedTwiceAndDoesNotChangeTheOtherDate() throws Exception {
        fixture.preparedOptionalDate("patient_status_date", "2025-01-02");
        fixture.currentField("patient_status_date", "2026-01-02");
        var document = render(reconcile());
        assertThat(text(document, "PersonStatusDate")).containsExactly("2026-01-02");
        assertThat(text(document, "Expirydate")).containsExactly("2028-01-02");
    }

    @Test
    void anEditedDateWithAnUnprojectedSourceOffsetRequiresReview() throws Exception {
        source.put("HealthCard[1]/Expirydate[1]", "2028-01-02Z");
        fixture.currentField("hc_renew_date", "2029-01-02");
        var result = reconcile();
        assertThat(result.representable()).isFalse();
        assertThatThrownBy(result::changes)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_OPTIONAL_DATES_REQUIRES_REVIEW");
    }

    @Test
    void explicitNativeClearsRemoveOnlyThoseDates() throws Exception {
        fixture.currentField("patient_status_date", null);
        fixture.currentField("hc_renew_date", null);
        var document = render(reconcile());
        assertThat(text(document, "PersonStatusDate")).isEmpty();
        assertThat(text(document, "Expirydate")).isEmpty();
        assertThat(text(document, "Number")).containsExactly("0123456789");
    }

    @Test
    void missingOptionalDatesCanBeAdded() throws Exception {
        source.remove("PersonStatusDate[1]");
        fixture.preparedOptionalDate("patient_status_date", null);
        fixture.currentField("patient_status_date", "2026-01-02");
        var result = reconcile();
        assertThat(result.changes().getFirst().expectedPriorFields()).isEmpty();
        assertThat(text(render(result), "PersonStatusDate")).containsExactly("2026-01-02");
    }

    @Test
    void invalidCurrentAndSourceDatesHaveNoHistoricalFallback() throws Exception {
        fixture.currentField("hc_renew_date", "2028-02-30");
        assertThat(reconcile().representable()).isFalse();
        setup();
        source.put("PersonStatusDate[1]", "2024-02-30");
        assertThat(reconcile().representable()).isFalse();
    }

    @Test
    void aMatchedExistingChartsDifferentDateCannotQualifyThePreparedProjection() throws Exception {
        fixture.oldField("patient_status_date", "2020-01-02");
        assertThat(reconcile().representable()).isFalse();
    }

    @Test
    void aStatusChangeDoesNotRewriteItsIndependentDate() throws Exception {
        fixture.currentField("patient_status", "IN");
        assertThat(reconcile().changes()).isEmpty();
    }

    @Test
    void bothAbsentDatesRemainAbsentAndExpiredValuesFail() throws Exception {
        source.remove("PersonStatusDate[1]");
        source.remove("HealthCard[1]/Expirydate[1]");
        fixture.preparedOptionalDate("patient_status_date", null);
        fixture.preparedOptionalDate("hc_renew_date", null);
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).isEmpty();
        active = false;
        assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES");
    }

    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph() throws Exception {
        return fixture.withSource(source);
    }

    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        return graph.index().nativeParents().values().iterator().next();
    }

    private CdsExportDemographicOptionalDateReconciler.Result reconcile() throws Exception {
        var graph = graph();
        return new CdsExportDemographicOptionalDateReconciler()
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

    private Document render(CdsExportDemographicOptionalDateReconciler.Result result)
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
