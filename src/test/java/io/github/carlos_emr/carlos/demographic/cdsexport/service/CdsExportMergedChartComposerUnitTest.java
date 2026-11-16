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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportStagedFileDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The patient file of charts linked by Merge Records, assembled from each chart's own document by
 * verified byte ranges: one patient (the head's Demographics), every chart's records, each merged
 * chart's demographics in its own schema-valid record, and the links record last.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportMergedChartComposerUnitTest {
    private static final int HEAD = 12, MERGED = 34;
    private static final Set<CdsCategory> SECTIONS =
            EnumSet.of(CdsCategory.DEMOGRAPHICS, CdsCategory.APPOINTMENTS, CdsCategory.NEW_CATEGORY);
    private Path base;
    private CdsExportStagingService store;

    @BeforeEach
    void privateStore() throws Exception {
        // A private root (cds.export.test.documentRoot), else the process home: never a shared
        // temporary directory.
        base =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "cds-export-merged-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        var root = Files.createDirectory(base.resolve("store"));
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

    @AfterEach
    void eraseStore() throws IOException {
        if (base != null && Files.exists(base))
            try (var files = Files.walk(base)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    @Test
    void shouldHoldEveryChartsRecords_withOneDemographicsAndTheMergedChartsOwnInItsRecord()
            throws Exception {
        var head = part(HEAD, "FAKE-Head", 1, "head-note");
        var merged = part(MERGED, "FAKE-Merged", 2, "merged-note");
        var output = new ByteArrayOutputStream();

        var result =
                new CdsExportMergedChartComposer()
                        .compose(store, output, List.of(head.part, merged.part), links(), true, () -> {});

        String file = output.toString(StandardCharsets.UTF_8);
        // One patient: the head's Demographics only.
        assertThat(count(file, "<Demographics>")).isEqualTo(1);
        assertThat(file).contains("<UniqueVendorIdSequence>C12</UniqueVendorIdSequence>");
        assertThat(file).doesNotContain("<UniqueVendorIdSequence>C34");
        // Every chart's appointments, the head's first.
        assertThat(count(file, "<Appointments>")).isEqualTo(3);
        assertThat(file.indexOf("head-note")).isLessThan(file.indexOf("merged-note"));
        // The merged chart's native demographic record is in its record, not the file's.
        assertThat(count(file, "<CategoryName>" + CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC_CATEGORY))
                .isEqualTo(1);
        // NewCategory ends with the merged chart's record, then the links record.
        int chart = file.indexOf("<CategoryName>CARLOS.MergedChart.v1</CategoryName>");
        int links = file.indexOf("<CategoryName>CARLOS.MergeLinks.v1</CategoryName>");
        assertThat(chart).isPositive();
        assertThat(links).isGreaterThan(chart);
        assertThat(file.indexOf("</NewCategory>", links)).isEqualTo(file.lastIndexOf("</NewCategory>"));
        assertThat(result.occurrences())
                .containsEntry(CdsCategory.DEMOGRAPHICS, 1L)
                .containsEntry(CdsCategory.APPOINTMENTS, 3L)
                .containsEntry(CdsCategory.NEW_CATEGORY, 3L);
        var member = result.members().getFirst();
        assertThat(member.chart()).isEqualTo(MERGED);
        assertThat(member.positions())
                .containsEntry(CdsCategory.APPOINTMENTS, new CdsMergedChartContract.Range(2, 2))
                .doesNotContainKey(CdsCategory.NEW_CATEGORY)
                .doesNotContainKey(CdsCategory.DEMOGRAPHICS);
        assertThat(member.carried()).hasSize(2);
        // The record's document is the merged chart's own standalone, schema-valid file.
        assertThat(member.carrierWritten()).isTrue();
        var record = carrier(output.toByteArray());
        assertThat(record.head()).isEqualTo(HEAD);
        CdsMergedChartContract.verifyDocument(record);
        assertThat(record.document())
                .contains("<UniqueVendorIdSequence>C34</UniqueVendorIdSequence>")
                .contains(CdsNativeExtensionContract.NATIVE_DEMOGRAPHIC_CATEGORY)
                .doesNotContain("<Appointments>");
        // A reader that is not CARLOS sees one valid patient with every record.
        var checked =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(output.toByteArray()),
                                "C" + HEAD,
                                SECTIONS,
                                "2026.08.0",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                reference -> {},
                                () -> {});
        assertThat(checked.senderPatientId()).isEqualTo("C" + HEAD);
        assertThat(checked.occurrences()).isEqualTo(result.occurrences());
    }

    @Test
    void shouldLeaveTheMergedChartsDemographicsOutAndWriteNoRecord_withoutNewCategory()
            throws Exception {
        var head = part(HEAD, "FAKE-Head", 1, "head-note");
        var merged = part(MERGED, "FAKE-Merged", 1, "merged-note");
        var output = new ByteArrayOutputStream();

        var result =
                new CdsExportMergedChartComposer()
                        .compose(store, output, List.of(head.part, merged.part), links(), false, () -> {});

        String file = output.toString(StandardCharsets.UTF_8);
        assertThat(count(file, "<Demographics>")).isEqualTo(1);
        assertThat(file).contains("merged-note").doesNotContain("CARLOS.MergedChart.v1");
        assertThat(file).doesNotContain("CARLOS.MergeLinks.v1");
        assertThat(result.members().getFirst().carrierWritten()).isFalse();
        // The head's own NewCategory record stays; the merged chart's native record goes nowhere.
        assertThat(result.occurrences()).containsEntry(CdsCategory.NEW_CATEGORY, 1L);
    }

    @Test
    void shouldRefuseTheFile_whenACopiedRangeIsNotTheBytesItsDispositionNamed() throws Exception {
        var head = part(HEAD, "FAKE-Head", 1, "head-note");
        var merged = part(MERGED, "FAKE-Merged", 1, "merged-note");
        // One recorded digest no longer names the bytes at its range.
        var forged = new CdsExportMergedChartComposer.Part(MERGED);
        var listener = forged.listener();
        for (var range : merged.ranges)
            listener.occurrence(
                    range.category(),
                    range.identity(),
                    range.offset(),
                    range.length(),
                    range.category() == CdsCategory.APPOINTMENTS ? "0".repeat(64) : range.sha256());
        forged.xml(merged.xml);

        assertThatThrownBy(
                        () ->
                                new CdsExportMergedChartComposer()
                                        .compose(
                                                store,
                                                new ByteArrayOutputStream(),
                                                List.of(head.part, forged),
                                                links(),
                                                true,
                                                () -> {}))
                .hasMessage("CDS_EXPORT_MERGED_CHART_COPY_MISMATCH");
    }

    @Test
    void shouldStreamAndEraseLargeMemberSpools_withoutChangingUnicodeOrWhitespace() throws Exception {
        String exact = " \t\r\n<>&😀".repeat(140_000);
        String nativeJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("value", exact));
        var head = part(HEAD, "FAKE-Head", 1, "head-note");
        var member = part(MERGED, "FAKE-Merged", 1, "member-note", nativeJson);
        long before;
        try (var files = Files.list(base.resolve("store"))) { before = files.count(); }
        var output = new ByteArrayOutputStream();
        new CdsExportMergedChartComposer().compose(store, output, List.of(head.part, member.part), links(), true, () -> {});
        var carried = carrier(output.toByteArray());
        CdsMergedChartContract.verifyDocument(carried);
        var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        // As in CdsXmlReaders: DTDs are prohibited; literal escaped text has no entity-size cap.
        factory.setAttribute("jdk.xml.maxGeneralEntitySizeLimit", "0");
        factory.setAttribute("jdk.xml.totalEntitySizeLimit", "0");
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(carried.document().getBytes(StandardCharsets.UTF_8)));
        String json = document.getElementsByTagNameNS("cds_dt", "Content").item(0).getTextContent();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).get("value").textValue()).isEqualTo(exact);
        try (var files = Files.list(base.resolve("store"))) { assertThat(files.count()).isEqualTo(before); }
        var broken = new java.io.OutputStream() {
            @Override public void write(int value) throws IOException { throw new IOException("SYNTHETIC_OUTPUT_FAILURE"); }
            @Override public void write(byte[] value, int offset, int length) throws IOException { throw new IOException("SYNTHETIC_OUTPUT_FAILURE"); }
        };
        assertThatThrownBy(() -> new CdsExportMergedChartComposer().compose(
                store, broken, List.of(head.part, member.part), links(), true, () -> {}))
                .hasMessage("SYNTHETIC_OUTPUT_FAILURE");
        try (var files = Files.list(base.resolve("store"))) { assertThat(files.count()).isEqualTo(before); }
    }

    private static CdsMergedChartContract.ChartRecord carrier(byte[] bytes) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        // As in CdsXmlReaders: DTDs are prohibited; literal escaped text has no entity-size cap.
        factory.setAttribute("jdk.xml.maxGeneralEntitySizeLimit", "0");
        factory.setAttribute("jdk.xml.totalEntitySizeLimit", "0");
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        var records = document.getElementsByTagNameNS("cds", "NewCategory");
        for (int index = 0; index < records.getLength(); index++) {
            var record = (org.w3c.dom.Element) records.item(index);
            if (CdsMergedChartContract.CHART_RECORD.equals(record.getElementsByTagNameNS("cds", "CategoryName").item(0).getTextContent()))
                return CdsMergedChartContract.chartRecord(record.getElementsByTagNameNS("cds_dt", "Content").item(0).getTextContent());
        }
        throw new AssertionError("Expected merged-chart carrier");
    }

    /** A chart's document with Demographics, appointments and a native demographic record. */
    private Composed part(int chart, String name, int appointments, String note) throws Exception {
        return part(chart, name, appointments, note, "{}");
    }

    private Composed part(int chart, String name, int appointments, String note, String nativeJson) throws Exception {
        var composed = new Composed(chart);
        String key =
                CdsImportKeys.hash("carlos-export-patient-v1", "run", "sender", Integer.toString(chart));
        var digest = "a".repeat(64);
        composed.xml =
                store.stage(
                        "PATIENT_XML",
                        output ->
                                new CdsExportXmlComposer()
                                        .composeFromSource(
                                                output,
                                                digest,
                                                key,
                                                "2026.08.0",
                                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                                SECTIONS,
                                                emitter -> {
                                                    emitter.emit(
                                                            new CdsExportXmlComposer.Section(
                                                                    identity(key, CdsCategory.DEMOGRAPHICS, "d"),
                                                                    xml -> demographics(xml, chart, name)));
                                                    for (int index = 0; index < appointments; index++) {
                                                        int at = index;
                                                        emitter.emit(
                                                                new CdsExportXmlComposer.Section(
                                                                        identity(key, CdsCategory.APPOINTMENTS, "a" + at),
                                                                        xml -> appointment(xml, note + " " + at)));
                                                    }
                                                    emitter.emit(
                                                            new CdsExportXmlComposer.Section(
                                                                    identity(key, CdsCategory.NEW_CATEGORY, "n"),
                                                                    xml ->
                                                                            residual(
                                                                                    xml,
                                                                                    CdsNativeExtensionContract
                                                                                            .NATIVE_DEMOGRAPHIC_CATEGORY,
                                                                                    nativeJson)));
                                                },
                                                disposition -> {},
                                                Long.MAX_VALUE,
                                                () -> {},
                                                composed.listener()));
        composed.part.xml(composed.xml);
        return composed;
    }

    /** A chart's part, with the ranges its listener received kept for the test. */
    private static final class Composed {
        final CdsExportMergedChartComposer.Part part;
        final List<CdsExportMergedChartComposer.Range> ranges = new ArrayList<>();
        CdsExportStagedFileDto xml;

        Composed(int chart) {
            part = new CdsExportMergedChartComposer.Part(chart);
        }

        CdsExportXmlComposer.Occurrences listener() {
            var delegate = part.listener();
            return (category, identity, offset, length, sha256) -> {
                ranges.add(
                        new CdsExportMergedChartComposer.Range(
                                category, identity, offset, length, sha256));
                delegate.occurrence(category, identity, offset, length, sha256);
            };
        }
    }

    private static CdsExportIdentityDto identity(String key, CdsCategory category, String id) {
        return new CdsExportIdentityDto(
                key, category, CdsImportKeys.hash("carlos-native-occurrence-v1", "fixture", id));
    }

    private static void demographics(CdsExportXmlComposer.ContentWriter xml, int chart, String name)
            throws IOException {
        xml.start("cds", "Names");
        xml.start("cds_dt", "LegalName");
        xml.attribute("", "namePurpose", "L");
        xml.start("cds_dt", "FirstName");
        xml.element("cds_dt", "Part", name);
        xml.element("cds_dt", "PartType", "GIV");
        xml.end();
        xml.start("cds_dt", "LastName");
        xml.element("cds_dt", "Part", "FAKE-Chart");
        xml.element("cds_dt", "PartType", "FAMC");
        xml.end();
        xml.end();
        xml.end();
        xml.element("cds", "DateOfBirth", "1971-03-04");
        xml.element("cds", "Gender", "F");
        xml.element("cds", "UniqueVendorIdSequence", "C" + chart);
        xml.start("cds", "PersonStatusCode");
        xml.element("cds", "PersonStatusAsEnum", "A");
        xml.end();
    }

    private static void appointment(CdsExportXmlComposer.ContentWriter xml, String note)
            throws IOException {
        xml.element("cds", "AppointmentTime", "09:00:00");
        xml.element("cds", "Duration", "15");
        xml.start("cds", "AppointmentDate");
        xml.element("cds_dt", "FullDate", "2026-01-02");
        xml.end();
        xml.element("cds", "AppointmentNotes", note);
    }

    private static void residual(CdsExportXmlComposer.ContentWriter xml, String name, String json)
            throws IOException {
        xml.element("cds", "CategoryName", name);
        xml.element("cds", "CategoryDescription", "Native demographic values");
        xml.start("cds", "ResidualInfo");
        CdsExportNativeHistoryXmlWriter.residualElement(xml, "CARLOS.Demographic.v1", "string", json);
        xml.end();
    }

    private static CdsMergedChartContract.LinksRecord links() {
        return new CdsMergedChartContract.LinksRecord(
                List.of(HEAD, MERGED),
                List.of(
                        new CdsMergedChartContract.LinkRow(
                                "5", "34", "12", "0", "999998", "2026-01-02", false)),
                List.of(
                        new CdsMergedChartContract.PrivilegeRow(
                                MERGED, "_all", "_eChart$34", "|or|", "0", "0")),
                new TreeSet<>(),
                new TreeSet<>());
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) count++;
        return count;
    }
}
