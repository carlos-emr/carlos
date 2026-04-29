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
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavioral tests for {@link BillingCorrectionRecordService}, the
 * orchestration service behind the correction-page workflow. The full
 * surface is large (836 LOC, 18 public methods, request-driven branches);
 * this suite covers the high-leverage shapes:
 *
 * <ul>
 *   <li>Read-only delegation methods (loader passthroughs)</li>
 *   <li>{@link BillingCorrectionRecordService#deleteBilling} — the
 *       cascade-status-to-items mutation</li>
 *   <li>{@link BillingCorrectionRecordService#getBillingNoStatusByAppt}
 *       and sibling query methods</li>
 * </ul>
 *
 * <p>The complex {@code updateBillingClaimHeader} / {@code updateBillingItem}
 * paths require an HttpServletRequest fixture with 20+ parameters and
 * are exercised end-to-end via {@code BillingCorrectionServiceIntegrationTest}.</p>
 */
@DisplayName("BillingCorrectionRecordService")
@Tag("unit")
@Tag("billing")
class BillingCorrectionRecordServiceUnitTest {

    private BillingOnCorrectionPersister correctionPersister;
    private BillingONCHeader1Dao cheader1Dao;
    private BillingONItemDao billOnItemDao;
    private BillingONExtDao billOnExtDao;
    private BillingOnLookupService lookupService;
    private BillingThirdPartyService thirdPartyService;
    private ServiceCodeLoader serviceCodeLoader;
    private BillingOnClaimPersister claimPersister;
    private BillingOnClaimLoader claimLoader;
    private BillingOnTransactionDao billOnTransDao;
    private BillingOnItemPaymentDao billOnItemPaymentDao;
    private BillingCorrectionRecordService service;

    @BeforeEach
    void setUp() {
        correctionPersister = mock(BillingOnCorrectionPersister.class);
        cheader1Dao = mock(BillingONCHeader1Dao.class);
        billOnItemDao = mock(BillingONItemDao.class);
        billOnExtDao = mock(BillingONExtDao.class);
        lookupService = mock(BillingOnLookupService.class);
        thirdPartyService = mock(BillingThirdPartyService.class);
        serviceCodeLoader = mock(ServiceCodeLoader.class);
        claimPersister = mock(BillingOnClaimPersister.class);
        claimLoader = mock(BillingOnClaimLoader.class);
        billOnTransDao = mock(BillingOnTransactionDao.class);
        billOnItemPaymentDao = mock(BillingOnItemPaymentDao.class);
        service = newService();
    }

    private BillingCorrectionRecordService newService() {
        // The constructor is package-private; reflect to instantiate.
        try {
            java.lang.reflect.Constructor<BillingCorrectionRecordService> ctor =
                    BillingCorrectionRecordService.class.getDeclaredConstructor(
                            BillingOnCorrectionPersister.class,
                            BillingONCHeader1Dao.class,
                            BillingONItemDao.class,
                            BillingONExtDao.class,
                            BillingOnLookupService.class,
                            BillingThirdPartyService.class,
                            ServiceCodeLoader.class,
                            BillingOnClaimPersister.class,
                            BillingOnClaimLoader.class,
                            BillingOnTransactionDao.class,
                            BillingOnItemPaymentDao.class);
            ctor.setAccessible(true);
            return ctor.newInstance(correctionPersister, cheader1Dao, billOnItemDao,
                    billOnExtDao, lookupService, thirdPartyService, serviceCodeLoader,
                    claimPersister, claimLoader, billOnTransDao, billOnItemPaymentDao);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- delegation methods --------------------------------------------

    @Test
    void shouldDelegateGetBillingRecordObj_toCorrectionPersister() {
        @SuppressWarnings("rawtypes")
        List stubbed = List.of("row");
        when(correctionPersister.getBillingRecordObj("42")).thenReturn(stubbed);

        assertThat(service.getBillingRecordObj("42")).isEqualTo(stubbed);
        verify(correctionPersister).getBillingRecordObj("42");
    }

    @Test
    void shouldDelegateGetBillingExplanatoryList_toCorrectionPersister() {
        @SuppressWarnings("rawtypes")
        List stubbed = Collections.emptyList();
        when(correctionPersister.getBillingExplanatoryList("42")).thenReturn(stubbed);

        assertThat(service.getBillingExplanatoryList("42")).isEqualTo(stubbed);
        verify(correctionPersister).getBillingExplanatoryList("42");
    }

    @Test
    void shouldDelegateGetBillingRejectList_toCorrectionPersister() {
        @SuppressWarnings("rawtypes")
        List stubbed = List.of("X1", "X2");
        when(correctionPersister.getBillingRejectList("42")).thenReturn(stubbed);

        assertThat(service.getBillingRejectList("42")).isEqualTo(stubbed);
        verify(correctionPersister).getBillingRejectList("42");
    }

    @Test
    void shouldDelegateGetBillingNoStatusByAppt_toCorrectionPersister() {
        List<String> stubbed = List.of("100,O");
        when(correctionPersister.getBillingCH1NoStatusByAppt("999")).thenReturn(stubbed);

        assertThat(service.getBillingNoStatusByAppt("999")).isEqualTo(stubbed);
    }

    @Test
    void shouldDelegateGetBillingNoStatusByBillNo_toCorrectionPersister() {
        @SuppressWarnings("rawtypes")
        List stubbed = List.of("100,O");
        when(correctionPersister.getBillingCH1NoStatusByBillNo("100")).thenReturn(stubbed);

        assertThat(service.getBillingNoStatusByBillNo("100")).isEqualTo(stubbed);
    }

    @Test
    void shouldDelegateGetFacilty_num_toLookupService() {
        @SuppressWarnings("rawtypes")
        List stubbed = List.of("FAC1", "FAC2");
        when(lookupService.getFacilty_num()).thenReturn(stubbed);

        assertThat(service.getFacilty_num()).isEqualTo(stubbed);
    }

    // ---- deleteBilling: cascade status to items ------------------------

    @Test
    void shouldCascadeStatusD_toItems_whenDeleteBillingWithDeleteStatus() {
        when(correctionPersister.updateBillingStatus("42", "D", "999998")).thenReturn(true);
        BillingONItem item1 = new BillingONItem();
        item1.setStatus("O");
        BillingONItem item2 = new BillingONItem();
        item2.setStatus("O");
        when(billOnItemDao.getBillingItemByCh1Id(42)).thenReturn(List.of(item1, item2));

        boolean ret = service.deleteBilling("42", "D", "999998");

        assertThat(ret).isTrue();
        assertThat(item1.getStatus()).isEqualTo("D");
        assertThat(item2.getStatus()).isEqualTo("D");
        verify(billOnItemDao, times(2)).merge(any(BillingONItem.class));
    }

    @Test
    void shouldNotCascadeStatusToItems_whenStatusIsNotDelete() {
        when(correctionPersister.updateBillingStatus(anyString(), anyString(), anyString()))
                .thenReturn(true);

        boolean ret = service.deleteBilling("42", "S", "999998");

        assertThat(ret).isTrue();
        // Non-D statuses do not iterate items.
        verify(billOnItemDao, never()).getBillingItemByCh1Id(org.mockito.ArgumentMatchers.anyInt());
        verify(billOnItemDao, never()).merge(any(BillingONItem.class));
    }

    @Test
    void shouldReturnFalse_whenDeleteBillingPersisterReturnsFalse() {
        when(correctionPersister.updateBillingStatus("42", "D", "999998")).thenReturn(false);
        // Even on a false return the cascade still runs because the legacy
        // contract does not gate on the persister's return value — pin the
        // behavior so a future caller change to gate-first surfaces here.
        when(billOnItemDao.getBillingItemByCh1Id(42)).thenReturn(Collections.emptyList());

        boolean ret = service.deleteBilling("42", "D", "999998");

        assertThat(ret).isFalse();
    }
}
