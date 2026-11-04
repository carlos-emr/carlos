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
package io.github.carlos_emr.carlos.billings.MSP;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.LogTeleplanTxDao;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BillingmasterDAO;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.ExtractClaimRow;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.ExtractBillRow;
import io.github.carlos_emr.carlos.billings.ca.bc.service.BcBillingService;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyInt;

/**
 * Issue #3950: the legacy BC extract ({@code billings.MSP.ExtractBean}) builds the same report HTML
 * as the current BC bean. Drives {@link ExtractBean#dbQuery()} in dry-run mode over detached DAO entities and checks every record-derived value in the header, both row shapes and the footer is
 * encoded.
 */
@DisplayName("ExtractBean (legacy MSP copy) report HTML encoding")
@Tag("unit")
@Tag("billing")
@Tag("security")
class ExtractBeanHtmlUnitTest extends CarlosUnitTestBase {

    private BillingmasterDAO billingmasterDao;

    private static final String SCRIPT_NAME = "<script>alert(1)</script>";

    @BeforeEach
    void registerExtractDaos() {
        createAndRegisterMock(LogTeleplanTxDao.class);
        createAndRegisterMock(BillingDao.class);
        billingmasterDao = createAndRegisterMock(BillingmasterDAO.class);
        var service = createAndRegisterMock(BcBillingService.class);
        doAnswer(call -> { ((ExtractBean) call.getArgument(0)).buildExtract(); return null; }).when(service).generateExtract(any());
    }

    @Test
    void shouldEncodeRecordValues_whenBuildingDryRunReport() throws Exception {
        ExtractBillRow bill = new ExtractBillRow(1001, SCRIPT_NAME);
        ExtractClaimRow claim = claimWithAmount("23.00");
        when(claim.phn()).thenReturn("<b>98</b>");
        when(claim.billingCode()).thenReturn("<u>01</u>");
        when(claim.serviceDate()).thenReturn("20260612");
        when(claim.dxCode1()).thenReturn("<i>");
        when(billingmasterDao.findBcExtractBills(anyString(), anyString(), any())).thenReturn(List.of(bill));
        when(billingmasterDao.findBcExtractClaims(1001)).thenReturn(List.of(claim, claim));
            ExtractBean bean = new ExtractBean();
            bean.seteFlag("0");
            bean.setProviderNo("<img src=x>");
            bean.dbQuery();

            String html = bean.getHtmlCode();
            assertThat(html)
                    .contains("Billing Invoice for Billing No." + SafeEncode.forHtmlContent("<img src=x>"))
                    .contains(SafeEncode.forHtmlContent(SCRIPT_NAME))
                    .contains(SafeEncode.forHtmlContent("<b>98</b>"))
                    .contains(SafeEncode.forHtmlContent("<u>01</u>"))
                    .contains("reprocessBill?billingmaster_no=0000042")
                    .doesNotContain("<script>")
                    .doesNotContain("<img")
                    .doesNotContain("<b>")
                    .doesNotContain("<u>")
                    .doesNotContain("<i>");
            // Second claim on the same invoice uses the continuation row: blank patient columns.
            assertThat(html).contains("<tr><td class='bodytext'></td><td class='bodytext'></td>");
            assertThat(html).endsWith("</table></body></html>");
    }

    private ExtractClaimRow claimWithAmount(String amount) {
        ExtractClaimRow claim = mock(ExtractClaimRow.class, call -> {
            if (call.getMethod().getReturnType() == String.class) return "0";
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        when(claim.billingmasterNo()).thenReturn(42);
        when(claim.billAmount()).thenReturn(amount);
        return claim;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n"})
    void shouldRejectMissingHtmlFilename_beforeReadingConfiguration(String filename) {
        ExtractBean bean = new ExtractBean();
        bean.setHtmlFilename(filename);
        assertThatThrownBy(() -> bean.writeHtml("<html/>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("HTML filename must be set before writing a Teleplan report");
    }

    @Test
    void shouldRejectBlankClaimFees_insteadOfSendingZeroValueClaims() {
        when(billingmasterDao.findBcExtractBills(anyString(), anyString(), any()))
                .thenReturn(List.of(new ExtractBillRow(1001, "FAKE")));
        ExtractClaimRow claim = claimWithAmount("       ");
        when(billingmasterDao.findBcExtractClaims(1001)).thenReturn(List.of(claim));
        ExtractBean bean = new ExtractBean();
        bean.seteFlag("0");
        bean.setProviderNo("91234");
        assertThatThrownBy(bean::dbQuery).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void shouldSkipNegativeInvoiceKeys_preservingTheLegacyNumericFilter() {
        when(billingmasterDao.findBcExtractBills(anyString(), anyString(), any()))
                .thenReturn(List.of(new ExtractBillRow(-1, "FAKE")));
        ExtractBean bean = new ExtractBean();
        bean.seteFlag("0");
        bean.setProviderNo("91234");
        bean.dbQuery();
        verify(billingmasterDao, never()).findBcExtractClaims(anyInt());
    }

}
