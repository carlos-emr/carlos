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

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link BillingOnCorrectionPersister} (renamed from
 * {@code BillingOnCorrectionPersistenceService} as part of PR #1967's
 * layer-names hypocrisy fix). Covers the high-value mutation paths:
 * status updates, total/paid money writes, and item-mutation flow.
 */
@DisplayName("BillingOnCorrectionPersister")
@Tag("unit")
@Tag("billing")
class BillingOnCorrectionPersisterUnitTest {

    private BillingONCHeader1Dao headerDao;
    private BillingONItemDao itemDao;
    private BillingONEAReportDao eaReportDao;
    private RaDetailDao raDetailDao;
    private BillingOnItemPaymentDao itemPaymentDao;
    private BillingONExtDao extDao;
    private BillingOnTransactionDao transDao;
    private BillingOnAuditLogService auditLog;
    private BillingOnCorrectionPersister persister;

    @BeforeEach
    void setUp() {
        headerDao = mock(BillingONCHeader1Dao.class);
        itemDao = mock(BillingONItemDao.class);
        eaReportDao = mock(BillingONEAReportDao.class);
        raDetailDao = mock(RaDetailDao.class);
        itemPaymentDao = mock(BillingOnItemPaymentDao.class);
        extDao = mock(BillingONExtDao.class);
        transDao = mock(BillingOnTransactionDao.class);
        auditLog = mock(BillingOnAuditLogService.class);
        persister = new BillingOnCorrectionPersister(headerDao, itemDao, eaReportDao,
                raDetailDao, itemPaymentDao, extDao, transDao, auditLog);
    }

    @Test
    void shouldUpdateStatusAndEmitAuditLog_whenHeaderFound() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setStatus("O");
        when(headerDao.find(Integer.valueOf(42))).thenReturn(header);
        when(itemDao.getBillingItemByCh1Id(42))
                .thenReturn(Collections.emptyList());

        boolean ret = persister.updateBillingStatus("42", "S", "999998");

        assertThat(ret).isTrue();
        assertThat(header.getStatus()).isEqualTo("S");
        verify(headerDao).merge(header);
        verify(auditLog).addBillingLog("999998", "updateBillingStatus", "", "42");
        verify(auditLog).addBillingLog("999998", "updateBillingStatus-items", "", "42");
    }

    @Test
    void shouldReturnFalseAndSkipMutation_whenHeaderNotFoundForStatusUpdate() {
        when(headerDao.find(Integer.valueOf(42))).thenReturn(null);

        boolean ret = persister.updateBillingStatus("42", "S", "999998");

        assertThat(ret).isFalse();
        verify(headerDao, never()).merge(any(BillingONCHeader1.class));
        verify(auditLog, never()).addBillingLog(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldCascadeStatusToItems_whenUpdateBillingStatus() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setStatus("O");
        BillingONItem item1 = new BillingONItem();
        item1.setStatus("O");
        BillingONItem item2 = new BillingONItem();
        item2.setStatus("O");
        when(headerDao.find(Integer.valueOf(42))).thenReturn(header);
        when(itemDao.getBillingItemByCh1Id(42))
                .thenReturn(List.of(item1, item2));

        persister.updateBillingStatus("42", "D", "999998");

        assertThat(item1.getStatus()).isEqualTo("D");
        assertThat(item2.getStatus()).isEqualTo("D");
    }

    @Test
    void shouldReturnTrueAndSetTotal_whenUpdateBillingTotalFindsHeader() {
        BillingONCHeader1 header = new BillingONCHeader1();
        // The persister calls the find(Object) overload here (string id),
        // not the find(int) variant — match the call shape exactly.
        when(headerDao.find(eq("42"))).thenReturn(header);

        boolean ret = persister.updateBillingTotal("33.70", "42");

        assertThat(ret).isTrue();
        assertThat(header.getTotal()).isEqualByComparingTo("33.70");
        verify(headerDao).merge(header);
    }

    @Test
    void shouldReturnFalseAndNotMerge_whenUpdateBillingTotalHeaderMissing() {
        when(headerDao.find(eq("42"))).thenReturn(null);

        boolean ret = persister.updateBillingTotal("33.70", "42");

        assertThat(ret).isFalse();
        verify(headerDao, never()).merge(any(BillingONCHeader1.class));
    }

    @Test
    void shouldReturnTrueAndSetPaid_whenUpdateBillingPaidFindsHeader() {
        BillingONCHeader1 header = new BillingONCHeader1();
        when(headerDao.find(eq("42"))).thenReturn(header);

        boolean ret = persister.updateBillingPaid("25.00", "42");

        assertThat(ret).isTrue();
        assertThat(header.getPaid()).isEqualByComparingTo("25.00");
        verify(headerDao).merge(header);
    }

    @Test
    void shouldReturnFormattedTotal_whenGetBillingTotalFindsHeader() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setTotal(new BigDecimal("75.50"));
        when(headerDao.find(eq("42"))).thenReturn(header);

        String total = persister.getBillingTotal("42");

        assertThat(total).isEqualTo("75.5");
    }

    @Test
    void shouldReturnEmptyString_whenGetBillingTotalHeaderMissing() {
        when(headerDao.find(eq("42"))).thenReturn(null);

        String total = persister.getBillingTotal("42");

        assertThat(total).isEmpty();
    }

    @Test
    void shouldReturnFormattedPaid_whenGetBillingPaidFindsHeader() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setPaid(new BigDecimal("12.50"));
        when(headerDao.find(eq("42"))).thenReturn(header);

        String paid = persister.getBillingPaid("42");

        assertThat(paid).isEqualTo("12.5");
    }
}
