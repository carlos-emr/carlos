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

import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct parser coverage for fixed-width OHIP RA description files. */
@DisplayName("RaDescriptionFileParser")
@Tag("unit")
@Tag("billing")
class RaDescriptionFileParserUnitTest {

    @TempDir private Path tempDir;

    private String previousDocumentDir;
    private final RaDescriptionFileParser parser = new RaDescriptionFileParser();

    @BeforeEach
    void setUp() {
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toAbsolutePath() + File.separator);
    }

    @AfterEach
    void tearDown() {
        if (previousDocumentDir != null) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        } else {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        }
    }

    @Test
    void shouldParseCompleteRaDescriptionFile_whenFileContainsAllRecordTypes() throws Exception {
        String filename = "ra-desc.txt";
        Files.writeString(tempDir.resolve(filename), String.join(System.lineSeparator(),
                h1Line("20260428", "000001234", " "),
                "H04",
                "H05",
                h6Line(),
                h7Line("50", "C", "claim <adjustment> & message"),
                h8Line("operator message")), StandardCharsets.ISO_8859_1);

        RaDescriptionFileParser.ParsedFile parsed = parser.parse(filename);

        assertThat(parsed.isCompleteForHeaderMerge()).isTrue();
        assertThat(parsed.paymentDate()).isEqualTo("20260428");
        assertThat(parsed.cheque()).isEqualTo("12.34");
        assertThat(parsed.recordCount()).isEqualTo(1);
        assertThat(parsed.claimCount()).isEqualTo(1);
        assertThat(parsed.balanceForwardRow().claimsAdjustment()).isEqualTo("0000001.111");
        assertThat(parsed.transactionRows()).singleElement().satisfies(row -> {
            assertThat(row.transaction()).isEqualTo("Accounting adjustment");
            assertThat(row.chequeIssued()).isEqualTo("Computer Cheque issued");
            assertThat(row.message()).contains("claim <adjustment> & message");
        });
        assertThat(parsed.messageTxt()).contains("operator message");
    }

    @Test
    void shouldFormatNegativeSignedMoney_whenRaDescriptionUsesSeparateSignFields() throws Exception {
        String filename = "ra-desc-negative.txt";
        Files.writeString(tempDir.resolve(filename), String.join(System.lineSeparator(),
                h1Line("20260428", "000001234", "-"),
                h6Line("-", "-", "-", "-"),
                h7Line("20", "C", "000123450", "-", "negative adjustment")), StandardCharsets.ISO_8859_1);

        RaDescriptionFileParser.ParsedFile parsed = parser.parse(filename);

        assertThat(parsed.isCompleteForHeaderMerge()).isTrue();
        assertThat(parsed.cheque()).isEqualTo("-12.34");
        assertThat(parsed.balanceForwardRow().claimsAdjustment()).isEqualTo("-0000001.111");
        assertThat(parsed.balanceForwardRow().advances()).isEqualTo("-0000002.222");
        assertThat(parsed.balanceForwardRow().reductions()).isEqualTo("-0000003.333");
        assertThat(parsed.balanceForwardRow().deductions()).isEqualTo("-0000004.444");
        assertThat(parsed.transactionRows()).singleElement().satisfies(row ->
                assertThat(row.amount()).isEqualTo("-000123.450"));
    }

    @Test
    void shouldEscapeXml_whenMergingParsedContent() throws Exception {
        String filename = "ra-desc-escape.txt";
        Files.writeString(tempDir.resolve(filename), String.join(System.lineSeparator(),
                h1Line("20260428", "000001234", " "),
                h7Line("50", "C", "unsafe <row> & \"quote\"")), StandardCharsets.ISO_8859_1);

        String merged = parser.mergedContent(parser.parse(filename),
                "<xml_local>1.00</xml_local><xml_other_total>2.00</xml_other_total>"
                        + "<xml_ob_total>3.00</xml_ob_total><xml_co_total>4.00</xml_co_total>");

        assertThat(merged)
                .contains("<xml_local>1.00</xml_local>")
                .contains("&lt;row&gt;")
                .contains("&amp;")
                .contains("&quot;quote&quot;");
    }

    @Test
    void shouldReportMissingFilename_whenFilenameBlank() {
        RaDescriptionFileParser.ParsedFile parsed = parser.parse("");

        assertThat(parsed.isCompleteForHeaderMerge()).isFalse();
        assertThat(parsed.parseFailureReason())
                .isEqualTo(RaDescriptionFileParser.ParseFailureReason.MISSING_FILENAME);
    }

    private static String h1Line(String paymentDate, String rawTotal, String status) {
        StringBuilder line = new StringBuilder(" ".repeat(77));
        replace(line, 0, "H");
        replace(line, 2, "1");
        replace(line, 21, paymentDate);
        replace(line, 59, rawTotal);
        replace(line, 68, status);
        return line.toString();
    }

    private static String h6Line() {
        return h6Line("", "", "", "");
    }

    private static String h6Line(String claimsSign, String advanceSign,
                                 String reductionSign, String deductionSign) {
        return "H06"
                + "0000001" + "111" + claimsSign
                + "0000002" + "222" + advanceSign
                + "0000003" + "333" + reductionSign
                + "0000004" + "444" + deductionSign;
    }

    private static String h7Line(String transactionCode, String chequeIndicator, String message) {
        return h7Line(transactionCode, chequeIndicator, "000123450", "", message);
    }

    private static String h7Line(String transactionCode, String chequeIndicator,
                                 String amount, String sign, String message) {
        return "H07" + transactionCode + chequeIndicator + "20260428" + amount + sign
                + String.format("%-50s", message);
    }

    private static String h8Line(String message) {
        return "H08" + String.format("%-70s", message);
    }

    private static void replace(StringBuilder target, int start, String value) {
        target.replace(start, start + value.length(), value);
    }
}
