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
package io.github.carlos_emr.carlos.integration.patientportal.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnInvoiceTotalsService;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.PortalPaymentRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalException;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalPaymentRequestDto;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalPaymentRequestRequest;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalService;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The amount is the invoice's, the portal is called after the local row exists, a lost answer is
 * repaired by the same operation, and paying never depends on the portal.
 */
@Tag("unit")
@Tag("patient-portal")
class PortalPaymentRequestServiceUnitTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-27T15:00:00Z"), ZoneOffset.UTC);
    private static final LocalDate DUE = LocalDate.parse("2026-12-20");
    private static final PatientPortalStaffContext STAFF = new PatientPortalStaffContext("999998", "Synthetic Provider",
            Set.of(PatientPortalStaffContext.PERMISSION_PAYMENT_MANAGE));

    private final PortalPaymentRequestDao dao = mock(PortalPaymentRequestDao.class);
    private final BillingONCHeader1Dao headers = mock(BillingONCHeader1Dao.class);
    private final BillingOnInvoiceTotalsService totals = mock(BillingOnInvoiceTotalsService.class);
    private final BillingONExtDao extensions = mock(BillingONExtDao.class);
    private final PatientPortalService portal = mock(PatientPortalService.class);
    private PortalPaymentRequestService service;

    @BeforeEach
    void setUp() {
        service = new PortalPaymentRequestService(dao, headers, totals, extensions,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), () -> portal, CLOCK);
        BillingONCHeader1 header = mock(BillingONCHeader1.class);
        when(header.getDemographicNo()).thenReturn(123);
        when(header.getTotal()).thenReturn(new BigDecimal("45.00"));
        when(headers.find(55)).thenReturn(header);
        when(headers.findForUpdate(55)).thenReturn(header);
        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal("45.00"));
        when(extensions.getAccountVal(55, BillingONExtDao.KEY_GST)).thenReturn(new BigDecimal("5.18"));
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of());
        // The database gives a new row its id when it is persisted; the row is then read back by it.
        org.mockito.Mockito.doAnswer(call -> {
            PortalPaymentRequest row = call.getArgument(0);
            row.setId(1L);
            persisted = row;
            return null;
        }).when(dao).persist(any());
        when(dao.findForUpdate(1L)).thenAnswer(call -> persisted);
        when(dao.find(1L)).thenAnswer(call -> persisted);
    }

    private PortalPaymentRequest persisted;

    private PortalPaymentRequest stored(String status) {
        PortalPaymentRequest row = new PortalPaymentRequest();
        row.setId(2L);
        row.setInvoiceRef("inv-ref");
        row.setOperationId("op-ref");
        row.setBillingNo(55);
        row.setDemographicNo(123);
        row.setAmountCents(4500);
        row.setTaxCents(518);
        row.setCategory("uninsured_service");
        row.setDueOn(DUE);
        row.setStatus(status);
        return row;
    }

    private PatientPortalPaymentRequestDto.Creation confirmed(long portalId, boolean created) {
        PatientPortalPaymentRequestDto dto = mock(PatientPortalPaymentRequestDto.class);
        when(dto.id()).thenReturn(portalId);
        return new PatientPortalPaymentRequestDto.Creation(dto, created);
    }

    @Test
    void shouldSendTheInvoicesBalanceAndGstInWholeCents_andCloseThePendingRowOnConfirmation() {
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, true));

        PortalPaymentRequestService.Sent sent = service.send(STAFF, "999998", 123, 55, "uninsured_service", DUE);

        ArgumentCaptor<PatientPortalPaymentRequestRequest> wire =
                ArgumentCaptor.forClass(PatientPortalPaymentRequestRequest.class);
        verify(portal).createPaymentRequest(eq(123), wire.capture(), same(STAFF));
        assertThat(wire.getValue().amountCents()).isEqualTo(4500);
        assertThat(wire.getValue().taxCents()).isEqualTo(518);
        assertThat(wire.getValue().category()).isEqualTo("uninsured_service");
        assertThat(wire.getValue().dueOn()).isEqualTo(DUE);
        assertThat(wire.getValue().invoiceRef()).isEqualTo(persisted.getInvoiceRef()).matches("[0-9a-f-]{36}");
        assertThat(wire.getValue().operationId()).isEqualTo(persisted.getOperationId()).matches("[0-9a-f-]{36}");
        assertThat(wire.getValue().invoiceRef()).isNotEqualTo(wire.getValue().operationId());
        assertThat(persisted.getStatus()).isEqualTo(PortalPaymentRequest.OPEN);
        assertThat(persisted.getPortalRequestId()).isEqualTo(9L);
        assertThat(persisted.getCreatedBy()).isEqualTo("999998");
        assertThat(sent.created()).isTrue();
    }

    @Test
    void shouldSendANewReferenceAndOperation_whenResendingAfterAWithdrawal() {
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, true));
        service.send(STAFF, "999998", 123, 55, "uninsured_service", DUE);
        PortalPaymentRequest first = persisted;
        first.setStatus(PortalPaymentRequest.WITHDRAWN);
        // A withdrawn request is not unsettled: the DAO no longer returns it for reuse.
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of());

        service.send(STAFF, "999998", 123, 55, "uninsured_service", DUE);

        ArgumentCaptor<PatientPortalPaymentRequestRequest> wire =
                ArgumentCaptor.forClass(PatientPortalPaymentRequestRequest.class);
        verify(portal, org.mockito.Mockito.times(2)).createPaymentRequest(eq(123), wire.capture(), same(STAFF));
        PatientPortalPaymentRequestRequest again = wire.getAllValues().get(1);
        assertThat(persisted).isNotSameAs(first);
        assertThat(again.invoiceRef()).isNotEqualTo(wire.getAllValues().get(0).invoiceRef());
        assertThat(again.operationId()).isNotEqualTo(wire.getAllValues().get(0).operationId());
        assertThat(again.invoiceRef()).isEqualTo(persisted.getInvoiceRef());
    }

    @Test
    void shouldScaleTheTaxToThePartStillOwing_whenTheInvoiceIsPartlyPaid() {
        // 100.00 plus 13.00 GST, 56.50 still owing: the patient is told 6.50 of tax, not the whole 13.00.
        BillingONCHeader1 partlyPaid = mock(BillingONCHeader1.class);
        when(partlyPaid.getDemographicNo()).thenReturn(123);
        when(partlyPaid.getTotal()).thenReturn(new BigDecimal("113.00"));
        when(headers.findForUpdate(57)).thenReturn(partlyPaid);
        when(totals.calculateBalanceOwing(57)).thenReturn(new BigDecimal("56.50"));
        when(extensions.getAccountVal(57, BillingONExtDao.KEY_GST)).thenReturn(new BigDecimal("13.00"));
        when(dao.findUnsettledForUpdate(57)).thenReturn(List.of());
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, true));

        service.send(STAFF, "999998", 123, 57, "other", DUE);

        ArgumentCaptor<PatientPortalPaymentRequestRequest> wire =
                ArgumentCaptor.forClass(PatientPortalPaymentRequestRequest.class);
        verify(portal).createPaymentRequest(eq(123), wire.capture(), same(STAFF));
        assertThat(wire.getValue().amountCents()).isEqualTo(5650);
        assertThat(wire.getValue().taxCents()).isEqualTo(650);
    }

    @ParameterizedTest
    @CsvSource({
            // gst, invoice total, owing, amount cents, expected tax cents
            "13.00, 113.00, 113.00, 11300, 1300",  // nothing paid: the whole GST
            "13.00, 113.00, 56.50, 5650, 650",     // half paid: half the GST
            "13.00, 113.00, 0.01, 1, 0",           // a cent owing carries no tax
            "5.18, 45.00, 45.00, 4500, 518",
            "1.00, 3.00, 1.00, 100, 33",           // 0.333.. rounds to the cent
            "0.05, 1.00, 0.50, 50, 2",             // 0.025 is a tie: half even goes to 2
            "0.15, 1.00, 0.50, 50, 8",             // 0.075 is a tie: half even goes to 8
            "20.00, 10.00, 10.00, 1000, 1000",     // never more tax than the amount holds
            "0.00, 45.00, 45.00, 4500, 0",         // no GST on the invoice
            "13.00, 0.00, 5.00, 500, 0",           // an unknown total claims no tax
    })
    void shouldComputeTheTaxPortionProportionallyAndNeverAboveTheAmount(
            String gst, String total, String owing, long amountCents, long expected) {
        assertThat(PortalPaymentRequestService.taxCentsOf(
                new BigDecimal(gst), new BigDecimal(total), new BigDecimal(owing), amountCents)).isEqualTo(expected);
    }

    @Test
    void shouldClaimNoTax_whenTheGstOrTheTotalIsMissing() {
        assertThat(PortalPaymentRequestService.taxCentsOf(null, new BigDecimal("10"), new BigDecimal("10"), 1000)).isZero();
        assertThat(PortalPaymentRequestService.taxCentsOf(new BigDecimal("1"), null, new BigDecimal("10"), 1000)).isZero();
    }

    @Test
    void shouldLockTheInvoiceBeforeLookingForAnOpenRequest_soTwoSendsQueue() {
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, true));

        service.send(STAFF, "999998", 123, 55, "other", DUE);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(headers, dao);
        order.verify(headers).findForUpdate(55);
        order.verify(dao).findUnsettledForUpdate(55);
        order.verify(dao).persist(any());
    }

    @Test
    void shouldReturnTheFirstRequestToASecondSend_thatWaitedForTheInvoiceLock() {
        // The second send gets the lock only after the first committed: it now finds the open request.
        PortalPaymentRequest first = stored("open");
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of(first));
        when(dao.findForUpdate(2L)).thenReturn(first);
        when(dao.find(2L)).thenReturn(first);
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, false));

        PortalPaymentRequestService.Sent second = service.send(STAFF, "999998", 123, 55, "uninsured_service", DUE);

        assertThat(second.request()).isSameAs(first);
        assertThat(second.created()).isFalse();
        verify(dao, never()).persist(any());
    }

    @ParameterizedTest
    @CsvSource({"0.00", "-5.00"})
    void shouldRefuseNothingOwing_beforeCallingThePortal(String owing) {
        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal(owing));

        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 55, "other", DUE))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.NOTHING_OWING));
        verify(portal, never()).createPaymentRequest(anyInt(), any(), any());
        verify(dao, never()).persist(any());
    }

    @Test
    void shouldRefuseAnInvoiceThatBelongsToAnotherPatient_beforeCallingThePortal() {
        assertThatThrownBy(() -> service.send(STAFF, "999998", 124, 55, "other", DUE))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.INVOICE_NOT_FOUND));
        when(headers.findForUpdate(56)).thenReturn(null);
        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 56, "other", DUE))
                .isInstanceOf(PortalPaymentRequestException.class);
        verify(portal, never()).createPaymentRequest(anyInt(), any(), any());
    }

    @Test
    void shouldRefuseAnAmountThePortalCannotTake() {
        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal("100000.01"));

        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 55, "other", DUE))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.AMOUNT_OUT_OF_RANGE));
        verify(portal, never()).createPaymentRequest(anyInt(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"2026-11-26", "2027-11-28"})
    void shouldRefuseADueDateOutsideTodayToAYearAhead(String due) {
        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 55, "other", LocalDate.parse(due)))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.DUE_DATE_INVALID));
        verify(portal, never()).createPaymentRequest(anyInt(), any(), any());
    }

    @Test
    void shouldAcceptTodayAndTheLastAllowedDay() {
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, true));

        service.send(STAFF, "999998", 123, 55, "other", LocalDate.parse("2026-11-27"));
        service.send(STAFF, "999998", 123, 55, "other", LocalDate.parse("2027-11-27"));

        verify(portal, org.mockito.Mockito.times(2)).createPaymentRequest(eq(123), any(), same(STAFF));
    }

    @Test
    void shouldReuseTheOpenRequestOfTheSameValues_andReportItAsNotCreated() {
        PortalPaymentRequest open = stored("open");
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of(open));
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenReturn(confirmed(9, false));
        when(dao.findForUpdate(2L)).thenReturn(open);
        when(dao.find(2L)).thenReturn(open);

        PortalPaymentRequestService.Sent sent = service.send(STAFF, "999998", 123, 55, "uninsured_service", DUE);

        assertThat(sent.created()).isFalse();
        verify(dao, never()).persist(any());
        ArgumentCaptor<PatientPortalPaymentRequestRequest> wire =
                ArgumentCaptor.forClass(PatientPortalPaymentRequestRequest.class);
        verify(portal).createPaymentRequest(eq(123), wire.capture(), same(STAFF));
        assertThat(wire.getValue().operationId()).isEqualTo("op-ref");
        assertThat(wire.getValue().invoiceRef()).isEqualTo("inv-ref");
    }

    @Test
    void shouldRefuseASecondRequestWithOtherValues_untilTheFirstIsWithdrawn() {
        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal("40.00"));
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of(stored("open")));

        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 55, "uninsured_service", DUE))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.OPEN_REQUEST_DIFFERS));
        verify(portal, never()).createPaymentRequest(anyInt(), any(), any());
    }

    @Test
    void shouldLeaveNothingBehind_whenThePatientHasNoPortalAccount() {
        PatientPortalException absent = PatientPortalException.ofStatus(404,
                "/internal/carlos/patients/%d/payment-requests", PatientPortalException.ACCOUNT_NOT_FOUND_DETAIL);
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenThrow(absent);

        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 55, "other", DUE)).isSameAs(absent);

        assertThat(persisted.getStatus()).isEqualTo(PortalPaymentRequest.WITHDRAWN);
        assertThat(persisted.getPortalRequestId()).isNull();
    }

    @Test
    void shouldKeepThePendingRow_whenThePortalCannotBeReached() {
        PatientPortalException down = PatientPortalException.ofTransportFailure(
                "/internal/carlos/patients/%d/payment-requests", new java.io.IOException("down"));
        when(portal.createPaymentRequest(eq(123), any(), same(STAFF))).thenThrow(down);

        assertThatThrownBy(() -> service.send(STAFF, "999998", 123, 55, "other", DUE)).isSameAs(down);

        // The same operation is sent again on the next try, and the portal answers with the original.
        assertThat(persisted.getStatus()).isEqualTo(PortalPaymentRequest.PENDING);
    }

    @Test
    void shouldWithdrawAtThePortalFirst_thenLocally() {
        PortalPaymentRequest open = stored("open");
        open.setPortalRequestId(9L);
        when(dao.find(4L)).thenReturn(open);
        when(dao.findForUpdate(4L)).thenReturn(open);

        PortalPaymentRequest result = service.withdraw(STAFF, 123, 55, 4);

        verify(portal).withdrawPaymentRequest(123, 9L, STAFF);
        assertThat(result.getStatus()).isEqualTo(PortalPaymentRequest.WITHDRAWN);
    }

    @Test
    void shouldNotWithdrawLocally_whenThePortalRefuses() {
        PortalPaymentRequest open = stored("open");
        open.setPortalRequestId(9L);
        when(dao.find(4L)).thenReturn(open);
        when(portal.withdrawPaymentRequest(123, 9L, STAFF)).thenThrow(PatientPortalException.ofStatus(
                409, "/internal/carlos/payment-requests/%d/withdraw",
                PatientPortalService.PAYMENT_REQUEST_STATE_CONFLICT_DETAIL));

        assertThatThrownBy(() -> service.withdraw(STAFF, 123, 55, 4)).isInstanceOf(PatientPortalException.class);

        assertThat(open.getStatus()).isEqualTo(PortalPaymentRequest.OPEN);
    }

    @Test
    void shouldFindThePortalIdByReference_whenTheConfirmationWasLost() {
        PortalPaymentRequest pending = stored("pending");
        pending.setId(4L);
        when(dao.find(4L)).thenReturn(pending);
        when(dao.findForUpdate(4L)).thenReturn(pending);
        PatientPortalPaymentRequestDto listed = mock(PatientPortalPaymentRequestDto.class);
        when(listed.invoiceRef()).thenReturn("inv-ref");
        when(listed.id()).thenReturn(12L);
        when(portal.listPaymentRequests(123, STAFF)).thenReturn(List.of(listed));

        service.withdraw(STAFF, 123, 55, 4);

        verify(portal).withdrawPaymentRequest(123, 12L, STAFF);
        assertThat(pending.getPortalRequestId()).isEqualTo(12L);
        assertThat(pending.getStatus()).isEqualTo(PortalPaymentRequest.WITHDRAWN);
    }

    @Test
    void shouldWithdrawLocallyOnly_whenThePortalNeverSawTheRequest() {
        PortalPaymentRequest pending = stored("pending");
        pending.setId(4L);
        when(dao.find(4L)).thenReturn(pending);
        when(dao.findForUpdate(4L)).thenReturn(pending);
        when(portal.listPaymentRequests(123, STAFF)).thenReturn(List.of());

        service.withdraw(STAFF, 123, 55, 4);

        verify(portal, never()).withdrawPaymentRequest(anyInt(), anyLong(), any());
        assertThat(pending.getStatus()).isEqualTo(PortalPaymentRequest.WITHDRAWN);
    }

    @Test
    void shouldRefuseToWithdrawAPaidRequest_andNeverTouchThePortal() {
        when(dao.find(4L)).thenReturn(stored("paid"));

        assertThatThrownBy(() -> service.withdraw(STAFF, 123, 55, 4))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.REQUEST_PAID));
        verify(portal, never()).withdrawPaymentRequest(anyInt(), anyLong(), any());
    }

    @Test
    void shouldTreatARequestOfAnotherInvoiceOrPatientAsNotFound() {
        when(dao.find(4L)).thenReturn(stored("open"));

        assertThatThrownBy(() -> service.withdraw(STAFF, 123, 56, 4)).isInstanceOf(PortalPaymentRequestException.class);
        assertThatThrownBy(() -> service.withdraw(STAFF, 124, 55, 4)).isInstanceOf(PortalPaymentRequestException.class);
        assertThatThrownBy(() -> service.withdraw(STAFF, 123, 55, 5)).isInstanceOf(PortalPaymentRequestException.class);
        verify(portal, never()).withdrawPaymentRequest(anyInt(), anyLong(), any());
    }

    @Test
    void shouldAnswerARepeatedWithdrawalWithoutCallingThePortal() {
        when(dao.find(4L)).thenReturn(stored("withdrawn"));

        assertThat(service.withdraw(STAFF, 123, 55, 4).getStatus()).isEqualTo(PortalPaymentRequest.WITHDRAWN);
        verify(portal, never()).withdrawPaymentRequest(anyInt(), anyLong(), any());
    }

    @Test
    void shouldSettleOnlyWhenTheBalanceIsZeroOrLess() {
        PortalPaymentRequest open = stored("open");
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of(open));
        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal("0.01"));
        assertThat(service.settleLocally(55)).isZero();
        assertThat(open.getStatus()).isEqualTo(PortalPaymentRequest.OPEN);

        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal("0.00"));
        assertThat(service.settleLocally(55)).isEqualTo(1);
        assertThat(open.getStatus()).isEqualTo(PortalPaymentRequest.PAID);
        assertThat(open.getPaidAt()).isEqualTo(java.util.Date.from(CLOCK.instant()));
        assertThat(open.isPortalSynced()).isFalse();
    }

    @Test
    void shouldSettleAnOverpayment_andDoNothingWhenNothingIsOpen() {
        PortalPaymentRequest open = stored("open");
        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of(open));
        when(totals.calculateBalanceOwing(55)).thenReturn(new BigDecimal("-3.00"));
        assertThat(service.settleLocally(55)).isEqualTo(1);

        when(dao.findUnsettledForUpdate(55)).thenReturn(List.of());
        assertThat(service.settleLocally(55)).isZero();
    }

    @Test
    void shouldReportPaidToThePortalAndMarkItSynced() {
        PortalPaymentRequest paid = stored("paid");
        paid.setPortalRequestId(9L);
        when(dao.findPaidNotSynced(55)).thenReturn(List.of(paid));
        when(dao.findForUpdate(anyLong())).thenReturn(paid);

        assertThat(service.reportPaid(STAFF, 55)).isEqualTo(1);

        verify(portal).markPaymentRequestPaid(123, 9L, STAFF);
        assertThat(paid.isPortalSynced()).isTrue();
    }

    @Test
    void shouldFindThePortalIdByReference_andReportIt_whenThePaidRequestWasNeverConfirmed() {
        // The portal call timed out, so the row never got a portal id; the invoice was then paid.
        PortalPaymentRequest paid = stored("paid");
        paid.setPortalRequestId(null);
        when(dao.findPaidNotSynced(55)).thenReturn(List.of(paid));
        when(dao.findForUpdate(2L)).thenReturn(paid);
        PatientPortalPaymentRequestDto listed = mock(PatientPortalPaymentRequestDto.class);
        when(listed.invoiceRef()).thenReturn("inv-ref");
        when(listed.id()).thenReturn(12L);
        when(portal.listPaymentRequests(123, STAFF)).thenReturn(List.of(listed));

        assertThat(service.reportPaid(STAFF, 55)).isEqualTo(1);

        verify(portal).markPaymentRequestPaid(123, 12L, STAFF);
        assertThat(paid.getPortalRequestId()).isEqualTo(12L);
        assertThat(paid.isPortalSynced()).isTrue();
    }

    @Test
    void shouldJustMarkItSynced_whenThePortalNeverStoredTheUnconfirmedRequest() {
        PortalPaymentRequest paid = stored("paid");
        paid.setPortalRequestId(null);
        when(dao.findPaidNotSynced(55)).thenReturn(List.of(paid));
        when(dao.findForUpdate(2L)).thenReturn(paid);
        when(portal.listPaymentRequests(123, STAFF)).thenReturn(List.of());

        assertThat(service.reportPaid(STAFF, 55)).isEqualTo(1);

        verify(portal, never()).markPaymentRequestPaid(anyInt(), anyLong(), any());
        assertThat(paid.isPortalSynced()).isTrue();
    }

    @Test
    void shouldStopAskingTheRequestIsPaid_whenThePortalRefusesBecauseItWasWithdrawnThere() {
        // Withdrawn at the portal while the payment was being saved: the portal answers 409 for ever.
        PortalPaymentRequest paid = stored("paid");
        paid.setPortalRequestId(9L);
        when(dao.findPaidNotSynced(55)).thenReturn(List.of(paid));
        when(dao.findForUpdate(2L)).thenReturn(paid);
        when(portal.markPaymentRequestPaid(123, 9L, STAFF)).thenThrow(PatientPortalException.ofStatus(
                409, "/internal/carlos/payment-requests/%d/paid",
                PatientPortalService.PAYMENT_REQUEST_STATE_CONFLICT_DETAIL));

        assertThat(service.reportPaid(STAFF, 55)).isZero();

        assertThat(paid.isPortalSynced()).isTrue();
        assertThat(paid.getStatus()).isEqualTo(PortalPaymentRequest.PAID);
    }

    @Test
    void shouldNeverThrow_whenThePortalCannotBeToldAboutAPayment_andLeaveItForARetry() {
        PortalPaymentRequest paid = stored("paid");
        paid.setPortalRequestId(9L);
        when(dao.findPaidNotSynced(55)).thenReturn(List.of(paid));
        when(portal.markPaymentRequestPaid(anyInt(), anyLong(), any())).thenThrow(PatientPortalException.ofTransportFailure(
                "/internal/carlos/payment-requests/%d/paid", new java.io.IOException("down")));

        assertThat(service.reportPaid(STAFF, 55)).isZero();

        assertThat(paid.isPortalSynced()).isFalse();
        assertThat(paid.getStatus()).isEqualTo(PortalPaymentRequest.PAID);
    }

    @Test
    void shouldSendNothing_whenTheCallerHoldsNoPortalPrivilege() {
        assertThat(service.reportPaid(null, 55)).isZero();

        verify(portal, never()).markPaymentRequestPaid(anyInt(), anyLong(), any());
        verify(dao, never()).findPaidNotSynced(anyInt());
    }

    @Test
    void shouldSettleAndRepairBeforeListing() {
        PortalPaymentRequest paid = stored("paid");
        when(dao.findPaidNotSynced(55)).thenReturn(List.of());
        when(dao.findByBillingNo(55)).thenReturn(List.of(paid));

        assertThat(service.listForInvoice(STAFF, 123, 55)).containsExactly(paid);

        verify(dao).findUnsettledForUpdate(55);
        verify(dao).findPaidNotSynced(55);
    }

    @Test
    void shouldSettleAndReportNothing_whenTheInvoiceIsNotTheSelectedPatients() {
        assertThatThrownBy(() -> service.listForInvoice(STAFF, 124, 55))
                .isInstanceOfSatisfying(PortalPaymentRequestException.class,
                        refused -> assertThat(refused.reason())
                                .isEqualTo(PortalPaymentRequestException.Reason.INVOICE_NOT_FOUND));

        verify(dao, never()).findUnsettledForUpdate(anyInt());
        verify(dao, never()).findPaidNotSynced(anyInt());
        verify(portal, never()).markPaymentRequestPaid(anyInt(), anyLong(), any());
    }

    @ParameterizedTest
    @CsvSource({"45.00,4500", "0.01,1", "100000.00,10000000", "12.345,1235", "12.344,1234"})
    void shouldConvertMoneyToWholeCentsRoundingHalfUp(String money, long cents) {
        assertThat(PortalPaymentRequestService.cents(new BigDecimal(money))).isEqualTo(cents);
    }
}
