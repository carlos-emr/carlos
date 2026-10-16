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
package io.github.carlos_emr.carlos.sms.viewmodel;

import java.util.List;

/**
 * The outbound SMS queue for {@code admin/smsQueue.jsp}, per SMS provider, plus this server's queue scheduler.
 * <p>
 * Display-ready values only: times are formatted, statuses and outcomes are enum names the page turns into
 * labels, and recipient numbers are masked to their last four digits. No message text, full phone number,
 * patient name or operator message is ever part of this model.
 *
 * @param generatedAt     when the page was assembled ({@code yyyy-MM-dd HH:mm}, server time)
 * @param overdueMinutes  how long past due a queued message must be to count as overdue
 * @param staleMinutes    how long after its last attempt a {@code SENDING} message counts as stale
 * @param pageSize        how many messages one page of a list covers ({@link SmsQueuePages#PAGE_SIZE})
 * @param demographicNumbersShown whether rows carry the patient's demographic number; only for users who
 *                        may also read demographics, since the number links a message to a patient
 * @param window          the {@link SmsQueueWindow#parameterValue()} of the time period the failed and
 *                        blocked sections cover; always one of {@code windowOptions}, never request text
 * @param windowOptions   the parameter values of every time period the page offers, in display order
 * @param windowFormPages the page parameters the time period form sends along, so that the lists the period
 *                        does not change stay on their pages; built here, never request text
 * @param scheduler       this server's queue scheduler
 * @param providers       one entry per SMS provider, in {@code SmsProviderType} order
 * @since 2026-09-28
 */
