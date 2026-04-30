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

import java.util.List;

import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingTypeDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDiagCodeDao;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.CtlBillingType;
import io.github.carlos_emr.carlos.commn.model.CtlDiagCode;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavior-pinning tests for {@link BillingFormConfigurationService}, the
 * round-4 atomic boundary covering 7 multi-write Manage Billing Form / Location
 * flows. The {@code @Transactional} rollback semantics themselves are
 * Spring-AOP-driven and can't be exercised without the proxy; what we DO
 * verify here:
 * <ul>
 *   <li>happy path: each method delegates to the right DAO with the right argument(s)</li>
 *   <li>mid-loop failure: an exception in step N propagates AND short-circuits steps N+1..</li>
 * </ul>
 *
 * <p>The propagation tests pin the contract that a future "swallow and
 * continue" change would re-introduce the partial-write bug round 4 fixed.
 */
@DisplayName("BillingFormConfigurationService")
@Tag("unit")
@Tag("billing")
class BillingFormConfigurationServiceUnitTest extends CarlosUnitTestBase {

    @Mock private CtlBillingServiceDao serviceDao;
    @Mock private CtlDiagCodeDao diagDao;
    @Mock private CtlBillingTypeDao typeDao;
    @Mock private CtlBillingServicePremiumDao premiumDao;
    @Mock private ClinicLocationDao locationDao;

    private BillingFormConfigurationService svc;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        svc = new BillingFormConfigurationService(serviceDao, diagDao, typeDao, premiumDao, locationDao);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    // ---- deleteServiceTypeAndCascade ------------------------------------

    @Test
    void shouldRemoveServicesDiagCodesAndType_whenDeleteServiceTypeAndCascade() {
        CtlBillingService svc1 = serviceWithId(11);
        CtlBillingService svc2 = serviceWithId(12);
        CtlDiagCode dx1 = diagWithId(21);
        when(serviceDao.findByServiceType("ABC")).thenReturn(List.of(svc1, svc2));
        when(diagDao.findByServiceType("ABC")).thenReturn(List.of(dx1));

        svc.deleteServiceTypeAndCascade("ABC");

        InOrder order = inOrder(serviceDao, diagDao, typeDao);
        order.verify(serviceDao).remove(11);
        order.verify(serviceDao).remove(12);
        order.verify(diagDao).remove(21);
        order.verify(typeDao).remove("ABC");
    }

