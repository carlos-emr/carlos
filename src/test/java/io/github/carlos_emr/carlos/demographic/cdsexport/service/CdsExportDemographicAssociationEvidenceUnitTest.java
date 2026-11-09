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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicAssociationEvidenceUnitTest {
    private CdsExportDemographicProjectionEvidenceUnitTest fixture;
    private CdsExportDemographicProjectionEvidenceUnitTest.Graph base;
    private Map<String, Map<String, CdsExportCapturedTextRowDto>> tables;
    private Map<String, List<CdsExportRetainedIndexDto.Reference>> references;
    private Map<String, List<CheckedReference>> proofs;
    private String demographic, patientKey, lineage, run, key, address, rowId, edgeId;

    @BeforeEach
    void setup() throws Exception {
        fixture = new CdsExportDemographicProjectionEvidenceUnitTest();
        fixture.setup();
        build();
    }

    private void build() throws Exception {
        base = fixture.withSource(Map.of("UniqueVendorIdSequence[1]", "old27"));
        tables = new LinkedHashMap<>();
        base.index().tables().forEach((name, rows) -> tables.put(name, new LinkedHashMap<>(rows)));
        references = new LinkedHashMap<>(base.index().references());
        proofs = new LinkedHashMap<>(base.checked().units());
        var parent =
                tables.get("cds_import_unit").values().stream()
                        .filter(row -> "Demographics".equals(row.fields().get("category")))
                        .findFirst()
                        .orElseThrow();
        run = parent.fields().get("run_id");
        demographic = run + ":" + parent.fields().get("unit_key");
        patientKey = parent.fields().get("patient_key");
        lineage = parent.fields().get("lineage_key");
    }

    private void add(String category, String ordinal) {
        key =
                CdsImportKeys.hash(
                        lineage,
                        "/OmdCds[1]/PatientRecord[1]/" + category + "[" + ordinal + "]",
                        "NATIVE",
                        "0");
        address = run + ":" + key;
        rowId = CdsImportKeys.hash(run, key);
        var values =
                Map.of(
                        "id",
                        rowId,
                        "run_id",
                        run,
                        "unit_key",
                        key,
                        "patient_key",
                        patientKey,
                        "lineage_key",
                        lineage,
                        "category",
                        category,
                        "ordinal",
                        ordinal,
                        "state",
                        "COMMITTED");
        tables.get("cds_import_unit")
                .put(rowId, new CdsExportCapturedTextRowDto("cds_import_unit", rowId, values));
        var ref =
                new CdsExportRetainedIndexDto.Reference(
                        "demographic", "27", base.witness().digest());
        references.put(address, List.of(ref));
        proofs.put(
                address,
                List.of(new CheckedReference(ref, base.witness().digest(), Reason.UNCHANGED_ROW)));
        String parent = demographic.substring(37);
        edgeId = CdsImportKeys.hash(run, key, parent);
        tables.computeIfAbsent("cds_import_dependency", ignored -> new LinkedHashMap<>())
                .put(
                        edgeId,
                        new CdsExportCapturedTextRowDto(
                                "cds_import_dependency",
                                edgeId,
                                Map.of(
                                        "id",
                                        edgeId,
                                        "run_id",
                                        run,
                                        "unit_key",
                                        key,
                                        "depends_on_unit_key",
                                        parent)));
    }

    private void unit(String field, String value) {
        var row = tables.get("cds_import_unit").get(rowId);
        var fields = new HashMap<>(row.fields());
        fields.put(field, value);
        tables.get("cds_import_unit")
                .put(rowId, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields));
    }

    private void edge(String field, String value) {
        var row = tables.get("cds_import_dependency").get(edgeId);
        var fields = new HashMap<>(row.fields());
        fields.put(field, value);
        tables.get("cds_import_dependency")
                .put(edgeId, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), fields));
    }

    private CdsExportRetainedIndexDto index() {
        return new CdsExportRetainedIndexDto(
                base.index().sourceManifestSha256(),
                20,
                1L << 20,
                tables,
                references.keySet(),
                references,
                base.index().nativeParents());
    }

    private CdsExportRetainedReceiptVerificationDto checked() {
        var unchanged = new HashSet<String>();
        var review = new HashSet<String>();
        proofs.forEach(
                (a, p) ->
                        (p.stream().allMatch(ref -> ref.reason() == Reason.UNCHANGED_ROW)
                                        ? unchanged
                                        : review)
                                .add(a));
        return new CdsExportRetainedReceiptVerificationDto(
                base.index().sourceManifestSha256(), proofs, unchanged, review);
    }

    private CdsExportDemographicProjectionEvidence.Result verify() throws Exception {
        return new CdsExportDemographicProjectionEvidence()
                .verify(
                        27,
                        demographic,
                        index(),
                        checked(),
                        base.current(),
                        base.witness(),
                        () -> {});
    }

    private void refused() {
        assertThatThrownBy(this::verify).hasMessage("CDS_EXPORT_DEMOGRAPHIC_ALIAS_REQUIRES_REVIEW");
    }

    @Test
    void shouldAllowTheExactNewCategoryAssociation_withoutChoosingAnyDemographicValuesFromIt() {
        add("NewCategory", "1");
        assertThatCode(this::verify).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowTheExactCareElementsAssociation_withoutASecondDemographicProjection() {
        add("CareElements", "1");
        assertThatCode(this::verify).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepSeveralIndependentSupplementalOccurrences_associatedWithOnePatient() {
        add("NewCategory", "1");
        add("NewCategory", "2");
        add("CareElements", "1");
        assertThatCode(this::verify).doesNotThrowAnyException();
    }

    @Test
    void shouldReconcileCurrentDemographicEdits_independentlyOfTheSourceOnlyAssociation()
            throws Exception {
        fixture.currentField("first_name", "Current edited given");
        build();
        add("NewCategory", "1");
        var result = verify();
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.GIVEN)
                                .preparedProjectionProven())
                .isTrue();
        assertThat(
                        result.groups()
                                .get(CdsExportDemographicProjectionEvidence.Group.GIVEN)
                                .changedColumns())
                .containsExactly("first_name");
    }

    @Test
    void shouldAcceptAnOriginalAssociationReceiptChangedByAnUnrelatedDemographicEdit() {
        add("NewCategory", "1");
        var prior = new CdsExportRetainedIndexDto.Reference("demographic", "27", "e".repeat(64));
        references.put(address, List.of(prior));
        proofs.put(
                address,
                List.of(new CheckedReference(prior, base.witness().digest(), Reason.CHANGED_ROW)));
        assertThatCode(this::verify).doesNotThrowAnyException();
    }

    @Test
    void shouldKeepASecondActualDemographicProjection_blockedAsAnAlias() {
        add("Demographics", "2");
        refused();
    }

    @Test
    void shouldKeepAnUnknownCategoryWithADemographicReference_blockedAsAnAlias() {
        add("ClinicalNotes", "1");
        refused();
    }

    @Test
    void shouldRefuseAnAssociationForAnotherPatientKey() {
        add("NewCategory", "1");
        unit("patient_key", "d".repeat(64));
        refused();
    }

    @Test
    void shouldRefuseAnAssociationForAnotherSourceLineage() {
        add("NewCategory", "1");
        unit("lineage_key", "d".repeat(64));
        refused();
    }

    @Test
    void shouldRefuseAnotherImportRun_evenIfItsTargetPatientNumberMatches() {
        add("NewCategory", "1");
        unit("run_id", "22222222-2222-4222-8222-222222222222");
        refused();
    }

    @Test
    void shouldRefuseAChangedUnitKey_withoutReinterpretingItsRecordedRole() {
        add("NewCategory", "1");
        unit("unit_key", "d".repeat(64));
        refused();
    }

    @Test
    void shouldRefuseAUnitKeyForAnotherOccurrence() {
        add("NewCategory", "1");
        unit("ordinal", "2");
        refused();
    }

    @Test
    void shouldRefuseANoncanonicalOrMissingOrdinal() {
        for (String value : Arrays.asList("01", "0", "-1", null)) {
            add("NewCategory", "1");
            unit("ordinal", value);
            refused();
        }
    }

    @Test
    void shouldRefuseAnUncommittedOrUnknownNativeUnitState() {
        for (String value : Arrays.asList("EXPECTED", "FAILED", null)) {
            add("NewCategory", "1");
            unit("state", value);
            refused();
        }
    }

    @Test
    void shouldRefuseTheAssociationWithoutItsOriginalDemographicDependency() {
        add("NewCategory", "1");
        tables.get("cds_import_dependency").remove(edgeId);
        refused();
    }

    @Test
    void shouldRefuseASubstitutedParentDependency() {
        add("NewCategory", "1");
        edge("depends_on_unit_key", "d".repeat(64));
        refused();
    }

    @Test
    void shouldRefuseAnExtraNativeReference_insteadOfTreatingTheUnitAsSourceOnly() {
        add("NewCategory", "1");
        var extra = new CdsExportRetainedIndexDto.Reference("Contact", "99", "d".repeat(64));
        var refs = new ArrayList<>(references.get(address));
        refs.add(extra);
        references.put(address, refs);
        var compared = new ArrayList<>(proofs.get(address));
        compared.add(new CheckedReference(extra, extra.digest(), Reason.UNCHANGED_ROW));
        proofs.put(address, compared);
        refused();
    }

    @Test
    void shouldRefuseUnavailableCurrentEvidence_forAnOtherwiseCorrectAssociation() {
        add("NewCategory", "1");
        var ref = references.get(address).getFirst();
        proofs.put(
                address,
                List.of(new CheckedReference(ref, null, Reason.RECEIPT_METADATA_UNAVAILABLE)));
        refused();
    }

    @Test
    void shouldRefuseAReceiptForAnotherCurrentWitness() {
        add("NewCategory", "1");
        var ref = references.get(address).getFirst();
        proofs.put(address, List.of(new CheckedReference(ref, "d".repeat(64), Reason.CHANGED_ROW)));
        refused();
    }

    @Test
    void shouldKeepAnAppliedReplayAssociation_explicitAndBoundToItsOriginalDependency() {
        add("NewCategory", "1");
        unit("state", "ALREADY_COMPLETE");
        assertThatCode(this::verify).doesNotThrowAnyException();
    }
}
