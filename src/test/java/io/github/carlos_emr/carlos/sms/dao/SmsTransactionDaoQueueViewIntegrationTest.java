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
package io.github.carlos_emr.carlos.sms.dao;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsInboundWebhookDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueCountDto;
import io.github.carlos_emr.carlos.sms.dto.SmsQueueRowDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the Administration &gt; SMS queue-view queries of {@link SmsTransactionDao} against the H2 (MySQL-mode)
 * schema generated from the entities.
 * <p>
 * Every test rolls back. To stay exact even if another test left rows behind, the overdue and stale cases
 * use times in 2001 (far older than any other test data), the grouped cases use error and reason codes no
 * other test uses, the newest-first cases push their rows' {@code updated_at} into 2090, and the time-period
 * cases into 2091.
 *
 * @since 2026-09-28
 */
@Tag("integration")
@Tag("dao")
@Tag("query")
@DisplayName("SmsTransactionDao queue-view queries (H2)")
class SmsTransactionDaoQueueViewIntegrationTest extends CarlosTestBase {
    private static final Instant NOW = Instant.parse("2001-02-03T12:00:00Z");
    /** The cutoff the queue view passes: five minutes before "now". */
    private static final Date FIVE_MINUTES_AGO = Date.from(NOW.minus(Duration.ofMinutes(5)));
    private static final List<SmsStatus> BLOCKED = List.of(SmsStatus.CONSENT_BLOCKED, SmsStatus.OPTOUT_BLOCKED);

    @Autowired
    private SmsTransactionDao smsTransactionDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private int inboundSequence;

    @Test
    @DisplayName("should count outbound rows by SMS provider and status, leaving inbound rows out")
    void shouldCountOutboundRows_byProviderAndStatus() {
        List<SmsQueueCountDto> before = smsTransactionDao.countOutboundByProviderAndStatus();
        persistQueued(SmsProviderType.STUB, NOW, NOW);
        persistQueued(SmsProviderType.STUB, NOW, NOW);
        persistFailed(SmsProviderType.STUB, "QVIEW_COUNT_ERR");
        persistSending(SmsProviderType.VOIPMS, NOW);
        entityManager.persist(inbound(SmsProviderType.STUB, SmsStatus.RECEIVED, NOW));
        entityManager.persist(inbound(SmsProviderType.STUB, SmsStatus.QUEUED, NOW));
        entityManager.flush();

        List<SmsQueueCountDto> after = smsTransactionDao.countOutboundByProviderAndStatus();

        assertThat(count(after, SmsProviderType.STUB, "QUEUED") - count(before, SmsProviderType.STUB, "QUEUED"))
                .isEqualTo(2);
        assertThat(count(after, SmsProviderType.STUB, "FAILED") - count(before, SmsProviderType.STUB, "FAILED"))
                .isEqualTo(1);
        assertThat(count(after, SmsProviderType.VOIPMS, "SENDING") - count(before, SmsProviderType.VOIPMS, "SENDING"))
                .isEqualTo(1);
        assertThat(count(after, SmsProviderType.STUB, "RECEIVED")).isEqualTo(count(before, SmsProviderType.STUB,
                "RECEIVED"));
    }

