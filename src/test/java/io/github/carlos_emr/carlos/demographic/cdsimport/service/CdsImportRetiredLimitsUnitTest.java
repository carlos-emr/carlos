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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The import's former size, count and ratio ceilings are retired: a source past each of them is
 * imported, and the free-disk reserve is what stops a source its storage cannot hold. The retired
 * settings are set here to tiny values, so each assertion is made past the old number without
 * writing more than a few tens of MiB.
 */
@Tag("unit")
class CdsImportRetiredLimitsUnitTest {
    @TempDir Path temp;

    @Test
    void shouldIgnoreRetiredSizeSettings_andNameThemOnceWithoutTheirValues() {
        Properties settings = new Properties();
        settings.setProperty("cds.import.maxSourceBytes", "2048");
        settings.setProperty("cds.import.maxExpandedBytes", "4096");
        settings.setProperty("cds.import.maxEntries", "2");
        settings.setProperty("cds.import.maxBinaryBytes", "1024");
        // Accepted whatever its value: a malformed retired setting fails nothing either.
        settings.setProperty("cds.import.maxCompressionRatio", "not-a-ratio-7319");
        var configuration = new CdsImportConfigurationService(settings);
        try (var log = LogCapture.forLogger(CdsImportConfigurationService.class)) {
            CdsInputLimitsDto first = configuration.limits();
            CdsInputLimitsDto second = configuration.limits();
            assertEquals(new CdsImportConfigurationService(new Properties()).limits(), first);
            assertEquals(first, second);
            assertEquals(1, log.events().size(), "One WARN per process, not per import");
            String message = log.messages().getFirst();
            assertTrue(message.startsWith("CDS_IMPORT_RETIRED_SETTINGS_IGNORED"));
            for (String key : CdsImportConfigurationService.RETIRED_SETTINGS)
                assertTrue(message.contains(key), key);
            for (String value : new String[] {"2048", "4096", "1024", "not-a-ratio-7319"})
                assertFalse(message.contains(value), "A retired setting's value is never logged");
        }
    }

    @Test
    void shouldSnapshotArchive_pastTheRetiredEntrySizeAndRatioCeilings() throws Exception {
        var limits = retiredTiny().limits();
        Path source = Files.createDirectory(temp.resolve("source"));
        Path archive = source.resolve("export.zip");
        int notes = 300;
        long blankPage = 64L << 20;
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new ZipEntry("patient.xml"));
            zip.write(fixture().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (int i = 0; i < notes; i++) {
                zip.putNextEntry(new ZipEntry("notes/note-" + i + ".txt"));
                zip.write(("note " + i + "\n").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            // A blank scanned page compresses far past the retired 200:1 ratio.
            zip.putNextEntry(new ZipEntry("reports/blank-page.bmp"));
            zeros(zip, blankPage);
            zip.closeEntry();
        }
        long compressed = Files.size(archive);
        Path durable = Files.createDirectory(temp.resolve("durable"));
        CdsSnapshotDto snapshot =
                new CdsSnapshotService()
                        .snapshot(archive, source, durable, "accepted", limits, () -> false);
        assertEquals(notes + 2, snapshot.files(), "past the retired two-entry ceiling");
        assertEquals(1, snapshot.patientXmlFiles());
        assertTrue(snapshot.totalBytes() > blankPage, "past the retired byte ceilings");
        assertTrue(
                snapshot.totalBytes() / compressed > 200,
                "past the retired 200:1 compression ratio");
        assertTrue(Files.isRegularFile(durable.resolve("accepted/snapshot.seal")));
        var reloaded = new CdsSnapshotService().load(snapshot.root());
        assertEquals(snapshot.sha256(), reloaded.sha256());
        assertEquals(snapshot.totalBytes(), reloaded.totalBytes());
    }

    @Test
    void shouldStopDecompressionBomb_atTheDiskReserveWithoutPublishingAnything() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path archive = source.resolve("bomb.zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            zip.putNextEntry(new ZipEntry("bomb.bin"));
            zeros(zip, 128L << 20);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("patient.xml"));
            zip.write(fixture().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        byte[] original = Files.readAllBytes(archive);
        Path durable = Files.createDirectory(temp.resolve("durable"));
        // A 64 MiB store with a 32 MiB reserve: the 128 MiB member cannot fit.
        long capacity = 64L << 20, reserve = 32L << 20;
        var disk = new CdsSnapshotService(path -> capacity - used(durable));
        var limits = new CdsInputLimitsDto(256, 65536, reserve, 86_400_000L, 8192);
        CdsInputException stopped =
                assertThrows(
                        CdsInputException.class,
                        () -> disk.snapshot(archive, source, durable, "bomb", limits, () -> false));
        assertEquals("CDS_INPUT_DISK_RESERVE", stopped.code());
        assertFalse(Files.exists(durable.resolve("bomb")), "Nothing is published");
        assertFalse(Files.exists(durable.resolve("bomb.pending/snapshot.seal")), "Nothing sealed");
        assertTrue(
                used(durable) <= capacity - reserve + CdsSnapshotService.DISK_CHECK_BYTES + (1L << 20),
                "The expansion stopped at the reserve, not at the end of the member");
        assertArrayEquals(original, Files.readAllBytes(archive));
    }

    @Test
    void shouldCheckTheReserveBeforeTheFirstByte_leavingNothingWritten() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path xml = Files.writeString(source.resolve("patient.xml"), fixture());
        Path durable = Files.createDirectory(temp.resolve("durable"));
        var full = new CdsSnapshotService(path -> 0);
        CdsInputException stopped =
                assertThrows(
                        CdsInputException.class,
                        () ->
                                full.snapshot(
                                        xml,
                                        source,
                                        durable,
                                        "full",
                                        CdsInputLimitsDto.defaults(),
                                        () -> false));
        assertEquals("CDS_INPUT_DISK_RESERVE", stopped.code());
        try (Stream<Path> members = Files.list(durable.resolve("full.pending/members"))) {
            assertEquals(0, members.count(), "No member was started");
        }
        assertFalse(Files.exists(durable.resolve("full")));
        assertEquals(fixture(), Files.readString(xml));
    }

