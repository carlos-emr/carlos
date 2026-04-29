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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;

@DisplayName("BillingRALookupService")
@Tag("unit")
@Tag("billing")
class RADataUnitTest {

    @Test
    void shouldSumPaidAmountsWithBillingMoneyRounding() {
        BillingRALookupService raData = new BillingRALookupService(mock(RaDetailDao.class));
        ArrayList<HashMap<String, String>> rows = new ArrayList<>();
        rows.add(row("100", "A001", "1.005"));
        rows.add(row("101", "A002", "2.004"));

        assertThat(raData.getAmountPaid(rows)).isEqualTo("3.01");
    }

    @Test
    void shouldSumOnlyMatchingBillingAndServiceCodePaidAmounts() {
        BillingRALookupService raData = new BillingRALookupService(mock(RaDetailDao.class));
        ArrayList<HashMap<String, String>> rows = new ArrayList<>();
        rows.add(row("100", "A001", "1.005"));
        rows.add(row("100", "A002", "9.995"));
        rows.add(row("101", "A001", "9.995"));

        assertThat(raData.getAmountPaid(rows, "100", "A001")).isEqualTo("1.01");
    }

    private static HashMap<String, String> row(String billingNo, String serviceCode, String amountPay) {
        HashMap<String, String> row = new HashMap<>();
        row.put("billing_no", billingNo);
        row.put("service_code", serviceCode);
        row.put("amountpay", amountPay);
        return row;
    }
}
