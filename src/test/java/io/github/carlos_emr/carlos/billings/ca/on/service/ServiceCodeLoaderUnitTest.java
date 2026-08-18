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
    void shouldProjectBillingServiceFields_whenGetBillingCodeAttr() {
        BillingService bs = new BillingService();
        bs.setDescription("Office visit");
        bs.setValue("75.00");
        bs.setPercentage("0");
        bs.setBillingserviceDate(new Date(0));
        bs.setGstFlag(Boolean.FALSE);
        when(dao.getBillingCodeAttr("A007")).thenReturn(List.of(bs));

        List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute> result =
                loader.getBillingCodeAttr("A007");

        assertThat(result).singleElement().satisfies(attr -> {
            assertThat(attr.serviceCode()).isEqualTo("A007");
            assertThat(attr.description()).isEqualTo("Office visit");
            assertThat(attr.value()).isEqualTo("75.00");
            assertThat(attr.percentage()).isEqualTo("0");
            assertThat(attr.billingServiceDate()).isNotNull();
            assertThat(attr.gstFlag()).isEqualTo("false");
        });
    }

    @Test
    void shouldReturnEmpty_whenServiceCodeNotFound() {
        when(dao.getBillingCodeAttr(anyString())).thenReturn(List.of());

        List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillingCodeAttribute> result =
                loader.getBillingCodeAttr("ZZZZZ");

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
        when(dao.finAllPrivateCodes()).thenReturn(List.of(
                new io.github.carlos_emr.carlos.billings.ca.on.dto.PrivateBillingCode("PRIV1", "Priv code 1")));

        List<io.github.carlos_emr.carlos.billings.ca.on.dto.PrivateBillingCode> result =
                loader.getPrivateBillingCodeDesc();

        assertThat(result)
                .extracting(io.github.carlos_emr.carlos.billings.ca.on.dto.PrivateBillingCode::serviceCode)
                .contains("PRIV1");
    }
}
