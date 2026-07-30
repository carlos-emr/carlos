package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDao;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDao;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the portable queue-claim and rate-limit DAO SQL against the H2 (MySQL-mode)
 * schema. The claim queries were rewritten from MySQL-only {@code UPDATE ... ORDER BY ... LIMIT} to a
 * pessimistic-write {@code SELECT ... FOR UPDATE} so they run on both MariaDB and H2; these tests pin
 * that behaviour and the {@code INSERT IGNORE} / {@code SELECT ... FOR UPDATE} rate-limit path.
 */
@Tag("integration")
@Tag("dao")
@DisplayName("SMS queue persistence integration")
class SmsQueuePersistenceIntegrationTest extends CarlosTestBase {

    @Autowired
    private SmsTransactionDao smsTransactionDao;

    @Autowired
    private SmsProviderRateLimitDao smsProviderRateLimitDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @DisplayName("claims due rows up to the limit and leaves the rest queued")
    void shouldClaimDueRowsAtomically_whenMultipleQueued() {
        persistQueued();
        persistQueued();
        persistQueued();
        entityManager.flush();
        Date claimAt = new Date(System.currentTimeMillis() + 1_000);

        List<SmsTransaction> firstClaim = smsTransactionDao.claimDueOutboundQueue(SmsProviderType.STUB, claimAt, 2);

        assertThat(firstClaim).hasSize(2);
        assertThat(firstClaim).allSatisfy(t -> {
            assertThat(t.getStatus()).isEqualTo(SmsStatus.SENDING);
            assertThat(t.getAttemptCount()).isEqualTo(1);
            assertThat(t.getClaimToken()).isNotBlank();
        });
        assertThat(queuedCount()).isEqualTo(1L);

        List<SmsTransaction> secondClaim = smsTransactionDao.claimDueOutboundQueue(SmsProviderType.STUB, claimAt, 2);
        assertThat(secondClaim).hasSize(1);
        assertThat(queuedCount()).isZero();

        assertThat(smsTransactionDao.claimDueOutboundQueue(SmsProviderType.STUB, claimAt, 2)).isEmpty();
    }

    @Test
    @DisplayName("recovers a stale SENDING row and tags it with a claim token")
    void shouldClaimStaleSendingRow_whenLastAttemptIsOld() {
        SmsTransaction stale = SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        );
        stale.markSending(Date.from(Instant.parse("2026-06-08T11:00:00Z")));
        entityManager.persist(stale);
        entityManager.flush();
        Date staleBefore = Date.from(Instant.parse("2026-06-08T12:00:00Z"));
        Date recoveryAt = Date.from(Instant.parse("2026-06-08T12:05:00Z"));

        List<SmsTransaction> recovered = smsTransactionDao.claimStaleOutboundSendingForRecovery(
                SmsProviderType.STUB, staleBefore, recoveryAt, 10
        );

        assertThat(recovered).singleElement().satisfies(t -> {
            assertThat(t.getStatus()).isEqualTo(SmsStatus.SENDING);
            assertThat(t.getLastAttemptAt()).isEqualTo(recoveryAt);
            assertThat(t.getClaimToken()).isNotBlank();
        });
    }

    @Test
    @DisplayName("rate limiter allows up to the window cap then denies (INSERT IGNORE + FOR UPDATE on H2)")
    void shouldAllowUpToCapThenDeny_withinWindow() {
        Clock fixed = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC);
        JpaSmsSendRateLimiter limiter = new JpaSmsSendRateLimiter(
                smsProviderRateLimitDao, 2, Duration.ofMinutes(5), fixed
        );

        assertThat(limiter.tryAcquire(SmsProviderType.VOIPMS)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.VOIPMS)).isTrue();
        assertThat(limiter.tryAcquire(SmsProviderType.VOIPMS)).isFalse();
    }

    private void persistQueued() {
        entityManager.persist(SmsTransaction.outboundAttempt(
                SmsSendCommand.direct(123, "416-555-1212", "Appointment reminder", "999998"),
                SmsProviderType.STUB
        ));
    }

    private long queuedCount() {
        return entityManager.createQuery(
                        "SELECT COUNT(t) FROM SmsTransaction t WHERE t.providerType = :pt AND t.status = :status",
                        Long.class
                )
                .setParameter("pt", SmsProviderType.STUB)
                .setParameter("status", SmsStatus.QUEUED)
                .getSingleResult();
    }
}
