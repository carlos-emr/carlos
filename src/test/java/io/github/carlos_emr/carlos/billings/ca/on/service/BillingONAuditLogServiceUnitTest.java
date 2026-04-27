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

import java.util.Date;

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONProcDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONProc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link BillingONAuditLogService}.
 *
 * <p>The service is a single-method audit-emission seam — every ON
 * billing state change flows through {@code addBillingLog} so the trail
 * stays uniform. These tests lock down the field-by-field mapping from
 * method args → {@code BillingONProc} entity, plus the timestamp policy
 * (server-clock {@code new Date()} at call time, not caller-provided).
 * A future refactor that drops a field, swaps the timestamp source, or
 * silently no-ops on null input must fail these tests loudly.</p>
 *
 * @since 2026-04-26
 */
@DisplayName("BillingONAuditLogService")
@Tag("unit")
@Tag("billing")
@Tag("service")
class BillingONAuditLogServiceUnitTest {

    private BillingONProcDao dao;
    private BillingONAuditLogService service;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(BillingONProcDao.class);
        service = new BillingONAuditLogService(dao);
    }

    @Test
    @DisplayName("persists a BillingONProc with all four fields populated")
    void shouldPersistAllFields_onAddBillingLog() {
        Date before = new Date();

        service.addBillingLog("999998", "updateBillingStatus", "manual reconcile", "12345");

        ArgumentCaptor<BillingONProc> captor = ArgumentCaptor.forClass(BillingONProc.class);
        verify(dao, times(1)).persist(captor.capture());
        BillingONProc persisted = captor.getValue();

        assertThat(persisted.getCreator()).isEqualTo("999998");
        assertThat(persisted.getAction()).isEqualTo("updateBillingStatus");
        assertThat(persisted.getComment()).isEqualTo("manual reconcile");
        assertThat(persisted.getObject()).isEqualTo("12345");
        // The timestamp is server-side new Date() — must lie in the test window.
        // Use explicit inclusive bounds: AssertJ's two-arg isBetween treats the
        // upper bound as exclusive, which races when both Dates land in the
        // same millisecond.
        assertThat(persisted.getCreateDateTime())
                .isBetween(before, new Date(), true, true);
    }

    @Test
    @DisplayName("each call produces a fresh persisted entity (no instance reuse)")
    void shouldEmitOneEntityPerCall() {
        service.addBillingLog("p1", "a1", "c1", "o1");
        service.addBillingLog("p2", "a2", "c2", "o2");

        ArgumentCaptor<BillingONProc> captor = ArgumentCaptor.forClass(BillingONProc.class);
        verify(dao, times(2)).persist(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(BillingONProc::getCreator)
                .containsExactly("p1", "p2");
    }

    @Test
    @DisplayName("propagates null fields verbatim — caller controls the contract")
    void shouldPersistNullFields_whenCallerPassesNulls() {
        // The audit table accepts nullable comment/object; the service does
        // not normalize them (intentional — caller decides what's audit-
        // worthy). Lock that contract down.
        service.addBillingLog("999998", "delete", null, null);

        ArgumentCaptor<BillingONProc> captor = ArgumentCaptor.forClass(BillingONProc.class);
        verify(dao).persist(captor.capture());
        BillingONProc persisted = captor.getValue();

        assertThat(persisted.getComment()).isNull();
        assertThat(persisted.getObject()).isNull();
    }
}
