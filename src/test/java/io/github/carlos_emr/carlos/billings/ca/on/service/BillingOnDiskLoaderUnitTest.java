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

import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link BillingOnDiskLoader}. Exercises the {@code
 * getMRIList} happy and exception paths — the latter pins the Phase 2
 * silent-failure fix that improved the log context when DAO calls throw.
 *
 * @since 2026-04-29
 */
@DisplayName("BillingOnDiskLoader")
@Tag("unit")
@Tag("billing")
class BillingOnDiskLoaderUnitTest {

    private BillingONHeaderDao headerDao;
    private BillingONDiskNameDao diskNameDao;
    private BillingONFilenameDao filenameDao;
    private BillingOnDiskLoader loader;

    @BeforeEach
    void setUp() {
        headerDao = mock(BillingONHeaderDao.class);
        diskNameDao = mock(BillingONDiskNameDao.class);
        filenameDao = mock(BillingONFilenameDao.class);
        loader = new BillingOnDiskLoader(headerDao, diskNameDao, filenameDao);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnPopulatedDtos_whenGetMRIListHappyPath() {
        BillingONDiskName d = new BillingONDiskName();
        d.setMonthCode("202604");
        d.setBatchCount(1);
        d.setOhipFilename("A.B");
        d.setGroupNo("0");
        d.setClaimRecord("0");
        d.setCreateDateTime(new Date());
        d.setTimestamp(new Date());
        d.setStatus("0");
        d.setTotal("0.00");
        when(diskNameDao.findByCreateDateRangeAndStatus(any(), any(), anyString()))
                .thenReturn(List.of(d));
        when(filenameDao.findByDiskIdAndStatus(anyInt(), anyString()))
                .thenReturn(List.<BillingONFilename>of());

        List<Object> result = loader.getMRIList("2026-01-01", "2026-12-31", "0");

        // Pin every projection field so a future regression that re-maps the
        // entity → DTO assignment surfaces in this test.
        assertThat(result).hasSize(1);
        io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto dto =
                (io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto) result.get(0);
        assertThat(dto.getMonthCode()).isEqualTo("202604");
        assertThat(dto.getOhipfilename()).isEqualTo("A.B");
        assertThat(dto.getGroupno()).isEqualTo("0");
        assertThat(dto.getStatus()).isEqualTo("0");
        assertThat(dto.getTotal()).isEqualTo("0.00");
    }

    @Test
    void shouldReturnNull_whenGetMRIListThrows() {
        // Phase 2 silent-failure-fix path: when the DAO throws, the loader
        // logs and returns null. Callers map null to empty list at the UI;
        // this test pins the null return so that mapping doesn't drift.
        when(diskNameDao.findByCreateDateRangeAndStatus(any(), any(), anyString()))
                .thenThrow(new RuntimeException("DB outage simulation"));

        List<?> result = loader.getMRIList("2026-01-01", "2026-12-31", "0");

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNull_whenDateStringsAreUnparseable() {
        // SimpleDateFormat.parse throws ParseException on bad input; the
        // catch returns null per the same Phase 2 fix.
        List<?> result = loader.getMRIList("not-a-date", "also-bad", "0");
        assertThat(result).isNull();
    }
}
