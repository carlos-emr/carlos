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
package io.github.carlos_emr.carlos.sms.assembler;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsQueueProcessingService;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SmsQueueViewModelAssembler}: per-provider grouping, the overdue and stale thresholds, the scheduler
 * state, and the redaction of every row (last four digits only, no message text).
 *
 * @since 2026-09-28
 */
@Tag("unit")
@Tag("service")
@DisplayName("SMS queue view model assembler")
class SmsQueueViewModelAssemblerUnitTest {
    private static final Instant NOW = Instant.parse("2026-09-28T14:00:00Z");
    // Synthetic number; the digits before the last four must never reach the model.
    private static final String RECIPIENT = "+16135559876";
    private static final List<SmsStatus> BLOCKED = List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED);

    private final SmsTransactionDao dao = mock(SmsTransactionDao.class);
    private final SmsConfigService configService = mock(SmsConfigService.class);
    private final SmsQueueScheduler scheduler = mock(SmsQueueScheduler.class);

    @Test
    @DisplayName("should list every SMS provider in enum order with its own counts and every status in enum order")
    void shouldGroupCounts_perProvider() {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "QUEUED", 2),
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "CONSENT_BLOCKED", 3),
                new SmsQueueCountDto(SmsProviderType.STUB, "OPTOUT_BLOCKED", 4),
                new SmsQueueCountDto(SmsProviderType.VOIPMS, "DELIVERED", 5)));

        SmsQueueViewModel model = assembler(() -> false).assemble(true);

        assertThat(model.providers()).extracting(SmsQueueViewModel.ProviderQueue::providerType)
                .containsExactly("VOIPMS", "CLOUDLI", "STUB");
        SmsQueueViewModel.ProviderQueue stub = provider(model, "STUB");
        assertThat(stub.outboundTotal()).isEqualTo(10);
        assertThat(stub.failedCount()).isEqualTo(1);
        assertThat(stub.blockedCount()).isEqualTo(7);
        // RECEIVED only describes inbound messages, so an outbound view leaves it out while it is zero.
        assertThat(stub.statusCounts()).containsExactly(
                new SmsQueueViewModel.StatusCount("QUEUED", 2),
                new SmsQueueViewModel.StatusCount("SENDING", 0),
                new SmsQueueViewModel.StatusCount("SENT", 0),
                new SmsQueueViewModel.StatusCount("DELIVERED", 0),
                new SmsQueueViewModel.StatusCount("FAILED", 1),
                new SmsQueueViewModel.StatusCount("CONSENT_BLOCKED", 3),
                new SmsQueueViewModel.StatusCount("OPTOUT_BLOCKED", 4));
        assertThat(provider(model, "VOIPMS").outboundTotal()).isEqualTo(5);
        assertThat(provider(model, "CLOUDLI").outboundTotal()).isZero();
        assertThat(provider(model, "CLOUDLI").statusCounts()).allSatisfy(count -> assertThat(count.count()).isZero());
    }

    @Test
    @DisplayName("should query the lists only for providers whose counts show something, capped at 50 rows")
    void shouldQueryLists_onlyWhenCountsArePositive() {
        when(dao.countOutboundByProviderAndStatus()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1),
                new SmsQueueCountDto(SmsProviderType.STUB, "OPTOUT_BLOCKED", 1)));
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.countStaleSendingOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));

        assembler(() -> false).assemble(true);

        verify(dao).findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50));
        verify(dao).findStaleSendingOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50));
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), 50);
        verify(dao).findRecentOutboundByStatuses(SmsProviderType.STUB, BLOCKED, 50);
        verify(dao, never()).findOverdueQueuedOutbound(eq(SmsProviderType.VOIPMS), any(), anyInt());
        verify(dao, never()).findStaleSendingOutbound(eq(SmsProviderType.CLOUDLI), any(), anyInt());
        verify(dao, never()).findRecentOutboundByStatuses(eq(SmsProviderType.VOIPMS), any(), anyInt());
    }

    @Test
    @DisplayName("should count overdue from five minutes ago and stale sends by the worker's own threshold")
    void shouldUseThresholds_fromOverdueGraceAndWorkerTimeout() {
        SmsQueueViewModel model = assembler(() -> false).assemble(true);

        verify(dao).countOverdueQueuedOutboundByProvider(Date.from(NOW.minus(Duration.ofMinutes(5))));
        verify(dao).countStaleSendingOutboundByProvider(
                Date.from(NOW.minus(SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT)));
        assertThat(model.overdueMinutes()).isEqualTo(5);
        assertThat(model.staleMinutes()).isEqualTo(SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT.toMinutes());
        assertThat(model.listLimit()).isEqualTo(50);
        assertThat(model.generatedAt()).isEqualTo("2026-09-28 14:00");
    }

    @Test
    @DisplayName("should count codes largest first and group a missing code under an empty code")
    void shouldSortCodeCounts_largestFirst() {
        when(dao.countFailedOutboundByProviderAndErrorCode()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.STUB, null, 2),
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_B", 5),
                new SmsQueueCountDto(SmsProviderType.STUB, "ERR_A", 5)));
        when(dao.countConsentBlockedOutboundByProviderAndReason()).thenReturn(List.of(
                new SmsQueueCountDto(SmsProviderType.VOIPMS, "SMS_CONSENT_OPT_OUT", 1)));

        SmsQueueViewModel model = assembler(() -> false).assemble(true);

        assertThat(provider(model, "STUB").failedByErrorCode()).containsExactly(
                new SmsQueueViewModel.CodeCount("ERR_A", 5),
                new SmsQueueViewModel.CodeCount("ERR_B", 5),
                new SmsQueueViewModel.CodeCount("", 2));
        assertThat(provider(model, "STUB").blockedByReason()).isEmpty();
        assertThat(provider(model, "VOIPMS").blockedByReason())
                .containsExactly(new SmsQueueViewModel.CodeCount("SMS_CONSENT_OPT_OUT", 1));
    }

    @Test
    @DisplayName("should show only the recipient's last four digits and no other digit of the number anywhere")
    void shouldMaskRecipient_toLastFourDigits() {
        when(dao.countOutboundByProviderAndStatus())
                .thenReturn(List.of(new SmsQueueCountDto(SmsProviderType.STUB, "FAILED", 1)));
        when(dao.findRecentOutboundByStatuses(SmsProviderType.STUB, List.of(SmsStatus.FAILED), 50))
                .thenReturn(List.of(new SmsQueueRowDto(42L, SmsProviderType.STUB, SmsStatus.FAILED, 4242, RECIPIENT,
                        3, "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED", null,
                        NOW.minus(Duration.ofHours(2)), NOW.minus(Duration.ofHours(1)), null,
                        NOW.minus(Duration.ofMinutes(61)))));

        SmsQueueViewModel model = assembler(() -> false).assemble(true);

        SmsQueueViewModel.Row row = provider(model, "STUB").recentFailed().get(0);
        assertThat(row).isEqualTo(new SmsQueueViewModel.Row("42", "STUB", "FAILED", "2026-09-28 12:00",
                "2026-09-28 13:00", "", "2026-09-28 12:59", 3, "QUEUE_PROVIDER_FAILURE_RETRY_EXHAUSTED", "", "4242",
                "***9876"));
        assertThat(model.toString()).doesNotContain("6135559876", "613555", "5559876", "+1613");
    }

    @Test
    @DisplayName("should show nothing for a number too short to mask")
    void shouldShowNothing_forNumberShorterThanFourDigits() {
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("12")).isEmpty();
        assertThat(SmsQueueViewModelAssembler.lastFourDigits(null)).isEmpty();
        assertThat(SmsQueueViewModelAssembler.lastFourDigits("416-555-0199")).isEqualTo("***0199");
    }

    @Test
    @DisplayName("should show a queued row's due time, falling back to its creation when no next attempt is set")
    void shouldShowDueTime_forQueuedRowsOnly() {
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 2L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50))).thenReturn(List.of(
                new SmsQueueRowDto(7L, SmsProviderType.STUB, SmsStatus.QUEUED, null, RECIPIENT, 0, null, null,
                        NOW.minus(Duration.ofMinutes(30)), NOW.minus(Duration.ofMinutes(30)), null, null),
                new SmsQueueRowDto(8L, SmsProviderType.STUB, SmsStatus.QUEUED, 99, RECIPIENT, 1,
                        "QUEUE_PROVIDER_FAILURE_RETRY_SCHEDULED", null, NOW.minus(Duration.ofMinutes(40)),
                        NOW.minus(Duration.ofMinutes(20)), NOW.minus(Duration.ofMinutes(10)),
                        NOW.minus(Duration.ofMinutes(20)))));

        List<SmsQueueViewModel.Row> overdue = provider(assembler(() -> false).assemble(true), "STUB").overdue();

        assertThat(overdue).extracting(SmsQueueViewModel.Row::id, SmsQueueViewModel.Row::dueAt,
                        SmsQueueViewModel.Row::demographicNo)
                .containsExactly(
                        tuple("7", "2026-09-28 13:30", ""),
                        tuple("8", "2026-09-28 13:50", "99"));
    }

    @Test
    @DisplayName("should leave demographic numbers out when the viewer may not read demographics")
    void shouldHideDemographicNumbers_whenViewerCannotReadDemographics() {
        when(dao.countOverdueQueuedOutboundByProvider(any())).thenReturn(Map.of(SmsProviderType.STUB, 1L));
        when(dao.findOverdueQueuedOutbound(eq(SmsProviderType.STUB), any(Date.class), eq(50))).thenReturn(List.of(
                new SmsQueueRowDto(8L, SmsProviderType.STUB, SmsStatus.QUEUED, 99, RECIPIENT, 1, null, null,
                        NOW.minus(Duration.ofMinutes(40)), NOW.minus(Duration.ofMinutes(20)), null, null)));

        SmsQueueViewModel model = assembler(() -> false).assemble(false);

        assertThat(model.demographicNumbersShown()).isFalse();
        assertThat(provider(model, "STUB").overdue()).extracting(SmsQueueViewModel.Row::demographicNo)
                .containsExactly("");
    }

    @Test
    @DisplayName("should expose no message text, full number, name or operator message on a row")
    void shouldHaveNoSensitiveComponent_onRowRecords() {
        List<String> rowComponents = componentNames(SmsQueueViewModel.Row.class);
        assertThat(rowComponents).containsExactly("id", "providerType", "status", "createdAt", "updatedAt", "dueAt",
                "lastAttemptAt", "attemptCount", "errorCode", "consentReasonCode", "demographicNo",
                "recipientLastFour");
        assertThat(rowComponents).noneMatch(name -> name.toLowerCase().matches(".*(body|message|phone|name).*"));
        // The projection the rows come from never selects the body or free-text messages either.
        assertThat(componentNames(SmsQueueRowDto.class))
                .noneMatch(name -> name.toLowerCase().matches(".*(body|message|metadata|from).*"));
    }

    @Test
    @DisplayName("should show the saved scheduler setting and this server's last completed run")
    void shouldShowScheduler_fromStoredSettingAndThisServer() {
        when(configService.storedSchedulerEnabled()).thenReturn(Optional.of(false));
        when(scheduler.isRunning()).thenReturn(true);
        when(scheduler.isRunInProgress()).thenReturn(true);
        when(scheduler.lastRunStartedAt()).thenReturn(Optional.of(NOW.minus(Duration.ofSeconds(30))));
        when(scheduler.lastCompletedRun()).thenReturn(Optional.of(new SmsQueueScheduler.CompletedRun(
                NOW.minus(Duration.ofMinutes(2)), NOW.minus(Duration.ofMinutes(1)),
                SmsQueueScheduler.RunOutcome.COMPLETED, 4)));

        SmsQueueViewModel.Scheduler state = assembler(() -> true).assemble(true).scheduler();

        assertThat(state).isEqualTo(new SmsQueueViewModel.Scheduler(false, true, true, true,
                "2026-09-28 13:59", "2026-09-28 13:59", "COMPLETED", 4));
    }

    @Test
    @DisplayName("should fall back to the scheduler property while nothing is saved, and show no run yet")
    void shouldFallBackToProperty_whenNothingSaved() {
        when(configService.storedSchedulerEnabled()).thenReturn(Optional.empty());

        SmsQueueViewModel.Scheduler state = assembler(() -> true).assemble(true).scheduler();

        assertThat(state).isEqualTo(new SmsQueueViewModel.Scheduler(true, false, false, false, "", "", "", 0));
    }

    private SmsQueueViewModelAssembler assembler(BooleanSupplier schedulerProperty) {
        return new SmsQueueViewModelAssembler(dao, configService, scheduler, Clock.fixed(NOW, ZoneOffset.UTC),
                schedulerProperty);
    }

    private static SmsQueueViewModel.ProviderQueue provider(SmsQueueViewModel model, String providerType) {
        return model.providers().stream()
                .filter(queue -> queue.providerType().equals(providerType))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> componentNames(Class<? extends Record> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents()).map(RecordComponent::getName).toList();
    }
}
