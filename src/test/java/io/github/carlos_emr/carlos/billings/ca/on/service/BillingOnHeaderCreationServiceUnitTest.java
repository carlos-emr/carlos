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

import java.math.BigDecimal;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingOnHeaderCreationService}. Owns the bill-creation
 * orchestration that used to live on {@code BillingONCHeader1DaoImpl}; verifies
 * the cross-DAO calls happen in the right order with the right inputs.
 *
 * @since 2026-04-27
 */
@DisplayName("BillingOnHeaderCreationService")
@Tag("unit")
@Tag("billing")
class BillingOnHeaderCreationServiceUnitTest {

    private BillingONCHeader1Dao headerDao;
    private ProviderDao providerDao;
    private DemographicDao demographicDao;
    private BillingServiceDao billingServiceDao;
    private GstControlDao gstControlDao;
    private BillingOnHeaderCreationService service;

    @BeforeEach
    void setUp() {
        headerDao = Mockito.mock(BillingONCHeader1Dao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        demographicDao = Mockito.mock(DemographicDao.class);
        billingServiceDao = Mockito.mock(BillingServiceDao.class);
        gstControlDao = Mockito.mock(GstControlDao.class);

        service = new BillingOnHeaderCreationService(
                headerDao, providerDao, demographicDao, billingServiceDao, gstControlDao);
    }

    @Test
    void shouldReturnNullAndNotPersist_whenDemographicNotFound() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        // No demographic stub -> getDemographicById returns null.
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        String total = service.createBill("999998", 1, "A007A", "00001", new Date(), "999998");

        assertThat(total).isNull();
        verify(headerDao, never()).persist(any());
    }

    @Test
    void shouldPersistOneHeaderWithCorrectFields_onSingleCodeBill() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        Date serviceDate = new Date();
        String total = service.createBill("999998", 1, "A007A", "00001", serviceDate, "999998");

        assertThat(total).isEqualTo("30.00");

        ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
        verify(headerDao, times(1)).persist(captor.capture());
        BillingONCHeader1 saved = captor.getValue();
        assertThat(saved.getDemographicNo()).isEqualTo(1);
        assertThat(saved.getProviderNo()).isEqualTo("999998");
        assertThat(saved.getCreator()).isEqualTo("999998");
        assertThat(saved.getPayProgram()).isEqualTo("HCP"); // ON HC type
        assertThat(saved.getStatus()).isEqualTo("O");
        assertThat(saved.getTotal()).isEqualByComparingTo("30.00");
        assertThat(saved.getBillingItems()).hasSize(1);
    }

    @Test
    void shouldUseRmbPayProgram_forNonOntarioHcType() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "BC"));
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        service.createBill("999998", 1, "A007A", "00001", new Date(), "999998");

        ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
        verify(headerDao).persist(captor.capture());
        assertThat(captor.getValue().getPayProgram()).isEqualTo("RMB");
    }

    @Test
    void shouldApplyGstPercent_whenServiceCodeIsGstFlagged() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        BillingService bs = stubServiceCode("A007A", "100.00", true, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("5"));

        String total = service.createBill("999998", 1, "A007A", "00001", new Date(), "999998");

        // 5% of 100 = 5; total = 100 + 5
        assertThat(new BigDecimal(total)).isEqualByComparingTo("105.00");
    }

    @Test
    void shouldApplyNonTerminatingGstPercent_withoutArithmeticException() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        BillingService bs = stubServiceCode("A007A", "100.00", true, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("8.333"));

        String total = service.createBill("999998", 1, "A007A", "00001", new Date(), "999998");

        assertThat(new BigDecimal(total)).isEqualByComparingTo("108.33");
    }

    @Test
    void shouldPersistOneHeaderPerDemographic_inBatchCreate() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        when(demographicDao.getDemographicById(2)).thenReturn(demographic(2, "ON"));
        when(demographicDao.getDemographicById(3)).thenReturn(demographic(3, "ON"));
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        String total = service.createBills("999998",
                java.util.List.of("1", "2", "3"),
                java.util.List.of("A007A"),
                java.util.List.of("401"),
                "00001", new Date(), "999998");

        assertThat(total).isEqualTo("30.00");
        verify(headerDao, times(3)).persist(any());
    }

    @Test
    void shouldSkipPersistForMissingDemographic_inBatchCreate() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        when(demographicDao.getDemographicById(2)).thenReturn(null); // missing
        when(demographicDao.getDemographicById(3)).thenReturn(demographic(3, "ON"));
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        service.createBills("999998",
                java.util.List.of("1", "2", "3"),
                java.util.List.of("A007A"),
                java.util.List.of("401"),
                "00001", new Date(), "999998");

        // Demographic 2 missing -> only 2 persists.
        verify(headerDao, times(2)).persist(any());
    }

    private Provider provider(String providerNo) {
        Provider p = new Provider();
        p.setProviderNo(providerNo);
        p.setOhipNo("OHIP-" + providerNo);
        p.setRmaNo("RMA-" + providerNo);
        return p;
    }

    private Demographic demographic(int id, String hcType) {
        Demographic d = new Demographic();
        d.setDemographicNo(id);
        d.setLastName("Doe");
        d.setFirstName("Jane");
        d.setHin("9876543225");
        d.setVer("AB");
        d.setHcType(hcType);
        d.setSex("F");
        d.setDateOfBirth("1985-06-15");
        return d;
    }

    private BillingService stubServiceCode(String code, String value, boolean gstFlag, String percentage) {
        BillingService s = new BillingService();
        s.setServiceCode(code);
        s.setValue(value);
        s.setGstFlag(gstFlag);
        s.setPercentage(percentage);
        return s;
    }

    private GstControl gstControl(String percent) {
        GstControl g = new GstControl();
        g.setGstPercent(new BigDecimal(percent));
        return g;
    }
}
