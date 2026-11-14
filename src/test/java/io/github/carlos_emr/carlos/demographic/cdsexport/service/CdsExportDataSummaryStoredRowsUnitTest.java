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

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.CdsExportDataSummaryReadDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Division;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Storage;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Weight;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A chart's data-summary rows are kept in protected storage and indexed, not held as objects: the
 * plan, the records and what is written of them are exactly those of the rows held in memory; no
 * count or size of rows is refused; and a record too large for one document is written as parts
 * that list every row once, in order. Synthetic rows only.
 */
@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportDataSummaryStoredRowsUnitTest {
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final String KEY = "a".repeat(64);
    private static final byte[] PNG =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    private static final CdsExportDataSummaryHtmlComposer.Patient PATIENT =
            CdsExportDataSummaryHtmlComposer.Patient.of(
                    Map.of(
                            "demographic_no", "27",
                            "last_name", "Fixture",
                            "first_name", "Synthetic"));

    @TempDir Path root;
    private CdsExportStagingService store;

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        store =
                new CdsExportStagingService(
                        root,
                        UUID.randomUUID().toString(),
                        1,
                        new SecretKeySpec(new byte[32], "AES"),
                        0,
                        () -> {});
    }

    private static String key(long id) {
        return CdsExportPatientTableSourceReader.key(id);
    }

    private static Row row(String table, String key, Object... columns) {
        var values = new LinkedHashMap<String, Value>();
        for (int i = 0; i < columns.length; i += 2) {
            Object value = columns[i + 1];
            values.put(
                    (String) columns[i],
                    value == null
                            ? Value.sqlNull()
                            : value instanceof Value given ? given : Value.text((String) value));
        }
        return new Row(table, key, values);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Value binary(byte[] content, boolean kept) throws Exception {
        return new Value(Storage.BINARY, null, content.length, sha256(content), kept ? content : null);
    }

    /** Delivers one whole row to the collector as the capture reader does, a field at a time. */
    private static void feed(CdsExportDataSummaryRowsReader.Collector collector, Row row)
            throws Exception {
        for (var entry : row.values().entrySet()) {
            var value = entry.getValue();
            byte[] data =
                    switch (value.storage()) {
                        case SQL_NULL -> null;
                        case TEXT -> value.text().getBytes(StandardCharsets.UTF_8);
                        case BINARY -> value.content();
                    };
            var field =
                    new CdsExportCapturedFieldDto(
                            row.table(),
                            row.recordKey(),
                            entry.getKey(),
                            switch (value.storage()) {
                                case SQL_NULL -> CdsExportCapturedFieldDto.Kind.SQL_NULL;
                                case TEXT -> CdsExportCapturedFieldDto.Kind.UTF8;
                                case BINARY -> CdsExportCapturedFieldDto.Kind.BINARY;
                            },
                            0,
                            value.storage() == Storage.BINARY
                                    ? value.bytes()
                                    : data == null ? 0 : data.length,
                            value.storage() == Storage.BINARY ? value.sha256() : sha256(new byte[0]));
            try (var output = collector.output(field)) {
                if (data != null) output.write(data);
            }
            collector.verified(field);
        }
    }

    /** Stores the rows as a capture delivers them (a table at a time) and returns their index. */
    private CdsExportDataSummaryRowsReader.Result stored(Map<String, List<Row>> rows) throws Exception {
        var collector = new CdsExportDataSummaryRowsReader.Collector(rows.keySet());
        CdsExportStagedFileDto member =
                store.stage(
                        CdsExportDataSummaryRowsReader.ROWS_PURPOSE,
                        output -> {
                            collector.store(output);
                            try {
                                for (var table : rows.values())
                                    for (var row : table) feed(collector, row);
                            } catch (IOException | RuntimeException failure) {
                                throw failure;
                            } catch (Exception failure) {
                                throw new IOException(failure);
                            }
                            collector.finish();
                            collector.stored();
                        });
        return collector.result(store, member);
    }

    private static CdsExportDataSummaryOccurrenceService.Plan plan(
            List<Group> groups, CdsExportDataSummaryRowsReader.Result stored) {
        return new CdsExportDataSummaryOccurrenceService.Plan(
                groups, PATIENT, List.of(), Map.of(), Map.of(), stored);
    }

    private long members() throws IOException {
        try (var listed = Files.list(root)) {
            return listed.count();
        }
    }

    private static Map<String, List<Row>> chart() throws Exception {
        var rows = new TreeMap<String, List<Row>>();
        rows.put(
                "tickler",
                List.of(
                        row("tickler", key(7), "tickler_no", "7", "service_date", "2024-03-10 09:30:00",
                                "message", "Call about results <b>unusual</b>\r\nsecond line 😀"),
                        row("tickler", key(8), "tickler_no", "8", "service_date", null, "message", "")));
        rows.put(
                "tickler_comments",
                List.of(
                        row("tickler_comments", key(1), "id", "1", "tickler_no", "7", "message", "first"),
                        row("tickler_comments", key(2), "id", "2", "tickler_no", "9", "message", "orphan")));
        rows.put(
                "formRourke2020",
                List.of(
                        row("formRourke2020", key(31), "ID", "31", "formCreated", "2024-02-02",
                                "formEdited", "2024-02-03 10:00:00", "c_lastVisited", "x".repeat(5000)),
                        row("formRourke2020", key(12), "ID", "12", "formCreated", "2024-01-01",
                                "formEdited", null, "c_lastVisited", null)));
        rows.put(
                "form_boolean_value",
                List.of(
                        row("form_boolean_value", "00000000000000000001", "form_name", "formRourke2020",
                                "form_id", "12", "field_name", "a", "value", "1"),
                        row("form_boolean_value", "00000000000000000002", "form_name", "formRourke2020",
                                "form_id", "31", "field_name", "b", "value", null),
                        row("form_boolean_value", "00000000000000000003", "form_name", "formRourke2020",
                                "form_id", "99", "field_name", "c", "value", "0")));
        rows.put(
                "eform_data",
                List.of(
                        row("eform_data", key(4), "fdid", "4", "form_date", "2024-05-06", "form_time",
                                "13:00:00", "patient_independent", "0", "status", "1", "form_data",
                                "<html>form</html>"),
                        row("eform_data", key(5), "fdid", "5", "form_date", "2024-05-07", "form_time",
                                "14:00:00", "patient_independent", "0", "status", "0", "form_data", "")));
        rows.put(
                "eform_values",
                List.of(row("eform_values", key(40), "id", "40", "fdid", "4", "var_value", "typed")));
        String imageKey = CdsExportDataSummaryReadDao.plan("client_image").keys().getFirst();
        rows.put(
                "client_image",
                List.of(
                        row("client_image", key(3), imageKey, "3", "contents", binary(PNG, true),
                                "image_type", "png")));
        // A binary column no presentation needs is listed by size and SHA-256; its bytes are not kept.
        rows.put(
                "emailAttachment",
                List.of(
                        row("emailAttachment", key(30), "id", "30", "logId", "77", "fileContents",
                                binary("attached".getBytes(StandardCharsets.UTF_8), false))));
        return rows;
    }

    @Test
    void shouldPlanAndWriteTheSameRecords_fromStoredRowsAsFromRowsInMemory() throws Exception {
        var rows = chart();
        var inMemory = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(inMemory).hasSizeGreaterThan(6);
        var composer = new CdsExportDataSummaryHtmlComposer();
        try (var result = stored(rows);
                var plan = plan(CdsExportDataSummaryPlanner.plan(result.rows(), TORONTO), result)) {
            assertThat(members()).isEqualTo(1);
            assertThat(plan.groups()).hasSameSizeAs(inMemory);
            for (int index = 0; index < inMemory.size(); index++) {
                var expected = inMemory.get(index);
                var indexed = plan.groups().get(index);
                // The index entry holds the planning columns alone, and plans the same record.
                for (var entry : indexed.rows())
                    assertThat(entry.values().keySet())
                            .isSubsetOf(CdsExportDataSummaryPlanner.planning(entry.table()));
                assertThat(indexed.subClass()).isEqualTo(expected.subClass());
                assertThat(indexed.title()).isEqualTo(expected.title());
                assertThat(indexed.event()).isEqualTo(expected.event());
                assertThat(CdsExportDataSummaryOccurrenceService.withheld(indexed))
                        .isEqualTo(CdsExportDataSummaryOccurrenceService.withheld(expected));
                assertThat(CdsExportDataSummaryOccurrenceService.identity(KEY, indexed))
                        .isEqualTo(CdsExportDataSummaryOccurrenceService.identity(KEY, expected));
                assertThat(
                                CdsExportDataSummaryPlanner.divide(
                                        indexed, result::weight, Division.STANDARD))
                        .containsExactly(indexed);
                // The loaded record is the record of the rows in memory, value for value.
                var loaded = plan.load(indexed);
                assertThat(loaded).isEqualTo(expected);
                assertThat(composer.compose(loaded, PATIENT))
                        .isEqualTo(composer.compose(expected, PATIENT));
                assertThat(CdsExportDataSummaryOccurrenceService.assessments(loaded))
                        .containsExactlyEntriesOf(
                                CdsExportDataSummaryOccurrenceService.assessments(expected));
                assertThat(CdsExportDataSummaryOccurrenceService.notes(loaded, null))
                        .isEqualTo(CdsExportDataSummaryOccurrenceService.notes(expected, null));
            }
        }
        // Closing the plan erases the stored rows.
        assertThat(members()).isZero();
    }

    @Test
    void shouldExportEveryRow_whenATableHasMoreRowsAndFieldsThanTheFormerLimits() throws Exception {
        // 100,001 checkbox rows of four fields: past the former 100,000 rows and 400,000 fields
        // of one table and chart, which held the chart.
        int children = 100_001;
        var checkboxes = new ArrayList<Row>(children);
        for (int i = 1; i <= children; i++)
            checkboxes.add(
                    row("form_boolean_value", String.format("%020d", i), "form_name", "formRourke2020",
                            "form_id", "12", "field_name", "p" + i, "value", i % 2 == 0 ? "1" : null));
        var rows = new TreeMap<String, List<Row>>();
        rows.put("formRourke2020", List.of(row("formRourke2020", key(12), "ID", "12", "formCreated", "2024-01-01")));
        rows.put("form_boolean_value", checkboxes);
        try (var result = stored(rows)) {
            assertThat(result.rows().get("form_boolean_value")).hasSize(children);
            // What stays in memory of a row is its two link columns; its field and value do not.
            assertThat(result.rows().get("form_boolean_value").getFirst().values().keySet())
                    .containsExactly("form_id", "form_name");
            var records = CdsExportDataSummaryPlanner.plan(result.rows(), TORONTO);
            assertThat(records).hasSize(1);
            var parts =
                    CdsExportDataSummaryPlanner.divide(
                            records.getFirst(), result::weight, Division.STANDARD);
            // The former field number now decides only where the record's second part begins.
            assertThat(parts).hasSize(2);
            assertThat(parts.get(0).accounted()).hasSize(100_001);
            assertThat(parts.get(1).accounted()).hasSize(1);
            try (var plan = plan(parts, null)) {
                assertThat(plan.groups()).hasSize(2);
            }
            var plan = plan(parts, result);
            var last = plan.load(parts.get(1));
            assertThat(last.rows()).extracting(Row::table).containsExactly("formRourke2020", "form_boolean_value");
            assertThat(last.accounted().getFirst()).isEqualTo(checkboxes.getLast());
            assertThat(last.title()).endsWith(", part 2 of 2");
            assertThat(CdsExportDataSummaryOccurrenceService.assessments(last)).hasSize(4);
            // A printer is given the whole record, in the record's order.
            var whole = plan.whole(parts.get(1));
            assertThat(whole.parts()).isZero();
            assertThat(whole.rows()).hasSize(children + 1);
            assertThat(whole.rows().subList(1, whole.rows().size())).isEqualTo(checkboxes);
        }
        assertThat(members()).isZero();
    }

    @Test
    void shouldExportEveryValue_whenAChartHoldsMoreTextAndImagesThanTheFormerLimits() throws Exception {
        // 70 MiB of eForm values (formerly 64 MiB of text held a chart), 36 MiB of photos (formerly
        // 32 MiB) and one photo of 17 MiB (formerly one over 16 MiB was not shown).
        String megabyte = "0123456789abcdef".repeat(65_536);
        var values = new ArrayList<Row>();
        for (int i = 1; i <= 70; i++)
            values.add(row("eform_values", key(100 + i), "id", Integer.toString(100 + i), "fdid", "4",
                    "var_value", megabyte));
        String imageKey = CdsExportDataSummaryReadDao.plan("client_image").keys().getFirst();
        byte[] photo = new byte[12 << 20];
        System.arraycopy(PNG, 0, photo, 0, PNG.length);
        byte[] large = new byte[17 << 20];
        System.arraycopy(PNG, 0, large, 0, PNG.length);
        var photos = new ArrayList<Row>();
        for (int i = 1; i <= 3; i++)
            photos.add(row("client_image", key(i), imageKey, Integer.toString(i), "contents", binary(photo, true)));
        photos.add(row("client_image", key(4), imageKey, "4", "contents", binary(large, true)));
        var rows = new TreeMap<String, List<Row>>();
        rows.put(
                "eform_data",
                List.of(row("eform_data", key(4), "fdid", "4", "patient_independent", "0", "status", "1")));
        rows.put("eform_values", values);
        rows.put("client_image", photos);
        try (var result = stored(rows)) {
            var records = CdsExportDataSummaryPlanner.plan(result.rows(), TORONTO);
            assertThat(records).extracting(Group::kind)
                    .containsExactlyInAnyOrder(
                            Kind.EFORM, Kind.PATIENT_PHOTO, Kind.PATIENT_PHOTO, Kind.PATIENT_PHOTO, Kind.PATIENT_PHOTO);
            var record = records.stream().filter(group -> group.kind() == Kind.EFORM).findFirst().orElseThrow();
            // The eForm's text passes what one document is given, so it is written as two parts.
            var eform = CdsExportDataSummaryPlanner.divide(record, result::weight, Division.STANDARD);
            assertThat(eform).hasSize(2);
            var all = new ArrayList<Row>();
            for (var part : eform) all.addAll(part.accounted());
            assertThat(all).extracting(Row::recordKey)
                    .containsExactlyElementsOf(record.rows().stream().map(Row::recordKey).toList());
            var plan = plan(eform, result);
            assertThat(plan.load(eform.get(1)).accounted().getLast()).isEqualTo(values.getLast());
            // Each photo is a record of its own, embedded whole, the 17 MiB one included.
            var loaded =
                    plan.load(
                            records.stream()
                                    .filter(group -> group.kind() == Kind.PATIENT_PHOTO && group.id().equals("4"))
                                    .findFirst()
                                    .orElseThrow());
            assertThat(loaded.root().values().get("contents").content()).isEqualTo(large);
            assertThat(loaded.hasImage()).isTrue();
        }
        assertThat(members()).isZero();
    }

    @Test
    void shouldListEveryRowOnceAndInOrder_whenARecordIsDividedByASmallInjectedDivision() throws Exception {
        var rows = new ArrayList<Row>();
        rows.add(row("tickler", key(7), "tickler_no", "7", "message", "root"));
        for (int i = 1; i <= 23; i++)
            rows.add(row("tickler_comments", key(i), "id", Integer.toString(i), "tickler_no", "7",
                    "message", "comment " + i));
        for (int i = 1; i <= 5; i++)
            rows.add(row("tickler_update", key(i), "id", Integer.toString(i), "tickler_no", "7"));
        var record = new Group(Kind.TICKLER, "tickler", "7", rows, 0, 0, null, false);
        java.util.function.Function<Row, Weight> weigh =
                row -> {
                    long text = 0;
                    for (var value : row.values().values())
                        if (value.storage() == Storage.TEXT)
                            text += value.text().getBytes(StandardCharsets.UTF_8).length;
                    return new Weight(row.values().size(), text, 0);
                };

        // One window: the record as it always was, the same object.
        assertThat(CdsExportDataSummaryPlanner.divide(record, weigh, Division.STANDARD)).containsExactly(record);

        // A small window: at most 12 fields of one table (four comments) in a part.
        var parts = CdsExportDataSummaryPlanner.divide(record, weigh, new Division(12, 1_000, 1 << 20, 1 << 20));
        assertThat(parts).hasSizeGreaterThan(5);
        var listed = new ArrayList<Row>();
        var decided = new LinkedHashMap<Object, Object>();
        var identities = new java.util.HashSet<>();
        for (int index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            assertThat(part.part()).isEqualTo(index + 1);
            assertThat(part.parts()).isEqualTo(parts.size());
            assertThat(part.root()).isSameAs(rows.getFirst());
            assertThat(part.subClass()).isEqualTo(record.subClass());
            assertThat(part.title()).isEqualTo(record.title() + ", part " + (index + 1) + " of " + parts.size());
            assertThat(CdsExportDataSummaryOccurrenceService.partNote(part))
                    .startsWith("Part " + (index + 1) + " of " + parts.size() + " of this record's data summary");
            assertThat(part.accounted()).isNotEmpty();
            listed.addAll(part.accounted());
            CdsExportDataSummaryOccurrenceService.assessments(part)
                    .forEach((field, decision) -> assertThat(decided.put(field, decision)).isNull());
            assertThat(identities.add(CdsExportDataSummaryOccurrenceService.identity(KEY, part))).isTrue();
        }
        // The same input through the small window and through one window: the same rows in the
        // same order, each once, and the same decision for every field.
        assertThat(listed).containsExactlyElementsOf(record.rows());
        assertThat(decided).isEqualTo(new LinkedHashMap<>(CdsExportDataSummaryOccurrenceService.assessments(record)));
        assertThat(CdsExportDataSummaryOccurrenceService.identity(KEY, parts.getFirst()))
                .isEqualTo(CdsExportDataSummaryOccurrenceService.identity(KEY, record));
        // A row larger than the window is a part of its own, never refused and never split.
        var one = CdsExportDataSummaryPlanner.divide(record, weigh, new Division(1, 1, 1, 1));
        assertThat(one).hasSize(rows.size());
        assertThat(one.get(1).accounted()).containsExactly(rows.get(1));
    }

    /** What one emission wrote: each summary's HTML, each entry's XML, receipts and attachments. */
    private record Emitted(
            List<String> html,
            String xml,
            List<Object> receipts,
            List<String> attachments,
            long count) {}

    private Emitted emit(CdsExportDataSummaryOccurrenceService.Plan plan) throws Exception {
        var blank = new java.io.ByteArrayOutputStream();
        try (var document = new org.apache.pdfbox.pdmodel.PDDocument()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(blank);
        }
        var html = new ArrayList<String>();
        var composer = new CdsExportDataSummaryHtmlComposer();
        var captures = org.mockito.Mockito.mock(CdsExportNativeCaptureService.class);
        var stores = org.mockito.Mockito.mock(CdsExportStagingFactory.class);
        org.mockito.Mockito.when(
                        stores.openExisting(
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.anyLong(),
                                org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                .thenReturn(store);
        var service =
                new CdsExportDataSummaryOccurrenceService(
                        captures,
                        stores,
                        (group, patient) -> {
                            html.add(composer.compose(group, patient));
                            return blank.toByteArray();
                        },
                        Map.of());
        var lease =
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportLeaseDto.class,
                        org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var source =
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportPatientCaptureDto.class,
                        org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(source.manifest().plaintextSha256()).thenReturn("d".repeat(64));
        var patient = org.mockito.Mockito.mock(CdsExportCohortService.Patient.class);
        org.mockito.Mockito.when(patient.patient()).thenReturn(27);
        var control =
                org.mockito.Mockito.mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl.class);
        var receipts = new ArrayList<Object>();
        var attachments = new ArrayList<String>();
        var demographic = new LinkedHashMap<String, String>();
        io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto.COLUMNS.forEach(
                column -> demographic.put(column, null));
        demographic.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "U"));
        var demographics =
                new CdsExportDemographicMapper()
                        .map(new io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto(27, demographic));
        long[] count = {0};
        var output = new java.io.ByteArrayOutputStream();
        var selected =
                Set.of(
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.DEMOGRAPHICS,
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory.REPORTS);
        // The real composer: the entries are compared as the patient file's own bytes.
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        "d".repeat(64),
                        KEY,
                        "2026.08.0-test",
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        emitter -> {
                            emitter.emit(
                                    new CdsExportXmlComposer.Section(
                                            new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                    .CdsExportIdentityDto(
                                                    KEY,
                                                    io.github.carlos_emr.carlos.demographic.cds.contract
                                                            .CdsCategory.DEMOGRAPHICS,
                                                    "b".repeat(64)),
                                            writer ->
                                                    new CdsExportDemographicXmlWriter()
                                                            .write(demographics, writer)));
                            count[0] =
                                    service.append(
                                            lease,
                                            patient,
                                            KEY,
                                            Set.of("demographic", "tickler"),
                                            source,
                                            control,
                                            emitter,
                                            new CdsExportClinicalOccurrenceService.Receipts() {
                                                @Override
                                                public void accept(
                                                        CdsExportClinicalOccurrenceService.Receipt receipt) {
                                                    receipts.add(
                                                            List.of(
                                                                    receipt.identity(),
                                                                    receipt.table(),
                                                                    receipt.nativeId(),
                                                                    Map.copyOf(receipt.fields())));
                                                }

                                                @Override
                                                public void attachment(
                                                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                                        .CdsExportIdentityDto
                                                                report,
                                                        io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                                        .CdsExportPackageMemberDto
                                                                member) {
                                                    attachments.add(report.occurrenceKey() + " " + member.relativePath());
                                                }
                                            },
                                            plan);
                        },
                        ignored -> {},
                        16L << 20,
                        () -> {});
        return new Emitted(
                html, output.toString(StandardCharsets.UTF_8), receipts, attachments, count[0]);
    }

    @Test
    void shouldEmitTheSameEntriesDocumentsAndReceipts_fromStoredRowsAsFromRowsInMemory() throws Exception {
        var rows = chart();
        // The same plan twice: once over rows held in memory, once over the stored rows.
        var inMemory = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        Emitted expected = emit(plan(inMemory, null));
        assertThat(expected.count()).isEqualTo(inMemory.size());
        assertThat(members()).isEqualTo(inMemory.size());
        try (var listed = Files.list(root)) {
            for (Path member : listed.toList()) Files.delete(member);
        }
        Emitted actual;
        try (var result = stored(rows);
                var plan = plan(CdsExportDataSummaryPlanner.plan(result.rows(), TORONTO), result)) {
            actual = emit(plan);
        }
        assertThat(actual.html()).containsExactlyElementsOf(expected.html());
        assertThat(actual.xml()).isEqualTo(expected.xml()).contains("<Reports>");
        assertThat(actual.receipts()).containsExactlyElementsOf(expected.receipts());
        assertThat(actual.attachments()).containsExactlyElementsOf(expected.attachments());
        assertThat(actual.count()).isEqualTo(expected.count());
        // Only the attachments remain: the stored rows are erased with the plan.
        assertThat(members()).isEqualTo(inMemory.size());
    }

    @Test
    void shouldEmitEachPartAsItsOwnEntry_whenARecordIsDivided() throws Exception {
        var rows = new ArrayList<Row>();
        rows.add(row("tickler", key(7), "tickler_no", "7", "message", "root"));
        for (int i = 1; i <= 9; i++)
            rows.add(row("tickler_comments", key(i), "id", Integer.toString(i), "tickler_no", "7"));
        var record = new Group(Kind.TICKLER, "tickler", "7", rows, 0, 0, null, false);
        var whole = emit(plan(List.of(record), null));
        var parts =
                CdsExportDataSummaryPlanner.divide(
                        record,
                        row -> new Weight(row.values().size(), 0, 0),
                        new Division(8, 1_000, 1 << 20, 1 << 20));
        assertThat(parts).hasSize(3);
        var divided = emit(plan(parts, null));
        assertThat(whole.count()).isEqualTo(1);
        assertThat(divided.count()).isEqualTo(3);
        // The first part keeps the record's identity and attachment; the others have their own.
        assertThat(divided.attachments().getFirst()).isEqualTo(whole.attachments().getFirst());
        assertThat(Set.copyOf(divided.attachments())).hasSize(3);
        assertThat(divided.xml())
                .contains("<SubClass>Tickler</SubClass>")
                .contains("Part 2 of 3 of this record's data summary");
        assertThat(divided.xml().split("<Reports>", -1)).hasSize(4);
        assertThat(divided.html().get(1))
                .contains("CARLOS data summary: Tickler, part 2 of 3")
                .contains("<p class=\"statement\">Part 2 of 3 of this record's data summary")
                .contains("Source row 1 of 5: table tickler");
        assertThat(whole.xml()).doesNotContain("Part ");
        // Every field of the record is decided once across the parts, exactly as in one piece.
        var decided = new java.util.HashMap<Object, Object>();
        for (Object receipt : divided.receipts())
            ((Map<?, ?>) ((List<?>) receipt).get(3))
                    .forEach((field, decision) -> assertThat(decided.put(field, decision)).isNull());
        assertThat(decided).isEqualTo(((List<?>) whole.receipts().getFirst()).get(3));
    }

    @Test
    void shouldKeepALongPlanningValueAsItsDigest_andARowExactlyInItsStoredForm() throws Exception {
        String imageKey = CdsExportDataSummaryReadDao.plan("client_image").keys().getFirst();
        String longValue = "7".repeat(CdsExportDataSummaryRowsReader.PLANNING_CHARACTERS + 1);
        var digest = MessageDigest.getInstance("SHA-256");
        var indexed = CdsExportDataSummaryRowsReader.planningValue(Value.text(longValue), digest);
        assertThat(indexed.text()).hasSize(72).isNotEqualTo(longValue);
        assertThat(CdsExportDataSummaryRowsReader.planningValue(Value.text(longValue), digest)).isEqualTo(indexed);
        assertThat(CdsExportDataSummaryRowsReader.planningValue(Value.text("7"), digest).text()).isEqualTo("7");
        assertThat(
                        CdsExportDataSummaryRowsReader.planningValue(binary(PNG, true), digest)
                                .content())
                .isNull();
        // Round trip of one whole row: SQL NULL, empty, text and both kinds of binary value.
        var row =
                row("client_image", key(3), imageKey, "3", "contents", binary(PNG, true), "image_type",
                        null, "update_date", "", "other", binary(new byte[] {1, 2}, false));
        byte[] encoded = CdsExportDataSummaryRowsReader.encode(row);
        assertThat(
                        CdsExportDataSummaryRowsReader.decode(
                                new java.io.DataInputStream(new java.io.ByteArrayInputStream(encoded))))
                .isEqualTo(row);
        assertThat(Set.copyOf(CdsExportDataSummaryPlanner.planning("form_boolean_value")))
                .contains("form_name", "form_id")
                .doesNotContain("value", "field_name");
    }
}
