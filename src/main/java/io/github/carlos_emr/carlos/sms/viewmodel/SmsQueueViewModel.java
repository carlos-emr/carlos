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
 * @param listLimit       the most rows any one list shows
 * @param demographicNumbersShown whether rows carry the patient's demographic number; only for users who
 *                        may also read demographics, since the number links a message to a patient
 * @param scheduler       this server's queue scheduler
 * @param providers       one entry per SMS provider, in {@code SmsProviderType} order
 * @since 2026-09-28
 */
public record SmsQueueViewModel(String generatedAt, long overdueMinutes, long staleMinutes, int listLimit,
                                boolean demographicNumbersShown, Scheduler scheduler, List<ProviderQueue> providers) {

    public SmsQueueViewModel {
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
     *
     * @param providerType      the {@code SmsProviderType} name
     * @param outboundTotal     all outbound messages recorded for this provider
     * @param statusCounts      outbound counts per status, in {@code SmsStatus} order
     * @param overdueCount      queued messages overdue by more than {@link SmsQueueViewModel#overdueMinutes()}
     * @param overdue           the oldest of those, oldest due first
     * @param staleCount        {@code SENDING} messages older than {@link SmsQueueViewModel#staleMinutes()}
     * @param stale             the oldest of those, oldest last attempt first
     * @param failedCount       {@code FAILED} messages
     * @param failedByErrorCode those counted by error code, largest first
     * @param recentFailed      the most recently updated of those
     * @param blockedCount      {@code CONSENT_BLOCKED} and {@code OPTOUT_BLOCKED} messages
     * @param blockedByReason   those counted by consent reason code, largest first
     * @param recentBlocked     the most recently updated of those
     */
    public record ProviderQueue(String providerType, long outboundTotal, List<StatusCount> statusCounts,
                                long overdueCount, List<Row> overdue,
                                long staleCount, List<Row> stale,
                                long failedCount, List<CodeCount> failedByErrorCode, List<Row> recentFailed,
                                long blockedCount, List<CodeCount> blockedByReason, List<Row> recentBlocked) {

        public ProviderQueue {
            statusCounts = copy(statusCounts);
            overdue = copy(overdue);
            stale = copy(stale);
            failedByErrorCode = copy(failedByErrorCode);
            recentFailed = copy(recentFailed);
            blockedByReason = copy(blockedByReason);
            recentBlocked = copy(recentBlocked);
        }

        private static <T> List<T> copy(List<T> values) {
            return values == null ? List.of() : List.copyOf(values);
        }
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
