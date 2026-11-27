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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalStaffContext;
import io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** A saved payment is never undone by the portal: nothing here throws, and the portal is told only by whoever may. */
@Tag("unit")
@Tag("patient-portal")
class PortalPaymentSettlementServiceUnitTest {
    private final PortalPaymentRequestService requests = mock(PortalPaymentRequestService.class);
    private final PortalStaffContextResolver resolver = mock(PortalStaffContextResolver.class);
    private final LoggedInInfo session = mock(LoggedInInfo.class);
    private final PatientPortalStaffContext staff = new PatientPortalStaffContext("999998", "Synthetic Provider",
            Set.of(PatientPortalStaffContext.PERMISSION_PAYMENT_MANAGE));

    @Test
    void shouldDoNothing_whenThePortalIsNotConfigured() {
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> false, Runnable::run);

        assertThat(service.afterPaymentSaved(session, 55, 123)).isZero();

        verifyNoInteractions(requests, resolver);
    }

    @Test
    void shouldCloseLocallyThenTellThePortal_whenTheUserMayUseThePaymentObject() {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), 123)).thenReturn(staff);
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, Runnable::run);

        assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);

        verify(requests).settleLocally(55);
        verify(requests).reportPaid(same(staff), eq(55));
    }

    @Test
    void shouldStillCloseLocallyAndSendNothing_whenTheUserHasNoPortalPrivilege() {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(any(), any(), eq(123))).thenThrow(new SecurityException("missing required sec object"));
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, Runnable::run);

        assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);

        verify(requests).settleLocally(55);
        verify(requests, never()).reportPaid(any(), eq(55));
    }

    @Test
    void shouldTellThePortalOnTheBackgroundThread_notBeforeTheAnswerIsReturned() {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), 123)).thenReturn(staff);
        List<Runnable> queued = new ArrayList<>();
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, queued::add);

        assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);

        // The local close is done and the answer is back; the portal has not been called yet.
        verify(requests).settleLocally(55);
        verify(requests, never()).reportPaid(any(), eq(55));
        assertThat(queued).hasSize(1);
        queued.getFirst().run();
        verify(requests).reportPaid(same(staff), eq(55));
    }

    @Test
    void shouldLeaveTheRowForTheRetry_whenTheReportIsRejected() {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), 123)).thenReturn(staff);
        java.util.concurrent.Executor full = task -> {
            throw new java.util.concurrent.RejectedExecutionException("queue full");
        };
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, full);

        // The payment's local close stands and nothing throws; the report is simply not sent now. The
        // row stays paid and unsynced, which is what findPaidNotSynced (next view or save) picks up.
        assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);

        verify(requests).settleLocally(55);
        verify(requests, never()).reportPaid(any(), eq(55));
    }

    @Test
    void shouldDropReportsBeyondTheBoundedQueue_withoutBlockingOrThrowing() throws Exception {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), 123)).thenReturn(staff);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            started.countDown();
            release.await();
            return 0;
        }).when(requests).reportPaid(any(), eq(55));
        java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L,
                java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(1),
                new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        try {
            PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, pool);

            // One running (blocked in the slow portal), one queued, the third is dropped: all return.
            assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);
            assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);
            assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);
            assertThat(pool.getQueue()).hasSize(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void shouldReturnTheSaveWhileThePortalIsStillSlow() throws Exception {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), 123)).thenReturn(staff);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch finished = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            release.await();
            finished.countDown();
            return 1;
        }).when(requests).reportPaid(any(), eq(55));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, pool);

            // The portal call is blocked until released: the save's answer must not wait for it.
            assertThat(java.util.concurrent.CompletableFuture.supplyAsync(() -> service.afterPaymentSaved(session, 55, 123))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(finished.getCount()).isEqualTo(1);

            release.countDown();
            assertThat(finished.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            verify(requests).reportPaid(same(staff), eq(55));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void shouldCreateOnePoolWithTheBean_andShutItDownWithTheContext() {
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests);

        // Not configured here, so nothing runs; the pool exists only for the bean's life.
        assertThat(service.afterPaymentSaved(session, 55, 123)).isZero();
        service.shutdown();
        service.shutdown();
    }

    @Test
    void shouldNeverThrowFromTheBackgroundThread_whenTheReportFails() {
        when(requests.settleLocally(55)).thenReturn(1);
        when(resolver.resolveForPatient(session, Set.of(PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST), 123)).thenReturn(staff);
        org.mockito.Mockito.doThrow(new IllegalStateException("portal down")).when(requests).reportPaid(any(), eq(55));
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, Runnable::run);

        assertThat(service.afterPaymentSaved(session, 55, 123)).isEqualTo(1);
    }

    @Test
    void shouldNeverThrow_whenSettlingFails() {
        when(requests.settleLocally(55)).thenThrow(new IllegalStateException("database down"));
        PortalPaymentSettlementService service = new PortalPaymentSettlementService(requests, () -> resolver, () -> true, Runnable::run);

        assertThat(service.afterPaymentSaved(session, 55, 123)).isZero();

        verify(requests, never()).reportPaid(any(), eq(55));
    }
}
