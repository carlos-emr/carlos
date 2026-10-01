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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
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

@DisplayName("PortalInviteCodeSweeper")
@Tag("unit")
class PortalInviteCodeSweeperUnitTest extends CarlosUnitTestBase {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final Duration IDLE = Duration.ofMinutes(15);

    private EmailLogDao emailLogs;
    private PortalInviteCodeSweeper sweeper;

    @BeforeEach
    void setUp() {
        emailLogs = mock(EmailLogDao.class);
        sweeper = new PortalInviteCodeSweeper(emailLogs, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("should query all ages using bounded pages and the idle cutoff")
    void shouldIncludeOldCodes_whenSweeping() {
        sweeper.forgetLeftoverCodes(IDLE);
        verify(emailLogs).findIdsByTransactionTypeChangedBeforeWithOtherBody(TransactionType.PORTAL_INVITE,
                Date.from(NOW.minus(IDLE)), PortalInviteEmailComposer.CODE_FORGOTTEN, 0, 200);
    }

    @Test
    @DisplayName("should retry after a transient database outage without a new invitation")
    void shouldRetry_whenAnEarlierScheduledRunFails() {
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(List.of(1));
        sweeper.run();
        sweeper.run();
        verify(emailLogs).replaceBodyIfUnchangedBefore(1, TransactionType.PORTAL_INVITE,
                Date.from(NOW.minus(IDLE)), PortalInviteEmailComposer.CODE_FORGOTTEN);
    }

    @Test
    @DisplayName("should count conditional writes and continue after a failed row")
    void shouldContinue_whenOneRowCannotBeCleared() {
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(1, 2, 3));
        when(emailLogs.replaceBodyIfUnchangedBefore(org.mockito.ArgumentMatchers.eq(1), any(), any(), any()))
                .thenThrow(new IllegalStateException("lock timeout"));
        when(emailLogs.replaceBodyIfUnchangedBefore(org.mockito.ArgumentMatchers.eq(2), any(), any(), any()))
                .thenReturn(0);
        when(emailLogs.replaceBodyIfUnchangedBefore(org.mockito.ArgumentMatchers.eq(3), any(), any(), any()))
                .thenReturn(1);
        assertThat(sweeper.forgetLeftoverCodes(IDLE)).isOne();
    }

    @Test
    @DisplayName("should advance beyond a full failed batch so older failures cannot starve later rows")
    void shouldContinuePaging_whenAFullBatchFails() {
        List<Integer> first = java.util.stream.IntStream.rangeClosed(1, 200).boxed().toList();
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.eq(200))).thenReturn(first);
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(200), org.mockito.ArgumentMatchers.eq(200))).thenReturn(List.of(201));
        when(emailLogs.replaceBodyIfUnchangedBefore(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("write failed"));
        assertThat(sweeper.forgetLeftoverCodes(IDLE)).isZero();
        verify(emailLogs, never()).replaceBodyIfUnchangedBefore(org.mockito.ArgumentMatchers.eq(201), any(), any(), any());
        assertThat(sweeper.forgetLeftoverCodes(IDLE)).isZero();
        verify(emailLogs).replaceBodyIfUnchangedBefore(201, TransactionType.PORTAL_INVITE,
                Date.from(NOW.minus(IDLE)), PortalInviteEmailComposer.CODE_FORGOTTEN);
    }

    @Test
    @DisplayName("should touch no email when none qualifies")
    void shouldChangeNothing_whenNoEmailQualifies() {
        assertThat(sweeper.forgetLeftoverCodes(IDLE)).isZero();
        verify(emailLogs, never()).replaceBodyIfUnchangedBefore(any(), any(), any(), any());
    }
    @Test
    @DisplayName("should register periodic retries after startup failure and cancel them on shutdown")
    void shouldManageScheduledRetries_whenTheWebappStartsAndStops() {
        org.springframework.scheduling.TaskScheduler scheduler = mock(org.springframework.scheduling.TaskScheduler.class);
        java.util.concurrent.ScheduledFuture<?> future = mock(java.util.concurrent.ScheduledFuture.class);
        registerMock(EmailLogDao.class, emailLogs);
        registerMock(org.springframework.scheduling.TaskScheduler.class, scheduler);
        when(emailLogs.findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenThrow(new IllegalStateException("database unavailable"));
        org.mockito.Mockito.doReturn(future).when(scheduler).scheduleWithFixedDelay(any(Runnable.class),
                any(Instant.class), org.mockito.ArgumentMatchers.eq(PortalInviteCodeSweeper.INTERVAL));
        var listener = new io.github.carlos_emr.carlos.utility.ContextStartupListener();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(listener, "forgetLeftoverPortalInviteCodes");
        org.mockito.ArgumentCaptor<Runnable> task = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).scheduleWithFixedDelay(task.capture(), any(Instant.class),
                org.mockito.ArgumentMatchers.eq(PortalInviteCodeSweeper.INTERVAL));
        org.mockito.Mockito.doReturn(List.of(1)).when(emailLogs)
                .findIdsByTransactionTypeChangedBeforeWithOtherBody(any(), any(), any(),
                        org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
        task.getValue().run();
        verify(emailLogs).replaceBodyIfUnchangedBefore(org.mockito.ArgumentMatchers.eq(1), any(), any(), any());
        var context = mock(jakarta.servlet.ServletContext.class);
        try (var misc = org.mockito.Mockito.mockStatic(io.github.carlos_emr.carlos.utility.MiscUtils.class)) {
            listener.contextDestroyed(new jakarta.servlet.ServletContextEvent(context));
        }
        verify(future).cancel(false);
    }

}