    @Test
    void shouldPropagateAndStopBeforeBillingTypeRemove_whenDiagCodeRemoveThrows() {
        CtlBillingService svc1 = serviceWithId(11);
        CtlDiagCode dx1 = diagWithId(21);
        when(serviceDao.findByServiceType("ABC")).thenReturn(List.of(svc1));
        when(diagDao.findByServiceType("ABC")).thenReturn(List.of(dx1));
        doThrow(new RuntimeException("boom")).when(diagDao).remove(21);

        assertThatThrownBy(() -> svc.deleteServiceTypeAndCascade("ABC"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("boom");

        verify(serviceDao).remove(11);                  // first phase ran
        verify(typeDao, never()).remove(eq("ABC"));     // third phase never reached
    }

    // ---- replaceServiceCodes --------------------------------------------

    @Test
    void shouldDeleteExistingThenPersistReplacements_whenReplaceServiceCodes() {
        CtlBillingService old = serviceWithId(99);
        when(serviceDao.findByServiceType("ABC")).thenReturn(List.of(old));
        CtlBillingService rep1 = new CtlBillingService();
        CtlBillingService rep2 = new CtlBillingService();

        svc.replaceServiceCodes("ABC", List.of(rep1, rep2));

        InOrder order = inOrder(serviceDao);
        order.verify(serviceDao).remove(99);
        order.verify(serviceDao).persist(same(rep1));
        order.verify(serviceDao).persist(same(rep2));
    }

    @Test
    void shouldPropagate_whenServicePersistThrowsMidLoop() {
        when(serviceDao.findByServiceType("ABC")).thenReturn(List.of());
        CtlBillingService rep1 = new CtlBillingService();
        CtlBillingService rep2 = new CtlBillingService();
        doThrow(new RuntimeException("fail2")).when(serviceDao).persist(same(rep2));

        assertThatThrownBy(() -> svc.replaceServiceCodes("ABC", List.of(rep1, rep2)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("fail2");

        verify(serviceDao).persist(same(rep1)); // first persist ran before the throw
    }

    // ---- replaceDiagCodes -----------------------------------------------

    @Test
    void shouldDeleteExistingThenPersistReplacements_whenReplaceDiagCodes() {
        CtlDiagCode old = diagWithId(7);
        when(diagDao.findByServiceType("ABC")).thenReturn(List.of(old));
        CtlDiagCode rep = new CtlDiagCode();

        svc.replaceDiagCodes("ABC", List.of(rep));

        InOrder order = inOrder(diagDao);
        order.verify(diagDao).remove(7);
        order.verify(diagDao).persist(same(rep));
    }

    // ---- addPremiumServiceCodes -----------------------------------------

    @Test
    void shouldPersistEachPremium_whenAddPremiumServiceCodes() {
        CtlBillingServicePremium p1 = new CtlBillingServicePremium();
        CtlBillingServicePremium p2 = new CtlBillingServicePremium();

        svc.addPremiumServiceCodes(List.of(p1, p2));

        verify(premiumDao).persist(same(p1));
        verify(premiumDao).persist(same(p2));
    }

    // ---- removePremiumServiceCodes --------------------------------------

    @Test
    void shouldRemoveAllPremiumRowsAcrossCodes_whenRemovePremiumServiceCodes() {
        CtlBillingServicePremium hit1 = premiumWithId(31);
        CtlBillingServicePremium hit2 = premiumWithId(32);
        when(premiumDao.findByServiceCode("X007A")).thenReturn(List.of(hit1));
        when(premiumDao.findByServiceCode("Y008B")).thenReturn(List.of(hit2));

        svc.removePremiumServiceCodes(List.of("X007A", "Y008B"));

        verify(premiumDao).remove(31);
        verify(premiumDao).remove(32);
    }

    @Test
    void shouldPropagate_whenSecondCodeRemoveThrows() {
        CtlBillingServicePremium hit1 = premiumWithId(31);
        CtlBillingServicePremium hit2 = premiumWithId(32);
        when(premiumDao.findByServiceCode("X007A")).thenReturn(List.of(hit1));
        when(premiumDao.findByServiceCode("Y008B")).thenReturn(List.of(hit2));
        doThrow(new RuntimeException("fail32")).when(premiumDao).remove(32);

        assertThatThrownBy(() -> svc.removePremiumServiceCodes(List.of("X007A", "Y008B")))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("fail32");

        verify(premiumDao).remove(31);
    }

    // ---- addBillingForm -------------------------------------------------

    @Test
    void shouldPersistServicesSeedDiagAndType_whenOptionalBillingTypeProvided() {
        CtlBillingService s1 = new CtlBillingService();
        CtlBillingService s2 = new CtlBillingService();
        CtlBillingService s3 = new CtlBillingService();
        CtlDiagCode seed = new CtlDiagCode();
        CtlBillingType type = new CtlBillingType();

        svc.addBillingForm(List.of(s1, s2, s3), seed, type);

        verify(serviceDao, times(3)).persist(org.mockito.ArgumentMatchers.any(CtlBillingService.class));
        verify(diagDao).persist(same(seed));
        verify(typeDao).persist(same(type));
    }

    @Test
    void shouldSkipBillingTypePersist_whenOptionalBillingTypeIsNull() {
        CtlBillingService s1 = new CtlBillingService();
        CtlDiagCode seed = new CtlDiagCode();

        svc.addBillingForm(List.of(s1), seed, null);

        verify(serviceDao).persist(same(s1));
        verify(diagDao).persist(same(seed));
        verify(typeDao, never()).persist(org.mockito.ArgumentMatchers.any(CtlBillingType.class));
    }

    // ---- saveLocations --------------------------------------------------

    @Test
    void shouldPersistEachLocation_whenSaveLocations() {
        ClinicLocation loc1 = new ClinicLocation();
        ClinicLocation loc2 = new ClinicLocation();

        svc.saveLocations(List.of(loc1, loc2));

        verify(locationDao).persist(same(loc1));
        verify(locationDao).persist(same(loc2));
    }

    @Test
    void shouldPropagate_whenLocationPersistThrowsMidLoop() {
        ClinicLocation loc1 = new ClinicLocation();
        ClinicLocation loc2 = new ClinicLocation();
        doThrow(new RuntimeException("loc-fail")).when(locationDao).persist(same(loc2));

        assertThatThrownBy(() -> svc.saveLocations(List.of(loc1, loc2)))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("loc-fail");

        verify(locationDao).persist(same(loc1));
    }

    // ---- helpers --------------------------------------------------------

    private static CtlBillingService serviceWithId(int id) {
        CtlBillingService b = mock(CtlBillingService.class);
        when(b.getId()).thenReturn(id);
        return b;
    }

    private static CtlDiagCode diagWithId(int id) {
        CtlDiagCode d = mock(CtlDiagCode.class);
        when(d.getId()).thenReturn(id);
        return d;
    }

    private static CtlBillingServicePremium premiumWithId(int id) {
        CtlBillingServicePremium p = mock(CtlBillingServicePremium.class);
        when(p.getId()).thenReturn(id);
        return p;
    }
}