public record SmsQueueViewModel(String generatedAt, long overdueMinutes, long staleMinutes, int pageSize,
                                boolean demographicNumbersShown, String window, List<String> windowOptions,
                                List<Parameter> windowFormPages, Scheduler scheduler, List<ProviderQueue> providers) {

    public SmsQueueViewModel {
        windowOptions = windowOptions == null ? List.of() : List.copyOf(windowOptions);
        windowFormPages = windowFormPages == null ? List.of() : List.copyOf(windowFormPages);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }

    /**
     * The queue scheduler. {@code running}, the run times and the outcome describe this server only: every
     * server runs its own scheduler and keeps this record in memory, so it starts empty after a restart.
     *
     * @param settingEnabled   whether the scheduler is turned on
     * @param settingStored    true when that comes from Administration &gt; SMS, false when from the property
     * @param running          whether the scheduler is running on this server
     * @param runInProgress    whether a run is going on right now on this server
     * @param lastRunStartedAt when the most recent run on this server started, or empty if none has
     * @param lastRunFinishedAt when the last finished run on this server ended, or empty if none has
     * @param lastRunOutcome   that run's {@code SmsQueueScheduler.RunOutcome} name, or empty
     * @param lastRunProcessed messages that run processed
     */
    public record Scheduler(boolean settingEnabled, boolean settingStored, boolean running, boolean runInProgress,
                            String lastRunStartedAt, String lastRunFinishedAt, String lastRunOutcome,
                            int lastRunProcessed) {
    }

    /**
     * One SMS provider's outbound queue.
     * <p>
     * The failed and blocked sections cover the selected time period ({@link SmsQueueViewModel#window()});
     * their all-time totals are given beside them. The overdue and stale sections and the counts per status
     * always cover all time. The counts per status and the section counts include the messages of patients
     * this viewer is restricted from, even though those messages are left out of the lists (see
     * {@link RowList}). The counts by code leave those messages out, so they can add up to less than their
     * section's count.
     *
     * @param providerType      the {@code SmsProviderType} name
     * @param outboundTotal     all outbound messages recorded for this provider
     * @param statusCounts      outbound counts per status, in {@code SmsStatus} order
     * @param overdueCount      queued messages overdue by more than {@link SmsQueueViewModel#overdueMinutes()}
     * @param overdue           one page of those, oldest due first
     * @param staleCount        {@code SENDING} messages older than {@link SmsQueueViewModel#staleMinutes()}
     * @param stale             one page of those, oldest last attempt first
     * @param failedCount       {@code FAILED} messages that last changed within the selected time period
     * @param failedTotal       {@code FAILED} messages of all time
     * @param failedByErrorCode the ones within the time period counted by error code, largest first,
     *                          without the messages of patients this viewer is restricted from
     * @param recentFailed      one page of the ones within the time period, most recently updated first
     * @param blockedCount      {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED} messages that last changed
     *                          within the selected time period
     * @param blockedTotal      {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED} messages of all time
     * @param blockedByReason   the ones within the time period counted by consent reason code, largest
     *                          first, without the messages of patients this viewer is restricted from
     * @param recentBlocked     one page of the ones within the time period, most recently updated first
     */
    public record ProviderQueue(String providerType, long outboundTotal, List<StatusCount> statusCounts,
                                long overdueCount, RowList overdue,
                                long staleCount, RowList stale,
                                long failedCount, long failedTotal, List<CodeCount> failedByErrorCode,
                                RowList recentFailed,
                                long blockedCount, long blockedTotal, List<CodeCount> blockedByReason,
                                RowList recentBlocked) {

        public ProviderQueue {
            statusCounts = copy(statusCounts);
            overdue = orEmpty(overdue);
            stale = orEmpty(stale);
            failedByErrorCode = copy(failedByErrorCode);
            recentFailed = orEmpty(recentFailed);
            blockedByReason = copy(blockedByReason);
            recentBlocked = orEmpty(recentBlocked);
        }

        private static <T> List<T> copy(List<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }

        private static RowList orEmpty(RowList list) {
            return list == null ? RowList.EMPTY : list;
        }
    }

    /**
     * One page of a message list. Messages of patients this viewer may not open are left out completely, so
     * nothing about them (id, status, reason, times, phone digits) is here; only how many were left out of
     * this page. A page therefore shows fewer than {@link SmsQueuePages#PAGE_SIZE} rows when some of its
     * messages were left out, and the pages still cover the list in fixed steps, so that every message is on
     * exactly one page whoever looks.
     *
     * @param rows        the messages shown on this page
     * @param hiddenCount how many of this page's messages were left out because the viewer has no access to
     *                    their patients
     * @param paging      where this page is in the list
     */
    public record RowList(List<Row> rows, int hiddenCount, Paging paging) {
        public static final RowList EMPTY = new RowList(List.of(), 0, Paging.SINGLE);

        public RowList {
            rows = rows == null ? List.of() : List.copyOf(rows);
            paging = paging == null ? Paging.SINGLE : paging;
        }

        /** A list that fits on one page. */
        public RowList(List<Row> rows, int hiddenCount) {
            this(rows, hiddenCount, Paging.SINGLE);
        }
    }

    /**
     * Where a page is in its list. Row numbers count every message in the list's order, hidden ones included,
     * so they match the list's count.
     *
     * @param page          this page's number, from 1
     * @param pageCount     how many pages the list has; 1 for a list that fits on one page
     * @param firstRow      the list position of this page's first message, from 1; 0 for an empty list
     * @param lastRow       the list position of this page's last message
     * @param rowCount      how many messages the list has
     * @param previousQuery the query string of the page with this list one page back and everything else as
     *                      it is, without the leading {@code ?}; empty on the first page
     * @param nextQuery     the same, one page on; empty on the last page
     */
    public record Paging(int page, int pageCount, long firstRow, long lastRow, long rowCount,
                         String previousQuery, String nextQuery) {
        /** A list that fits on one page: nothing to page through. */
        public static final Paging SINGLE = new Paging(1, 1, 0, 0, 0, "", "");

        public Paging {
            previousQuery = previousQuery == null ? "" : previousQuery;
            nextQuery = nextQuery == null ? "" : nextQuery;
        }
    }

    /**
     * A request parameter for the page to send, built by {@link SmsQueuePages}.
     *
     * @param name  the parameter name
     * @param value its value
     */
    public record Parameter(String name, String value) {
    }

    /**
     * @param status the {@code SmsStatus} name
     * @param count  outbound messages in it
     */
    public record StatusCount(String status, long count) {
    }

    /**
     * @param code  an error code or consent reason code, or empty when the messages have none
     * @param count messages with it
     */
    public record CodeCount(String code, long count) {
    }

    /**
     * One message, redacted for the operational view. There is deliberately no message text, full phone
     * number, patient name or operator message here; the patient appears only as a demographic number.
     *
     * @param id                the {@code sms_transaction} id
     * @param providerType      the {@code SmsProviderType} name
     * @param status            the {@code SmsStatus} name
     * @param createdAt         when it was created
     * @param updatedAt         when it last changed
     * @param dueAt             when a queued message is due (its next attempt, or its creation); empty otherwise
     * @param lastAttemptAt     when its last send attempt started, or empty
     * @param attemptCount      send attempts so far
     * @param errorCode         the last error code, or empty
     * @param consentReasonCode the consent reason code, or empty
     * @param demographicNo     the patient's demographic number; empty for system tests and when the viewer
     *                          may not read demographics
     * @param recipientLastFour the recipient's last four digits as {@code ***1234}, or empty
     */
    public record Row(String id, String providerType, String status, String createdAt, String updatedAt,
                      String dueAt, String lastAttemptAt, int attemptCount, String errorCode,
                      String consentReasonCode, String demographicNo, String recipientLastFour) {
    }
}
