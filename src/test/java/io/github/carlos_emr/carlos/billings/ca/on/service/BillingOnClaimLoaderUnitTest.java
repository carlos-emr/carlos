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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link BillingOnClaimLoader}. Focuses on the
 * {@code getCodeFee} surface, including the Phase 2 silent-failure-fix
 * path that converted a context-less catch into a sanitized error log.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnClaimLoader")
@Tag("unit")
@Tag("billing")
class BillingOnClaimLoaderUnitTest {

    private ClinicLocationDao clinicLocationDao;
    private BillingONCHeader1Dao dao;
    private BillingONExtDao extDao;
    private BillingONPaymentDao payDao;
    private BillingServiceDao serviceDao;
    private BillingOnItemPaymentDao billOnItemPaymentDao;
    private BillingPercLimitDao percLimitDao;
    private BillingPaymentTypeDao paymentTypeDao;
    private ProviderDao providerDao;
    private BillingONItemDao itemDao;
    private CtlBillingServiceDao ctlBillingServiceDao;
    private BillingOnClaimLoader loader;

    @BeforeEach
    void setUp() {
        clinicLocationDao = mock(ClinicLocationDao.class);
        dao = mock(BillingONCHeader1Dao.class);
        extDao = mock(BillingONExtDao.class);
        payDao = mock(BillingONPaymentDao.class);
        serviceDao = mock(BillingServiceDao.class);
        billOnItemPaymentDao = mock(BillingOnItemPaymentDao.class);
        percLimitDao = mock(BillingPercLimitDao.class);
        paymentTypeDao = mock(BillingPaymentTypeDao.class);
        providerDao = mock(ProviderDao.class);
        itemDao = mock(BillingONItemDao.class);
        ctlBillingServiceDao = mock(CtlBillingServiceDao.class);
        loader = new BillingOnClaimLoader(clinicLocationDao, dao, extDao, payDao, serviceDao,
                billOnItemPaymentDao, percLimitDao, paymentTypeDao, providerDao, itemDao,
                ctlBillingServiceDao);
    }

    @Test
    void shouldReturnFeeValue_whenServiceCodeFoundAndNotDefunct() {
        BillingService bs = new BillingService();
        bs.setValue("75.00");
        // Termination date in the future: not defunct.
        bs.setTerminationDate(new Date(System.currentTimeMillis() + 86_400_000L));
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of(bs));

        String fee = loader.getCodeFee("A007", "2026-04-29");

        assertThat(fee).isEqualTo("75.00");
    }

    @Test
    void shouldReturnDefunct_whenTerminationDateIsBeforeServiceDate() {
        BillingService bs = new BillingService();
        bs.setValue("75.00");
        // Termination date in the distant past: defunct.
        bs.setTerminationDate(new Date(0));
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of(bs));

        String fee = loader.getCodeFee("A007", "2026-04-29");

        assertThat(fee).isEqualTo("defunct");
    }

    @Test
    void shouldReturnNull_whenNoServiceCodeMatches() {
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of());

        String fee = loader.getCodeFee("UNKNOWN", "2026-04-29");

        assertThat(fee).isNull();
    }

    @Test
    void shouldReturnNullAndLogContext_whenServiceDaoThrows() {
        // Phase 2 silent-failure-fix path: the catch now logs the offending
        // service code and date. The null-return contract is preserved so
        // existing callers that handle null don't change behavior.
        when(serviceDao.findByServiceCodeAndLatestDate(anyString(), any()))
                .thenThrow(new RuntimeException("DB outage simulation"));

        String fee = loader.getCodeFee("A007", "2026-04-29");

        assertThat(fee).isNull();
    }

    @Test
    void shouldReturnNull_whenDateStringUnparseable() {
        // ConversionUtils.fromDateString throws on bad input; loader catches
        // and returns null per the same contract.
        String fee = loader.getCodeFee("A007", "not-a-date");
        assertThat(fee).isNull();
    }
}
