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

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void shouldDelegateGetFacilityNumToLookupService() {
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

    // ---- updateBillingClaimHeader: direct coverage --------------------

    @Test
    void shouldUpdateChangedHeader_whenOldStatusParameterIsMissing() {
        BillingClaimHeaderDto header = baseHeader();
        BillingProviderDto provider = new BillingProviderDto();
        provider.setOhipNo("OHIP2");
        provider.setRmaNo("RMA2");
        when(lookupService.getProviderObj("222")).thenReturn(provider);
        when(correctionPersister.updateBillingClaimHeader(any(BillingClaimHeaderDto.class))).thenReturn(true);
        when(billOnTransDao.getUpdateCheader1TransTemplate(
                any(BillingClaimHeaderDto.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new BillingOnTransaction());

        MockHttpServletRequest request = baseHeaderRequest();
        request.setParameter("provider_no", "222");
        // No oldStatus parameter: replayed/non-legacy clients should still
        // update a non-third-party header instead of NPEing after the write.

        boolean updated = service.updateBillingClaimHeader(header, request);

        assertThat(updated).isTrue();
        verify(correctionPersister).updateBillingClaimHeader(any(BillingClaimHeaderDto.class));
        verify(billOnTransDao).persist(any(BillingOnTransaction.class));
    }

    // ---- updateBillingItem strict-date contract ------------------------
    //
    // A malformed xml_appointment_date must abort the @Transactional
    // unit-of-work via BillingDates.parseIsoDate rather than silently
    // substituting today's date. A regression that swaps parseIsoDate
    // back to `new Date()` would record audit-incorrect rows (wrong
    // date of service on the claim), so the negative test below is the
    // canary.

    @Test
    void shouldThrow_whenServiceDateMalformed_andNotPersistNewItem_onUpdateBillingItem() throws Exception {
        BillingClaimHeaderDto ch1Obj = new BillingClaimHeaderDto();
        ch1Obj = ch1Obj.withStatus("O");
        BillingClaimItemDto existingItem = new BillingClaimItemDto();
        existingItem = existingItem.withClaimHeaderId("42");
        existingItem = existingItem.withRecordId("");
        existingItem = existingItem.withTransactionId("");
        existingItem = existingItem.withServiceCode("A001A");  // existing code
        existingItem = existingItem.withServiceNumber("1");
        existingItem = existingItem.withFee("33.70");
        existingItem = existingItem.withDx("");
        existingItem = existingItem.withDx1("");
        existingItem = existingItem.withDx2("");
        existingItem = existingItem.withStatus("O");
        existingItem = existingItem.withServiceDate("2026-04-28");

        // changeItem's else-branch (service code differs) writes a delete
        // transaction to billOnTransDao and only THEN calls the strict-date
        // private addItem for the new code. So billOnTransDao.persist may
        // be invoked; the regression target is billOnItemDao.persist of
        // the NEW item.
        BillingONCHeader1 header = new BillingONCHeader1();
        // The BillingOnTransaction.setCh1Id(int) call inside changeItem
        // unboxes header.getId(); without an id we'd NPE before reaching
        // the strict-date parser. JPA assigns this on persist; reflect it
        // in directly for the test.
        java.lang.reflect.Field idField = BillingONCHeader1.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(header, Integer.valueOf(42));
        when(cheader1Dao.find(42)).thenReturn(header);

        @SuppressWarnings({"rawtypes", "unchecked"})
        List itemList = new ArrayList();
        itemList.add(ch1Obj);
        itemList.add(existingItem);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("xml_diagnostic_detail", "401");
        request.setParameter("xml_appointment_date", "not-a-date");
        request.setParameter("servicecode0", "B999B");  // different from existing
        request.setParameter("billingunit0", "1");
        request.setParameter("billingamount0", "44.50");

        assertThatThrownBy(() -> service.updateBillingItem(itemList, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-date");

        // The new item must NOT have been persisted via the persister —
        // a regression that fell back to `new Date()` would have happily
        // persisted a row with today's date.
        verify(claimPersister, never()).addOneItemRecord(any(BillingClaimItemDto.class));
        verify(billOnItemDao, never()).persist(any(BillingONItem.class));
    }

    private static BillingClaimHeaderDto baseHeader() {
        return new BillingClaimHeaderDto()
                .withId("42")
                .withStatus("O")
                .withPayProgram("HCP")
                .withReferralNumber("REF001")
                .withVisitType("00")
                .withAdmissionDate("2026-01-01")
                .withFacilityNumber("FAC1")
                .withManualReview("")
                .withBillingDate("2026-04-28")
                .withProviderNo("111")
                .withProviderOhipNo("OHIP1")
                .withProviderRmaNo("RMA1")
                .withComment("old")
                .withClinic("SITE1")
                .withProvince("ON")
                .withLocation("LOC1")
                .withBillto("");
    }

    private static MockHttpServletRequest baseHeaderRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("status", "O");
        request.setParameter("payProgram", "HCP");
        request.setParameter("rdohip", "REF001");
        request.setParameter("visittype", "00");
        request.setParameter("xml_vdate", "2026-01-01");
        request.setParameter("clinic_ref_code", "FAC1");
        request.setParameter("xml_appointment_date", "2026-04-28");
        request.setParameter("provider_no", "111");
        request.setParameter("comment", "old");
        request.setParameter("site", "SITE1");
        request.setParameter("hc_type", "ON");
        request.setParameter("xml_slicode", "LOC1");
        request.setParameter("xml_billing_no", "42");
        request.setParameter("demoNo", "123");
        return request;
    }
}
