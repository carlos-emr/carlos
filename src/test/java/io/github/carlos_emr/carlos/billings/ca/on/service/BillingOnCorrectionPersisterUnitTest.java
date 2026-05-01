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
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONEAReportDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

/**
 * Behavioral tests for {@link BillingOnCorrectionPersister} (renamed from
 * {@code BillingOnCorrectionPersistenceService} during the {@code *Persister}
 * / {@code *Loader} split — see {@code docs/architecture/layer-names.md}).
 * Covers the high-value mutation paths: status updates, total/paid money
 * writes, and item-mutation flow.
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

    // ---- audit-trail emission: addInsertOneBillItemTrans -----------------

    @Test
    void shouldPersistInsertTrans_whenHeaderDatesAreValid() {
        BillingClaimHeaderDto h = headerDto("2026-01-15", "2026-01-20");
        BillingClaimItemDto i = itemDto();

        persister.addInsertOneBillItemTrans(h, i, "999998");

        ArgumentCaptor<BillingOnTransaction> captor =
                ArgumentCaptor.forClass(BillingOnTransaction.class);
        verify(transDao).persist(captor.capture());
        BillingOnTransaction t = captor.getValue();
        assertThat(t.getActionType()).isEqualTo("C");
        assertThat(t.getAdmissionDate()).isNotNull();
        assertThat(t.getBillingDate()).isNotNull();
    }

    @Test
    void shouldThrow_whenInsertTransAdmissionDateIsMalformed() {
        BillingClaimHeaderDto h = headerDto("not-a-date", "2026-01-20");
        BillingClaimItemDto i = itemDto();

        assertThatThrownBy(() -> persister.addInsertOneBillItemTrans(h, i, "999998"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admission_date")
                .hasMessageContaining("not-a-date");

        verify(transDao, never()).persist(any(BillingOnTransaction.class));
    }

    @Test
    void shouldThrow_whenInsertTransBillingDateIsMalformed() {
        BillingClaimHeaderDto h = headerDto("2026-01-15", "not-a-date");
        BillingClaimItemDto i = itemDto();

        assertThatThrownBy(() -> persister.addInsertOneBillItemTrans(h, i, "999998"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billing_date");

        verify(transDao, never()).persist(any(BillingOnTransaction.class));
    }

    @Test
    void shouldTolerateBlankDates_whenInsertTrans() {
        BillingClaimHeaderDto h = headerDto("", "");
        BillingClaimItemDto i = itemDto();

        persister.addInsertOneBillItemTrans(h, i, "999998");

        ArgumentCaptor<BillingOnTransaction> captor =
                ArgumentCaptor.forClass(BillingOnTransaction.class);
        verify(transDao).persist(captor.capture());
        assertThat(captor.getValue().getAdmissionDate()).isNull();
        assertThat(captor.getValue().getBillingDate()).isNull();
    }

    // ---- audit-trail emission: addUpdateOneBillItemTrans -----------------

    @Test
    void shouldPersistUpdateTrans_whenHeaderDatesAreValid() {
        BillingClaimHeaderDto h = headerDto("2026-01-15", "2026-01-20");
        BillingClaimItemDto i = itemDto();

        persister.addUpdateOneBillItemTrans(h, i, "999998");

        ArgumentCaptor<BillingOnTransaction> captor =
                ArgumentCaptor.forClass(BillingOnTransaction.class);
        verify(transDao).persist(captor.capture());
        assertThat(captor.getValue().getActionType()).isEqualTo("U");
    }

    @Test
    void shouldThrow_whenUpdateTransAdmissionDateIsMalformed() {
        BillingClaimHeaderDto h = headerDto("not-a-date", "2026-01-20");
        BillingClaimItemDto i = itemDto();

        assertThatThrownBy(() -> persister.addUpdateOneBillItemTrans(h, i, "999998"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("admission_date");

        verify(transDao, never()).persist(any(BillingOnTransaction.class));
    }

    /**
     * Regression test for the copy-paste bug at
     * {@code BillingOnCorrectionPersister.addUpdateOneBillItemTrans:522-524}
     * where three identical {@code setServiceCodePaid(BigDecimal.ZERO)} calls
     * were emitted instead of {@code setServiceCodePaid} /
     * {@code setServiceCodeDiscount} / {@code setServiceCodeRefund}. The
     * sibling {@code addInsertOneBillItemTrans:479-481} has the correct trio.
     *
     * <p>The {@code BillingOnTransaction} entity defaults the three fields to
     * {@code new BigDecimal("0.00")} (scale=2), so the bug doesn't corrupt
     * runtime values — but it does mean the persister's intent isn't expressed.
     * We pin intent by asserting each field equals the canonical
     * {@code BigDecimal.ZERO} (scale=0). With the bug present, the un-set
     * fields keep their scale-2 default and {@code .isEqualTo(ZERO)} fails.</p>
     */
    @Test
    void shouldSetAllThreeMoneyFieldsExplicitly_whenUpdateTrans() {
        BillingClaimHeaderDto h = headerDto("2026-01-15", "2026-01-20");
        BillingClaimItemDto i = itemDto();

        persister.addUpdateOneBillItemTrans(h, i, "999998");

        ArgumentCaptor<BillingOnTransaction> captor =
                ArgumentCaptor.forClass(BillingOnTransaction.class);
        verify(transDao).persist(captor.capture());
        BillingOnTransaction t = captor.getValue();
        // .isEqualTo uses BigDecimal.equals (scale-sensitive); ZERO has scale 0.
        assertThat(t.getServiceCodePaid()).isEqualTo(BigDecimal.ZERO);
        assertThat(t.getServiceCodeDiscount()).isEqualTo(BigDecimal.ZERO);
        assertThat(t.getServiceCodeRefund()).isEqualTo(BigDecimal.ZERO);
    }

    // ---- updateBillingClaimHeader (round-10 dropped throws ParseException;
    //      now throws IAE via BillingDates.parseIso* on malformed dates) -----

    @Test
    void shouldMergeHeader_whenUpdateBillingClaimHeaderDatesValid() {
        BillingONCHeader1 existing = new BillingONCHeader1();
        when(headerDao.find("100")).thenReturn(existing);
        BillingClaimHeaderDto h = fullHeaderDto("2026-01-15", "2026-01-20", "12:34:56");

        boolean ret = persister.updateBillingClaimHeader(h);

        assertThat(ret).isTrue();
        verify(headerDao).merge(existing);
    }

    @Test
    void shouldThrowAndNotMerge_whenUpdateBillingClaimHeaderBillingTimeMalformed() {
        BillingONCHeader1 existing = new BillingONCHeader1();
        when(headerDao.find("100")).thenReturn(existing);
        BillingClaimHeaderDto h = fullHeaderDto("2026-01-15", "2026-01-20", "not-a-time");

        assertThatThrownBy(() -> persister.updateBillingClaimHeader(h))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-time");

        verify(headerDao, never()).merge(any(BillingONCHeader1.class));
    }

    @Test
    void shouldThrowAndNotMerge_whenUpdateBillingClaimHeaderAdmissionDateMalformed() {
        BillingONCHeader1 existing = new BillingONCHeader1();
        when(headerDao.find("100")).thenReturn(existing);
        BillingClaimHeaderDto h = fullHeaderDto("not-a-date", "2026-01-20", "12:34:56");

        assertThatThrownBy(() -> persister.updateBillingClaimHeader(h))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-date");

        verify(headerDao, never()).merge(any(BillingONCHeader1.class));
    }

    @Test
    void shouldThrowValidation_whenUpdateBillingClaimHeaderTotalIsNegative() {
        BillingONCHeader1 existing = new BillingONCHeader1();
        when(headerDao.find("100")).thenReturn(existing);
        BillingClaimHeaderDto h = fullHeaderDto("2026-01-15", "2026-01-20", "12:34:56");
        h.setTotal("-50.00");

        assertThatThrownBy(() -> persister.updateBillingClaimHeader(h))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("total")
                .hasMessageContaining("cannot be negative");

        verify(headerDao, never()).merge(any(BillingONCHeader1.class));
    }

    @Test
    void shouldThrowValidation_whenUpdateBillingPaidIsNegative() {
        BillingONCHeader1 existing = new BillingONCHeader1();
        when(headerDao.find("100")).thenReturn(existing);

        assertThatThrownBy(() -> persister.updateBillingPaid("-25.00", "100"))
                .isInstanceOf(io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException.class)
                .hasMessageContaining("paid")
                .hasMessageContaining("cannot be negative");

        verify(headerDao, never()).merge(any(BillingONCHeader1.class));
    }

    // ---- updateBillingOneItem (round-10 dropped throws ParseException) ----

    @Test
    void shouldMergeItem_whenUpdateBillingOneItemServiceDateValid() {
        BillingONItem existing = new BillingONItem();
        when(itemDao.find("1")).thenReturn(existing);
        BillingClaimItemDto i = itemDto();
        i.setService_date("2026-01-15");

        boolean ret = persister.updateBillingOneItem(i);

        assertThat(ret).isTrue();
        verify(itemDao).merge(existing);
    }

    @Test
    void shouldThrowAndNotMerge_whenUpdateBillingOneItemServiceDateMalformed() {
        BillingONItem existing = new BillingONItem();
        when(itemDao.find("1")).thenReturn(existing);
        BillingClaimItemDto i = itemDto();
        i.setService_date("not-a-date");

        assertThatThrownBy(() -> persister.updateBillingOneItem(i))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not-a-date");

        verify(itemDao, never()).merge(any(BillingONItem.class));
    }

    // ---- helpers ---------------------------------------------------------

    private static BillingClaimHeaderDto fullHeaderDto(String admissionDate,
                                                       String billingDate,
                                                       String billingTime) {
        BillingClaimHeaderDto h = headerDto(admissionDate, billingDate);
        h.setBilling_time(billingTime);
        h.setDemographic_no("7");
        h.setAppointment_no("0");
        h.setTotal("33.70");
        h.setPaid("0");
        h.setStatus("O");
        h.setSex("M");
        h.setHin("");
        h.setVer("");
        h.setDob("");
        h.setPayee("");
        h.setRef_lab_num("");
        h.setDemographic_name("");
        h.setProvider_no("999998");
        h.setProvider_ohip_no("");
        h.setProvider_rma_no("");
        h.setApptProvider_no("");
        h.setAsstProvider_no("");
        h.setTransc_id("");
        h.setRec_id("");
        return h;
    }

    private static BillingClaimHeaderDto headerDto(String admissionDate, String billingDate) {
        BillingClaimHeaderDto h = new BillingClaimHeaderDto();
        h.setId("100");
        h.setAdmission_date(admissionDate);
        h.setBilling_date(billingDate);
        h.setComment("");
        h.setClinic("");
        h.setCreator("999998");
        h.setDemographic_no("7");
        h.setFacilty_num("");
        h.setMan_review("");
        h.setProviderNo("999998");
        h.setProvince("ON");
        h.setPay_program("HCP");
        h.setRef_num("");
        h.setLocation("");
        h.setVisittype("");
        return h;
    }

    private static BillingClaimItemDto itemDto() {
        BillingClaimItemDto i = new BillingClaimItemDto();
        i.setId("1");
        i.setService_code("A001A");
        i.setFee("33.70");
        i.setSer_num("1");
        i.setDx("V70");
        i.setStatus("O");
        return i;
    }
}
