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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Document;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicRetainedPhonesReconcilerUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source = new LinkedHashMap<>();
    private final List<CdsExportCapturedTextRowDto> old = new ArrayList<>(),
            current = new ArrayList<>();
    private boolean active;

    @BeforeEach
    void setup() {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
        fixture.preparedPhones(
                new CdsDemographicNativeDto.Phone("6135550000", "0012"),
                null,
                new CdsDemographicNativeDto.Phone("613-555-0105", "009"));
        source.clear();
        old.clear();
        current.clear();
        active = true;
        source.put("Names[1]/LegalName[1]/@namePurpose", "L");
        source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada");
        source.put("Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV");
        source.put("Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family");
        source.put("Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC");
        source.put("DateOfBirth[1]", "1980-01-02");
        source.put("Gender[1]", "F");
        source.put("UniqueVendorIdSequence[1]", "27");
        source.put("PersonStatusCode[1]/PersonStatusAsEnum[1]", "A");
        source.put("PhoneNumber[1]/@phoneNumberType", "R");
        source.put("PhoneNumber[1]/phoneNumber[1]", "6135550000");
        source.put("PhoneNumber[1]/extension[1]", "0012");
        source.put("PhoneNumber[2]/@phoneNumberType", "C");
        source.put("PhoneNumber[2]/areaCode[1]", "613");
        source.put("PhoneNumber[2]/number[1]", "0105");
        source.put("PhoneNumber[2]/extension[1]", "009");
        source.put("PhoneNumber[2]/exchange[1]", "555");
        old.add(property(1, "hPhoneExt", "0012"));
        old.add(property(2, "demo_cell", "613-555-0105"));
        current.addAll(old);
    }

    @Test
    void originalPhoneComponentsAndUnprojectedMobileExtensionsSurviveUnchangedNativeRows()
            throws Exception {
        var result = reconcile();
        assertThat(result.phonesRepresentable()).isTrue();
        assertThat(result.fields()).hasSize(18);
        assertThat(result.changes()).isEmpty();
        var document = render(result);
        assertThat(text(document, "extension")).containsExactly("0012", "009");
        assertThat(text(document, "areaCode")).containsExactly("613");
        assertThat(result.toString()).doesNotContain("613", result.unitAddress());
    }

    @Test
    void anExtensionEditReplacesOnlyItsSourceField() throws Exception {
        change(0, "value", "0023");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath())
                .isEqualTo("PhoneNumber[1]/extension[1]");
        assertThat(text(render(result), "extension")).containsExactly("0023", "009");
    }

    @Test
    void anExtensionClearDoesNotReplayTheOriginal() throws Exception {
        change(0, "value", null);
        assertThat(text(render(reconcile()), "extension")).containsExactly("009");
    }

    @Test
    void changingAHomeNumberPreservesItsProvenExtension() throws Exception {
        fixture.currentField("phone", "613-555-0111");
        var document = render(reconcile());
        assertThat(text(document, "phoneNumber")).containsExactly("613-555-0111");
        assertThat(text(document, "extension")).containsExactly("0012", "009");
        assertThat(text(document, "areaCode")).containsExactly("613");
    }

    @Test
    void changingAMobileWithAnUnprojectedExtensionRequiresReview() throws Exception {
        change(1, "value", "613-555-0111");
        var result = reconcile();
        assertThat(result.phonesRepresentable()).isFalse();
        assertThatThrownBy(result::changes)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONES_REQUIRES_REVIEW");
    }

    @Test
    void aMobileWithoutAnUnprojectedExtensionCanUseAnExactLaterNumber() throws Exception {
        source.remove("PhoneNumber[2]/extension[1]");
        fixture.preparedPhones(
                new CdsDemographicNativeDto.Phone("6135550000", "0012"),
                null,
                new CdsDemographicNativeDto.Phone("613-555-0105", null));
        change(1, "value", "613-555-0111");
        var document = render(reconcile());
        assertThat(text(document, "phoneNumber")).containsExactly("6135550000", "613-555-0111");
        assertThat(text(document, "areaCode")).isEmpty();
    }

    @Test
    void clearingANumberWithAnUnclearedExtensionRequiresReview() throws Exception {
        fixture.currentField("phone", "");
        assertThat(reconcile().phonesRepresentable()).isFalse();
        change(0, "value", null);
        assertThat(text(render(reconcile()), "phoneNumber")).isEmpty();
        assertThat(text(render(reconcile()), "extension")).containsExactly("009");
    }

    @Test
    void subsequentlyAddedWorkPhonesAreAppendedAfterOriginalOccurrences() throws Exception {
        fixture.currentField("phone2", "613-555-0122");
        current.add(property(3, "wPhoneExt", "AB"));
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("PhoneNumber[3]");
        var document = render(result);
        assertThat(text(document, "phoneNumber")).containsExactly("6135550000", "613-555-0122");
        assertThat(text(document, "extension")).containsExactly("0012", "009", "AB");
    }

    @Test
    void hiddenDuplicateAndUnknownNativePropertiesRemainVisibleToTheEnclosingAssembly()
            throws Exception {
        change(0, "hidden", "1");
        assertThat(reconcile().phonesRepresentable()).isFalse();
        setup();
        current.add(property(3, "hPhoneExt", "0012"));
        assertThat(reconcile().phonesRepresentable()).isFalse();
        setup();
        current.add(property(3, "otherProperty", "Keep outside phone decisions"));
        var result = reconcile();
        assertThat(result.deferredPropertyRows())
                .containsExactly(CdsExportPatientTableSourceReader.key(3));
        assertThat(result.phonesRepresentable()).isTrue();
    }

    @Test
    void aDeletedImportedPropertyCannotResurrectItsOriginalValue() throws Exception {
        current.removeFirst();
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROPERTY_DELETION_REQUIRES_REVIEW");
    }

    @Test
    void changingAnImportedPropertyKeyCannotHideItAmongDeferredProperties() throws Exception {
        change(0, "key_val", "anotherKey");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_PROPERTY_PROJECTION_REQUIRES_REVIEW");
    }

    @Test
    void aMatchedExistingChartCannotBeAssumedToHaveReceivedPreparedExtensions() throws Exception {
        old.removeFirst();
        assertThat(reconcile().phonesRepresentable()).isFalse();
    }

    @Test
    void repeatedSourcePhonesArePreservedUntilAnAmbiguousLaterEdit() throws Exception {
        var copy = new HashMap<>(source);
        copy.forEach(
                (path, value) -> {
                    if (path.startsWith("PhoneNumber[1]/"))
                        source.put(path.replace("PhoneNumber[1]", "PhoneNumber[3]"), value);
                });
        assertThat(reconcile().changes()).isEmpty();
        change(0, "value", "0023");
        assertThat(reconcile().phonesRepresentable()).isFalse();
    }

    @Test
    void expiredValuesAndSubstitutedFrozenRowsCannotQualifyPhoneDecisions() throws Exception {
        active = false;
        assertThatThrownBy(this::reconcile).hasMessage("EXPIRED_VALUES");
        active = true;
        var graph = graph();
        var substituted = new ArrayList<>(current);
        substituted.set(0, changed(current.getFirst(), "value", "Substituted"));
        assertThatThrownBy(() -> invoke(graph, substituted, graph.witnesses()))
                .hasMessage("CDS_EXPORT_CAPTURED_TEXT_WITNESS_SCOPE_INVALID");
    }

    @Test
    void anEmptyNewExtensionWithoutANumberDoesNotConsumeAnOutputOrdinal() throws Exception {
        var copy = new LinkedHashMap<>(source);
        source.clear();
        copy.forEach(
                (path, value) -> {
                    if (!path.startsWith("PhoneNumber[1]/"))
                        source.put(path.replace("PhoneNumber[2]", "PhoneNumber[1]"), value);
                });
        fixture.preparedPhones(
                null, null, new CdsDemographicNativeDto.Phone("613-555-0105", "009"));
        old.removeFirst();
        current.removeFirst();
        current.add(property(3, "hPhoneExt", null));
        fixture.currentField("phone2", "613-555-0122");
        var result = reconcile();
        assertThat(result.changes()).hasSize(1);
        assertThat(result.changes().getFirst().relativePath()).isEqualTo("PhoneNumber[2]");
        assertThat(text(render(result), "phoneNumber")).containsExactly("613-555-0122");
    }

    @Test
    void unknownRowsStillCountAgainstResourceBoundsAndEveryWitnessIsRequired() throws Exception {
        current.add(property(3, "otherProperty", "Retained elsewhere"));
        var graph = graph();
        var base = graph.base();
        assertThatThrownBy(
                        () ->
                                new CdsExportDemographicRetainedPhonesReconciler()
                                        .reconcile(
                                                27,
                                                graph.address(),
                                                base.index(),
                                                base.checked(),
                                                base.fields(),
                                                base.current(),
                                                base.witness(),
                                                current,
                                                graph.witnesses(),
                                                2,
                                                1L << 20,
                                                values(),
                                                () -> {}))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONE_RESOURCE_LIMIT");
        assertThatThrownBy(
                        () ->
                                invoke(
                                        graph,
                                        current,
                                        graph.witnesses().subList(1, graph.witnesses().size())))
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_PHONE_RESOURCE_LIMIT");
    }

    @Test
    void aCapturedInvisibleKeyRequiresItsOwnExactPhoneKeyDisposition() throws Exception {
        change(0, "dashboard_single_value_key", "Substituted");
        assertThatThrownBy(this::reconcile)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_PHONE_DERIVED_KEY_REQUIRES_REVIEW");
    }

    private void change(int index, String column, String value) {
        current.set(index, changed(current.get(index), column, value));
    }

    private static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new LinkedHashMap<>(row.fields());
        fields.put(column, value);
        if (column.equals("key_val")) fields.put("dashboard_single_value_key", value);
        return new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields);
    }

    private static CdsExportCapturedTextRowDto property(int id, String key, String value) {
        var fields = new LinkedHashMap<String, String>();
        Plan.DEMOGRAPHIC_EXT.columns().forEach(column -> fields.put(column, null));
        fields.put("id", Integer.toString(id));
        fields.put("demographic_no", "27");
        fields.put("provider_no", "12");
        fields.put("date_time", "2026-11-06 01:02:03");
        fields.put("hidden", "0");
        fields.put("key_val", key);
        fields.put("dashboard_single_value_key", key);
        fields.put("value", value);
        return new CdsExportCapturedTextRowDto(
                "demographicExt", CdsExportPatientTableSourceReader.key(id), fields);
    }

    private static CdsNativeRowState state(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var entry : row.fields().entrySet()) {
            if (entry.getKey().equals("dashboard_single_value_key")) continue;
            columns.add(new CdsNativeRowDigest.Column(entry.getKey(), "VARCHAR", 255, 0));
            String value = entry.getValue();
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false,
                                    value.getBytes(StandardCharsets.UTF_8).length,
                                    sha(value)));
        }
        return new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells);
    }

    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var state = state(row);
        return new CdsExportNativeRowReceiptDto(
                row.table(), state.id(), row.recordKey(), state.digest(), state);
    }

    private static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private record Graph(
            CdsExportDemographicProjectionEvidenceUnitTest.Graph base,
            String address,
            List<CdsExportNativeRowReceiptDto> witnesses) {}

    private Graph graph() throws Exception {
        var original = fixture.withSource(source);
        String address = original.index().nativeParents().values().iterator().next(),
                run = address.substring(0, 36),
                key = address.substring(37),
                unitId = CdsImportKeys.hash(run, key);
        var unit = original.index().tables().get("cds_import_unit").get(unitId);
        var raw = new LinkedHashMap<>(unit.fields());
        var baseline =
                CdsNativeBaselineCodec.decode(
                        raw.get("native_baseline"),
                        raw.get("target_references"),
                        raw.get("target_digest"));
        var states = new ArrayList<>(baseline.rows());
        var refs = new ArrayList<>(original.index().references().get(address));
        var checkedRefs = new ArrayList<>(original.checked().units().get(address));
        for (var row : old) {
            var oldState = state(row);
            states.add(oldState);
            var reference =
                    new CdsExportRetainedIndexDto.Reference(
                            row.table(), oldState.id(), oldState.digest());
            refs.add(reference);
            var actual =
                    current.stream()
                            .filter(candidate -> candidate.fields().get("id").equals(oldState.id()))
                            .findFirst()
                            .orElse(null);
            if (actual == null)
                checkedRefs.add(new CheckedReference(reference, null, Reason.ROW_NOT_IN_CAPTURE));
            else {
                var currentState = state(actual);
                var difference = CdsNativeRowComparison.compare(oldState, currentState);
                checkedRefs.add(
                        new CheckedReference(
                                reference,
                                currentState.digest(),
                                oldState.digest().equals(currentState.digest())
                                        ? Reason.UNCHANGED_ROW
                                        : Reason.CHANGED_ROW,
                                difference));
            }
        }
        String
                references =
                        new ObjectMapper().writeValueAsString(Map.of("protocol", 2, "refs", refs)),
                digest = sha(references);
        raw.put("target_references", references);
        raw.put("target_digest", digest);
        raw.put("native_baseline", CdsNativeBaselineCodec.encode(references, digest, states));
        var tables = new LinkedHashMap<String, Map<String, CdsExportCapturedTextRowDto>>();
        original.index()
                .tables()
                .forEach((table, rows) -> tables.put(table, new LinkedHashMap<>(rows)));
        tables.get("cds_import_unit")
                .put(unitId, new CdsExportCapturedTextRowDto("cds_import_unit", unitId, raw));
        var allRefs = new LinkedHashMap<>(original.index().references());
        allRefs.put(address, refs);
        var allChecked = new LinkedHashMap<>(original.checked().units());
        allChecked.put(address, checkedRefs);
        var index =
                new CdsExportRetainedIndexDto(
                        original.index().sourceManifestSha256(),
                        original.index().metadataRows(),
                        original.index().metadataBytes(),
                        tables,
                        original.index().appliedUnits(),
                        allRefs,
                        original.index().nativeParents());
        var checked =
                new CdsExportRetainedReceiptVerificationDto(
                        original.checked().sourceManifestSha256(),
                        allChecked,
                        original.checked().unchangedUnits(),
                        original.checked().reviewUnits());
        var base =
                new CdsExportDemographicProjectionEvidenceUnitTest.Graph(
                        index, checked, original.fields(), original.current(), original.witness());
        return new Graph(
                base,
                address,
                current.stream()
                        .map(CdsExportDemographicRetainedPhonesReconcilerUnitTest::witness)
                        .toList());
    }

    private CdsExportDemographicRetainedPhonesReconciler.Result reconcile() throws Exception {
        return invoke(graph(), current, null);
    }

    private CdsExportDemographicRetainedPhonesReconciler.Result invoke(
            Graph graph,
            List<CdsExportCapturedTextRowDto> rows,
            List<CdsExportNativeRowReceiptDto> witnesses)
            throws Exception {
        var base = graph.base();
        return new CdsExportDemographicRetainedPhonesReconciler()
                .reconcile(
                        27,
                        graph.address(),
                        base.index(),
                        base.checked(),
                        base.fields(),
                        base.current(),
                        base.witness(),
                        rows,
                        witnesses == null ? graph.witnesses() : witnesses,
                        100,
                        1L << 20,
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
                throw new AssertionError("No binary phone");
            }
        };
    }

    private Document render(CdsExportDemographicRetainedPhonesReconciler.Result result)
            throws Exception {
        var graph = graph();
        var plan =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(
                                graph.base().fields(), values(), result.changes(), () -> {});
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
        for (int n = 0; n < nodes.getLength(); n++) values.add(nodes.item(n).getTextContent());
        return values;
    }
}
