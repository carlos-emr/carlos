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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A device code email whose body still holds its code, because the one write after its send failed, is
 * cleared by this sweep with the same conditional queries as the invitation sweep.
 *
 * @since 2026-11-12
 */
@DisplayName("PortalMyCarlosCodeEmailSweeper")
@Tag("unit")
class PortalMyCarlosCodeEmailSweeperUnitTest extends CarlosUnitTestBase {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    /** A settled email must be unchanged for 5 minutes; any email for the code's 10 minutes plus 20. */
    private static final Date SETTLED_BEFORE = Date.from(NOW.minus(Duration.ofMinutes(5)));
    private static final Date EXPIRED_BEFORE = Date.from(NOW);
    private static final Date AGED_BEFORE = Date.from(NOW.minus(Duration.ofMinutes(30)));

    private EmailLogDao emailLogs;
    private PortalMyCarlosCodeEmailSweeper sweeper;

    @BeforeEach
    void setUp() {
        emailLogs = mock(EmailLogDao.class);
        sweeper = new PortalMyCarlosCodeEmailSweeper(emailLogs, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("should look only for device code emails that still hold their code")
    void shouldSelectDeviceCodeEmails_withTheirOwnCutoffs() {
        sweeper.forgetLeftoverCodes();

        verify(emailLogs).findIdsByTransactionTypeChangedBeforeWithOtherBody(TransactionType.MYCARLOS_CODE,
                SETTLED_BEFORE, EXPIRED_BEFORE, AGED_BEFORE, EmailLog.MYCARLOS_CODE_BODY_FORGOTTEN, 0, 200);
    }

    @Test
    @DisplayName("should recheck the same rule when it clears each selected email")
    void shouldClearEachEmail_withAConditionalWrite() {
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of(7));
        when(emailLogs.replaceBodyIfUnchangedBefore(any(), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(sweeper.forgetLeftoverCodes()).isOne();
        verify(emailLogs).replaceBodyIfUnchangedBefore(7, TransactionType.MYCARLOS_CODE, SETTLED_BEFORE,
                EXPIRED_BEFORE, AGED_BEFORE, EmailLog.MYCARLOS_CODE_BODY_FORGOTTEN);
    }

    @Test
    @DisplayName("should go on after an email that cannot be cleared")
    void shouldContinue_whenOneRowCannotBeCleared() {
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of(1, 2));
        when(emailLogs.replaceBodyIfUnchangedBefore(eq(1), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("lock timeout"));
        when(emailLogs.replaceBodyIfUnchangedBefore(eq(2), any(), any(), any(), any(), any())).thenReturn(1);

        assertThat(sweeper.forgetLeftoverCodes()).isOne();
    }

    @Test
    @DisplayName("should not let a failed run stop later runs")
    void shouldSwallowAFailedRun_soTheScheduleKeepsRunning() {
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(), any(), any(),
                anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of(3));

        sweeper.run();
        sweeper.run();

        verify(emailLogs).replaceBodyIfUnchangedBefore(3, TransactionType.MYCARLOS_CODE, SETTLED_BEFORE,
                EXPIRED_BEFORE, AGED_BEFORE, EmailLog.MYCARLOS_CODE_BODY_FORGOTTEN);
    }

    @Test
    @DisplayName("should page past a full batch of failures so later emails are reached")
    void shouldContinuePaging_whenAFullBatchFails() {
        List<Integer> first = java.util.stream.IntStream.rangeClosed(1, 200).boxed().toList();
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(), any(), any(),
                eq(0), eq(200))).thenReturn(first);
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(), any(), any(),
                eq(200), eq(200))).thenReturn(List.of(201));
        when(emailLogs.replaceBodyIfUnchangedBefore(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("write failed"));

        assertThat(sweeper.forgetLeftoverCodes()).isZero();
        verify(emailLogs, never()).replaceBodyIfUnchangedBefore(eq(201), any(), any(), any(), any(), any());
        assertThat(sweeper.forgetLeftoverCodes()).isZero();
        verify(emailLogs).replaceBodyIfUnchangedBefore(201, TransactionType.MYCARLOS_CODE, SETTLED_BEFORE,
                EXPIRED_BEFORE, AGED_BEFORE, EmailLog.MYCARLOS_CODE_BODY_FORGOTTEN);
    }

    @Test
    @DisplayName("should be scheduled when the webapp starts and cancelled when it stops")
    void shouldScheduleAndCancelTheSweep_whenTheWebappStartsAndStops() {
        org.springframework.scheduling.TaskScheduler scheduler = mock(org.springframework.scheduling.TaskScheduler.class);
        java.util.concurrent.ScheduledFuture<?> future = mock(java.util.concurrent.ScheduledFuture.class);
        registerMock(EmailLogDao.class, emailLogs);
        registerMock(org.springframework.scheduling.TaskScheduler.class, scheduler);
        org.mockito.Mockito.doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class),
                any(Instant.class), eq(PortalMyCarlosCodeEmailSweeper.INTERVAL));
        var listener = new io.github.carlos_emr.carlos.utility.ContextStartupListener();

        org.springframework.test.util.ReflectionTestUtils.invokeMethod(listener, "forgetLeftoverMyCarlosCodes");

        org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(task.capture(), any(Instant.class),
                eq(PortalMyCarlosCodeEmailSweeper.INTERVAL));
        assertThat(task.getValue()).isInstanceOf(PortalMyCarlosCodeEmailSweeper.class);
        var context = mock(jakarta.servlet.ServletContext.class);
        try (var misc = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.utility.MiscUtils.class)) {
            listener.contextDestroyed(new jakarta.servlet.ServletContextEvent(context));
        }
        verify(future).cancel(false);
    }

    @Test
    @DisplayName("should run every 5 minutes and wait out the code's 10-minute life plus 20 minutes")
    void shouldKeepItsCadenceAndCutoffs_forTheDeviceCodeLife() {
        assertThat(PortalMyCarlosCodeEmailSweeper.INTERVAL).isEqualTo(Duration.ofMinutes(5));
        assertThat(PortalMyCarlosCodeEmailSweeper.IDLE).isEqualTo(Duration.ofMinutes(5));
        assertThat(PortalMyCarlosCodeEmailSweeper.CODE_LIFETIME.plus(PortalMyCarlosCodeEmailSweeper.CODE_AGE_MARGIN))
                .isEqualTo(Duration.ofMinutes(30));
    }
}
