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

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BillingPaymentSaveService} — the atomic
 * 10-write payment save service. Pins:
 * <ul>
 *   <li>BillingValidationException when bill row missing (concurrent delete)</li>
 *   <li>Happy-path delegation order: header merge → ext upserts → payment row → per-item rows</li>
 *   <li>Zero sums skip ext upserts BUT pay-method always upserts</li>
 *   <li>Unknown selection silently skipped (does NOT fail the whole batch)</li>
 *   <li>Per-item payment+discount=0 short-circuit</li>
 *   <li>Mid-loop DAO failure propagates so @Transactional rolls back</li>
 * </ul>
 */
@DisplayName("BillingPaymentSaveService")
@Tag("unit")
@Tag("billing")
class BillingPaymentSaveServiceUnitTest extends CarlosUnitTestBase {

    @Mock private BillingONCHeader1Dao bCh1Dao;
    @Mock private BillingONExtDao bExtDao;
    @Mock private BillingONItemDao bItemDao;
    @Mock private BillingONPaymentDao bPaymentDao;
    @Mock private BillingOnItemPaymentDao bItemPaymentDao;
    @Mock private BillingOnTransactionDao bTransactionDao;
    @Mock private BillingThirdPartyService thirdPartyService;

    private BillingPaymentSaveService svc;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        svc = new BillingPaymentSaveService(bCh1Dao, bExtDao, bItemDao, bPaymentDao,
                bItemPaymentDao, bTransactionDao, thirdPartyService);
        // Default: ext lookups return zero (mimics no-prior-ext-row).
        when(bExtDao.getAccountVal(org.mockito.ArgumentMatchers.anyInt(), anyString()))
                .thenReturn(BigDecimal.ZERO);
        // Default: parent payment persist assigns a generated id (mimics Hibernate).
        doAnswer(inv -> {
            assignIdField(inv.getArgument(0), 9001);
            return null;
        }).when(bPaymentDao).persist(any(BillingONPayment.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mockitoCloseable != null) mockitoCloseable.close();
    }

    @Test
    void shouldThrowBillingValidationException_whenBillRowMissing() {
        when(bCh1Dao.findForUpdate(101)).thenReturn(null);
        BillingPaymentSaveService.Command cmd = sampleCommand(101, List.of());

        assertThatThrownBy(() -> svc.saveThirdPartyPayment(cmd))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("101");

        // No writes attempted on missing-bill path.
        verify(bCh1Dao, never()).merge(any());
        verify(bPaymentDao, never()).persist(any());
        verify(bItemPaymentDao, never()).persist(any());
    }