    @Test
    @DisplayName("should count a queued row as overdue only when it was due more than five minutes ago")
    void shouldCountOverdueQueued_atFiveMinuteBoundary() {
        // Retries (one attempt made): the scheduled retry time decides, however old the row is.
        SmsTransaction justOver = persistRetry(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)),
                NOW.minus(Duration.ofMinutes(5)).minusSeconds(1));
        persistRetry(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)),
                NOW.minus(Duration.ofMinutes(5)).plusSeconds(1));
        // Exactly at the threshold is not yet overdue.
        persistRetry(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofMinutes(5)));
        persistRetry(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(1)));
        // Never attempted: due since creation, so the creation time decides.
        SmsTransaction createdLongAgo = persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofMinutes(10)), null);
        persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofMinutes(4)), null);
        persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofMinutes(5)), null);
        persistQueued(SmsProviderType.VOIPMS, NOW.minus(Duration.ofHours(1)), null);
        entityManager.persist(inbound(SmsProviderType.STUB, SmsStatus.QUEUED, NOW.minus(Duration.ofHours(1))));
        entityManager.flush();

        Map<SmsProviderType, Long> counts = smsTransactionDao.countOverdueQueuedOutboundByProvider(FIVE_MINUTES_AGO);
        List<SmsQueueRowDto> rows = smsTransactionDao.findOverdueQueuedOutbound(
                SmsProviderType.STUB, FIVE_MINUTES_AGO, 10);

        assertThat(counts).containsEntry(SmsProviderType.STUB, 2L).containsEntry(SmsProviderType.VOIPMS, 1L);
        assertThat(rows).extracting(SmsQueueRowDto::id)
                .containsExactly(createdLongAgo.getId(), justOver.getId());
    }

    @Test
    @DisplayName("should count a never-attempted row as overdue even when the rate limit keeps resetting its due time")
    void shouldCountOverdueQueued_whenRateLimitKeepsReleasingIt() {
        // The worker claimed this row and handed it back a moment ago because the rate limit held it.
        SmsTransaction held = persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)), NOW);
        entityManager.flush();

        Map<SmsProviderType, Long> counts = smsTransactionDao.countOverdueQueuedOutboundByProvider(FIVE_MINUTES_AGO);
        List<SmsQueueRowDto> rows = smsTransactionDao.findOverdueQueuedOutbound(
                SmsProviderType.STUB, FIVE_MINUTES_AGO, 10);

        assertThat(counts).containsEntry(SmsProviderType.STUB, 1L);
        assertThat(rows).extracting(SmsQueueRowDto::id).containsExactly(held.getId());
    }

    @Test
    @DisplayName("should cap the overdue list and keep the oldest due rows")
    void shouldCapOverdueList_keepingOldestFirst() {
        SmsTransaction oldest = persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofHours(3)), null);
        SmsTransaction middle = persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofHours(2)), null);
        persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)), null);
        entityManager.flush();

        List<SmsQueueRowDto> rows = smsTransactionDao.findOverdueQueuedOutbound(
                SmsProviderType.STUB, FIVE_MINUTES_AGO, 2);

        assertThat(rows).extracting(SmsQueueRowDto::id).containsExactly(oldest.getId(), middle.getId());
        assertThat(rows.get(0)).satisfies(row -> {
            assertThat(row.providerType()).isEqualTo(SmsProviderType.STUB);
            assertThat(row.status()).isEqualTo(SmsStatus.QUEUED);
            assertThat(row.demographicNo()).isEqualTo(123);
            assertThat(row.toPhoneNumber()).isEqualTo("+14165551212");
            assertThat(row.createdAt()).isEqualTo(NOW.minus(Duration.ofHours(3)));
            assertThat(row.nextAttemptAt()).isNull();
        });
    }

    @Test
    @DisplayName("should count a SENDING row as stale only when its last attempt is older than the threshold")
    void shouldCountStaleSending_atThresholdBoundary() {
        SmsTransaction oldest = persistSending(SmsProviderType.STUB, NOW.minus(Duration.ofMinutes(30)));
        SmsTransaction justOver = persistSending(SmsProviderType.STUB,
                NOW.minus(Duration.ofMinutes(5)).minusSeconds(1));
        persistSending(SmsProviderType.STUB, NOW.minus(Duration.ofMinutes(5)).plusSeconds(1));
        // Exactly at the threshold is not yet stale, as in the worker's own recovery query.
        persistSending(SmsProviderType.STUB, NOW.minus(Duration.ofMinutes(5)));
        persistQueued(SmsProviderType.STUB, NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofHours(1)));
        SmsTransaction inbound = inbound(SmsProviderType.STUB, SmsStatus.SENDING, NOW.minus(Duration.ofHours(1)));
        ReflectionTestUtils.setField(inbound, "lastAttemptAt", Date.from(NOW.minus(Duration.ofHours(1))));
        entityManager.persist(inbound);
        entityManager.flush();

        Map<SmsProviderType, Long> counts = smsTransactionDao.countStaleSendingOutboundByProvider(FIVE_MINUTES_AGO);
        List<SmsQueueRowDto> rows = smsTransactionDao.findStaleSendingOutbound(
                SmsProviderType.STUB, FIVE_MINUTES_AGO, 10);

        assertThat(counts).containsEntry(SmsProviderType.STUB, 2L).doesNotContainKey(SmsProviderType.VOIPMS);
        assertThat(rows).extracting(SmsQueueRowDto::id).containsExactly(oldest.getId(), justOver.getId());
        assertThat(rows.get(0).lastAttemptAt()).isEqualTo(NOW.minus(Duration.ofMinutes(30)));
        assertThat(rows.get(0).attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should count FAILED outbound rows by error code, ignoring retries and inbound rows")
    void shouldCountFailed_byErrorCode() {
        persistFailed(SmsProviderType.STUB, "QVIEW_ERR_A");
        persistFailed(SmsProviderType.STUB, "QVIEW_ERR_A");
        persistFailed(SmsProviderType.STUB, "QVIEW_ERR_B");
        persistFailed(SmsProviderType.VOIPMS, "QVIEW_ERR_A");
        // Still queued for a retry: its error code is not a terminal failure.
        SmsTransaction retrying = outbound(SmsProviderType.STUB);
        retrying.markRetryScheduled(SmsProviderSendResultDto.failed("QVIEW_ERR_A", "synthetic retry"),
                Date.from(NOW));
        entityManager.persist(retrying);
        SmsTransaction inbound = inbound(SmsProviderType.STUB, SmsStatus.FAILED, NOW);
        ReflectionTestUtils.setField(inbound, "errorCode", "QVIEW_ERR_A");
        entityManager.persist(inbound);
        entityManager.flush();

        List<SmsQueueCountDto> counts = smsTransactionDao.countFailedOutboundByProviderAndErrorCode(null);

        assertThat(count(counts, SmsProviderType.STUB, "QVIEW_ERR_A")).isEqualTo(2);
        assertThat(count(counts, SmsProviderType.STUB, "QVIEW_ERR_B")).isEqualTo(1);
        assertThat(count(counts, SmsProviderType.VOIPMS, "QVIEW_ERR_A")).isEqualTo(1);
    }

    @Test
    @DisplayName("should count both consent-blocking statuses by reason code, leaving inbound rows out")
    void shouldCountConsentBlocked_byReasonCode() {
        persistBlocked(SmsProviderType.STUB, SmsStatus.CONSENT_BLOCKED, "QVIEW_REASON_X");
        persistBlocked(SmsProviderType.STUB, SmsStatus.CONSENT_BLOCKED, "QVIEW_REASON_X");
        persistBlocked(SmsProviderType.STUB, SmsStatus.OPTOUT_BLOCKED, "QVIEW_REASON_X");
        persistBlocked(SmsProviderType.STUB, SmsStatus.OPTOUT_BLOCKED, "QVIEW_REASON_Y");
        persistFailed(SmsProviderType.STUB, "QVIEW_REASON_X");
        SmsTransaction inbound = inbound(SmsProviderType.STUB, SmsStatus.CONSENT_BLOCKED, NOW);
        ReflectionTestUtils.setField(inbound, "consentReasonCode", "QVIEW_REASON_X");
        entityManager.persist(inbound);
        entityManager.flush();

        List<SmsQueueCountDto> counts = smsTransactionDao.countConsentBlockedOutboundByProviderAndReason(null);
        List<SmsQueueRowDto> recent =
                smsTransactionDao.findRecentOutboundByStatuses(SmsProviderType.STUB, BLOCKED, null, 500);

        assertThat(count(counts, SmsProviderType.STUB, "QVIEW_REASON_X")).isEqualTo(3);
        assertThat(count(counts, SmsProviderType.STUB, "QVIEW_REASON_Y")).isEqualTo(1);
        assertThat(recent).filteredOn(row -> Objects.equals(row.consentReasonCode(), "QVIEW_REASON_X"))
                .extracting(SmsQueueRowDto::status)
                .containsExactlyInAnyOrder(SmsStatus.CONSENT_BLOCKED, SmsStatus.CONSENT_BLOCKED,
                        SmsStatus.OPTOUT_BLOCKED);
    }

    @Test
    @DisplayName("should list terminal rows most recently updated first, capped at the limit")
    void shouldListRecentRows_newestFirstAndCapped() {
        SmsTransaction first = persistFailed(SmsProviderType.STUB, "QVIEW_ERR_ORDER");
        SmsTransaction newest = persistFailed(SmsProviderType.STUB, "QVIEW_ERR_ORDER");
        SmsTransaction second = persistFailed(SmsProviderType.STUB, "QVIEW_ERR_ORDER");
        entityManager.flush();
        setUpdatedAt(first, Instant.parse("2090-01-01T10:00:00Z"));
        setUpdatedAt(newest, Instant.parse("2090-01-01T12:00:00Z"));
        setUpdatedAt(second, Instant.parse("2090-01-01T11:00:00Z"));
        entityManager.clear();

        List<SmsQueueRowDto> rows = smsTransactionDao.findRecentOutboundByStatuses(
                SmsProviderType.STUB, List.of(SmsStatus.FAILED), null, 2);

        assertThat(rows).extracting(SmsQueueRowDto::id).containsExactly(newest.getId(), second.getId());
        assertThat(rows.get(0).updatedAt()).isEqualTo(Instant.parse("2090-01-01T12:00:00Z"));
        assertThat(rows.get(0).errorCode()).isEqualTo("QVIEW_ERR_ORDER");
    }

    @Test
    @DisplayName("should count and list failed rows only when they were updated within the time period")
    void shouldLimitFailed_toRowsUpdatedSince() {
        Instant since = Instant.parse("2091-03-01T00:00:00Z");
        SmsTransaction before = persistFailed(SmsProviderType.STUB, "QVIEW_ERR_WINDOW");
        SmsTransaction atStart = persistFailed(SmsProviderType.STUB, "QVIEW_ERR_WINDOW");
        SmsTransaction after = persistFailed(SmsProviderType.STUB, "QVIEW_ERR_WINDOW");
        entityManager.flush();
        setUpdatedAt(before, since.minusSeconds(1));
        // Exactly at the start of the time period is inside it.
        setUpdatedAt(atStart, since);
        setUpdatedAt(after, since.plus(Duration.ofDays(1)));
        entityManager.clear();

        List<SmsQueueCountDto> windowed = smsTransactionDao.countFailedOutboundByProviderAndErrorCode(
                Date.from(since));
        List<SmsQueueCountDto> allTime = smsTransactionDao.countFailedOutboundByProviderAndErrorCode(null);
        List<SmsQueueRowDto> rows = smsTransactionDao.findRecentOutboundByStatuses(
                SmsProviderType.STUB, List.of(SmsStatus.FAILED), Date.from(since), 500);
        List<SmsQueueRowDto> allRows = smsTransactionDao.findRecentOutboundByStatuses(
                SmsProviderType.STUB, List.of(SmsStatus.FAILED), null, 500);

        assertThat(count(windowed, SmsProviderType.STUB, "QVIEW_ERR_WINDOW")).isEqualTo(2);
        assertThat(count(allTime, SmsProviderType.STUB, "QVIEW_ERR_WINDOW")).isEqualTo(3);
        assertThat(rows).filteredOn(row -> Objects.equals(row.errorCode(), "QVIEW_ERR_WINDOW"))
                .extracting(SmsQueueRowDto::id)
                .containsExactly(after.getId(), atStart.getId());
        assertThat(allRows).extracting(SmsQueueRowDto::id).contains(before.getId());
    }

    @Test
    @DisplayName("should count and list consent-blocked rows only when they were updated within the time period")
    void shouldLimitConsentBlocked_toRowsUpdatedSince() {
        Instant since = Instant.parse("2091-03-01T00:00:00Z");
        SmsTransaction before = persistBlocked(SmsProviderType.STUB, SmsStatus.CONSENT_BLOCKED, "QVIEW_REASON_WINDOW");
        SmsTransaction inside = persistBlocked(SmsProviderType.STUB, SmsStatus.OPTOUT_BLOCKED, "QVIEW_REASON_WINDOW");
        entityManager.flush();
        setUpdatedAt(before, since.minus(Duration.ofDays(1)));
        setUpdatedAt(inside, since.plus(Duration.ofDays(1)));
        entityManager.clear();

        List<SmsQueueCountDto> windowed = smsTransactionDao.countConsentBlockedOutboundByProviderAndReason(
                Date.from(since));
        List<SmsQueueCountDto> allTime = smsTransactionDao.countConsentBlockedOutboundByProviderAndReason(null);
        List<SmsQueueRowDto> rows = smsTransactionDao.findRecentOutboundByStatuses(
                SmsProviderType.STUB, BLOCKED, Date.from(since), 500);

        assertThat(count(windowed, SmsProviderType.STUB, "QVIEW_REASON_WINDOW")).isEqualTo(1);
        assertThat(count(allTime, SmsProviderType.STUB, "QVIEW_REASON_WINDOW")).isEqualTo(2);
        assertThat(rows).filteredOn(row -> Objects.equals(row.consentReasonCode(), "QVIEW_REASON_WINDOW"))
                .extracting(SmsQueueRowDto::id)
                .containsExactly(inside.getId());
    }

    private SmsTransaction outbound(SmsProviderType providerType) {
        return SmsTransaction.outboundAttempt(
                SmsSendCommand.patientMessage(123, "416-555-1212", "Synthetic queue view text", "999998"),
                providerType
        );
    }

    /** A queued outbound row created at {@code createdAt} and next due at {@code nextAttemptAt} (null: none). */
    private SmsTransaction persistQueued(SmsProviderType providerType, Instant createdAt, Instant nextAttemptAt) {
        SmsTransaction transaction = outbound(providerType);
        // No setters exist for these; @PrePersist keeps a non-null createdAt.
        ReflectionTestUtils.setField(transaction, "createdAt", Date.from(createdAt));
        Date nextAttempt = nextAttemptAt == null ? null : Date.from(nextAttemptAt);
        ReflectionTestUtils.setField(transaction, "nextAttemptAt", nextAttempt);
        entityManager.persist(transaction);
        return transaction;
    }

    /** A queued row that has been attempted once and is waiting for its retry at {@code nextAttemptAt}. */
    private SmsTransaction persistRetry(SmsProviderType providerType, Instant createdAt, Instant nextAttemptAt) {
        SmsTransaction transaction = outbound(providerType);
        ReflectionTestUtils.setField(transaction, "createdAt", Date.from(createdAt));
        ReflectionTestUtils.setField(transaction, "attemptCount", 1);
        ReflectionTestUtils.setField(transaction, "nextAttemptAt", Date.from(nextAttemptAt));
        entityManager.persist(transaction);
        return transaction;
    }

    private SmsTransaction persistSending(SmsProviderType providerType, Instant lastAttemptAt) {
        SmsTransaction transaction = outbound(providerType);
        transaction.markSending(Date.from(lastAttemptAt));
        entityManager.persist(transaction);
        return transaction;
    }

    private SmsTransaction persistFailed(SmsProviderType providerType, String errorCode) {
        SmsTransaction transaction = outbound(providerType);
        transaction.markProviderResult(SmsProviderSendResultDto.failed(errorCode, "synthetic failure"));
        entityManager.persist(transaction);
        return transaction;
    }

    private SmsTransaction persistBlocked(SmsProviderType providerType, SmsStatus status, String reasonCode) {
        SmsTransaction transaction = outbound(providerType);
        transaction.markConsentBlocked(SmsConsentDecisionDto.blocked(status, reasonCode, "synthetic block"));
        entityManager.persist(transaction);
        return transaction;
    }

    /**
     * An unsaved inbound row forced into {@code status} and created at {@code createdAt}, to prove the queue
     * view never counts inbound messages even when they look like queue work. Fields are set before persist,
     * so no reliance on dirty checking of reflective writes.
     */
    private SmsTransaction inbound(SmsProviderType providerType, SmsStatus status, Instant createdAt) {
        inboundSequence++;
        SmsTransaction transaction = SmsTransaction.inboundMessage(new SmsInboundWebhookDto(providerType,
                "qview-inbound-" + inboundSequence, "+14165550100", "+14165550000", "synthetic reply",
                createdAt, null));
        ReflectionTestUtils.setField(transaction, "status", status);
        ReflectionTestUtils.setField(transaction, "createdAt", Date.from(createdAt));
        return transaction;
    }

    /** Bulk update so the entity's own @PreUpdate cannot overwrite the chosen time. */
    private void setUpdatedAt(SmsTransaction transaction, Instant updatedAt) {
        entityManager.createQuery("UPDATE SmsTransaction t SET t.updatedAt = :updatedAt WHERE t.id = :id")
                .setParameter("updatedAt", Date.from(updatedAt))
                .setParameter("id", transaction.getId())
                .executeUpdate();
    }

    private static long count(List<SmsQueueCountDto> counts, SmsProviderType providerType, String code) {
        return counts.stream()
                .filter(count -> count.providerType() == providerType && Objects.equals(count.code(), code))
                .mapToLong(SmsQueueCountDto::count)
                .sum();
    }
}
