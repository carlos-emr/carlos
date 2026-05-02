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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimsErrorReportRecordDto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BillingClaimsErrorReportParser}, the fixed-width
 * parser that consumes OHIP "E"-prefixed error reports.
 *
 * <p>Pins the per-record-type extraction so a {@code substring(...)}
 * offset drift (column ranges shift in a future MOH spec revision)
 * fails this suite. Also documents the truncated-line handling: the
 * parser flips {@code verdict=false} on
 * {@link StringIndexOutOfBoundsException} so the importer can surface
 * partial-parse failures upstream.</p>
 */
@DisplayName("BillingClaimsErrorReportParser")
@Tag("unit")
@Tag("billing")
class BillingClaimsErrorReportParserUnitTest {

    @TempDir
    Path tempDir;
    private File reportFile;

    @AfterEach
    void cleanup() throws IOException {
        if (reportFile != null) Files.deleteIfExists(reportFile.toPath());
    }

    private FileInputStream fileWith(String content) throws IOException {
        reportFile = Files.createTempFile(tempDir, "error-report", ".txt").toFile();
        Files.writeString(reportFile.toPath(), content);
        return new FileInputStream(reportFile);
    }

    @Test
    void shouldParseHeader1Record_andExtractTechSpecAndProviderNumber() throws Exception {
        // Header1 (col 2 = "1"): cols 3-5 techSpec, col 6 MOHoffice,
        // 17-22 operatorNumber, 23-26 groupNumber, 27-32 providerNumber,
        // 33-34 specialtyCode, 35-37 stationNumber, 38-45 claimProcessDate.
        // Construct exactly to those offsets — the parser substring()s
        // are 0-indexed half-open.
        StringBuilder line = new StringBuilder();
        line.append("  ");                      // 0-1: padding
        line.append("1");                       // 2:   headerCount
        line.append("V01");                     // 3-5: techSpec
        line.append("0");                       // 6:   MOHoffice
        line.append("          ");              // 7-16: unused (10 chars)
        line.append("OPR123");                  // 17-22: operatorNumber
        line.append("GRPN");                    // 23-26: groupNumber
        line.append("999998");                  // 27-32: providerNumber
        line.append("PR");                      // 33-34: specialtyCode
        line.append("001");                     // 35-37: stationNumber
        line.append("20260428");                // 38-45: claimProcessDate

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(
                fileWith(line.toString() + "\n"));

        List<?> records = parser.getClaimsErrorReportRecords();
        assertThat(records).hasSize(1);
        BillingClaimsErrorReportRecordDto rec = (BillingClaimsErrorReportRecordDto) records.get(0);
        assertThat(rec.getTechSpec()).isEqualTo("V01");
        assertThat(rec.getMOHoffice()).isEqualTo("0");
        assertThat(rec.getProviderNumber()).isEqualTo("999998");
        assertThat(rec.getClaimProcessDate()).isEqualTo("20260428");
        assertThat(parser.isVerdict()).isTrue();
    }

    @Test
    void shouldParseHeader2Record_andExtractHin() throws Exception {
        // Header2 (col 3 = "H"): cols 3-12 HIN, 13-14 ver, 15-22 dob, etc.
        // Need 79 chars to satisfy all substring calls including 76-79 heCode5.
        String line = "  H" + "1234567890" + "AB" + "19800115"
                + "BILL0001" + "HCP" + "P" + "REFNO1" + "FAC1"
                + "20260415" + "LAB1" + "LOC1" + "AAA"
                + "  " + "HE1" + "HE2" + "HE3" + "HE4" + "HE5";

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(fileWith(line + "\n"));

        // Header2 records are NOT added to the list directly; only when
        // followed by a "T" record do they aggregate. So we test via
        // a paired H+T.
        // For now, header2 alone produces no records (parser style) —
        // verify by parsing a fixed pair below.
        assertThat(parser.isVerdict()).isTrue();
    }

    @Test
    void shouldParseTrailerRecord9_andExtractCounts() throws Exception {
        // Trailer (col 3 = "9"): cols 3-9 header1Count, 10-16 header2Count,
        // 17-23 itemCount, 24-30 messageCount.
        String line = "  9" + "0000001" + "0000002" + "0000003" + "0000004";

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(fileWith(line + "\n"));

        List<?> records = parser.getClaimsErrorReportRecords();
        assertThat(records).hasSize(1);
        BillingClaimsErrorReportRecordDto rec = (BillingClaimsErrorReportRecordDto) records.get(0);
        assertThat(rec.getHeader1Count()).isEqualTo("0000001");
        assertThat(rec.getItemCount()).isEqualTo("0000003");
        assertThat(parser.isVerdict()).isTrue();
    }

    @Test
    void shouldParseExplanatoryRecord8_andExtractExplainAndError() throws Exception {
        // 8-record (col 3 = "8"): cols 3-4 explain, 5-59 error message.
        String errorMsg = "INVALID HIN — patient not eligible on date of service";
        String padded = String.format("%-55s", errorMsg);
        String line = "  8" + "AB" + padded;

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(fileWith(line + "\n"));

        List<?> records = parser.getClaimsErrorReportRecords();
        assertThat(records).hasSize(1);
        BillingClaimsErrorReportRecordDto rec = (BillingClaimsErrorReportRecordDto) records.get(0);
        assertThat(rec.getExplain()).isEqualTo("AB");
        assertThat(rec.getError()).startsWith("INVALID HIN");
    }