    @Test
    void shouldParseDocument_pastTheRetiredSourceBinaryAndSpoolCeilings() throws Exception {
        var limits = retiredTiny().limits();
        byte[] scan = new byte[3 << 20];
        new Random(5).nextBytes(scan);
        String document =
                fixture()
                        .replace(
                                "</PatientRecord>",
                                "<Reports><Format>Binary</Format><Content><cdsd:Media>"
                                        + Base64.getEncoder().encodeToString(scan)
                                        + "</cdsd:Media></Content>"
                                        + "<Class>Medical Records Report</Class></Reports>"
                                        + "</PatientRecord>");
        Path xml = Files.writeString(temp.resolve("patient.xml"), document);
        List<CdsRecordDto> records = new ArrayList<>();
        var parsed =
                new CdsXmlParser()
                        .parse(xml, temp.resolve("spool"), limits, () -> false, records::add);
        assertEquals(Files.size(xml), parsed.sourceBytes());
        assertTrue(parsed.sourceBytes() > 4096, "past the retired source ceiling");
        assertEquals(scan.length, parsed.decodedBinaryBytes(), "past the retired binary ceiling");
        assertTrue(parsed.valuesBytes() > 4096, "past the retired expanded ceiling");
        var report = records.getLast();
        assertEquals("Reports", report.section());
        var fields = new CdsFieldParser();
        List<io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto> binary =
                new ArrayList<>();
        fields.forEachField(
                report,
                field -> {
                    if (field.kind().equals("BINARY")) binary.add(field);
                });
        assertEquals(1, binary.size());
        try (InputStream decoded = fields.openValue(report, binary.getFirst())) {
            assertArrayEquals(scan, decoded.readAllBytes());
        }
    }

    /** A deployment that still sets every retired ceiling to a tiny value. */
    static CdsImportConfigurationService retiredTiny() {
        Properties settings = new Properties();
        settings.setProperty("cds.import.maxSourceBytes", "2048");
        settings.setProperty("cds.import.maxExpandedBytes", "4096");
        settings.setProperty("cds.import.maxEntries", "2");
        settings.setProperty("cds.import.maxBinaryBytes", "1024");
        settings.setProperty("cds.import.maxCompressionRatio", "1");
        settings.setProperty("cds.import.minimumFreeDiskBytes", "1");
        return new CdsImportConfigurationService(settings);
    }

    static String fixture() throws IOException {
        try (InputStream input =
                CdsImportRetiredLimitsUnitTest.class.getResourceAsStream(
                        "/demographic/cds-import-summary.xml")) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void zeros(OutputStream output, long bytes) throws IOException {
        byte[] block = new byte[1 << 20];
        for (long written = 0; written < bytes; written += block.length)
            output.write(block, 0, (int) Math.min(block.length, bytes - written));
    }

    private static long used(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            long total = 0;
            for (Path file : files.filter(Files::isRegularFile).toList()) total += Files.size(file);
            return total;
        }
    }
}
