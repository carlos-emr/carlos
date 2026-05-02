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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies BillingClaimBatchAcknowledgementReportParser flips
 * {@code verdict=false} on IOException so a torn read cannot masquerade
 * as a clean import.
 *
 * @since 2026-04-30
 */
@DisplayName("BillingClaimBatchAcknowledgementReportParser verdict")
@Tag("unit")
@Tag("billing")
class BillingClaimBatchAcknowledgementReportParserUnitTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFlipVerdictFalse_onIOException() throws Exception {
        // Drive the IOException catch by feeding a closed stream — the
        // parser's BufferedReader.readLine() will throw "Stream closed",
        // which must flip verdict to false so a partial read is observable.
        File f = Files.createTempFile(tempDir, "ioex", ".txt").toFile();
        FileInputStream fis = new FileInputStream(f);
        fis.close();

        BillingClaimBatchAcknowledgementReportParser parser =
                new BillingClaimBatchAcknowledgementReportParser(fis);

        assertThat(parser.verdict).as("IOException must flip verdict to false").isFalse();
    }

    @Test
    void shouldKeepVerdictTrue_onValidEmptyStream() throws Exception {
        File f = Files.createTempFile(tempDir, "ok", ".txt").toFile();
        BillingClaimBatchAcknowledgementReportParser parser =
                new BillingClaimBatchAcknowledgementReportParser(new FileInputStream(f));

        assertThat(parser.verdict).isTrue();
    }

    @Test
    void shouldClearParsedRecords_whenLaterLineIsMalformed() throws Exception {
        File f = Files.createTempFile(tempDir, "partial", ".txt").toFile();
        Files.writeString(f.toPath(), headerLine() + System.lineSeparator() + "AB1too-short");

        BillingClaimBatchAcknowledgementReportParser parser =
                new BillingClaimBatchAcknowledgementReportParser(new FileInputStream(f));

        assertThat(parser.verdict).isFalse();
        assertThat(parser.getBatchAcknowledgementRecords()).isEmpty();
    }

    private static String headerLine() {
        StringBuilder line = new StringBuilder(" ".repeat(111));
        replace(line, 0, "AB1");
        replace(line, 6, "BATCH");
        replace(line, 11, "OPER01");
        replace(line, 17, "20260428");
        replace(line, 25, "0001");
        replace(line, 29, "MICROSTART1");
        replace(line, 40, "END01");
        replace(line, 45, "TYPE001");
        replace(line, 52, "GRP1");
        replace(line, 56, "PRV001");
        replace(line, 62, "CLM01");
        replace(line, 67, "REC001");
        replace(line, 73, "20260429");
        replace(line, 81, "processed ok");
        return line.toString();
    }

    private static void replace(StringBuilder target, int start, String value) {
        target.replace(start, start + value.length(), value);
    }
}
