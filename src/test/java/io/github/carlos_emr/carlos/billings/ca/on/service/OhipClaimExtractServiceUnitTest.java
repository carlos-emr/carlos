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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;

@DisplayName("OHIP OhipClaimExtractService")
@Tag("unit")
@Tag("billing")
class OhipClaimExtractServiceUnitTest {

    @Test
    void shouldCalculateTotalsWithoutBinaryFloatingPointRounding() {
        BillingDao billingDao = mock(BillingDao.class);
        BillingDetailDao billingDetailDao = mock(BillingDetailDao.class);
        Billing billing = mock(Billing.class);
        BillingDetail detail = mock(BillingDetail.class);

        when(billing.getId()).thenReturn(100);
        when(billing.getClinicNo()).thenReturn(0);
        when(billing.getDemographicName()).thenReturn("Jane Test");
        when(billing.getHin()).thenReturn("1234567890");
        when(billing.getDob()).thenReturn("19700101");
        when(billing.getVisitDate()).thenReturn(new java.util.Date());
        when(billing.getVisitType()).thenReturn("00");
        when(billing.getClinicRefCode()).thenReturn("");
        when(billing.getStatus()).thenReturn("O");
        when(billing.getContent()).thenReturn("");

        when(detail.getServiceCode()).thenReturn("A001A");
        when(detail.getBillingAmount()).thenReturn("1.005");
        when(detail.getDiagnosticCode()).thenReturn(":::");
        when(detail.getBillingUnit()).thenReturn("1");
        when(detail.getAppointmentDate()).thenReturn(new java.util.Date());

        when(billingDao.findByProviderStatusAndDates(eq("123456"), anyList(), any()))
                .thenReturn(List.of(billing));
        when(billingDetailDao.findByBillingNoAndStatus(100, "O"))
                .thenReturn(List.of(detail));

        OhipClaimExtractService extract = new OhipClaimExtractService(billingDao, billingDetailDao);
        extract.seteFlag("0");
        extract.setOhipVer("V03");
        extract.setProviderNo("123456");
        extract.setOhipCenter("T");
        extract.setGroupNo("0000");
        extract.setSpecialty("00");
        extract.setBatchCount("1");

        extract.dbQuery();

        assertThat(extract.getTotalAmount()).isEqualTo("0.0101");
    }
}
