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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.CheckedReference;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedReceiptVerificationDto.Reason;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Import, delete and export of CDS-imported care values. The import has the rows {@code
 * CdsCareImportService} stores for each CareElements child (the measurement and its
 * cdsCareElement, date_precision, cdsSourceDate, cdsSourcePath and unit extensions) beside the
 * parent's retained source fields. A clinician corrects an imported value as {@code
 * EctDeleteData2Action} does: the measurement row moves to {@code measurementsDeleted} with its
 * {@code originalId} and its extensions stay behind; the correction is a new native measurement.
 * The export runs the imported-parent assembly of {@link CdsExportCareOccurrenceService} and
 * renders the result through the receiving schema.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportCareImportedDeletionUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            HASH = "a".repeat(64),
            OPTIONS = "b".repeat(64),
            RULES = "c".repeat(64),
            SOURCE = "d".repeat(64),
            ROOT = "/OmdCds[1]/PatientRecord[1]/CareElements[1]";
    private static final String PATIENT = CdsImportKeys.hash("fixture-source", "27", "default"),
            PARENT = CdsImportKeys.hash(PATIENT, ROOT, "NATIVE", "0"),
            PAGE = CdsImportKeys.hash(PATIENT, ROOT, "FIELDS", "1"),
            WEIGHT = CdsImportKeys.hash(PATIENT, ROOT + "/Weight[1]", "_CARE", "1"),
            HEIGHT = CdsImportKeys.hash(PATIENT, ROOT + "/Height[1]", "_CARE", "2");
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @TempDir Path directory;
    private final Map<String, Map<String, CdsExportCapturedTextRowDto>> tables =
            new LinkedHashMap<>();
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private final Map<String, CdsExportCapturedTextRowDto> current = new TreeMap<>();
    private final Map<String, CdsExportNativeRowReceiptDto> exact = new HashMap<>();
    /** The rows the import wrote, as the import receipt froze them. */
    private final Map<String, List<CdsExportCapturedTextRowDto>> imported =
            new LinkedHashMap<>();
    private final Set<String> deleted = new HashSet<>();
    private CdsExportRetainedIndexDto index;
    private CdsExportRetainedReceiptVerificationDto checked;
    private CdsExportCareOccurrenceService.Dependencies dependencies;

    @BeforeEach
    void setup() throws Exception {
        parse(
                "<CareElements><Weight><dt:Weight>75</dt:Weight><dt:WeightUnit>kg</dt:WeightUnit>"
                        + "<dt:Date>2020-02-28</dt:Date></Weight><Height><dt:Height>170</dt:Height>"
                        + "<dt:HeightUnit>cm</dt:HeightUnit><dt:Date>2020-02-28</dt:Date></Height>"
                        + "</CareElements>");
        imported.put(WEIGHT, observation(7, 20, "Weight", "WT", "75", "kg"));
        imported.put(HEIGHT, observation(8, 30, "Height", "HT", "170", "cm"));
    }

    @Test
    void shouldExportTheUnchangedImportedCareElementsExactlyAsBefore_whenNothingWasDeleted()
            throws Exception {
        var result = assemble();
        assertThat(result.content()).isNotNull();
        assertThat(result.content().replacedFields()).isEmpty();
        assertThat(result.content().retainedFields()).isEqualTo(ids(""));
        String xml = render(result);
        assertThat(xml).contains("<cdsd:Weight>75</cdsd:Weight>", "<cdsd:Height>170</cdsd:Height>");
        assertThat(CdsExportCareOccurrenceService.removedImportedParents(dependencies, index, current))
                .isEmpty();
    }

    @Test
    void shouldLeaveADeletedImportedValueOut_andExportTheCorrectionAsANativeValue()
            throws Exception {
        // EctDeleteData2Action on the imported weight, then a corrected weight saved natively.
        delete(WEIGHT, 7, 40);
        var correction = row(Plan.MEASUREMENTS_CURRENT, 9, measurement(9, "WT", "72", "in kg"));
        current.put("measurements:9", correction);
        var result = assemble();
        assertThat(result.content()).isNotNull();
        assertThat(result.content().replacedFields()).isEqualTo(ids("Weight[1]"));
        assertThat(result.content().retainedFields()).isEqualTo(ids("Height[1]"));
        String xml = render(result);
        assertThat(xml).contains("<cdsd:Height>170</cdsd:Height>").doesNotContain("Weight");
        // The deleted version and the extensions it left behind are the deleted-version pass's;
        // the correction is an ordinary native observation.
        var planned = new CdsExportNativeCarePlanner().plan(index, current, () -> {});
        assertThat(planned)
                .extracting(CdsExportNativeCarePlanner.Occurrence::table, o -> o.id())
                .containsExactlyInAnyOrder(
                        tuple("measurements", "9"), tuple("measurementsDeleted", "40"));
        var retired =
                planned.stream()
                        .filter(CdsExportNativeCarePlanner.Occurrence::deleted)
                        .findFirst()
                        .orElseThrow();
        assertThat(retired.extensions()).hasSize(5);
        var claims = claims();
        assemble(claims);
        var owned = new HashSet<>(claims.owned());
        for (var extension : retired.extensions()) owned.add("measurementsExt:" + extension.fields().get("id"));
        owned.add("measurements:9");
        var expected = new HashSet<String>();
        current.forEach(
                (key, row) -> {
                    if (Set.of("measurements", "measurementsExt").contains(row.table()))
                        expected.add(key);
                });
        assertThat(owned).isEqualTo(expected);
        var mapped =
                new CdsExportNativeCareMapper()
                        .map(27, correction, List.of(), List.of());
        assertThat(mapped.element()).isEqualTo("Weight");
        assertThat(CdsExportCareOccurrenceService.removedImportedParents(dependencies, index, current))
                .isEmpty();
    }

    @Test
    void shouldExportNoCareElements_whenEveryImportedValueWasDeleted() throws Exception {
        delete(WEIGHT, 7, 40);
        delete(HEIGHT, 8, 41);
        var claims = claims();
        var result = assemble(claims);
        assertThat(result.content()).isNull();
        // Every retained source field is claimed as removed; the inventory expects neither the
        // occurrence nor the fields.
        assertThat(claims.consumed()).isEqualTo(ids(""));
        assertThat(CdsExportCareOccurrenceService.removedImportedParents(dependencies, index, current))
                .containsExactly(RUN + ":" + PARENT);
    }

    @Test
    void shouldStillHoldAnImportedValueChangedOtherwise_forAnUnexplainedChange() throws Exception {
        // The receipt no longer proves the weight, but no measurementsDeleted row explains it.
        var weight = new HashMap<>(current.get("measurements:7").fields());
        weight.put("comments", "changed outside any screen");
        var changed = row(Plan.MEASUREMENTS_CURRENT, 7, weight);
        current.put("measurements:7", changed);
        exact.put("measurements:7", witness(changed));
        build();
        assertThatThrownBy(() -> assemble(claims()))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportReviewException.class);
    }

    private CdsExportCareOccurrenceService.ImportedParent assemble() throws Exception {
        return assemble(claims());
    }

    private CdsExportCareOccurrenceService.ImportedParent assemble(
            CdsExportCareOccurrenceService.Claims claims) throws Exception {
        if (index == null) build();
        CdsExportCareOccurrenceService.requireParentEvidence(
                27, RUN + ":" + PARENT, index, checked, exact);
        return CdsExportCareOccurrenceService.importedParent(
                27,
                RUN + ":" + PARENT,
                tables.get("cds_import_unit").get(CdsImportKeys.hash(RUN, PARENT)),
                dependencies,
                index,
                checked,
                fields,
                current,
                exact,
                claims,
                values(),
                () -> {});
    }

    private static CdsExportCareOccurrenceService.Claims claims() {
        return new CdsExportCareOccurrenceService.Claims(
                new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
    }

    /** EctDeleteData2Action: the row moves to measurementsDeleted; its extensions stay behind. */
    private void delete(String unit, int id, int deletedId) {
        var original = current.remove("measurements:" + id);
        exact.remove("measurements:" + id);
        var moved = new HashMap<String, String>();
        Plan.MEASUREMENTS_DELETED.columns().forEach(column -> moved.put(column, null));
        for (String column : Plan.MEASUREMENTS_DELETED.columns())
            if (original.fields().containsKey(column)) moved.put(column, original.fields().get(column));
        moved.put("id", Integer.toString(deletedId));
        moved.put("originalId", Integer.toString(id));
        moved.put("dateDeleted", "2026-11-01 10:00:00.0");
        current.put(
                "measurementsDeleted:" + deletedId,
                row(Plan.MEASUREMENTS_DELETED, deletedId, moved));
        deleted.add(unit);
        index = null;
    }

    private List<CdsExportCapturedTextRowDto> observation(
            int id, int firstExtension, String element, String type, String value, String unit) {
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        var observation = row(Plan.MEASUREMENTS_CURRENT, id, measurement(id, type, value, "in " + unit));
        rows.add(observation);
        int next = firstExtension;
        for (var entry :
                List.of(
                        Map.entry("cdsCareElement", element),
                        Map.entry("date_precision", "FULL_DATE"),
                        Map.entry("cdsSourceDate", "2020-02-28"),
                        Map.entry("cdsSourcePath", ROOT + "/" + element + "[1]"),
                        Map.entry("unit", unit))) {
            var ext = new HashMap<String, String>();
            ext.put("id", Integer.toString(next));
            ext.put("measurement_id", Integer.toString(id));
            ext.put("keyval", entry.getKey());
            ext.put("val", entry.getValue());
            rows.add(row(Plan.MEASUREMENTS_EXT, next++, ext));
        }
        for (var row : rows) {
            current.put(row.table() + ":" + row.fields().get("id"), row);
            exact.put(row.table() + ":" + row.fields().get("id"), witness(row));
        }
        return rows;
    }

    private static Map<String, String> measurement(int id, String type, String value, String instruction) {
        var row = new HashMap<String, String>();
        Plan.MEASUREMENTS_CURRENT.columns().forEach(column -> row.put(column, null));
        row.putAll(
                Map.of(
                        "id", Integer.toString(id),
                        "demographicNo", "27",
                        "type", type,
                        "dataField", value,
                        "measuringInstruction", instruction,
                        "comments", "",
                        "dateObserved", "2020-02-28 05:00:00.0",
                        "dateEntered", "2026-01-01 09:00:00.0",
                        "providerNo", "999998",
                        "appointmentNo", "0"));
        return row;
    }

    private static CdsExportCapturedTextRowDto row(Plan plan, int id, Map<String, String> fields) {
        return new CdsExportCapturedTextRowDto(
                plan.table(), CdsExportPatientTableSourceReader.key(id), new HashMap<>(fields));
    }

    private void parse(String care) throws Exception {
        String xml =
                "<OmdCds xmlns=\"cds\" xmlns:dt=\"cds_dt\"><PatientRecord><Demographics><Names>"
                        + "<dt:LegalName namePurpose=\"L\"><dt:FirstName><dt:Part>Patient</dt:Part>"
                        + "<dt:PartType>GIV</dt:PartType></dt:FirstName><dt:LastName><dt:Part>Fixture"
                        + "</dt:Part><dt:PartType>FAMC</dt:PartType></dt:LastName></dt:LegalName>"
                        + "</Names><DateOfBirth>1980-01-01</DateOfBirth><Gender>U</Gender>"
                        + "<UniqueVendorIdSequence>27</UniqueVendorIdSequence><PersonStatusCode>"
                        + "<PersonStatusAsEnum>A</PersonStatusAsEnum></PersonStatusCode></Demographics>"
                        + care
                        + "</PatientRecord></OmdCds>";
        Path folder = Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
        Path input = Files.writeString(folder.resolve("input.xml"), xml);
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        input,
                        folder.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var parser = new CdsFieldParser();
        var record =
                records.stream()
                        .filter(candidate -> candidate.section().equals("CareElements"))
                        .findFirst()
                        .orElseThrow();
        parser.forEachField(
                record,
                field -> {
                    try {
                        String path = ROOT + "/" + field.path(),
                                id = CdsImportKeys.hash(PAGE, field.namespace(), path, "1");
                        var v = new HashMap<String, String>();
                        v.putAll(
                                Map.of(
                                        "run_id", RUN,
                                        "patient_key", PATIENT,
                                        "unit_key", PAGE,
                                        "demographic_no", "27",
                                        "category", "CareElements",
                                        "source_path", path,
                                        "occurrence", "1",
                                        "source_namespace", field.namespace(),
                                        "value_type", field.schemaType()));
                        v.put("original_value", parser.readText(record, field, 32768));
                        v.put("normalized_value", parser.readLogicalText(record, field, 65536));
                        v.put("payload_offset", "0");
                        v.put("payload_length", "0");
                        put(Plan.IMPORT_FIELDS, id, v);
                        fields.add(
                                new CdsExportRetainedFieldMapper()
                                        .map(
                                                27,
                                                Set.of(CdsCategory.CARE_ELEMENTS),
                                                tables.get("cds_import_field").get(id)));
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
    }

    /** The applied import and the receipt verification of the current rows. */
    private void build() throws Exception {
        put(
                Plan.IMPORT_RUNS,
                RUN,
                Map.of(
                        "source_namespace", "fixture-source",
                        "options_digest", OPTIONS,
                        "ruleset_digest", RULES,
                        "dry_run", "0",
                        "profile", "omd-5.2",
                        "snapshot_location", "/owned/job-" + HASH + ".json"));
        tables.put(
                "retained_import_options",
                Map.of(
                        RUN,
                        new CdsExportCapturedTextRowDto(
                                "retained_import_options",
                                RUN,
                                Map.of(
                                        "options",
                                        CdsExportRetainedImportOptionsReader.encodeCaptured(
                                                new CdsExportRetainedImportOptionsReader.Options(
                                                        RUN, HASH, OPTIONS, RULES, "omd-5.2", false, 0))))));
        unit(PARENT, "CareElements", "1");
        unit(PAGE, "CareElements_FIELDS", "1");
        unit(WEIGHT, "CareElements_CARE", "1");
        unit(HEIGHT, "CareElements_CARE", "2");
        var prepared =
                Map.of(
                        WEIGHT, care("Weight", "WT", "75", "kg"),
                        HEIGHT, care("Height", "HT", "170", "cm"));
        var references = new LinkedHashMap<String, List<CdsExportRetainedIndexDto.Reference>>();
        var slots = new LinkedHashMap<String, List<CheckedReference>>();
        var demographic = demographicWitness();
        exact.put("demographic:27", demographic);
        var parentRef = new CdsExportRetainedIndexDto.Reference("demographic", "27", demographic.digest());
        references.put(RUN + ":" + PARENT, List.of(parentRef));
        slots.put(
                RUN + ":" + PARENT,
                List.of(new CheckedReference(parentRef, demographic.digest(), Reason.UNCHANGED_ROW)));
        var pageRefs =
                fields.stream()
                        .map(f -> new CdsExportRetainedIndexDto.Reference("cds_import_field", f.id(), HASH))
                        .toList();
        references.put(RUN + ":" + PAGE, pageRefs);
        slots.put(
                RUN + ":" + PAGE,
                pageRefs.stream().map(r -> new CheckedReference(r, r.digest(), Reason.UNCHANGED_ROW)).toList());
        var unchanged = new HashSet<>(Set.of(RUN + ":" + PARENT, RUN + ":" + PAGE));
        var review = new HashSet<String>();
        for (var child : imported.entrySet()) {
            String address = RUN + ":" + child.getKey();
            var refs = new ArrayList<CdsExportRetainedIndexDto.Reference>();
            var proof = new ArrayList<CheckedReference>();
            for (var row : child.getValue()) {
                var frozen = witness(row);
                var ref = new CdsExportRetainedIndexDto.Reference(row.table(), row.fields().get("id"), frozen.digest());
                refs.add(ref);
                var now = exact.get(row.table() + ":" + row.fields().get("id"));
                proof.add(
                        now == null
                                ? new CheckedReference(ref, null, Reason.ROW_NOT_IN_CAPTURE)
                                : now.digest().equals(ref.digest())
                                        ? new CheckedReference(ref, now.digest(), Reason.UNCHANGED_ROW)
                                        : new CheckedReference(ref, now.digest(), Reason.CHANGED_ROW));
            }
            references.put(address, refs);
            slots.put(address, proof);
            if (proof.stream().allMatch(ref -> ref.reason() == Reason.UNCHANGED_ROW))
                unchanged.add(address);
            else review.add(address);
            put(
                    Plan.IMPORT_DEPENDENCIES,
                    CdsImportKeys.hash(RUN, child.getKey(), PARENT),
                    Map.of("run_id", RUN, "unit_key", child.getKey(), "depends_on_unit_key", PARENT));
            var payload = JSON.writeValueAsString(prepared.get(child.getKey()));
            String type = CdsCareElementDto.class.getName();
            put(
                    Plan.IMPORT_PREPARATIONS,
                    CdsImportKeys.hash(RUN, child.getKey()),
                    Map.of(
                            "run_id", RUN,
                            "unit_key", child.getKey(),
                            "source_digest", SOURCE,
                            "options_digest", OPTIONS,
                            "ruleset_digest", RULES,
                            "payload_type", type,
                            "payload", payload,
                            "digest", CdsPreparationSeal.digest(type, SOURCE, OPTIONS, RULES, payload)));
        }
        var parents = new HashMap<String, String>();
        fields.forEach(f -> parents.put(f.id(), RUN + ":" + PARENT));
        index = new CdsExportRetainedIndexDto(HASH, 64, 1 << 20, tables, references.keySet(), references, parents);
        checked = new CdsExportRetainedReceiptVerificationDto(HASH, slots, unchanged, review);
        var units = new LinkedHashMap<String, CdsExportCapturedTextRowDto>();
        for (String key : List.of(PARENT, PAGE, WEIGHT, HEIGHT))
            units.put(RUN + ":" + key, tables.get("cds_import_unit").get(CdsImportKeys.hash(RUN, key)));
        var children = new HashMap<String, List<String>>();
        for (String key : List.of(WEIGHT, HEIGHT)) children.put(RUN + ":" + key, List.of(RUN + ":" + PARENT));
        dependencies =
                new CdsExportCareOccurrenceService.Dependencies(
                        units,
                        new TreeMap<>(Map.of(RUN + ":" + PARENT, units.get(RUN + ":" + PARENT))),
                        children,
                        Set.copyOf(children.keySet()));
    }

    private void unit(String key, String category, String ordinal) {
        put(
                Plan.IMPORT_UNITS,
                CdsImportKeys.hash(RUN, key),
                Map.of(
                        "run_id", RUN,
                        "unit_key", key,
                        "patient_key", PATIENT,
                        "lineage_key", PATIENT,
                        "ordinal", ordinal,
                        "state", "COMMITTED",
                        "category", category,
                        "source_digest", SOURCE));
    }

    private static CdsCareElementDto care(String element, String type, String value, String unit) {
        return new CdsCareElementDto(
                element,
                type,
                value,
                unit,
                null,
                CdsTemporal.parse(null, null, "2020-02-28", null, "Date"),
                false,
                false,
                null,
                List.of());
    }

    private void put(Plan plan, String key, Map<String, String> source) {
        var row = new HashMap<String, String>();
        plan.columns().forEach(c -> row.put(c, null));
        row.putAll(source);
        row.put("id", key);
        tables.computeIfAbsent(plan.table(), ignored -> new LinkedHashMap<>())
                .put(key, new CdsExportCapturedTextRowDto(plan.table(), key, row));
    }

    private static CdsExportNativeRowReceiptDto demographicWitness() {
        var columns = List.of(new CdsNativeRowDigest.Column("demographic_no", "INT", 11, 0));
        var cells = List.of(new CdsNativeRowState.Cell(false, 2, sha("27")));
        var state = new CdsNativeRowState("demographic", "27", columns, cells);
        return new CdsExportNativeRowReceiptDto(
                "demographic", "27", CdsExportPatientTableSourceReader.key(27), state.digest(), state);
    }

    private static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var column : new TreeSet<>(row.fields().keySet())) {
            columns.add(new CdsNativeRowDigest.Column(column, "VARCHAR", 255, 0));
            String value = row.fields().get(column);
            cells.add(
                    value == null
                            ? new CdsNativeRowState.Cell(true, 0, null)
                            : new CdsNativeRowState.Cell(
                                    false, value.getBytes(StandardCharsets.UTF_8).length, sha(value)));
        }
        var state = new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), row.fields().get("id"), row.recordKey(), state.digest(), state);
    }

    private static String sha(String value) {
        return HexFormat.of()
                .formatHex(CdsImportKeys.digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    /** Retained field ids under one child root ("" for all of them). */
    private Set<String> ids(String child) {
        var ids = new HashSet<String>();
        for (var field : fields)
            if (child.isEmpty() || field.sourcePath().startsWith(ROOT + "/" + child + "/"))
                ids.add(field.id());
        assertThat(ids).isNotEmpty();
        return ids;
    }

    private static CdsExportRetainedSectionWriter.Values values() {
        return new CdsExportRetainedSectionWriter.Values() {
            public Reader text(CdsExportRetainedFieldDto field) {
                return new StringReader(
                        field.logicalInline() == null
                                ? field.originalInline()
                                : field.logicalInline());
            }

            public InputStream binary(CdsExportRetainedFieldDto field) {
                throw new AssertionError("No binary value");
            }
        };
    }

    private static String render(CdsExportCareOccurrenceService.ImportedParent result)
            throws Exception {
        return new String(
                CdsExportNativeHistoryMapperUnitTest.render(
                        CdsCategory.CARE_ELEMENTS, result.content().content()),
                StandardCharsets.UTF_8);
    }
}