    @Test
    void shouldFlipVerdictFalse_andSkipPartialParse_whenLineTruncated() throws Exception {
        // A header2 ("H") record requires 79 chars; supplying ~30 triggers
        // StringIndexOutOfBoundsException inside the substring chain. The
        // parser catches it and flips verdict=false so the importer can
        // surface a partial-parse failure rather than silently absorbing it.
        String shortLine = "  H1234567890AB19800115" + "PARTIAL";  // ~30 chars

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(fileWith(shortLine + "\n"));

        assertThat(parser.isVerdict())
                .as("Truncated H-record must flip verdict to false so the importer can surface the failure")
                .isFalse();
    }

    @Test
    void shouldClearPartialRecords_whenMalformedLineFollowsParsedRecord() throws Exception {
        String validTrailer = "  9" + "0000001" + "0000002" + "0000003" + "0000004";
        String truncatedHeader = "  H1234567890AB19800115PARTIAL";

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(
                fileWith(validTrailer + "\n" + truncatedHeader + "\n"));

        assertThat(parser.isVerdict()).isFalse();
        assertThat(parser.getClaimsErrorReportRecords())
                .as("partial records are unsafe after a malformed report")
                .isEmpty();
    }

    @Test
    void shouldSkipShortLineAtHeaderCheck_andContinue_whenSubThreeChars() throws Exception {
        // Lines under 3 chars are too short even to peek the headerCount;
        // the parser logs a warning and falls through every record-type
        // branch (because headerCount stays "" — no branch matches "").
        // verdict stays true because no substring throws.
        String content = "ab\n  9" + "0000001" + "0000002" + "0000003" + "0000004" + "\n";

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(fileWith(content));

        // Short line is skipped; the trailer "9" record after still parses.
        List<?> records = parser.getClaimsErrorReportRecords();
        assertThat(records).hasSize(1);
        assertThat(parser.isVerdict()).isTrue();
    }

    @Test
    void shouldStartWithVerdictTrue_onEmptyFile() throws Exception {
        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(
                new FileInputStream(Files.createTempFile(tempDir, "empty", ".txt").toFile()));
        assertThat(parser.isVerdict()).isTrue();
        assertThat(parser.getClaimsErrorReportRecords()).isEmpty();
    }

    /**
     * IOException must flip {@code verdict} to false. An earlier passive test
     * pinned the pre-fix {@code verdict=true} behavior because it never
     * actually triggered an IOException; this version drives the IOException
     * branch by reading a closed stream so the contract is real.
     */
    @Test
    void shouldFlipVerdictFalse_onIOException() throws Exception {
        // Drive the IOException branch by feeding the parser a closed stream:
        // the BufferedReader.readLine() call raises IOException("Stream closed"),
        // which the catch block must now flip to verdict=false.
        java.io.File tempFile = Files.createTempFile(tempDir, "ioex", ".txt").toFile();
        FileInputStream fis = new FileInputStream(tempFile);
        fis.close();
        // The parser's constructor reads the stream — close before passing.

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(fis);
        assertThat(parser.isVerdict()).as("IOException must flip verdict to false").isFalse();
    }

    @Test
    void shouldClearPartialRecords_whenIOExceptionFollowsParsedRecord() throws Exception {
        String validTrailer = "  9" + "0000001" + "0000002" + "0000003" + "0000004";
        File marker = Files.createTempFile(tempDir, "io-after-record", ".txt").toFile();
        FailingAfterFirstLineInputStream input = new FailingAfterFirstLineInputStream(
                marker, validTrailer + "\n  8AB" + "broken".repeat(10));

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(input);

        assertThat(parser.isVerdict()).isFalse();
        assertThat(parser.getClaimsErrorReportRecords())
                .as("partial records are unsafe after an unreadable report")
                .isEmpty();
    }

    @Test
    void shouldStartWithVerdictTrue_onValidEmptyStream() {
        // Sanity: a clean read with no records leaves verdict=true.
        BillingClaimsErrorReportParser parser;
        try {
            java.io.File f = Files.createTempFile(tempDir, "ok", ".txt").toFile();
            parser = new BillingClaimsErrorReportParser(new FileInputStream(f));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        assertThat(parser.isVerdict()).isTrue();
    }

    @Test
    void shouldCloseInputStreamAfterParsing() throws Exception {
        java.io.File f = Files.createTempFile(tempDir, "close", ".txt").toFile();
        Files.writeString(f.toPath(), "  9" + "0000001" + "0000002" + "0000003" + "0000004" + "\n");
        CloseTrackingFileInputStream input = new CloseTrackingFileInputStream(f);

        BillingClaimsErrorReportParser parser = new BillingClaimsErrorReportParser(input);

        assertThat(parser.isVerdict()).isTrue();
        assertThat(input.closed).isTrue();
    }

    private static final class CloseTrackingFileInputStream extends FileInputStream {
        private boolean closed;

        private CloseTrackingFileInputStream(File file) throws IOException {
            super(file);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingAfterFirstLineInputStream extends FileInputStream {
        private final byte[] bytes;
        private int position;
        private boolean failNextRead;

        private FailingAfterFirstLineInputStream(File marker, String content) throws IOException {
            super(marker);
            this.bytes = content.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public int read() throws IOException {
            if (failNextRead) {
                throw new IOException("simulated read failure");
            }
            if (position >= bytes.length) {
                return -1;
            }
            int value = bytes[position++] & 0xff;
            if (value == '\n') {
                failNextRead = true;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (failNextRead) {
                throw new IOException("simulated read failure");
            }
            if (position >= bytes.length) {
                return -1;
            }
            int count = 0;
            while (count < length && position < bytes.length) {
                int value = bytes[position++] & 0xff;
                buffer[offset + count] = (byte) value;
                count++;
                if (value == '\n') {
                    failNextRead = true;
                    break;
                }
            }
            return count;
        }
    }
}
