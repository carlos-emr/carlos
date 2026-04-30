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

import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.BillingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link ServiceCodeLoader}, the read-only billing
 * service-code lookup helper. Covers the three pre-existing read methods.
 *
 * @since 2026-04-29
 */
@DisplayName("ServiceCodeLoader")
@Tag("unit")
@Tag("billing")
class ServiceCodeLoaderUnitTest {

    private BillingServiceDao dao;
    private ServiceCodeLoader loader;

    @BeforeEach
    void setUp() {
        dao = mock(BillingServiceDao.class);
        loader = new ServiceCodeLoader(dao);
    }

    @Test
    void shouldFlattenBillingServiceFields_whenGetBillingCodeAttr() {
        BillingService bs = new BillingService();
        bs.setDescription("Office visit");
        bs.setValue("75.00");
        bs.setPercentage("0");
        bs.setBillingserviceDate(new Date(0));
        bs.setGstFlag(Boolean.FALSE);
        when(dao.getBillingCodeAttr("A007")).thenReturn(List.of(bs));

        @SuppressWarnings("unchecked")
        List<Object> result = loader.getBillingCodeAttr("A007");

        // Method appends 6 fields per matching service: code, desc, value,
        // percentage, billing-date-string, gst-flag. Pin every position so a
        // future regression that re-orders / drops a field surfaces here.
        assertThat(result).hasSize(6);
        assertThat(result.get(0)).isEqualTo("A007");
        assertThat(result.get(1)).isEqualTo("Office visit");
        assertThat(result.get(2)).isEqualTo("75.00");
        assertThat(result.get(3)).isEqualTo("0");          // percentage
        assertThat(result.get(4)).isNotNull();             // billing-date string
        assertThat(result.get(5)).isEqualTo(Boolean.FALSE); // gst flag
    }

    @Test
    void shouldReturnEmpty_whenServiceCodeNotFound() {
        when(dao.getBillingCodeAttr(anyString())).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        List<Object> result = loader.getBillingCodeAttr("ZZZZZ");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldMapServiceCodeToDescription_whenGetCodeDescByNames() {
        BillingService a = new BillingService();
        a.setServiceCode("A007");
        a.setDescription("Office visit");
        BillingService b = new BillingService();
        b.setServiceCode("A008");
        b.setDescription("Re-assessment");
        when(dao.findByServiceCodes(anyList())).thenReturn(List.of(a, b));

        Properties result = loader.getCodeDescByNames(List.of("A007", "A008"));

        assertThat(result.getProperty("A007")).isEqualTo("Office visit");
        assertThat(result.getProperty("A008")).isEqualTo("Re-assessment");
    }

    @Test
    void shouldReturnAllPrivateCodes_whenGetPrivateBillingCodeDesc() {
        BillingService a = new BillingService();
        a.setServiceCode("PRIV1");
        when(dao.finAllPrivateCodes()).thenReturn(List.of(a));

        List<String> result = loader.getPrivateBillingCodeDesc();

        assertThat(result).contains("PRIV1");
    }
}
