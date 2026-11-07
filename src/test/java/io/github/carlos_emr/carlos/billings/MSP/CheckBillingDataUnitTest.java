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

import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.billings.ca.bc.data.BcBillingRows.ExtractClaimRow;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirror of {@code billings.ca.bc.MSP.HtmlTeleplanHelperUnitTest}'s
 * validation-row coverage for the duplicate legacy copy of
 * {@code CheckBillingData}; both copies must keep the same encoding contract.
 */
@DisplayName("CheckBillingData (legacy MSP copy)")
@Tag("unit")
class CheckBillingDataUnitTest {

    @Test
    void shouldEncodeValidationRows_whenRenderingTeleplanErrors() {
        CheckBillingData checkData = new CheckBillingData();

        String errorHtml = checkData.printErrorMsg("1&2=3#4", "bad <message> & more");
        String warningHtml = checkData.printWarningMsg("warn <b>");

        assertThat(errorHtml)
                .contains(SafeEncode.forJavaScriptAttribute(SafeEncode.forUriComponent("1&2=3#4")))
                .contains(SafeEncode.forHtmlContent("bad <message> & more"))
                .doesNotContain("billingmaster_no=1&2=3#4")
                .doesNotContain("bad <message>");
        assertThat(warningHtml)
                .contains(SafeEncode.forHtmlContent("warn <b>"))
                .doesNotContain("warn <b>");
    }
    @Test
    void shouldValidateEveryTypedClaimField_whenAllInputsAreMissing() {
        var row = mock(ExtractClaimRow.class);
        var html = new CheckBillingData().checkC02("synthetic-7", row);
        assertThat(html).contains("C02:P00", "C02:P02", "C02:P06", "C02:P08", "C02:P14",
                "C02:P18", "C02:P20", "C02:P24", "C02:P26", "C02:P27", "C02:P30",
                "C02:P34", "C02:P36", "C02:P40", "Referral 1", "Referral 2", "C02:P52",
                "C02:P56", "C02:P60", "C02:P100", "C02:P104", "C02:P112");
        verify(row).billingmasterNo();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "-12.30", "12,30", "1e3", "NaN", "12.30<script>"})
    void shouldRefuseMalformedAmounts_whenTypedClaimContainsInvalidMoney(String amount) {
        var row = validClaim();
        when(row.billAmount()).thenReturn(amount);
        assertThat(new CheckBillingData().checkC02("synthetic-7", row))
                .contains("C02:P27 Billed Amount Wrong!").doesNotContain("<script>");
    }

    @Test
    void shouldRetainLegacyAcceptedClaim_whenTypedInputIsValid() {
        assertThat(new CheckBillingData().checkC02("synthetic-7", validClaim())).doesNotContain("C02:");
    }

    private ExtractClaimRow validClaim() {
        var row = mock(ExtractClaimRow.class);
        when(row.claimcode()).thenReturn("C02");
        when(row.datacenter()).thenReturn("12345");
        when(row.payeeNo()).thenReturn("12345");
        when(row.practitionerNo()).thenReturn("12345");
        when(row.phn()).thenReturn("");
        when(row.oinInsurerCode()).thenReturn("ON");
        when(row.dependentNum()).thenReturn("00");
        when(row.billingUnit()).thenReturn("1");
        when(row.afterHour()).thenReturn("0");
        when(row.billingCode()).thenReturn("00100");
        when(row.billAmount()).thenReturn("12.30");
        when(row.serviceDate()).thenReturn("20260801");
        when(row.submissionCode()).thenReturn("0");
        when(row.dxCode1()).thenReturn("123");
        when(row.serviceLocation()).thenReturn("O");
        when(row.referralFlag1()).thenReturn("0");
        when(row.referralNo1()).thenReturn("00000");
        when(row.referralFlag2()).thenReturn("");
        when(row.referralNo2()).thenReturn("");
        when(row.birthDate()).thenReturn("20000101");
        when(row.billingmasterNo()).thenReturn(7);
        when(row.correspondenceCode()).thenReturn("0");
        when(row.mvaClaimCode()).thenReturn("N");
        when(row.oinBirthdate()).thenReturn("20000101");
        when(row.oinSexCode()).thenReturn("F");
        return row;
    }

}
