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
class CdsExportDemographicScalarReconcilerUnitTest {
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
        source.put("ChartNumber[1]", "C27");
        source.put("Email[1]", "old@example.test");
        source.put("SIN[1]", "123456789");
        source.put("PreferredOfficialLanguage[1]", "ENG");
        source.put("PreferredSpokenLanguage[1]", "fra");
    }

    @Test
    void shouldVerifyUnchangedValuesRetainTheirExactSourceRepresentation_forDemographicScalarReconcilerContract() throws Exception {
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.fields()).hasSize(7);
        assertThat(result.changes()).isEmpty();
        assertThat(text(render(result), "PreferredSpokenLanguage")).containsExactly("fra");
        assertThat(result.toString()).doesNotContain("old@example.test", result.unitAddress());
    }

    @Test
    void shouldVerifyEditingOneScalarChangesOnlyItsOwnSourceIdentity_forDemographicScalarReconcilerContract() throws Exception {
        fixture.currentField("email", "edited@example.test");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("Email[1]");
        var document = render(result);
        assertThat(text(document, "Email")).containsExactly("edited@example.test");
        assertThat(text(document, "PreferredSpokenLanguage")).containsExactly("fra");
    }

    @Test
    void shouldVerifyClearingAnOptionalValueDoesNotReplayItsOriginal_forDemographicScalarReconcilerContract() throws Exception {
        fixture.currentField("chart_no", "");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes().getFirst().content()).isNull();
        assertThat(text(render(result), "ChartNumber")).isEmpty();
    }

    @Test
    void shouldVerifyAStatusEditReplacesTheChoiceAndKeepsItsIndependentOriginalDate_forDemographicScalarReconcilerContract() throws Exception {
        fixture.currentField("patient_status", "IN");
        var result = reconcile();
        var document = render(result);
        assertThat(text(document, "PersonStatusAsEnum")).containsExactly("I");
        assertThat(text(document, "PersonStatusDate")).containsExactly("2024-01-02");
        assertThat(result.changes()).hasSize(1);
        fixture.currentField("patient_status", "Away");
        document = render(reconcile());
        assertThat(text(document, "PersonStatusAsEnum")).isEmpty();
        assertThat(text(document, "PersonStatusAsPlainText")).containsExactly("Away");
    }

    @Test
    void shouldVerifyAStatusDateEditDoesNotMakeAnUnchangedStatusLookEdited_forDemographicScalarReconcilerContract() throws Exception {
        fixture.currentField("patient_status_date", "2025-01-02");
        assertThat(reconcile().changes()).isEmpty();
    }

    @Test
    void shouldVerifyLaterKnownLanguageEditsUseReviewedCodeMappings_forDemographicScalarReconcilerContract() throws Exception {
        fixture.currentField("spoken_lang", "English");
        fixture.currentField("official_lang", "French");
        var document = render(reconcile());
        assertThat(text(document, "PreferredSpokenLanguage")).containsExactly("eng");
        assertThat(text(document, "PreferredOfficialLanguage")).containsExactly("FRE");
    }

    @Test
    void shouldVerifyUnsupportedCurrentValuesCannotFallBack_toHistoricalSource() throws Exception {
        // Values the element cannot hold (an e-mail outside the pattern, a 16-character chart
        // number, a short SIN, an unlisted language) are carried in the native demographic
        // record and leave the element out; they no longer hold the imported chart.
        for (var entry :
                List.of(
                        Map.entry("email", "bad address"),
                        Map.entry("chart_no", "A".repeat(16)),
                        Map.entry("sin", "123"),
                        Map.entry("spoken_lang", "Unknown"),
                        Map.entry("official_lang", "Unknown"))) {
            setup();
            fixture.currentField(entry.getKey(), entry.getValue());
            var result = reconcile();
            assertThat(result.representable()).isTrue();
        }
        for (var entry : List.of(Map.entry("sex", ""), Map.entry("patient_status", ""))) {
            setup();
            fixture.currentField(entry.getKey(), entry.getValue());
            var result = reconcile();
            assertThat(result.representable()).isFalse();
            assertThatThrownBy(result::changes)
                    .hasMessage("CDS_EXPORT_DEMOGRAPHIC_SCALARS_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldReplaceASourceOnlyValue_whenALaterNativeValueSupersedesIt() throws Exception {
        // Updated: a value typed over a source-only one used to hold the imported chart. The
        // CARLOS edit is the truth; the superseded source element is listed as replaced.
        fixture.preparedScalar("Email", null);
        fixture.oldField("email", null);
        assertThat(reconcile().changes()).isEmpty();
        fixture.currentField("email", "new@example.test");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(rule(result, "email"))
                .isEqualTo("EDITED_CURRENT_SCALAR_SUPERSEDES_UNPROJECTED_SOURCE");
        assertThat(text(render(result), "Email")).containsExactly("new@example.test");
        assertThat(result.changes().getFirst().expectedPriorFields()).hasSize(1);
    }

    @Test
    void shouldKeepASourceOnlyValue_whenAFormSaveWritesAnEmptyColumnOverIt() throws Exception {
        fixture.preparedScalar("Email", null);
        fixture.oldField("email", null);
        fixture.currentField("email", "");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).isEmpty();
        assertThat(rule(result, "email"))
                .isEqualTo("UNPROJECTED_SOURCE_SCALAR_KEPT_WITHOUT_A_CURRENT_VALUE");
        assertThat(text(render(result), "Email")).containsExactly("old@example.test");
    }

    private static String rule(
            CdsExportDemographicScalarReconciler.Result result, String column) {
        return result.fields().entrySet().stream()
                .filter(entry -> entry.getKey().column().equals(column))
                .findFirst()
                .orElseThrow()
                .getValue()
                .rule();
    }

    @Test
    void shouldVerifyAbsentOptionalValuesCanBeAdded_withoutInventingPreviousSourceFields() throws Exception {
        source.remove("Email[1]");
        fixture.preparedScalar("Email", null);
        fixture.oldField("email", null);
        fixture.currentField("email", "new@example.test");
        var result = reconcile();
        assertThat(result.changes().getFirst().expectedPriorFields()).isEmpty();
        assertThat(text(render(result), "Email")).containsExactly("new@example.test");
    }

    @Test
    void shouldVerifyMatchedExistingNativeValuesAndMissingPreparedSourceFieldsRequireReview_forDemographicScalarReconcilerContract() throws Exception {
        fixture.oldField("email", "existing@example.test");
        assertThat(reconcile().representable()).isFalse();
        setup();
        source.remove("Email[1]");
        assertThat(reconcile().representable()).isFalse();
    }

    @Test
    void shouldVerifyExpiredValuesAndCancellationPreventScalarDecisions_forDemographicScalarReconcilerContract() throws Exception {
        active = false;
        assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES");
        active = true;
        var graph = graph();
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicScalarReconciler()
                                        .reconcile(
                                                27,
                                                address(graph),
                                                graph.index(),
                                                graph.checked(),
                                                graph.fields(),
                                                graph.current(),
                                                graph.witness(),
                                                values(),
                                                () -> {
                                                    throw new IllegalStateException("CANCELLED");
                                                }))
                .hasMessage("CANCELLED");
    }

    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph() throws Exception {
        return fixture.withSource(source);
    }

    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        return graph.index().nativeParents().values().iterator().next();
    }

    private CdsExportDemographicScalarReconciler.Result reconcile() throws Exception {
        var graph = graph();
        return new CdsExportDemographicScalarReconciler()
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

    private Document render(CdsExportDemographicScalarReconciler.Result result) throws Exception {
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
