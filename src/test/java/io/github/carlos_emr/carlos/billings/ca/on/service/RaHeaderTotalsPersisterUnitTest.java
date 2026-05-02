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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaSummaryViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit coverage for {@code RaHeaderTotalsPersister} RA header total recalculation and persistence rules. */
@DisplayName("RaHeaderTotalsPersister")
@Tag("unit")
@Tag("billing")
class RaHeaderTotalsPersisterUnitTest {

    @TempDir private Path tempDir;

    private String previousDocumentDir;

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
    void shouldMergeParsedDescriptionHeaderAndPopulatePremiums() throws Exception {
        String filename = "ra-desc-test.txt";
        Files.writeString(tempDir.resolve(filename), String.join(System.lineSeparator(),
                h1Line("20260428"),
                h6Line(),
                h7Line("<script>alert(1)</script> & text")), StandardCharsets.ISO_8859_1);

        RaHeaderDao raHeaderDao = mock(RaHeaderDao.class);
        BillingONPremiumDao premiumDao = mock(BillingONPremiumDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        RaHeader header = new RaHeader();
        header.setFilename(filename);
        header.setStatus("A");
        header.setContent("<xml_local>1.00</xml_local><xml_other_total>2.00</xml_other_total>"
                + "<xml_ob_total>3.00</xml_ob_total><xml_co_total>4.00</xml_co_total>");
        when(raHeaderDao.find((Object) 7)).thenReturn(header);
        when(raHeaderDao.findByFilenamePaymentDate(eq(filename), eq("20260428"))).thenReturn(List.of(header));
        when(premiumDao.getRAPremiumsByRaHeaderNo(7)).thenReturn(List.of());

        RaHeaderTotalsPersister persister = new RaHeaderTotalsPersister(
                raHeaderDao, premiumDao, new RaDescriptionFileParser());

        persister.refreshDescriptionHeaderAndPremiums(loggedInInfo, 7, Locale.CANADA);

        assertThat(header.getTotalAmount()).isEqualTo("12.34 ");
        assertThat(header.getRecords()).isEqualTo("0");
        assertThat(header.getClaims()).isEqualTo("0");
        assertThat(header.getContent())
                .contains("<xml_transaction>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; text")
                .contains("<xml_local>1.00</xml_local>")
                .contains("<xml_ob_total>3.00</xml_ob_total>")
                .doesNotContain("<table", "<tr>", "<tr ", "<td", "<script>");
        verify(raHeaderDao).merge(header);
        verify(premiumDao).parseAndSaveRAPremiums(loggedInInfo, 7, Locale.CANADA);
    }

    @Test
    void shouldSkipDescriptionMerge_whenH1TotalIsNonNumeric() throws Exception {
        String filename = "ra-h1-bad-total.txt";
        Files.writeString(tempDir.resolve(filename), h1LineWithRawTotal("ABCDEFGHI") + "\n",
                StandardCharsets.ISO_8859_1);

        RaHeaderDao raHeaderDao = mock(RaHeaderDao.class);
        BillingONPremiumDao premiumDao = mock(BillingONPremiumDao.class);
        RaHeader header = new RaHeader();
        header.setFilename(filename);
        header.setStatus("A");
        header.setContent("");
        when(raHeaderDao.find((Object) 9)).thenReturn(header);

        RaHeaderTotalsPersister persister = new RaHeaderTotalsPersister(
                raHeaderDao, premiumDao, new RaDescriptionFileParser());

        persister.refreshDescriptionHeaderAndPremiums(null, 9, Locale.CANADA);

        verify(raHeaderDao, never()).findByFilenamePaymentDate(anyString(), anyString());
        verify(raHeaderDao, never()).merge(any(RaHeader.class));
    }

    @Test
    void shouldMergeSummaryTotalsPreservingTransactionAndBalanceForwardXml() {
        RaHeaderDao raHeaderDao = mock(RaHeaderDao.class);
        BillingONPremiumDao premiumDao = mock(BillingONPremiumDao.class);
        RaHeader header = new RaHeader();
        header.setContent("<xml_transaction><row>existing</row></xml_transaction>"
                + "<xml_balancefwd><claimsAdjustment>1</claimsAdjustment></xml_balancefwd>"
                + "<xml_local>old</xml_local><xml_total>old</xml_total>");
        when(raHeaderDao.find((Object) 22)).thenReturn(header);

        GenerateRaSummaryViewModel model = GenerateRaSummaryViewModel.builder()
                .raNo("22")
                .paidTotal("100.00")
                .raHeaderLocalTotal("75.00")
                .otherPayTotal("25.00")
                .obTotal("10.00")
                .coTotal("5.00")
                .build();
        RaHeaderTotalsPersister persister = new RaHeaderTotalsPersister(
                raHeaderDao, premiumDao, new RaDescriptionFileParser());

        persister.mergeSummaryTotals(model);

        assertThat(header.getContent())
                .contains("<xml_transaction><row>existing</row></xml_transaction>")
                .contains("<xml_balancefwd><claimsAdjustment>1</claimsAdjustment></xml_balancefwd>")
                .contains("<xml_local>75.00</xml_local>")
                .contains("<xml_total>100.00</xml_total>")
                .contains("<xml_other_total>25.00</xml_other_total>")
                .contains("<xml_ob_total>10.00</xml_ob_total>")
                .contains("<xml_co_total>5.00</xml_co_total>");
        verify(raHeaderDao).merge(header);
    }

    @Test
    void shouldThrowValidation_whenSummaryTotalsModelIsNull() {
        RaHeaderTotalsPersister persister = new RaHeaderTotalsPersister(
                mock(RaHeaderDao.class), mock(BillingONPremiumDao.class), new RaDescriptionFileParser());

        assertThatThrownBy(() -> persister.mergeSummaryTotals(null))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("missing RA summary totals");
    }

    @Test
    void shouldThrowValidation_whenSummaryTotalsRaNoIsInvalid() {
        GenerateRaSummaryViewModel model = GenerateRaSummaryViewModel.builder()
                .raNo("not-a-number")
                .build();
        RaHeaderTotalsPersister persister = new RaHeaderTotalsPersister(
                mock(RaHeaderDao.class), mock(BillingONPremiumDao.class), new RaDescriptionFileParser());

        assertThatThrownBy(() -> persister.mergeSummaryTotals(model))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("invalid RA number");
    }

    @Test
    void shouldThrowValidation_whenSummaryTotalsRaHeaderIsMissing() {
        RaHeaderDao raHeaderDao = mock(RaHeaderDao.class);
        when(raHeaderDao.find((Object) 22)).thenReturn(null);
        GenerateRaSummaryViewModel model = GenerateRaSummaryViewModel.builder()
                .raNo("22")
                .build();
        RaHeaderTotalsPersister persister = new RaHeaderTotalsPersister(
                raHeaderDao, mock(BillingONPremiumDao.class), new RaDescriptionFileParser());

        assertThatThrownBy(() -> persister.mergeSummaryTotals(model))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("RA header not found")
                .hasMessageContaining("22");
    }

    private static String h1LineWithRawTotal(String rawTotal) {
        StringBuilder line = new StringBuilder(" ".repeat(77));
        replace(line, 0, "H");
        replace(line, 2, "1");
        replace(line, 21, "20260428");
        replace(line, 59, rawTotal);
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
