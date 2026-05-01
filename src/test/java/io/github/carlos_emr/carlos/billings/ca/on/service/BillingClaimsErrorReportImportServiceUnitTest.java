/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingErrorReportDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Behavioral tests for {@link BillingClaimsErrorReportImportService}, the
 * fixed-width MOH claims-error-report parser. Covers the three line shapes
 * used during a successful parse (header "1", claim "H" + transaction "T",
 * footer "9") plus the malformed-input failure path which now throws
 * {@link BillingFileImportException} so the surrounding {@code @Transactional}
 * boundary rolls back every per-line write.
 */
@DisplayName("BillingClaimsErrorReportImportService")
@Tag("unit")
@Tag("billing")
class BillingClaimsErrorReportImportServiceUnitTest {

    @TempDir
    Path tempDir;

    private BillingOnErrorReportService erRepObj;
    private BillingClaimsErrorReportImportService svc;

    @BeforeEach
    void setUp() {
        erRepObj = mock(BillingOnErrorReportService.class);
        svc = new BillingClaimsErrorReportImportService(erRepObj);
    }

    @Test
    void shouldParseHeaderClaimAndTransaction_andPersistOneErrorReportRecord() throws IOException {
        // Build a minimal valid 3-line report: H1 header (provider/group)
        // → HE claim (HIN/billing-no) → HET transaction (service-code/fee).
        // Each line is 79 chars wide (the fixed-width spec the parser
        // unpacks substrings out of).
        String content = headerLine("1") + "\n" + claimLine() + "\n"
                + transactionLine() + "\n" + footerLine() + "\n";
        FileInputStream input = writeAndOpen(content);

        BillingClaimsErrorReportParser parser = svc.importStream(input, "test.err");

        assertThat(parser.isVerdict()).isTrue();
        // Per H line, the parser deletes any prior error report row.
        verify(erRepObj, atLeastOnce()).deleteErrorReport(org.mockito.ArgumentMatchers.any(BillingErrorReportDto.class));
        // Per T line, the parser persists the new error report row.
        verify(erRepObj, atLeastOnce()).addErrorReportRecord(org.mockito.ArgumentMatchers.any(BillingErrorReportDto.class));
    }

    @Test
    void shouldThrowBillingFileImportException_whenLineIsTooShortToSubstring() throws IOException {
        // Lines under the substring offsets the parser uses raise a
        // StringIndexOutOfBoundsException — pre-fix the catch silently set
        // verdict=false and KEPT every prior persist committed. The new
        // contract throws so Spring rolls back the entire batch.
        String content = "AB1" + repeat(' ', 80) + "\n"  // header passes
                + "ABH" + repeat(' ', 5) + "\n"           // claim: too short for substring(15, 23)
                + footerLine() + "\n";
        FileInputStream input = writeAndOpen(content);

        assertThatThrownBy(() -> svc.importStream(input, "malformed.err"))
                .isInstanceOf(BillingFileImportException.class)
                .hasMessageContaining("malformed.err");
    }

    @Test
    void shouldSkipShortLines_andContinueParsing_whenHeaderCountUnreadable() throws IOException {
        // Lines under 3 chars cannot yield a headerCount substring — the
        // parser logs and continues. The remaining valid lines must still
        // produce records.
        String content = "AB" + "\n"               // 2 chars - too short for headerCount
                + headerLine("1") + "\n"
                + claimLine() + "\n"
                + transactionLine() + "\n"
                + footerLine() + "\n";
        FileInputStream input = writeAndOpen(content);

        BillingClaimsErrorReportParser parser = svc.importStream(input, "mixed.err");

        assertThat(parser.isVerdict()).isTrue();
        verify(erRepObj, atLeastOnce()).addErrorReportRecord(org.mockito.ArgumentMatchers.any(BillingErrorReportDto.class));
    }

    @Test
    void shouldNotPersistAnything_whenInputIsEmpty() throws IOException {
        FileInputStream input = writeAndOpen("");

        BillingClaimsErrorReportParser parser = svc.importStream(input, "empty.err");

        assertThat(parser.isVerdict()).isTrue();
        verify(erRepObj, never()).addErrorReportRecord(org.mockito.ArgumentMatchers.any(BillingErrorReportDto.class));
        verify(erRepObj, never()).deleteErrorReport(org.mockito.ArgumentMatchers.any(BillingErrorReportDto.class));
    }

    @Test
    void shouldRecordExtractedFieldsOnTransactionLine() throws IOException {
        String content = headerLine("1") + "\n" + claimLine() + "\n"
                + transactionLine() + "\n" + footerLine() + "\n";
        FileInputStream input = writeAndOpen(content);

        BillingClaimsErrorReportParser parser = svc.importStream(input, "fields.err");

        assertThat(parser.getClaimsErrorReportRecords()).isNotEmpty();
        // Header "1", transaction "T", footer "9" each push a CERBean — the
        // claim "H" line mutates erObj but does NOT push a record. So the
        // expected count for this fixture is 3.
        assertThat(parser.getClaimsErrorReportRecords()).hasSize(3);
    }

