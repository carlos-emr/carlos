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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A CARLOS sender's merged-chart records are checked from the patient file's sealed parse before
 * anything of the patient is planned; one that fails refuses the patient for review.
 */
@Tag("unit")
@Tag("cds-import")
class CdsMergedChartReaderUnitTest {
    private static final int HEAD = 12, MERGED = 34;
    private final AtomicInteger sequence = new AtomicInteger();
    private Path base;

    @BeforeEach
    void privateRoot() throws Exception {
        // A private root (cds.export.test.documentRoot), else the process home.
        base =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "cds-merged-reader-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
    }

    @AfterEach
    void erase() throws Exception {
        if (base != null && Files.exists(base))
            try (var files = Files.walk(base)) {
                for (var path : files.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
    }

    @Test
    void shouldKeepTheMergedChartsRecords_whenEveryCheckPasses() throws Exception {
        var carriers = read(file(chart(MERGED, HEAD, range(2, 1), member()), links()), "C12");
        assertThat(carriers.merged()).isEqualTo(1);
        assertThat(carriers.charts().getFirst().chart()).isEqualTo(MERGED);
        assertThat(carriers.links().charts()).containsExactly(HEAD, MERGED);
    }

    @Test
    void shouldReadNothing_fromAFileWithoutMergedChartRecords() throws Exception {
        assertThat(read(file(), "C12").merged()).isZero();
        assertThat(read(file(), "C12").links()).isNull();
    }

    @Test
    void shouldRefuseThePatient_whenAMergedChartRecordFailsAnyCheck() throws Exception {
        String document = member();
        String record = chart(MERGED, HEAD, range(2, 1), document);
        for (String[] invalid :
                new String[][] {
                    // The document does not have the digest it states.
                    {
                        record.replace(
                                CdsMergedChartContract.sha256(
                                        document.getBytes(StandardCharsets.UTF_8)),
                                "0".repeat(64)),
                        links()
                    },
                    // Another head than the file's chart.
                    {chart(MERGED, 13, range(2, 1), document), links()},
                    // A merged chart in another form than C and its number.
                    {record.replace("\"chart\":\"C34\"", "\"chart\":\"34\""), links()},
                    // Records outside their section.
                    {chart(MERGED, HEAD, range(2, 5), document), links()},
                    // Merged charts without their links.
                    {record, ""},
                    // Links that name other charts.
                    {record, links(List.of(HEAD, 35))}
                }) {
            Path parsed = file(invalid[0], invalid[1]);
            assertThatThrownBy(() -> read(parsed, "C12"))
                    .isInstanceOf(CdsMergedChartReader.InvalidCarrier.class)
                    .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        }
        // A record that is not even JSON.
        Path broken = file(record.replace("{", "["), links());
        assertThatThrownBy(() -> read(broken, "C12"))
                .isInstanceOf(CdsMergedChartReader.InvalidCarrier.class);
    }

    private CdsMergedChartReader.Carriers read(Path manifest, String sender) throws Exception {
        return new CdsMergedChartReader(new CdsFieldParser()).read(manifest, sender);
    }

    /** Parses a patient file of the head with two appointments and the given NewCategory records. */
    private Path file(String... records) throws Exception {
        int number = sequence.incrementAndGet();
        Path source = base.resolve("source-" + number + ".xml");
        var xml = new StringBuilder();
        xml.append("<OmdCds xmlns='cds' xmlns:cdsd='cds_dt'><PatientRecord>")
                .append(demographics("C12"))
                .append(appointment())
                .append(appointment());
        for (String record : records) xml.append(record);
        xml.append("</PatientRecord></OmdCds>");
        Files.writeString(source, xml.toString());
        var parsed =
                new CdsXmlParser()
                        .parse(
                                source,
                                base.resolve("spool-" + number),
                                CdsInputLimitsDto.defaults(),
                                () -> false,
                                record -> {});
        return parsed.recordsManifestFile();
    }

    private static String demographics(String sender) {
        return "<Demographics><Names><cdsd:LegalName namePurpose='L'><cdsd:FirstName>"
                + "<cdsd:Part>FAKE-Merged</cdsd:Part><cdsd:PartType>GIV</cdsd:PartType>"
                + "</cdsd:FirstName><cdsd:LastName><cdsd:Part>FAKE-Chart</cdsd:Part>"
                + "<cdsd:PartType>FAMC</cdsd:PartType></cdsd:LastName></cdsd:LegalName></Names>"
                + "<DateOfBirth>1971-03-04</DateOfBirth><Gender>F</Gender>"
                + "<UniqueVendorIdSequence>"
                + sender
                + "</UniqueVendorIdSequence><PersonStatusCode><PersonStatusAsEnum>A"
                + "</PersonStatusAsEnum></PersonStatusCode></Demographics>";
    }

    private static String appointment() {
        return "<Appointments><AppointmentTime>09:00:00</AppointmentTime><Duration>15</Duration>"
                + "<AppointmentDate><cdsd:FullDate>2026-01-02</cdsd:FullDate></AppointmentDate>"
                + "</Appointments>";
    }

    /** The merged chart's own standalone file. */
    private static String member() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><OmdCds xmlns=\"cds\" xmlns:cds=\"cds\""
                + " xmlns:cdsd=\"cds_dt\"><PatientRecord>"
                + demographics("C34").replace('\'', '"')
                + "</PatientRecord></OmdCds>";
    }

    private static TreeMap<CdsCategory, CdsMergedChartContract.Range> range(long first, long count) {
        var positions = new TreeMap<CdsCategory, CdsMergedChartContract.Range>();
        positions.put(CdsCategory.APPOINTMENTS, new CdsMergedChartContract.Range(first, count));
        return positions;
    }

    private static String chart(
            int chart,
            int head,
            TreeMap<CdsCategory, CdsMergedChartContract.Range> positions,
            String document) {
        return record(
                CdsMergedChartContract.CHART_RECORD,
                CdsMergedChartContract.json(
                        new CdsMergedChartContract.ChartRecord(
                                chart,
                                head,
                                positions,
                                document,
                                CdsMergedChartContract.sha256(
                                        document.getBytes(StandardCharsets.UTF_8)))));
    }

    private static String links() {
        return links(List.of(HEAD, MERGED));
    }

    private static String links(List<Integer> charts) {
        return record(
                CdsMergedChartContract.LINKS_RECORD,
                CdsMergedChartContract.json(
                        new CdsMergedChartContract.LinksRecord(
                                charts,
                                List.of(
                                        new CdsMergedChartContract.LinkRow(
                                                "5", "34", "12", "0", "999998", "2026-01-02", false)),
                                List.of(),
                                new TreeSet<>(),
                                new TreeSet<>())));
    }

    /** A NewCategory record whose string data element holds the JSON, as a CARLOS sender writes it. */
    private static String record(String name, String json) {
        String token = json.replace(" ", "\\u0020");
        String content =
                token.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<NewCategory><CategoryName>"
                + name
                + "</CategoryName><CategoryDescription>CARLOS record</CategoryDescription>"
                + "<ResidualInfo><cdsd:DataElement><cdsd:Name>"
                + name
                + "</cdsd:Name><cdsd:DataType>string</cdsd:DataType><cdsd:Content>"
                + content
                + "</cdsd:Content></cdsd:DataElement></ResidualInfo></NewCategory>";
    }
}
