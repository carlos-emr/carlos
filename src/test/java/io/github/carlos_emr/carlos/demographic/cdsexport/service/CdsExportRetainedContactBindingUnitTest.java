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
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.util.*;
import org.junit.jupiter.api.*;

/** Source binding only; historical metadata here is a unit fixture, not an installed import/reconciliation claim. */
@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedContactBindingUnitTest {
    private static final String RUN = "22222222-2222-4222-8222-222222222222",
            HASH = "a".repeat(64),
            ROOT = "/OmdCds[1]/PatientRecord[1]/Demographics[1]";
    private static final String PATIENT = CdsImportKeys.hash("fixture-source", "old27", "default"),
            PARENT = CdsImportKeys.hash(PATIENT, ROOT, "NATIVE", "0"),
            PAGE = CdsImportKeys.hash(PATIENT, ROOT, "FIELDS", "1");
    private final CdsExportRetainedContactBinding binder = new CdsExportRetainedContactBinding();
    private final Map<String, Map<String, CdsExportCapturedTextRowDto>> tables =
            new LinkedHashMap<>();
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private CdsExportRetainedIndexDto index;
    private CdsExportRetainedReceiptVerificationDto checked;
    private String child;

    @BeforeEach
    void setup() {
        tables.clear();
        fields.clear();
        child = CdsImportKeys.hash(PATIENT, ROOT + "/Contact[1]", "_CONTACT", "1");
        put(Plan.IMPORT_RUNS, RUN, Map.of("id", RUN, "source_namespace", "fixture-source"));
        put(
                Plan.IMPORT_PATIENTS,
                CdsImportKeys.hash(RUN, PATIENT),
                Map.of(
                        "id",
                        CdsImportKeys.hash(RUN, PATIENT),
                        "run_id",
                        RUN,
                        "patient_key",
                        PATIENT,
                        "lineage_key",
                        PATIENT,
                        "target_demographic_no",
                        "27",
                        "source_patient_id",
                        "old27",
                        "copy_key",
                        "default",
                        "held",
                        "0"));
        unit(PARENT, "Demographics", "1");
        unit(child, "Demographics_CONTACT", "1");
        unit(PAGE, "Demographics_FIELDS", "1");
        put(
                Plan.IMPORT_DEPENDENCIES,
                "b".repeat(64),
                Map.of("run_id", RUN, "unit_key", child, "depends_on_unit_key", PARENT));
        field("Names[1]/LegalName[1]/FirstName[1]/Part[1]", "Patient");
        field("Contact[1]/Name[1]/FirstName[1]", "Private first 😀");
        field("Contact[1]/Name[1]/MiddleName[1]", "Source middle");
        field("Contact[10]/Name[1]/FirstName[1]", "Other contact");
        build(false);
    }

    @Test
    void shouldVerifyNativeChildResolves_toTheParentsSeparatePreservationPagesAndOnlyItsOwnSubtree()
            throws Exception {
        var result = invoke();
        assertThat(result.parentUnitAddress()).isEqualTo(RUN + ":" + PARENT);
        assertThat(result.childUnitAddress()).isEqualTo(RUN + ":" + child);
        assertThat(result.ordinal()).isEqualTo(1);
        assertThat(result.sourceRoot()).isEqualTo(ROOT + "/Contact[1]");
        assertThat(result.fields())
                .hasSize(2)
                .allSatisfy(
                        field ->
                                assertThat(index.nativeParents().get(field.id()))
                                        .isEqualTo(RUN + ":" + PARENT));
        assertThat(result.fields()).noneMatch(field -> field.sourcePath().contains("Contact[10]"));
        assertThatThrownBy(() -> result.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.toString()).doesNotContain("Private", "Source middle", RUN);
    }

    @Test
    void
            shouldVerifyParentFieldReceiptsCanBeVerifiedWhileNativeContactEditsStillRequireSeparateClinicalReconciliation_forRetainedContactBindingContract()
                    throws Exception {
        var slots = new LinkedHashMap<>(checked.units());
        slots.put(
                RUN + ":" + child,
                index.references().get(RUN + ":" + child).stream()
                        .map(ref -> new CheckedReference(ref, "e".repeat(64), Reason.CHANGED_ROW))
                        .toList());
        var unchanged = new HashSet<>(checked.unchangedUnits());
        unchanged.remove(RUN + ":" + child);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, slots, unchanged, Set.of(RUN + ":" + child));
        assertThat(invoke().fields()).hasSize(2);
    }

    @Test
    void shouldVerifyAChangedParentPreservationPageIsNeverTreatedAsOriginalSourceEvidence_forRetainedContactBindingContract() {
        build(true);
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_SOURCE_FIELD_EVIDENCE_CHANGED");
    }

    @Test
    void shouldVerifyMissingAndDuplicateSuppliedFieldsCannotHideBehindMatchingCounts_forRetainedContactBindingContract() {
        fields.remove(1);
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_ASSEMBLY_SCOPE_INVALID");
        setup();
        fields.add(fields.get(1));
        assertThatThrownBy(this::invoke)
                .hasMessage("CDS_EXPORT_DEMOGRAPHIC_RETAINED_ASSEMBLY_SCOPE_INVALID");
    }

    @Test
    void shouldVerifyForeignPatientLineageAndRunNamespacesCannotSubstitute_forTheOriginalImportCopy() {
        for (var edit :
                List.of(
                        Map.entry("target_demographic_no", "28"),
                        Map.entry("lineage_key", "f".repeat(64)),
                        Map.entry("copy_key", "another-copy"),
                        Map.entry("held", "1"))) {
            setup();
            change(
                    "cds_import_patient",
                    CdsImportKeys.hash(RUN, PATIENT),
                    edit.getKey(),
                    edit.getValue());
            build(false);
            assertThatThrownBy(this::invoke)
                    .hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
        }
        setup();
        change("cds_import_run", RUN, "source_namespace", "another-source");
        build(false);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
    }

    @Test
    void shouldVerifyAnUnknownOrMissingParentDependencyCannotBeGuessed_fromTheSamePatient() {
        tables.get("cds_import_dependency").clear();
        build(false);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
        setup();
        put(
                Plan.IMPORT_DEPENDENCIES,
                "c".repeat(64),
                Map.of("run_id", RUN, "unit_key", child, "depends_on_unit_key", PAGE));
        build(false);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
    }

    @Test
    void shouldVerifyNativeCategoryStateAndCanonicalUnitKeysAreRequired_forRetainedContactBindingContract() {
        for (var edit :
                List.of(
                        Map.entry("category", "Demographics"),
                        Map.entry("state", "REGISTERED"),
                        Map.entry("ordinal", "01"),
                        Map.entry("lineage_key", "e".repeat(64)))) {
            setup();
            change(
                    "cds_import_unit",
                    CdsImportKeys.hash(RUN, child),
                    edit.getKey(),
                    edit.getValue());
            build(false);
            assertThatThrownBy(this::invoke)
                    .hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
        }
        setup();
        change("cds_import_unit", CdsImportKeys.hash(RUN, child), "state", null);
        build(false);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
    }

    @Test
    void shouldVerifyAnotherSourceGenerationOrUnknownUnitAddressCannotBind_forRetainedContactBindingContract() {
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        "e".repeat(64),
                        checked.units(),
                        checked.unchangedUnits(),
                        checked.reviewUnits());
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
        setup();
        assertThatThrownBy(
                        () ->
                                binder.bind(
                                        27,
                                        RUN + ":" + "f".repeat(64),
                                        index,
                                        checked,
                                        fields,
                                        () -> {}))
                .hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
    }

    @Test
    void shouldVerifyANativeChildOrdinal_withoutTheMatchingSourceSubtreeCannotBeInvented() {
        change("cds_import_unit", CdsImportKeys.hash(RUN, child), "ordinal", "2");
        build(false);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_FIELDS_REQUIRED");
    }

    @Test
    void shouldVerifyParentNativeIdentityCannotBeReplaced_byThePreservationUnit() {
        change(
                "cds_import_unit",
                CdsImportKeys.hash(RUN, PARENT),
                "category",
                "Demographics_FIELDS");
        build(false);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_BINDING_INVALID");
    }

    @Test
    void shouldVerifySourceFieldsKeepTheirParentLinks_insteadOfBeingReassignedToTheNativeChild() {
        var parents = new HashMap<>(index.nativeParents());
        for (var field : fields)
            if (field.sourcePath().contains("/Contact[1]/"))
                parents.put(field.id(), RUN + ":" + child);
        index =
                new CdsExportRetainedIndexDto(
                        HASH,
                        index.metadataRows(),
                        index.metadataBytes(),
                        tables,
                        index.appliedUnits(),
                        index.references(),
                        parents);
        assertThatThrownBy(this::invoke).hasMessage("CDS_EXPORT_CONTACT_SOURCE_FIELDS_REQUIRED");
    }

    @Test
    void shouldVerifyCancellation_duringSourceBindingPropagates() {
        assertThatThrownBy(
                        () ->
                                binder.bind(
                                        27,
                                        RUN + ":" + child,
                                        index,
                                        checked,
                                        fields,
                                        () -> {
                                            throw new IllegalStateException("CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
    }

    private CdsExportRetainedContactBinding.Result invoke() throws Exception {
        return binder.bind(27, RUN + ":" + child, index, checked, fields, () -> {});
    }

    private void unit(String key, String category, String ordinal) {
        put(
                Plan.IMPORT_UNITS,
                CdsImportKeys.hash(RUN, key),
                Map.of(
                        "id",
                        CdsImportKeys.hash(RUN, key),
                        "run_id",
                        RUN,
                        "unit_key",
                        key,
                        "patient_key",
                        PATIENT,
                        "lineage_key",
                        PATIENT,
                        "category",
                        category,
                        "ordinal",
                        ordinal,
                        "state",
                        "COMMITTED"));
    }

    private void field(String relative, String value) {
        String path = ROOT + "/" + relative, id = CdsImportKeys.hash(PAGE, "cds_dt", path, "1");
        var values = new HashMap<String, String>();
        values.putAll(
                Map.of(
                        "id",
                        id,
                        "run_id",
                        RUN,
                        "patient_key",
                        PATIENT,
                        "unit_key",
                        PAGE,
                        "demographic_no",
                        "27",
                        "category",
                        "Demographics",
                        "source_path",
                        path,
                        "occurrence",
                        "1",
                        "source_namespace",
                        "cds_dt",
                        "value_type",
                        "{cds_dt}personNameField"));
        values.put("original_value", value);
        values.put("normalized_value", value);
        values.put("payload_offset", "0");
        values.put("payload_length", "0");
        put(Plan.IMPORT_FIELDS, id, values);
        try {
            fields.add(
                    new CdsExportRetainedFieldMapper()
                            .map(
                                    27,
                                    Set.of(CdsCategory.DEMOGRAPHICS),
                                    tables.get("cds_import_field").get(id)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void put(Plan plan, String key, Map<String, String> values) {
        var row = new LinkedHashMap<String, String>();
        plan.columns().forEach(column -> row.put(column, null));
        row.putAll(values);
        tables.computeIfAbsent(plan.table(), ignored -> new LinkedHashMap<>())
                .put(key, new CdsExportCapturedTextRowDto(plan.table(), key, row));
    }

    private void change(String table, String key, String column, String value) {
        var old = tables.get(table).get(key);
        var row = new LinkedHashMap<>(old.fields());
        row.put(column, value);
        tables.get(table).put(key, new CdsExportCapturedTextRowDto(table, key, row));
    }

    private void build(boolean changedPage) {
        var refs = new LinkedHashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        refs.put(
                RUN + ":" + PARENT,
                List.of(new CdsExportRetainedIndexDto.Reference("demographic", "27", HASH)));
        refs.put(
                RUN + ":" + child,
                List.of(
                        new CdsExportRetainedIndexDto.Reference("Contact", "9", HASH),
                        new CdsExportRetainedIndexDto.Reference("DemographicContact", "-1", HASH)));
        refs.put(
                RUN + ":" + PAGE,
                tables.get("cds_import_field").keySet().stream()
                        .map(
                                id ->
                                        new CdsExportRetainedIndexDto.Reference(
                                                "cds_import_field", id, HASH))
                        .toList());
        var parents = new HashMap<String, String>();
        for (String id : tables.get("cds_import_field").keySet())
            parents.put(id, RUN + ":" + PARENT);
        index =
                new CdsExportRetainedIndexDto(
                        HASH, 20, 16384, tables, refs.keySet(), refs, parents);
        var slots = new LinkedHashMap<String, List<CheckedReference>>();
        refs.forEach(
                (address, items) ->
                        slots.put(
                                address,
                                items.stream()
                                        .map(
                                                ref ->
                                                        new CheckedReference(
                                                                ref,
                                                                changedPage
                                                                                && address.equals(
                                                                                        RUN + ":"
                                                                                                + PAGE)
                                                                        ? "e".repeat(64)
                                                                        : ref.digest(),
                                                                changedPage
                                                                                && address.equals(
                                                                                        RUN + ":"
                                                                                                + PAGE)
                                                                        ? Reason.CHANGED_ROW
                                                                        : Reason.UNCHANGED_ROW))
                                        .toList()));
        var unchanged = new HashSet<>(refs.keySet());
        if (changedPage) unchanged.remove(RUN + ":" + PAGE);
        checked =
                new CdsExportRetainedReceiptVerificationDto(
                        HASH, slots, unchanged, changedPage ? Set.of(RUN + ":" + PAGE) : Set.of());
    }
}