    // ---- fixtures --------------------------------------------------------

    /** A "1" header line padded to 79 chars (parser substrings up to index 46). */
    private static String headerLine(String code) {
        // Layout — substrings used: [3,6) techSpec, [6,7) MOH office,
        // [17,23) operator, [23,27) group, [27,33) provider, [33,35) specialty,
        // [35,38) station, [38,46) claim-process-date.
        StringBuilder sb = new StringBuilder();
        sb.append("HE").append(code);            // 0..2: HE marker + code
        sb.append("V03");                          // 3..6: techSpec
        sb.append("G");                            // 6..7: MOH office
        sb.append(repeat(' ', 10));                // 7..17: pad
        sb.append("OP1234");                       // 17..23: operator
        sb.append("GRP1");                         // 23..27: group
        sb.append("PRV001");                       // 27..33: provider
        sb.append("00");                           // 33..35: specialty
        sb.append("ST1");                          // 35..38: station
        sb.append("20260101");                     // 38..46: claim-process-date
        sb.append(repeat(' ', 79 - sb.length()));
        return sb.toString();
    }

    private static String claimLine() {
        // Layout — substrings used: [3,13) HIN, [13,15) ver, [15,23) DOB,
        // [23,31) account, [31,34) billtype, [34,35) payee, [35,41) referNumber,
        // [41,45) facilityNumber, [45,53) admitDate, [53,57) referLab,
        // [57,61) location, [64,67) heCode1, [67,70) heCode2, [70,73) heCode3,
        // [73,76) heCode4, [76,79) heCode5.
        StringBuilder sb = new StringBuilder();
        sb.append("HEH");                          // 0..3: HE marker + H
        sb.append("9999999990");                   // 3..13: HIN
        sb.append("AB");                           // 13..15: ver
        sb.append("19800101");                     // 15..23: DOB
        sb.append("00012345");                     // 23..31: account
        sb.append("HCP");                          // 31..34: billtype
        sb.append("P");                            // 34..35: payee
        sb.append("REF001");                       // 35..41: referNumber
        sb.append("FAC1");                         // 41..45: facilityNumber
        sb.append("20260101");                     // 45..53: admitDate
        sb.append("LAB1");                         // 53..57: referLab
        sb.append("LOC1");                         // 57..61: location
        sb.append(repeat(' ', 3));                 // 61..64: pad
        sb.append("E01");                          // 64..67: heCode1
        sb.append("E02");                          // 67..70: heCode2
        sb.append("E03");                          // 70..73: heCode3
        sb.append("E04");                          // 73..76: heCode4
        sb.append("E05");                          // 76..79: heCode5
        return sb.toString();
    }

    private static String transactionLine() {
        // Layout — substrings used: [3,8) servicecode, [10,16) amountsubmit,
        // [16,18) serviceno, [18,26) servicedate, [26,30) dxcode,
        // [64,79) error codes.
        StringBuilder sb = new StringBuilder();
        sb.append("HET");                          // 0..3: HE marker + T
        sb.append("A001A");                        // 3..8: service code
        sb.append("  ");                           // 8..10: pad
        sb.append("003500");                       // 10..16: amount-submitted
        sb.append("01");                           // 16..18: service-no
        sb.append("20260101");                     // 18..26: service-date
        sb.append("V70");                          // 26..29: dx-code (3 chars)
        sb.append(' ');                            // 29..30: dx pad
        sb.append(repeat(' ', 34));                // 30..64
        sb.append("E11");                          // 64..67
        sb.append("E12");                          // 67..70
        sb.append("E13");                          // 70..73
        sb.append("E14");                          // 73..76
        sb.append("E15");                          // 76..79
        return sb.toString();
    }

    private static String footerLine() {
        // Layout — substrings used: [3,10) header1Count, [10,17) header2Count,
        // [17,24) itemCount, [24,31) messageCount.
        StringBuilder sb = new StringBuilder();
        sb.append("HE9");                          // 0..3: HE marker + 9
        sb.append("0000001");                      // 3..10
        sb.append("0000001");                      // 10..17
        sb.append("0000001");                      // 17..24
        sb.append("0000005");                      // 24..31
        sb.append(repeat(' ', 79 - sb.length()));
        return sb.toString();
    }

    private static String repeat(char c, int n) {
        char[] buf = new char[n];
        java.util.Arrays.fill(buf, c);
        return new String(buf);
    }

    private FileInputStream writeAndOpen(String content) throws IOException {
        File f = tempDir.resolve("err-" + System.nanoTime() + ".txt").toFile();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(content.getBytes());
        }
        return new FileInputStream(f);
    }
}
