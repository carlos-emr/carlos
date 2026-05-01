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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.service.RaDescriptionFileParser;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaDescriptionViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GenerateRaDescriptionViewModelAssembler")
@Tag("unit")
@Tag("billing")
class GenerateRaDescriptionViewModelAssemblerUnitTest extends CarlosUnitTestBase {

    @TempDir private Path tempDir;

    @Mock private RaHeaderDao mockRaHeaderDao;
    @Mock private BillingONPremiumDao mockBillingONPremiumDao;
    @Mock private ProviderDao mockProviderDao;

    private AutoCloseable mockitoCloseable;
    private String previousDocumentDir;
    private RaDescriptionFileParser raDescriptionFileParser;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", tempDir.toAbsolutePath() + File.separator);
        raDescriptionFileParser = new RaDescriptionFileParser();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (previousDocumentDir != null) {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        } else {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        }
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldExposeRaBalanceAndTransactionsAsStructuredRows() throws Exception {
        String filename = "ra-desc-test.txt";
        Files.writeString(tempDir.resolve(filename), String.join(System.lineSeparator(),
                h1Line("20260428"),
                h6Line(),
                h7Line("<script>alert(1)</script> & text")), StandardCharsets.ISO_8859_1);

        RaHeader header = new RaHeader();
        header.setFilename(filename);
        header.setStatus("A");
        header.setContent("<xml_local>1.00</xml_local><xml_other_total>2.00</xml_other_total>"
                + "<xml_ob_total>3.00</xml_ob_total><xml_co_total>4.00</xml_co_total>");

        when(mockRaHeaderDao.find((Object) 7)).thenReturn(header);
        when(mockRaHeaderDao.findByFilenamePaymentDate(eq(filename), eq("20260428")))
                .thenReturn(List.of(header));
        when(mockBillingONPremiumDao.getRAPremiumsByRaHeaderNo(7)).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("rano", "7");

        GenerateRaDescriptionViewModel model = new GenerateRaDescriptionViewModelAssembler(
                mockRaHeaderDao, mockBillingONPremiumDao, mockProviderDao, raDescriptionFileParser)
                .assemble(request, null);

        assertThat(model.getBalanceForwardRow().claimsAdjustment()).isEqualTo("0000001.111");
        assertThat(model.getBalanceForwardRow().advances()).isEqualTo("0000002.222");
        assertThat(model.getBalanceForwardRow().reductions()).isEqualTo("0000003.333");
        assertThat(model.getBalanceForwardRow().deductions()).isEqualTo("0000004.444");

        assertThat(model.getTransactionRows()).singleElement().satisfies(row -> {
            assertThat(row.transaction()).isEqualTo("Accounting adjustment");
            assertThat(row.transactionDate()).isEqualTo("20260428");
            assertThat(row.chequeIssued()).isEqualTo("Computer Cheque issued");
            assertThat(row.amount()).isEqualTo("000123.450");
            assertThat(row.message()).contains("<script>alert(1)</script> & text");
            assertThat(row.message()).doesNotContain("<tr", "<td", "<table");
        });

        verify(mockRaHeaderDao, never()).merge(any(RaHeader.class));
    }

    @Test
    void shouldSkipRaHeaderMerge_whenH1TotalIsNonNumeric() throws Exception {
        // Build an H1 line with the cheque-total substring [59,68) set to a
        // non-numeric value. parseH1 catches the NumberFormatException and
        // leaves h1Parsed=false, so the assemble() merge MUST be skipped.
        String filename = "ra-h1-bad-total.txt";
        Files.writeString(tempDir.resolve(filename), h1LineWithRawTotal("ABCDEFGHI") + "\n",
                StandardCharsets.ISO_8859_1);

        RaHeader header = new RaHeader();
        header.setFilename(filename);
        header.setStatus("A");
        header.setContent("");
        when(mockRaHeaderDao.find((Object) 9)).thenReturn(header);
        when(mockBillingONPremiumDao.getRAPremiumsByRaHeaderNo(9)).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("rano", "9");

        new GenerateRaDescriptionViewModelAssembler(
                mockRaHeaderDao, mockBillingONPremiumDao, mockProviderDao, raDescriptionFileParser)
                .assemble(request, null);

        // CRITICAL: when H1 totals can't be parsed, the merge path that would
        // overwrite RaHeader.content with a fake "$0.00" cheque must be
        // skipped entirely.
        verify(mockRaHeaderDao, never()).findByFilenamePaymentDate(anyString(), anyString());
        verify(mockRaHeaderDao, never()).merge(any(RaHeader.class));
    }

    @Test
    void shouldSkipRaHeaderMerge_whenFileReadDoesNotComplete() throws Exception {
        // Point at a filename that doesn't exist — FileInputStream construction
        // throws, validatePath probably throws SecurityException first or the
        // try-with-resources fails to open. Either way, fileReadComplete stays
        // false and the merge must NOT run.
        String filename = "does-not-exist.txt";
        // (intentionally do NOT create the file)

        RaHeader header = new RaHeader();
        header.setFilename(filename);
        header.setStatus("A");
        header.setContent("");
        when(mockRaHeaderDao.find((Object) 11)).thenReturn(header);
        when(mockBillingONPremiumDao.getRAPremiumsByRaHeaderNo(11)).thenReturn(List.of());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("rano", "11");

        new GenerateRaDescriptionViewModelAssembler(
                mockRaHeaderDao, mockBillingONPremiumDao, mockProviderDao, raDescriptionFileParser)
                .assemble(request, null);

        verify(mockRaHeaderDao, never()).findByFilenamePaymentDate(anyString(), anyString());
        verify(mockRaHeaderDao, never()).merge(any(RaHeader.class));
    }

    /** Variant of h1Line() that lets a test inject any 9-char total slot. */
    private static String h1LineWithRawTotal(String rawTotal) {
        StringBuilder line = new StringBuilder(" ".repeat(77));
        replace(line, 0, "H");
        replace(line, 2, "1");
        replace(line, 21, "20260428");
        replace(line, 59, rawTotal);  // [59,68) — 9 chars
        replace(line, 68, " ");
        return line.toString();
    }

    private static String h1Line(String paymentDate) {
        StringBuilder line = new StringBuilder(" ".repeat(77));
        replace(line, 0, "H");
        replace(line, 2, "1");
        replace(line, 21, paymentDate);
        replace(line, 59, "000001234");
        replace(line, 68, " ");
        return line.toString();
    }

    private static String h6Line() {
        return "H06"
                + "0000001" + "111"
                + "0000002" + "222"
                + "0000003" + "333"
                + "0000004" + "444";
    }

    private static String h7Line(String message) {
        String paddedMessage = String.format("%-50s", message);
        return "H07" + "50" + "C" + "20260428" + "000123" + "450" + paddedMessage;
    }

    private static void replace(StringBuilder target, int start, String value) {
        target.replace(start, start + value.length(), value);
    }
}
