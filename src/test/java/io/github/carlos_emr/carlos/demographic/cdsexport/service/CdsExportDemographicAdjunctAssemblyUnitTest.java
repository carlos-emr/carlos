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
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
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
class CdsExportDemographicAdjunctAssemblyUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private final Map<String, String> source = new LinkedHashMap<>();
    private final List<CdsExportCapturedTextRowDto> old = new ArrayList<>(),
            current = new ArrayList<>();
    private boolean active;
    private Set<CdsCategory> selected;

    @BeforeEach
    void setup() {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
        fixture.preparedNote("Original 😀 note", false);
        source.clear();
        old.clear();
        current.clear();
        active = true;
        selected = Set.of(CdsCategory.DEMOGRAPHICS);
        source.put("Names[1]/LegalName[1]/@namePurpose", "L");
        source.put("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Ada");
        source.put("Names[1]/LegalName[1]/FirstName[1]/PartType[1]", "GIV");
        source.put("Names[1]/LegalName[1]/LastName[1]/Part[1]", "Family");
        source.put("Names[1]/LegalName[1]/LastName[1]/PartType[1]", "FAMC");
        source.put("DateOfBirth[1]", "1980-01-02");
        source.put("Gender[1]", "F");
        source.put("UniqueVendorIdSequence[1]", "27");
        source.put("PersonStatusCode[1]/PersonStatusAsEnum[1]", "A");
        source.put("NoteAboutPatient[1]", "Original 😀 note");
        old.add(cust(DemographicCust.wrapPatientNotes("Original 😀 note"), true));
        current.add(cust(DemographicCust.wrapPatientNotes("Original 😀 note"), false));
        source.put("Names[1]/NamePrefix[1]", "Dr");
        source.put("Names[1]/LegalName[1]/OtherName[1]/Part[1]", "Source Middle");
        source.put("Names[1]/LegalName[1]/OtherName[1]/PartType[1]", "GIV");
        source.put("HealthCard[1]/Number[1]", "0123456789");
        source.put("HealthCard[1]/Version[1]", "AB");
        source.put("HealthCard[1]/Expirydate[1]", "2028-01-02");
        source.put("HealthCard[1]/ProvinceCode[1]", "CA-ON");
        source.put("ChartNumber[1]", "C27");
        source.put("Email[1]", "old@example.test");
        source.put("SIN[1]", "123456789");
        source.put("PreferredOfficialLanguage[1]", "ENG");
        source.put("PreferredSpokenLanguage[1]", "fra");
        source.put("PersonStatusDate[1]", "2024-01-02");
        source.put("Address[1]/@addressType", "M");
        source.put("Address[1]/Structured[1]/Line1[1]", "123 Source");
        source.put("Address[1]/Structured[1]/City[1]", "Town");
        source.put("Address[1]/Structured[1]/CountrySubdivisionCode[1]", "CA-ON");
        source.put("Address[1]/Structured[1]/PostalZipCode[1]/PostalCode[1]", "K1A0B1");
        source.put("PhoneNumber[1]/@phoneNumberType", "R");
        source.put("PhoneNumber[1]/phoneNumber[1]", "6135550000");
        source.put("PhoneNumber[1]/extension[1]", "0012");
        old.add(property(1, "hPhoneExt", "0012"));
        current.add(property(1, "hPhoneExt", "0012"));
    }

    @Test
    void shouldVerifyEverySuppliedNativeFieldAndSourceIdHasADecisionOrAnExplicitHandOff_forDemographicAdjunctAssemblyContract() throws Exception {
        var result = reconcile();
        assertThat(result.coreRepresentable()).isTrue();
        assertThat(result.demographicComplete()).isFalse();
        assertThat(result.fields()).hasSize(39);
        assertThat(result.deferredNativeFields()).hasSize(33);
        assertThat(result.coreChanges()).isEmpty();
        var all = new HashSet<>(result.fields().keySet());
        assertThat(Collections.disjoint(all, result.deferredNativeFields())).isTrue();
        all.addAll(result.deferredNativeFields());
        assertThat(all).hasSize(72);
        assertThat(result.deferredSourceFields()).hasSize(1);
        render(result);
    }

    @Test
    void shouldVerifySimultaneousCorePhoneAndNoteEditsCompose_withExactSourceCoverage() throws Exception {
        fixture.currentField("email", "edited@example.test");
        change("content", DemographicCust.wrapPatientNotes("Edited note"));
        current.set(1, changed(current.get(1), "value", "0023"));
        var result = reconcile();
        assertThat(result.coreChanges()).hasSize(3);
        var document = render(result);
        assertThat(text(document, "Email")).containsExactly("edited@example.test");
        assertThat(text(document, "extension")).containsExactly("0023");
        assertThat(text(document, "NoteAboutPatient")).containsExactly("Edited note");
    }

    @Test
    void shouldVerifyAMissingImportedNoteRowBlocksAllChangesEven_whenNoCustFieldsRemainToMark()
            throws Exception {
        current.removeFirst();
        fixture.currentField("email", "edited@example.test");
        var result = reconcile();
        assertThat(result.coreRepresentable()).isFalse();
        assertThat(result.blockers())
                .containsExactly("CDS_EXPORT_DEMOGRAPHIC_NOTE_ROW_DELETION_REQUIRES_REVIEW");
        assertThat(result.proposedChanges()).isEmpty();
        assertThatThrownBy(result::coreChanges)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_CORE_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyUnknownPropertiesAndCareTeamValuesStayExplicitlyDeferred_forDemographicAdjunctAssemblyContract() throws Exception {
        current.add(property(2, "anotherKey", "Do not discard"));
        change("cust1", "Care team reference");
        var result = reconcile();
        assertThat(result.coreRepresentable()).isTrue();
        assertThat(result.demographicComplete()).isFalse();
        assertThat(result.deferredNativeFields()).hasSize(41);
        assertThat(result.fields()).hasSize(39);
        assertThat(
                        result.deferredNativeFields().stream()
                                .filter(key -> key.table().equals("demographicExt")))
                .hasSize(8);
    }

    @Test
    void shouldVerifyOneUnrepresentableCoreFieldPreventsIndependentValidEdits_fromLeakingIntoAPlan()
            throws Exception {
        fixture.currentField("sex", "BAD");
        current.set(1, changed(current.get(1), "value", "0023"));
        var result = reconcile();
        assertThat(result.coreRepresentable()).isFalse();
        assertThat(result.proposedChanges()).isEmpty();
    }

    private void change(String column, String value) {
        current.set(0, changed(current.getFirst(), column, value));
    }

    private static CdsExportCapturedTextRowDto changed(
            CdsExportCapturedTextRowDto row, String column, String value) {
        var fields = new LinkedHashMap<>(row.fields());
        fields.put(column, value);
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

    private static CdsExportCapturedTextRowDto cust(String content, boolean full) {
        var fields = new LinkedHashMap<String, String>();
        (full ? Plan.DEMOGRAPHIC_CUST : Plan.DEMOGRAPHIC_CUST_DEMOGRAPHICS)
                .columns()
                .forEach(column -> fields.put(column, null));
        fields.put("demographic_no", "27");
        fields.put("content", content);
        if (full) fields.put("cust3", "Unselected alert body");
        return new CdsExportCapturedTextRowDto(
                "demographiccust", CdsExportPatientTableSourceReader.key(27), fields);
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
        return new CdsNativeRowState(
                row.table(),
                row.fields().get(row.table().equals("demographicExt") ? "id" : "demographic_no"),
                columns,
                cells);
    }

    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var state = state(row);
        return new CdsExportNativeRowReceiptDto(
                row.table(),
                state.id(),
                row.recordKey(),
                state.digest(),
                state,
                row.table().equals("demographiccust") && !row.fields().containsKey("cust3"));
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
                            .filter(
                                    candidate ->
                                            candidate.table().equals(oldState.table())
                                                    && candidate
                                                            .fields()
                                                            .get(
                                                                    candidate
                                                                                    .table()
                                                                                    .equals(
                                                                                            "demographicExt")
                                                                            ? "id"
                                                                            : "demographic_no")
                                                            .equals(oldState.id()))
                            .findFirst()
                            .orElse(null);
            if (actual == null)
                checkedRefs.add(new CheckedReference(reference, null, Reason.ROW_NOT_IN_CAPTURE));
            else {
                var currentState = state(actual);
                if (actual.table().equals("demographiccust")
                        && !actual.fields().containsKey("cust3")) {
                    var partial = CdsExportPartialRowComparator.compare(oldState, currentState);
                    checkedRefs.add(
                            new CheckedReference(
                                    reference,
                                    currentState.digest(),
                                    Reason.RECEIPT_PARTIAL_PROJECTION,
                                    null,
                                    partial));
                } else {
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
                        .map(CdsExportDemographicAdjunctAssemblyUnitTest::witness)
                        .toList());
    }

    private CdsExportDemographicRetainedCoreReconciler.Result reconcile() throws Exception {
        return invoke(graph(), current, null);
    }

    private CdsExportDemographicRetainedCoreReconciler.Result invoke(
            Graph graph,
            List<CdsExportCapturedTextRowDto> rows,
            List<CdsExportNativeRowReceiptDto> witnesses)
            throws Exception {
        var base = graph.base();
        var proof = witnesses == null ? graph.witnesses() : witnesses;
        var properties = rows.stream().filter(row -> row.table().equals("demographicExt")).toList();
        var propertyWitnesses =
                proof.stream().filter(row -> row.table().equals("demographicExt")).toList();
        var custom =
                rows.stream()
                        .filter(row -> row.table().equals("demographiccust"))
                        .findFirst()
                        .orElse(null);
        var customWitness =
                proof.stream()
                        .filter(row -> row.table().equals("demographiccust"))
                        .findFirst()
                        .orElse(null);
        return new CdsExportDemographicRetainedCoreReconciler()
                .reconcileWithAdjuncts(
                        27,
                        selected,
                        graph.address(),
                        base.index(),
                        base.checked(),
                        base.fields(),
                        base.current(),
                        base.witness(),
                        properties,
                        propertyWitnesses,
                        custom,
                        customWitness,
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

    private Document render(CdsExportDemographicRetainedCoreReconciler.Result result)
            throws Exception {
        var graph = graph();
        var plan =
                new CdsExportRetainedSectionWriter()
                        .prepareChanges(
                                graph.base().fields(), values(), result.coreChanges(), () -> {});
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
