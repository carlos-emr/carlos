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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicNamesReconcilerUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source = new LinkedHashMap<>();
    private boolean active;

    @BeforeEach
    void setup() {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
        active = true;
        source.clear();
        source.put("Names[1]/NamePrefix[1]", "Dr");
        source.put("Names[1]/LegalName[1]/@namePurpose", "L");
        name("FirstName[1]", "Ada", "GIV");
        name("LastName[1]", "Family", "FAMC");
        name("OtherName[1]", "Source Middle", "GIV");
        source.put("DateOfBirth[1]", "1980-01-02");
        source.put("Gender[1]", "F");
        source.put("UniqueVendorIdSequence[1]", "27");
        source.put("PersonStatusCode[1]/PersonStatusAsEnum[1]", "A");
    }

    @Test
    void unchangedNamesKeepEveryOriginalNamePartAndAlternateFamilyName() throws Exception {
        name("OtherName[2]", "Alternate Family", "FAMC");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(result.changes()).isEmpty();
        assertThat(result.fields()).hasSize(4);
        assertThat(result.sourceFields()).hasSize(10);
        var output = render(result);
        assertThat(text(output, "Part"))
                .containsExactly("Ada", "Family", "Source Middle", "Alternate Family");
        assertThat(result.toString()).doesNotContain("Ada", "Family", result.unitAddress());
    }

    @Test
    void changingOnlyOneNameDoesNotFlattenOtherNameParts() throws Exception {
        name("OtherName[2]", "Alternate Family", "FAMC");
        fixture.currentField("first_name", "Edited😀");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath())
                .isEqualTo("Names[1]/LegalName[1]/FirstName[1]/Part[1]");
        assertThat(text(render(result), "Part"))
                .containsExactly("Edited😀", "Family", "Source Middle", "Alternate Family");
    }

    @Test
    void oneMiddlePartCanBeChangedExactly() throws Exception {
        fixture.currentField("middleNames", "New middle");
        var result = reconcile();
        assertThat(result.representable()).isTrue();
        assertThat(text(render(result), "Part")).containsExactly("Ada", "Family", "New middle");
    }

    @Test
    void aJoinedMiddleNameEditRequiresReviewRatherThanBeingCopiedIntoEveryOriginalPart()
            throws Exception {
        name("OtherName[1]", "Source", "GIV");
        name("OtherName[2]", "Middle", "GIV");
        fixture.currentField("middleNames", "Later edit");
        var result = reconcile();
        assertThat(result.representable()).isFalse();
        assertThatThrownBy(result::changes)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_NAMES_REQUIRES_REVIEW");
    }

    @Test
    void
            clearingTheNativeJoinedMiddleNameDeletesAllProjectedGivenPartsAndKeepsAnAlternateFamilyPart()
                    throws Exception {
        name("OtherName[1]", "Source", "GIV");
        name("OtherName[2]", "Middle", "GIV");
        name("OtherName[3]", "Alternate", "FAMC");
        fixture.currentField("middleNames", null);
        var result = reconcile();
        assertThat(result.changes()).hasSize(2);
        assertThat(text(render(result), "Part")).containsExactly("Ada", "Family", "Alternate");
    }

    @Test
    void anUnprojectedOriginalMiddleNameIsPreservedAndCannotBeOverwrittenByALaterSingleValue()
            throws Exception {
        fixture.preparedScalar(CdsDemographicNativeDto.MIDDLE_NAMES_SCALAR, null);
        fixture.oldField("middleNames", null);
        assertThat(reconcile().changes()).isEmpty();
        fixture.currentField("middleNames", "New");
        assertThat(reconcile().representable()).isFalse();
    }

    @Test
    void aNewMiddlePartIsAppendedAfterAnExistingAlternateFamilyName() throws Exception {
        removeName("OtherName[1]");
        name("OtherName[1]", "Alternate", "FAMC");
        fixture.preparedScalar(CdsDemographicNativeDto.MIDDLE_NAMES_SCALAR, null);
        fixture.oldField("middleNames", null);
        fixture.currentField("middleNames", "New");
        var result = reconcile();
        assertThat(result.changes().getFirst().relativePath())
                .isEqualTo("Names[1]/LegalName[1]/OtherName[2]");
        assertThat(text(render(result), "Part"))
                .containsExactly("Ada", "Family", "Alternate", "New");
    }

    @Test
    void anAbsentPrefixCanBeAddedAndAnExistingPrefixCanBeCleared() throws Exception {
        source.remove("Names[1]/NamePrefix[1]");
        fixture.preparedScalar("Names/NamePrefix", null);
        fixture.oldField("title", null);
        fixture.currentField("title", "Prof");
        assertThat(text(render(reconcile()), "NamePrefix")).containsExactly("Prof");
        setup();
        fixture.currentField("title", null);
        assertThat(text(render(reconcile()), "NamePrefix")).isEmpty();
    }

    @Test
    void invalidRequiredNamesAndUnrepresentablePrefixesHaveNoSourceFallback() throws Exception {
        for (var change :
                List.of(
                        Map.entry("first_name", ""),
                        Map.entry("last_name", " "),
                        Map.entry("first_name", "A".repeat(51)),
                        Map.entry("title", "Unknown"))) {
            setup();
            fixture.currentField(change.getKey(), change.getValue());
            assertThat(reconcile().representable()).isFalse();
        }
    }

    @Test
    void aMatchedExistingChartsDifferentNameCannotBeReplacedByReplayingTheSource()
            throws Exception {
        fixture.oldField("first_name", "Different existing");
        var result = reconcile();
        assertThat(result.representable()).isFalse();
        assertThat(result.fields().values())
                .anyMatch(
                        field -> field.rule().equals("PREPARED_NATIVE_NAME_PROJECTION_NOT_PROVEN"));
    }

    @Test
    void aSubstitutedOriginalNameAndAMissingLegalPurposeCannotQualifyNames() throws Exception {
        source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Other");
        assertThat(reconcile().representable()).isFalse();
        setup();
        source.remove("Names[1]/LegalName[1]/@namePurpose");
        assertThat(reconcile().representable()).isFalse();
    }

    @Test
    void expiredValuesAndCancellationCannotProduceNameChanges() throws Exception {
        active = false;
        assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES");
        active = true;
        var graph = graph();
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicNamesReconciler()
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

    private void name(String child, String part, String kind) {
        source.put("Names[1]/LegalName[1]/" + child + "/Part[1]", part);
        source.put("Names[1]/LegalName[1]/" + child + "/PartType[1]", kind);
    }

    private void removeName(String child) {
        source.keySet().removeIf(key -> key.startsWith("Names[1]/LegalName[1]/" + child + "/"));
    }

    private CdsExportDemographicProjectionEvidenceUnitTest.Graph graph() throws Exception {
        return fixture.withSource(source);
    }

    private static String address(CdsExportDemographicProjectionEvidenceUnitTest.Graph graph) {
        return graph.index().nativeParents().values().iterator().next();
    }

    private CdsExportDemographicNamesReconciler.Result reconcile() throws Exception {
        var graph = graph();
        return new CdsExportDemographicNamesReconciler()
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
                throw new AssertionError("No binary name");
            }
        };
    }

    private Document render(CdsExportDemographicNamesReconciler.Result result) throws Exception {
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
