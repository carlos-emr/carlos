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
import io.github.carlos_emr.carlos.sms.service.SmsPatientRestrictionLookup;
import io.github.carlos_emr.carlos.sms.service.SmsQueueProcessingService;
import io.github.carlos_emr.carlos.sms.service.SmsQueueScheduler;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueViewModel;
import io.github.carlos_emr.carlos.sms.viewmodel.SmsQueueWindow;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntPredicate;

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
 * The failed and blocked sections cover only the chosen {@link SmsQueueWindow} (messages that last changed
 * within it); their all-time totals are given beside them. The overdue and stale sections and the counts by
 * status always cover all time.
 * <p>
 * A message whose patient this viewer is restricted from is left out of the lists completely; each list
 * only says how many were left out. It is left out of the counts by code too, wherever it stands in the
 * list's order, so nothing on the page tells a hidden patient's error or reason code. The counts by status
 * and the section totals still include those messages, so the counts by code can add up to less than the
 * section's count.
 * <p>
 * Only a patient with a security entry of their own can be restricted ({@link SmsPatientRestrictionLookup}),
 * so access is checked for those patients alone, once each per page view. The counts by code leave the
 * restricted patients out in the database query itself, not by taking their counts off afterwards: the
 * database groups codes by its collation, which ignores letter case and spaces at the end, so one code can
 * come back spelled differently from two queries, and a subtraction matched by spelling would miss it. That
 * costs zero to two extra queries per section, each split into parts of at most
 * {@link SmsTransactionDao#PATIENT_COUNT_CHUNK_SIZE} patients, and none when no patient has an entry of their
 * own: one for which of those patients have a message in the section (skipped when the section is empty),
 * and one to count again without the restricted ones (only when there are any).
 * <p>
 * All queries, the read of the patients with an entry of their own included, run in one read-only
 * transaction so the counts and lists agree. Rows come from a projection that never loads the message body;
 * only display-safe values leave this class, with the recipient masked to its last four digits. The
 * scheduler part is this server's in-memory state.
 * <p>
 * Callers must have checked {@code _admin.sms} read.
 *
 * @since 2026-09-28
 */
@Service
public class SmsQueueViewModelAssembler {
    private static final Logger LOGGER = MiscUtils.getLogger();
    /** The most rows any one list shows. */
    public static final int LIST_LIMIT = 50;
    private static final int MIN_DIGITS_TO_MASK = 7;
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    /** The status line that stands for both {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED}. */
    static final String BLOCKED_BY_CONSENT = "BLOCKED_BY_CONSENT";
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
    private final SmsPatientRestrictionLookup restrictionLookup;
    private final Clock clock;
    private final BooleanSupplier schedulerProperty;
    private final DateTimeFormatter dateTimeFormatter;

    @Autowired
    public SmsQueueViewModelAssembler(SmsTransactionDao smsTransactionDao, SmsConfigService configService,
                                      SmsQueueScheduler scheduler, SmsPatientRestrictionLookup restrictionLookup) {
        this(smsTransactionDao, configService, scheduler, restrictionLookup, Clock.systemDefaultZone(),
                () -> CarlosProperties.getInstance().isPropertyActive(SmsQueueScheduler.ENABLED_PROPERTY));
    }

    /**
     * @param restrictionLookup names the patients that can be restricted; read once per page view
     * @param clock             supplies "now" for the overdue and stale thresholds, and the zone times are shown in
     * @param schedulerProperty the {@link SmsQueueScheduler#ENABLED_PROPERTY} value, used while nothing is saved
     */
    SmsQueueViewModelAssembler(SmsTransactionDao smsTransactionDao, SmsConfigService configService,
                               SmsQueueScheduler scheduler, SmsPatientRestrictionLookup restrictionLookup,
                               Clock clock, BooleanSupplier schedulerProperty) {
        this.smsTransactionDao = smsTransactionDao;
        this.configService = configService;
        this.scheduler = scheduler;
        this.restrictionLookup = restrictionLookup;
        this.clock = clock;
        this.schedulerProperty = schedulerProperty;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN, Locale.ROOT).withZone(clock.getZone());
    }

    /**
     * What {@link #assemble} returns: the page's model, and separately the patients whose messages are in it.
     * The patients are for the audit record only. They are kept out of the model because the model goes to
     * the page, and a viewer without {@code _demographic} read must not be given demographic numbers.
     *
     * @param model                       what the page shows
     * @param displayedDemographicNumbers the patients with at least one message in any of the lists, whether
     *                                    or not the demographic number column is shown; patients whose
     *                                    messages were hidden are not included
     */
    public record Result(SmsQueueViewModel model, Set<Integer> displayedDemographicNumbers) {
        public Result {
            displayedDemographicNumbers = displayedDemographicNumbers == null
                    ? Set.of() : Set.copyOf(displayedDemographicNumbers);
        }

        @Override
        public String toString() {
            return "SmsQueueViewModelAssembler.Result[redacted]";
        }
    }

    /**
     * @param window                 the time period for the failed and blocked sections
     * @param showDemographicNumbers whether rows may carry the patient's demographic number; the action passes
     *                               the viewer's {@code _demographic} read right
     * @param mayAccessPatient       asked at most once per patient, with the demographic number, and only
     *                               about a patient with a security entry of their own (see
     *                               {@link SmsPatientRestrictionLookup}): whether this viewer may open that
     *                               patient's record. When the answer is no, or the check fails, that
     *                               patient's messages are left out of the lists and of the counts by code
     * @return the queue per SMS provider and this server's scheduler state, and the patients shown
     * @throws RuntimeException when the patients with an entry of their own cannot be read; nothing is shown
     *                          then, rather than every patient
     */
    @Transactional(readOnly = true)
    public Result assemble(SmsQueueWindow window, boolean showDemographicNumbers, IntPredicate mayAccessPatient) {
        Objects.requireNonNull(window, "window is required");
        Objects.requireNonNull(mayAccessPatient, "mayAccessPatient is required");
        // Only these patients can be restricted. Read first and not caught: if they cannot be read, the page
        // fails before anything is queried.
        RowFilter filter = new RowFilter(showDemographicNumbers, restrictionLookup.patientsWithOwnEntries(),
                mayAccessPatient);
        Instant now = clock.instant();
        Date dueBefore = Date.from(now.minus(OVERDUE_QUEUED_AFTER));
        Date staleBefore = Date.from(now.minus(SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT));
        // Null for "all time": the failed and blocked queries then have no time limit.
        Instant windowStart = window.since(now);
        Date since = windowStart == null ? null : Date.from(windowStart);

        Map<SmsProviderType, Map<String, Long>> statusCountsByProvider =
                byProvider(smsTransactionDao.countOutboundByProviderAndStatus());
        Map<SmsProviderType, Long> overdueCounts = smsTransactionDao.countOverdueQueuedOutboundByProvider(dueBefore);
        Map<SmsProviderType, Long> staleCounts = smsTransactionDao.countStaleSendingOutboundByProvider(staleBefore);
        // Every message counted, hidden ones included: the section counts are these added up.
        Map<SmsProviderType, Map<String, Long>> failedByErrorCode =
                byProvider(smsTransactionDao.countFailedOutboundByProviderAndErrorCode(since));
        Map<SmsProviderType, Map<String, Long>> blockedByReason =
                byProvider(smsTransactionDao.countConsentBlockedOutboundByProviderAndReason(since));
        // What the counts by code show: the same counts without the messages of restricted patients.
        Map<SmsProviderType, Map<String, Long>> failedByErrorCodeShown = filter.codeCountsShown(failedByErrorCode,
                patients -> smsTransactionDao.findPatientsWithFailedOutbound(since, patients),
                restricted -> smsTransactionDao.countFailedOutboundByProviderAndErrorCode(since, restricted));
        Map<SmsProviderType, Map<String, Long>> blockedByReasonShown = filter.codeCountsShown(blockedByReason,
                patients -> smsTransactionDao.findPatientsWithConsentBlockedOutbound(since, patients),
                restricted -> smsTransactionDao.countConsentBlockedOutboundByProviderAndReason(since, restricted));

        List<SmsQueueViewModel.ProviderQueue> providers = new ArrayList<>();
        for (SmsProviderType providerType : SmsProviderType.values()) {
            Map<String, Long> byStatus = statusCountsByProvider.getOrDefault(providerType, Map.of());
            Map<String, Long> failedCodes = failedByErrorCode.getOrDefault(providerType, Map.of());
            Map<String, Long> blockedCodes = blockedByReason.getOrDefault(providerType, Map.of());
            long overdueCount = overdueCounts.getOrDefault(providerType, 0L);
            long staleCount = staleCounts.getOrDefault(providerType, 0L);
            // Within the time period: the by-code counts added up, hidden messages included. Of all time:
            // from the counts by status.
            long failedCount = sum(failedCodes);
            long failedTotal = byStatus.getOrDefault(SmsStatus.FAILED.name(), 0L);
            long blockedCount = sum(blockedCodes);
            long blockedTotal = CONSENT_BLOCKED_STATUSES.stream()
                    .mapToLong(status -> byStatus.getOrDefault(status.name(), 0L))
                    .sum();
            // Lists are only queried when their count says there is something to show.
            SmsQueueViewModel.RowList recentFailed = failedCount == 0 ? SmsQueueViewModel.RowList.EMPTY
                    : filter.rows(smsTransactionDao.findRecentOutboundByStatuses(
                            providerType, List.of(SmsStatus.FAILED), since, LIST_LIMIT));
            SmsQueueViewModel.RowList recentBlocked = blockedCount == 0 ? SmsQueueViewModel.RowList.EMPTY
                    : filter.rows(smsTransactionDao.findRecentOutboundByStatuses(
                            providerType, CONSENT_BLOCKED_STATUSES, since, LIST_LIMIT));
            providers.add(new SmsQueueViewModel.ProviderQueue(
                    providerType.name(),
                    sum(byStatus),
                    statusCounts(byStatus),
                    overdueCount,
                    overdueCount == 0 ? SmsQueueViewModel.RowList.EMPTY : filter.rows(
                            smsTransactionDao.findOverdueQueuedOutbound(providerType, dueBefore, LIST_LIMIT)),
                    staleCount,
                    staleCount == 0 ? SmsQueueViewModel.RowList.EMPTY : filter.rows(
                            smsTransactionDao.findStaleSendingOutbound(providerType, staleBefore, LIST_LIMIT)),
                    failedCount,
                    failedTotal,
                    codeCounts(failedByErrorCodeShown.getOrDefault(providerType, Map.of())),
                    recentFailed,
                    blockedCount,
                    blockedTotal,
                    codeCounts(blockedByReasonShown.getOrDefault(providerType, Map.of())),
                    recentBlocked
            ));
        }
        SmsQueueViewModel model = new SmsQueueViewModel(
                dateTimeFormatter.format(now),
                OVERDUE_QUEUED_AFTER.toMinutes(),
                SmsQueueProcessingService.DEFAULT_STALE_SENDING_TIMEOUT.toMinutes(),
                LIST_LIMIT,
                showDemographicNumbers,
                window.parameterValue(),
                SmsQueueWindow.parameterValues(),
                schedulerState(),
                providers
        );
        return new Result(model, filter.displayed);
    }

    /**
     * Turns query rows into display rows for one page view: leaves out the messages of patients the viewer
     * is restricted from, has those messages left out of the counts by code, and remembers which patients
     * were shown. One instance per call of {@link #assemble}.
     */
    private final class RowFilter {
        private final boolean showDemographicNumbers;
        private final Set<Integer> patientsWithOwnEntries;
        private final IntPredicate mayAccessPatient;
        // Asked once per patient, not once per row: the same patient can appear in several lists and counts.
        private final Map<Integer, Boolean> accessible = new HashMap<>();
        private final Set<Integer> displayed = new TreeSet<>();

        private RowFilter(boolean showDemographicNumbers, Collection<Integer> patientsWithOwnEntries,
                          IntPredicate mayAccessPatient) {
            this.showDemographicNumbers = showDemographicNumbers;
            this.patientsWithOwnEntries = Set.copyOf(patientsWithOwnEntries);
            this.mayAccessPatient = mayAccessPatient;
        }

        /** A patient without an entry of their own cannot be restricted, so nothing is asked about them. */
        private boolean restricted(Integer demographicNo) {
            return patientsWithOwnEntries.contains(demographicNo)
                    && !accessible.computeIfAbsent(demographicNo, this::mayAccess);
        }

        /**
         * A section's counts by code as the page shows them. When some of the patients with an entry of their
         * own have messages in the section and any of those is restricted, the counts are queried again with
         * those patients left out, and that result is shown. The codes are then as that query spells them:
         * what is not a code is replaced only afterwards, and the section's count still comes from
         * {@code byCode}.
         *
         * @param byCode               the section's counts by SMS provider and code, hidden messages included
         * @param patientsWithMessages given patients, returns those with at least one message in the section
         * @param countWithout         given patients, returns the section's counts without their messages
         * @return {@code byCode} when nobody needs to be left out; otherwise the counts without the restricted
         *         patients
         */
        private Map<SmsProviderType, Map<String, Long>> codeCountsShown(
                Map<SmsProviderType, Map<String, Long>> byCode,
                Function<Set<Integer>, Set<Integer>> patientsWithMessages,
                Function<Set<Integer>, List<SmsQueueCountDto>> countWithout) {
            long sectionCount = byCode.values().stream().mapToLong(SmsQueueViewModelAssembler::sum).sum();
            // Nothing to leave out of an empty section, and no one to leave out when nobody can be restricted.
            if (sectionCount == 0 || patientsWithOwnEntries.isEmpty()) {
                return byCode;
            }
            Set<Integer> restrictedInSection = new TreeSet<>();
            for (Integer demographicNo : patientsWithMessages.apply(patientsWithOwnEntries)) {
                if (demographicNo != null && restricted(demographicNo)) {
                    restrictedInSection.add(demographicNo);
                }
            }
            return restrictedInSection.isEmpty() ? byCode : byProvider(countWithout.apply(restrictedInSection));
        }

        private SmsQueueViewModel.RowList rows(List<SmsQueueRowDto> rows) {
            List<SmsQueueViewModel.Row> shown = new ArrayList<>();
            int hidden = 0;
            for (SmsQueueRowDto row : rows) {
                Integer demographicNo = row.demographicNo();
                // A message without a patient (a system test) has nobody to protect, so it is always shown.
                if (demographicNo != null && restricted(demographicNo)) {
                    hidden++;
                    continue;
                }
                if (demographicNo != null) {
                    displayed.add(demographicNo);
                }
                shown.add(toRow(row, showDemographicNumbers));
            }
            return new SmsQueueViewModel.RowList(shown, hidden);
        }

        private boolean mayAccess(Integer demographicNo) {
            try {
                return mayAccessPatient.test(demographicNo);
            } catch (RuntimeException e) {
                // Fail closed. Only the exception type is logged: its message could name the patient.
                // If the failure came from the database, the surrounding transaction is already marked
                // for rollback and the whole page fails instead, which shows nothing either.
                LOGGER.warn("SMS queue view: a patient access check failed ({}); that patient's messages are "
                                + "not shown or counted by code",
                        e.getClass().getName());
                return false;
            }
        }
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
        long blocked = 0;
        for (SmsStatus status : SmsStatus.values()) {
            long count = byStatus.getOrDefault(status.name(), 0L);
            if (CONSENT_BLOCKED_STATUSES.contains(status)) {
                blocked += count;
            } else if (status != SmsStatus.RECEIVED || count > 0) {
                counts.add(new SmsQueueViewModel.StatusCount(status.name(), count));
            }
        }
        // One line for both kinds of consent block. Shown apart, the two counts would tell a viewer who sees
        // every other row which kind a hidden patient's message is.
        counts.add(new SmsQueueViewModel.StatusCount(BLOCKED_BY_CONSENT, blocked));
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

    private static long sum(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private SmsQueueViewModel.Row toRow(SmsQueueRowDto row, boolean showDemographicNumber) {
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
                row.demographicNo() == null || !showDemographicNumber ? "" : String.valueOf(row.demographicNo()),
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
        if (!CODE.matcher(code).matches()) {
            return NOT_A_CODE;
        }
        // A phone number or a health number fits the code pattern too; no real code holds that many digits.
        long digits = code.chars().filter(Character::isDigit).count();
        return digits >= MIN_DIGITS_TO_MASK ? NOT_A_CODE : code;
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
