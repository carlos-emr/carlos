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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.billing.CA.dao.GstControlDao;
import io.github.carlos_emr.carlos.billing.CA.model.GstControl;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private BillingPercLimitDao billingPercLimitDao;
    private BillingOnHeaderCreationService service;

    @BeforeEach
    void setUp() {
        headerDao = Mockito.mock(BillingONCHeader1Dao.class);
        providerDao = Mockito.mock(ProviderDao.class);
        demographicDao = Mockito.mock(DemographicDao.class);
        billingServiceDao = Mockito.mock(BillingServiceDao.class);
        gstControlDao = Mockito.mock(GstControlDao.class);
        billingPercLimitDao = Mockito.mock(BillingPercLimitDao.class);

        service = new BillingOnHeaderCreationService(
                headerDao, providerDao, demographicDao, billingServiceDao, gstControlDao, billingPercLimitDao);
    }

    @Test
    void shouldThrowValidationAndNotPersist_whenDemographicNotFound() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        // No demographic stub -> getDemographicById returns null.
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        assertThatThrownBy(() -> service.createBill("999998", 1, "A007A", "00001", new Date(), "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("demographicNo=1");

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
    void shouldPersistZeroFee_whenLegacyServiceValueIsWhitespaceOnly() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        BillingService bs = stubServiceCode("A007A", "   ", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        String total = service.createBill("999998", 1, "A007A", "00001", new Date(), "999998");

        assertThat(total).isEqualTo("0.00");
        ArgumentCaptor<BillingONCHeader1> captor = ArgumentCaptor.forClass(BillingONCHeader1.class);
        verify(headerDao).persist(captor.capture());
        assertThat(captor.getValue().getBillingItems()).singleElement()
                .satisfies(item -> assertThat(new BigDecimal(item.getFee())).isEqualByComparingTo("0.00"));
    }

    @Test
    void shouldThrowValidationAndNotPersist_whenServiceCodeMissingForItemCreation() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        when(billingServiceDao.searchBillingCode(eq("ZZ999"), eq("ON"), any())).thenReturn(null);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        assertThatThrownBy(() -> service.createBill("999998", 1, "ZZ999", "00001", new Date(), "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("service code not found")
                .hasMessageContaining("ZZ999");

        verify(headerDao, never()).persist(any());
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
    void shouldThrowValidationBeforePersistingAnyRows_whenBatchDemographicMissing() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        when(demographicDao.getDemographicById(2)).thenReturn(null); // missing
        when(demographicDao.getDemographicById(3)).thenReturn(demographic(3, "ON"));
        BillingService bs = stubServiceCode("A007A", "30.00", false, "");
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any())).thenReturn(bs);
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));

        assertThatThrownBy(() -> service.createBills("999998",
                java.util.List.of("1", "2", "3"),
                java.util.List.of("A007A"),
                java.util.List.of("401"),
                "00001", new Date(), "999998"))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("demographicNo=2");

        verify(headerDao, never()).persist(any());
    }

    @Test
    @DisplayName("should cap a percentage code at the maximum in effect on the service date")
    void shouldCapPercentageCode_atConfiguredMaximum() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(7, "1.00", "10.00")));

        String total = createPercentageBatch(serviceDate);

        // 30.00 base + min(30.00 x 0.50 = 15.00, 10.00)
        assertThat(total).isEqualTo("40.00");
    }

    @Test
    @DisplayName("should raise a percentage code to the minimum in effect on the service date")
    void shouldRaisePercentageCode_toConfiguredMinimum() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(7, "20.00", "99.00")));

        String total = createPercentageBatch(serviceDate);

        // 30.00 base + max(15.00, 20.00)
        assertThat(total).isEqualTo("50.00");
    }

    @Test
    @DisplayName("should leave a percentage code unclamped when no limit is configured")
    void shouldLeavePercentageUnclamped_whenNoLimitConfigured() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate)).thenReturn(List.of());

        String total = createPercentageBatch(serviceDate);

        assertThat(total).isEqualTo("45.00");
    }

    @Test
    @DisplayName("should look up limits only for percentage codes, by resolved code and service date")
    void shouldLookUpLimits_onlyForPercentageCodes() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate(anyString(), any())).thenReturn(List.of());

        createPercentageBatch(serviceDate);

        verify(billingPercLimitDao).findByServiceCodeAndLatestDate("E400B", serviceDate);
        verify(billingPercLimitDao, never()).findByServiceCodeAndLatestDate(eq("A007A"), any());
    }

    @Test
    @DisplayName("should apply the most recently added limit when two share the effective date")
    void shouldApplyMostRecentlyAddedLimit_whenTwoShareTheEffectiveDate() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(9, "1.00", "12.00"), limit(3, "1.00", "5.00")));

        String total = createPercentageBatch(serviceDate);

        assertThat(total).isEqualTo("42.00");
    }

    @Test
    @DisplayName("should refuse the batch without persisting when a configured limit is not an amount")
    void shouldRefuseBatchWithoutPersisting_whenConfiguredLimitIsNotAnAmount() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(7, "1.00", " ")));

        assertThatThrownBy(() -> createPercentageBatch(serviceDate))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("maximum percentage limit")
                .hasMessageContaining("E400B");

        verify(headerDao, never()).persist(any());
    }

    @Test
    @DisplayName("should refuse the batch without persisting when a configured minimum exceeds its maximum")
    void shouldRefuseBatchWithoutPersisting_whenConfiguredMinimumExceedsMaximum() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(7, "20.00", "10.00")));

        assertThatThrownBy(() -> createPercentageBatch(serviceDate))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("minimum above its maximum")
                .hasMessageContaining("E400B");

        verify(headerDao, never()).persist(any());
    }

    @Test
    @DisplayName("should refuse the batch without persisting when a configured limit is negative")
    void shouldRefuseBatchWithoutPersisting_whenConfiguredLimitIsNegative() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(7, "-10.00", "-5.00")));

        assertThatThrownBy(() -> createPercentageBatch(serviceDate))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("percentage limit for service code=E400B is negative");

        verify(headerDao, never()).persist(any());
    }

    @Test
    @DisplayName("should accept a limit whose minimum equals its maximum")
    void shouldBillTheFixedAmount_whenMinimumEqualsMaximum() {
        Date serviceDate = stubPercentageBatch();
        when(billingPercLimitDao.findByServiceCodeAndLatestDate("E400B", serviceDate))
                .thenReturn(List.of(limit(7, "12.00", "12.00")));

        assertThat(createPercentageBatch(serviceDate)).isEqualTo("42.00");
    }

    private Date stubPercentageBatch() {
        when(providerDao.getProvider("999998")).thenReturn(provider("999998"));
        when(demographicDao.getDemographicById(1)).thenReturn(demographic(1, "ON"));
        when(billingServiceDao.searchBillingCode(eq("A007A"), eq("ON"), any()))
                .thenReturn(stubServiceCode("A007A", "30.00", false, ""));
        when(billingServiceDao.searchBillingCode(eq("E400B"), eq("ON"), any()))
                .thenReturn(stubServiceCode("E400B", "", false, "0.50"));
        when(gstControlDao.find(any(Integer.class))).thenReturn(gstControl("0"));
        return new Date();
    }

    private String createPercentageBatch(Date serviceDate) {
        return service.createBills("999998", List.of("1"), List.of("A007A", "E400B"), List.of("401"),
                "00001", serviceDate, "999998");
    }

    private BillingPercLimit limit(int id, String min, String max) {
        BillingPercLimit limit = new BillingPercLimit();
        limit.setId(id);
        limit.setService_code("E400B");
        limit.setMin(min);
        limit.setMax(max);
        return limit;
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