    @Test
    void shouldLockAndMergeHeader_whenStatusOnlyUpdateRequested() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(BigDecimal.ZERO, 7);
        ch1.setStatus("O");
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);

        svc.updateStatusOnly(101, "S");

        assertThat(ch1.getStatus()).isEqualTo("S");
        verify(bCh1Dao).findForUpdate(101);
        verify(bCh1Dao).merge(ch1);
        verify(bPaymentDao, never()).persist(any());
        verify(thirdPartyService, never()).keyExists(anyString(), anyString());
    }

    @Test
    void shouldDelegateInOrder_whenAllSumsPositive() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(new BigDecimal("0.00"), 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);
        when(bExtDao.getAccountVal(eq(101), anyString())).thenReturn(BigDecimal.ZERO);
        BillingONItem item = itemForBill(101);
        when(bItemDao.find(11)).thenReturn(item);
        when(bTransactionDao.getTransTemplate(eq(ch1), eq(item), any(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new BillingOnTransaction());

        BillingPaymentSaveService.Line line = new BillingPaymentSaveService.Line(
                11, "payment", new BigDecimal("80.00"), new BigDecimal("0.00"));
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(line));

        svc.saveThirdPartyPayment(cmd);

        // Payment-key ext upsert + header.paid bumped + header merge happens before payment-row persist.
        InOrder order = inOrder(thirdPartyService, bCh1Dao, bPaymentDao, bItemPaymentDao, bTransactionDao);
        order.verify(thirdPartyService).keyExists("101", BillingONExtDao.KEY_PAYMENT);
        order.verify(bCh1Dao).merge(ch1);
        // Pay-method ext-key upsert is unconditional.
        order.verify(thirdPartyService).keyExists("101", BillingONExtDao.KEY_PAY_METHOD);
        order.verify(bPaymentDao).persist(any(BillingONPayment.class));
        order.verify(bItemPaymentDao).persist(any(BillingOnItemPayment.class));
        order.verify(bTransactionDao).persist(any(BillingOnTransaction.class));
    }

    @Test
    void shouldThrowBillingValidationException_beforeHeaderPaidChangesWhenItemRowMissing() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(new BigDecimal("20.00"), 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);
        when(bItemDao.find(11)).thenReturn(null);

        BillingPaymentSaveService.Line line = new BillingPaymentSaveService.Line(
                11, "payment", new BigDecimal("80.00"), new BigDecimal("0.00"));
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(line));

        assertThatThrownBy(() -> svc.saveThirdPartyPayment(cmd))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("11");

        assertThat(ch1.getPaid()).isEqualByComparingTo("20.00");
        verify(bCh1Dao, never()).merge(any());
        verify(thirdPartyService, never()).keyExists(anyString(), anyString());
        verify(bPaymentDao, never()).persist(any());
        verify(bItemPaymentDao, never()).persist(any());
        verify(bTransactionDao, never()).persist(any());
    }

    @Test
    void shouldThrowBillingValidationException_whenItemBelongsToDifferentBill() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(new BigDecimal("20.00"), 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);
        when(bItemDao.find(11)).thenReturn(itemForBill(202));

        BillingPaymentSaveService.Line line = new BillingPaymentSaveService.Line(
                11, "payment", new BigDecimal("80.00"), new BigDecimal("0.00"));
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                new BigDecimal("80.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(line));

        assertThatThrownBy(() -> svc.saveThirdPartyPayment(cmd))
                .isInstanceOf(BillingValidationException.class)
                .hasMessageContaining("does not belong to bill 101");

        assertThat(ch1.getPaid()).isEqualByComparingTo("20.00");
        verify(bCh1Dao, never()).merge(any());
        verify(thirdPartyService, never()).keyExists(anyString(), anyString());
        verify(bPaymentDao, never()).persist(any());
        verify(bItemPaymentDao, never()).persist(any());
        verify(bTransactionDao, never()).persist(any());
    }

    @Test
    void shouldSkipZeroSumExtUpserts_butAlwaysUpsertPayMethod() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(BigDecimal.ZERO, 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);

        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of());

        svc.saveThirdPartyPayment(cmd);

        // None of the 4 conditional ext upserts fire when their sum is zero.
        verify(thirdPartyService, never()).keyExists("101", BillingONExtDao.KEY_PAYMENT);
        verify(thirdPartyService, never()).keyExists("101", BillingONExtDao.KEY_DISCOUNT);
        verify(thirdPartyService, never()).keyExists("101", BillingONExtDao.KEY_REFUND);
        verify(thirdPartyService, never()).keyExists("101", BillingONExtDao.KEY_CREDIT);
        // Pay-method is unconditional.
        verify(thirdPartyService, times(1)).keyExists("101", BillingONExtDao.KEY_PAY_METHOD);
        // Header merge skipped because nothing changed.
        verify(bCh1Dao, never()).merge(any());
        // Parent payment row still persists (caller intent: this is the audit trail).
        verify(bPaymentDao, times(1)).persist(any(BillingONPayment.class));
    }

    @Test
    void shouldSilentlySkipUnknownSelection_andNotFailBatch() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(BigDecimal.ZERO, 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);
        when(bItemDao.find(11)).thenReturn(new BillingONItem());

        BillingPaymentSaveService.Line unknownLine = new BillingPaymentSaveService.Line(
                11, "transfer", new BigDecimal("10.00"), BigDecimal.ZERO); // unknown selection
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(unknownLine));

        svc.saveThirdPartyPayment(cmd);

        // Parent payment persists; the unknown-selection line is silently skipped.
        verify(bPaymentDao, times(1)).persist(any(BillingONPayment.class));
        verify(bItemPaymentDao, never()).persist(any());
        verify(bTransactionDao, never()).persist(any());
    }

    @Test
    void shouldSkipPaymentLine_whenAmountAndDiscountBothZero() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(BigDecimal.ZERO, 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);
        when(bItemDao.find(11)).thenReturn(new BillingONItem());

        BillingPaymentSaveService.Line zeroLine = new BillingPaymentSaveService.Line(
                11, "payment", BigDecimal.ZERO, BigDecimal.ZERO);
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, List.of(zeroLine));

        svc.saveThirdPartyPayment(cmd);

        // Both amount and discount zero on a "payment" line → no ItemPayment row.
        verify(bItemPaymentDao, never()).persist(any());
    }

    @Test
    void shouldPropagate_whenItemPaymentDaoThrowsMidLoop() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(BigDecimal.ZERO, 7);
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);
        when(bItemDao.find(11)).thenReturn(itemForBill(101));
        doThrow(new RuntimeException("ip-fail")).when(bItemPaymentDao).persist(any());

        BillingPaymentSaveService.Line line = new BillingPaymentSaveService.Line(
                11, "refund", new BigDecimal("5.00"), BigDecimal.ZERO);
        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5.00"), BigDecimal.ZERO,
                null, List.of(line));

        assertThatThrownBy(() -> svc.saveThirdPartyPayment(cmd))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ip-fail");

        // Parent payment DID persist before the throw — Spring's @Transactional
        // proxy rolls it back, NOT the service.
        verify(bPaymentDao, times(1)).persist(any(BillingONPayment.class));
    }

    @Test
    void shouldUpdateHeaderStatus_whenNewStatusDiffers() {
        BillingONCHeader1 ch1 = headerWithPaidAndDemo(BigDecimal.ZERO, 7);
        ch1.setStatus("O");
        when(bCh1Dao.findForUpdate(101)).thenReturn(ch1);

        BillingPaymentSaveService.Command cmd = new BillingPaymentSaveService.Command(
                101, new Date(), "999998", 1, "1",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "S", List.of());

        svc.saveThirdPartyPayment(cmd);

        assertThat(ch1.getStatus()).isEqualTo("S");
        verify(bCh1Dao, times(1)).merge(ch1);
    }

    private static BillingONCHeader1 headerWithPaidAndDemo(BigDecimal paid, int demoNo) {
        BillingONCHeader1 h = new BillingONCHeader1();
        try {
            java.lang.reflect.Field paidField = BillingONCHeader1.class.getDeclaredField("paid");
            paidField.setAccessible(true);
            paidField.set(h, paid);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("BillingONCHeader1.paid field structure changed?", e);
        }
        h.setDemographicNo(demoNo);
        return h;
    }

    private static BillingONItem itemForBill(int billNo) {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(billNo);
        return item;
    }

    private static void assignIdField(Object entity, int id) {
        try {
            java.lang.reflect.Field f = entity.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("id field structure changed on " + entity.getClass(), e);
        }
    }

    private static BillingPaymentSaveService.Command sampleCommand(int billNo,
                                                                   List<BillingPaymentSaveService.Line> items) {
        return new BillingPaymentSaveService.Command(
                billNo, new Date(), "999998", 1, "1",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, items);
    }
}
