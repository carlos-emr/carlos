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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import io.github.carlos_emr.carlos.sms.service.SmsConfigService;
import io.github.carlos_emr.carlos.sms.service.SmsQueueProcessingService;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Assembles {@link SmsQueueViewModel} for Administration &gt; SMS &gt; SMS queue ({@code admin/smsQueue.jsp}).
 * <p>
 * For each SMS provider it reports the outbound counts by status and four problem lists, each capped at
 * {@link #LIST_LIMIT} rows:
 * <ul>
 *   <li><b>Overdue queued</b>: {@code QUEUED} messages due more than {@link #OVERDUE_QUEUED_AFTER} ago (due
 *       work that is not draining), oldest first.</li>
 *   <li><b>Stale sends</b>: {@code SENDING} messages whose last attempt is older than the worker's
 *       {@link SmsQueueProcessingService#DEFAULT_STALE_SENDING_TIMEOUT}, oldest first. Their outcome is
 *       unknown until stale recovery checks them with the SMS provider.</li>
 *   <li><b>Failed</b>: {@code FAILED} messages, counted by error code, newest first.</li>
 *   <li><b>Blocked by consent</b>: {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED} messages, counted by
 *       consent reason code, newest first.</li>
 * </ul>
 * All queries run in one read-only transaction so the counts and lists agree. Rows come from a projection
 * that never loads the message body; only display-safe values leave this class, with the recipient masked
 * to its last four digits. The scheduler part is this server's in-memory state.
 * <p>
 * Callers must have checked {@code _admin.sms} read.
 *
 * @since 2026-09-28
 */
@Service
public class SmsQueueViewModelAssembler {
    /** The most rows any one list shows. */
    public static final int LIST_LIMIT = 50;
    private static final int MIN_DIGITS_TO_MASK = 7;
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    /** Shown in place of a stored error or reason code that is not a plain code. */
    static final String NOT_A_CODE = "NOT_A_CODE";
    /** How long past due a queued message must be before it counts as overdue. */
    public static final Duration OVERDUE_QUEUED_AFTER = Duration.ofMinutes(5);

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
    private static final List<SmsStatus> CONSENT_BLOCKED_STATUSES =
            List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED);
    private static final Comparator<SmsQueueViewModel.CodeCount> LARGEST_FIRST =
            Comparator.comparingLong(SmsQueueViewModel.CodeCount::count).reversed()
                    .thenComparing(SmsQueueViewModel.CodeCount::code);

    private final SmsTransactionDao smsTransactionDao;
    private final SmsConfigService configService;
    private final SmsQueueScheduler scheduler;
    private final Clock clock;
    private final BooleanSupplier schedulerProperty;
    private final DateTimeFormatter dateTimeFormatter;

    @Autowired
    public SmsQueueViewModelAssembler(SmsTransactionDao smsTransactionDao, SmsConfigService configService,
                                      SmsQueueScheduler scheduler) {
        this(smsTransactionDao, configService, scheduler, Clock.systemDefaultZone(),
                () -> CarlosProperties.getInstance().isPropertyActive(SmsQueueScheduler.ENABLED_PROPERTY));
    }

    /**
     * @param clock             supplies "now" for the overdue and stale thresholds, and the zone times are shown in
     * @param schedulerProperty the {@link SmsQueueScheduler#ENABLED_PROPERTY} value, used while nothing is saved
     */
    SmsQueueViewModelAssembler(SmsTransactionDao smsTransactionDao, SmsConfigService configService,
                               SmsQueueScheduler scheduler, Clock clock, BooleanSupplier schedulerProperty) {
        this.smsTransactionDao = smsTransactionDao;
        this.configService = configService;
        this.scheduler = scheduler;
        this.clock = clock;
        this.schedulerProperty = schedulerProperty;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN, Locale.ROOT).withZone(clock.getZone());
    }

    /**
     * @param showDemographicNumbers whether rows may carry the patient's demographic number; the action passes
     *                               the viewer's {@code _demographic} read right
     * @param mayReadPatient         asked per row, with the row's demographic number: whether this viewer may
     *                               read that patient, so a per-patient restriction hides the number too
     * @return the queue per SMS provider and this server's scheduler state
     */
    @Transactional(readOnly = true)
    public SmsQueueViewModel assemble(boolean showDemographicNumbers, IntPredicate mayReadPatient) {
        Objects.requireNonNull(mayReadPatient, "mayReadPatient is required");
        // Asked once per patient, not once per row: the same patient can appear in several lists.
        Map<Integer, Boolean> readable = new HashMap<>();
        IntPredicate showPatient = demographicNo -> showDemographicNumbers
                && readable.computeIfAbsent(demographicNo, mayReadPatient::test);
        Instant now = clock.instant();
        Date dueBefore = Date.from(now.minus(OVERDUE_QUEUED_AFTER));
        Date staleBefore = Date.from(now.minus(SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT));

        Map<SmsProviderType, Map<String, Long>> statusCountsByProvider =
                byProvider(smsTransactionDao.countOutboundByProviderAndStatus());
        Map<SmsProviderType, Long> overdueCounts = smsTransactionDao.countOverdueQueuedOutboundByProvider(dueBefore);
        Map<SmsProviderType, Long> staleCounts = smsTransactionDao.countStaleSendingOutboundByProvider(staleBefore);
        Map<SmsProviderType, Map<String, Long>> failedByErrorCode =
                byProvider(smsTransactionDao.countFailedOutboundByProviderAndErrorCode());
        Map<SmsProviderType, Map<String, Long>> blockedByReason =
                byProvider(smsTransactionDao.countConsentBlockedOutboundByProviderAndReason());

        List<SmsQueueViewModel.ProviderQueue> providers = new ArrayList<>();
        for (SmsProviderType providerType : SmsProviderType.values()) {
            Map<String, Long> byStatus = statusCountsByProvider.getOrDefault(providerType, Map.of());
            long overdueCount = overdueCounts.getOrDefault(providerType, 0L);
            long staleCount = staleCounts.getOrDefault(providerType, 0L);
            long failedCount = byStatus.getOrDefault(SmsStatus.FAILED.name(), 0L);
            long blockedCount = CONSENT_BLOCKED_STATUSES.stream()
                    .mapToLong(status -> byStatus.getOrDefault(status.name(), 0L))
                    .sum();
            // Lists are only queried when their count says there is something to show.
            providers.add(new SmsQueueViewModel.ProviderQueue(
                    providerType.name(),
                    byStatus.values().stream().mapToLong(Long::longValue).sum(),
                    statusCounts(byStatus),
                    overdueCount,
                    overdueCount == 0 ? List.of() : rows(showPatient,
                            smsTransactionDao.findOverdueQueuedOutbound(providerType, dueBefore, LIST_LIMIT)),
                    staleCount,
                    staleCount == 0 ? List.of() : rows(showPatient,
                            smsTransactionDao.findStaleSendingOutbound(providerType, staleBefore, LIST_LIMIT)),
                    failedCount,
                    codeCounts(failedByErrorCode.getOrDefault(providerType, Map.of())),
                    failedCount == 0 ? List.of() : rows(showPatient, smsTransactionDao.findRecentOutboundByStatuses(
                            providerType, List.of(SmsStatus.FAILED), LIST_LIMIT)),
                    blockedCount,
                    codeCounts(blockedByReason.getOrDefault(providerType, Map.of())),
                    blockedCount == 0 ? List.of() : rows(showPatient, smsTransactionDao.findRecentOutboundByStatuses(
                            providerType, CONSENT_BLOCKED_STATUSES, LIST_LIMIT))
            ));
        }
        return new SmsQueueViewModel(
                dateTimeFormatter.format(now),
                OVERDUE_QUEUED_AFTER.toMinutes(),
                SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT.toMinutes(),
                LIST_LIMIT,
                showDemographicNumbers,
                schedulerState(),
                providers
        );
    }

    /**
     * The scheduler setting as the scheduler itself resolves it at startup: the saved Administration &gt; SMS
     * value, or the property while nothing is saved. Running state and run times are this server's only.
     */
    private SmsQueueViewModel.Scheduler schedulerState() {
        Optional<Boolean> stored = configService.storedSchedulerEnabled();
        // In-progress first: a run that ends between the two reads then shows as finished, with its result.
        // With no run in progress, the start comes from the finished run's own record, so start, finish and
        // outcome always describe one run even if a new run starts while the page is being built.
        boolean runInProgress = scheduler.isRunInProgress();
        Optional<SmsQueueScheduler.CompletedRun> lastRun = scheduler.lastCompletedRun();
        Optional<Instant> latestStart = scheduler.lastRunStartedAt();
        if (runInProgress && lastRun.isPresent() && latestStart.equals(lastRun.map(
                SmsQueueScheduler.CompletedRun::startedAt))) {
            // The run that was in progress finished while these values were being read.
            runInProgress = false;
        }
        Optional<Instant> startedAt = runInProgress
                ? latestStart
                : lastRun.map(SmsQueueScheduler.CompletedRun::startedAt);
        return new SmsQueueViewModel.Scheduler(
                stored.orElseGet(schedulerProperty::getAsBoolean),
                stored.isPresent(),
                scheduler.isRunning(),
                runInProgress,
                startedAt.map(dateTimeFormatter::format).orElse(""),
                lastRun.map(run -> format(run.finishedAt())).orElse(""),
                lastRun.map(run -> run.outcome().name()).orElse(""),
                lastRun.map(SmsQueueScheduler.CompletedRun::processed).orElse(0)
        );
    }

    /**
     * Every status in enum order, zero counts included, so a healthy queue shows its zeros. {@code RECEIVED}
     * only ever describes inbound messages, so it is left out unless an outbound row somehow has it.
     */
    private static List<SmsQueueViewModel.StatusCount> statusCounts(Map<String, Long> byStatus) {
        List<SmsQueueViewModel.StatusCount> counts = new ArrayList<>();
        for (SmsStatus status : SmsStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (status != SmsStatus.RECEIVED || count > 0) {
                counts.add(new SmsQueueViewModel.StatusCount(status.name(), count));
            }
        }
        return counts;
    }

    private static List<SmsQueueViewModel.CodeCount> codeCounts(Map<String, Long> byCode) {
        // Summed after filtering, so several stored values that are not codes share one line.
        Map<String, Long> shown = new HashMap<>();
        byCode.forEach((code, count) -> shown.merge(codeOnly(code), count, Long::sum));
        return shown.entrySet().stream()
                .map(entry -> new SmsQueueViewModel.CodeCount(entry.getKey(), entry.getValue()))
                .sorted(LARGEST_FIRST)
                .toList();
    }

    /** Groups counts by SMS provider, then by code; a missing code is grouped under the empty string. */
    private static Map<SmsProviderType, Map<String, Long>> byProvider(List<SmsQueueCountDto> counts) {
        Map<SmsProviderType, Map<String, Long>> grouped = new EnumMap<>(SmsProviderType.class);
        for (SmsQueueCountDto count : counts) {
            if (count.providerType() != null) {
                grouped.computeIfAbsent(count.providerType(), ignored -> new HashMap<>())
                        .merge(nullToEmpty(count.code()), count.count(), Long::sum);
            }
        }
        return grouped;
    }

    private List<SmsQueueViewModel.Row> rows(IntPredicate showPatient, List<SmsQueueRowDto> rows) {
        return rows.stream().map(row -> toRow(row, showPatient)).toList();
    }

    private SmsQueueViewModel.Row toRow(SmsQueueRowDto row, IntPredicate showPatient) {
        // Same rule as the overdue query: never attempted means due since it was created.
        Instant dueAt = row.attemptCount() == 0 || row.nextAttemptAt() == null
                ? row.createdAt()
                : row.nextAttemptAt();
        return new SmsQueueViewModel.Row(
                row.id() == null ? "" : String.valueOf(row.id()),
                code(row.providerType()),
                code(row.status()),
                format(row.createdAt()),
                format(row.updatedAt()),
                row.status() == SmsStatus.QUEUED ? format(dueAt) : "",
                format(row.lastAttemptAt()),
                row.attemptCount(),
                codeOnly(row.errorCode()),
                codeOnly(row.consentReasonCode()),
                row.demographicNo() == null || !showPatient.test(row.demographicNo())
                        ? "" : String.valueOf(row.demographicNo()),
                lastFourDigits(row.toPhoneNumber())
        );
    }

    /**
     * Shows only the last four digits (as {@code ***1234}), the same masking as the patient SMS history, so this
     * view never displays a full phone number. A number with fewer than seven digits shows nothing, so the last four are never most of it.
     */
    static String lastFourDigits(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
        return digits.length() < MIN_DIGITS_TO_MASK ? "" : "***" + digits.substring(digits.length() - 4);
    }

    /**
     * Error and reason codes are shown as they are stored, but only when they look like codes. An SMS
     * provider could put free text into an error code (a phone number, part of a message), and this page
     * promises to show none.
     */
    static String codeOnly(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        return CODE.matcher(code).matches() ? code : NOT_A_CODE;
    }

    /** The enum name; the page turns statuses into labels through {@code sms.queue.status.*}. */
    private static String code(Enum<?> value) {
        return value == null ? "" : value.name();
    }

    private String format(Instant instant) {
        return instant == null ? "" : dateTimeFormatter.format(instant);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
