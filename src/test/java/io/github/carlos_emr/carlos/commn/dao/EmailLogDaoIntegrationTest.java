/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Channel;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.Outcome;
import io.github.carlos_emr.carlos.commn.model.PatientPortalInviteDelivery.State;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteCodeSweeper;
import io.github.carlos_emr.carlos.integration.patientportal.PortalInviteDeliveryService;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailLogDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
class EmailLogDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private EmailLogDao emailLogDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    @DisplayName("should include logs with null optional associations")
    void shouldIncludeLogs_whenOptionalAssociationsAreNull() {
        Date timestamp = new Date();
        EmailLog log = new EmailLog();
        log.setFromEmail("smoke.sender@example.org");
        log.setToEmail(new String[] {"smoke.recipient@example.org"});
        log.setSubject("Null association regression");
        log.setBody("Body");
        log.setStatus(EmailLog.EmailStatus.SUCCESS);
        log.setTimestamp(timestamp);

        entityManager.persist(log);
        entityManager.flush();
        entityManager.clear();

        List<EmailLog> result = emailLogDao.getEmailStatusByDateDemographicSenderStatus(
                timestamp, timestamp, null, "smoke.sender@example.org", "SUCCESS");

        assertThat(result).extracting(EmailLog::getId).contains(log.getId());

        List<EmailLog> demographicFiltered = emailLogDao.getEmailStatusByDateDemographicSenderStatus(
                timestamp, timestamp, "999999", "smoke.sender@example.org", "SUCCESS");

        assertThat(demographicFiltered).extracting(EmailLog::getId).doesNotContain(log.getId());
    }

    @Test
    @DisplayName("should compare and set status using the persisted enum mapping")
    void shouldTransitionStatus_onlyFromExpectedPersistedStatus() {
        Date originalTimestamp = new Date(1_700_000_000_000L);
        EmailLog log = new EmailLog();
        log.setFromEmail("transition.sender@example.org");
        log.setToEmail(new String[] {"transition.recipient@example.org"});
        log.setSubject("Status transition regression");
        log.setBody("Body");
        log.setStatus(EmailLog.EmailStatus.PENDING);
        log.setTimestamp(originalTimestamp);

        entityManager.persist(log);
        entityManager.flush();

        assertThat(emailLogDao.transitionEmailStatus(
                log.getId(), EmailLog.EmailStatus.FAILED, EmailLog.EmailStatus.SUCCESS,
                "wrong predecessor", new Date())).isZero();

        Date completedAt = new Date(1_700_000_060_000L);
        assertThat(emailLogDao.transitionEmailStatus(
                log.getId(), EmailLog.EmailStatus.PENDING, EmailLog.EmailStatus.SUCCESS,
                "", completedAt)).isOne();

        assertThat(log.getStatus()).as("bulk update refreshes the cached entity")
                .isEqualTo(EmailLog.EmailStatus.SUCCESS);
        // Simulate a competing request's update while this persistence context retains SUCCESS.
        entityManager.createNativeQuery("UPDATE emailLog SET status = 'RESOLVED' WHERE id = ?1")
                .setParameter(1, log.getId()).executeUpdate();
        assertThat(emailLogDao.transitionEmailStatus(log.getId(), EmailLog.EmailStatus.PENDING,
                EmailLog.EmailStatus.FAILED, "stale failure", new Date())).isZero();
        assertThat(log.getStatus()).as("lost compare-and-set exposes the winning status")
                .isEqualTo(EmailLog.EmailStatus.RESOLVED);
        entityManager.clear();
        EmailLog updated = entityManager.find(EmailLog.class, log.getId());
        assertThat(updated.getStatus()).isEqualTo(EmailLog.EmailStatus.RESOLVED);
        assertThat(updated.getErrorMessage()).isEmpty();
        assertThat(updated.getTimestamp().getTime()).isEqualTo(completedAt.getTime());
    }

    @Test
    @DisplayName("should list uncleared emails of one transaction type by when they last changed")
    void shouldFindIds_byTransactionTypeLastChangeAndBody() {
        // Whole seconds: the column keeps no fraction, so the boundaries compare exactly.
        long now = System.currentTimeMillis() / 1000 * 1000;
        Date since = new Date(now - 8L * 24 * 60 * 60 * 1000);
        Date before = new Date(now - 15L * 60 * 1000);
        Integer idleInvite = persisted(EmailLog.TransactionType.PORTAL_INVITE, new Date(now - 60L * 60 * 1000));
        Integer atSince = persisted(EmailLog.TransactionType.PORTAL_INVITE, since);
        Integer atBefore = persisted(EmailLog.TransactionType.PORTAL_INVITE, before);
        Integer busyInvite = persisted(EmailLog.TransactionType.PORTAL_INVITE, new Date(now));
        Integer expiredInvite = persisted(EmailLog.TransactionType.PORTAL_INVITE,
                new Date(now - 9L * 24 * 60 * 60 * 1000));
        Integer otherType = persisted(EmailLog.TransactionType.DIRECT, new Date(now - 60L * 60 * 1000));
        Integer cleared = persisted(EmailLog.TransactionType.PORTAL_INVITE, new Date(now - 60L * 60 * 1000));
        EmailLog clearedRow = entityManager.find(EmailLog.class, cleared);
        clearedRow.setBody("code removed");
        entityManager.flush();

        List<Integer> ids = emailLogDao.findIdsByTransactionTypeChangedBeforeWithOtherBody(
                EmailLog.TransactionType.PORTAL_INVITE, before, "code removed", 0, 200);

        assertThat(ids).contains(idleInvite, atSince, expiredInvite)
                .doesNotContain(atBefore, busyInvite, otherType, cleared);
        assertThat(emailLogDao.findIdsByTransactionTypeChangedBeforeWithOtherBody(
                EmailLog.TransactionType.PORTAL_INVITE, before, "code removed", idleInvite, 1))
                .containsExactly(atSince);
    }

    @Test
    @DisplayName("should leave unfinished and manually resolved sends untouched, regardless of age")
    void shouldExcludeAmbiguousSends_whenSelectingCleanup() {
        Date old = new Date(System.currentTimeMillis() - 10L * 24 * 60 * 60 * 1000);
        Date cutoff = new Date(System.currentTimeMillis() - 15L * 60 * 1000);
        java.util.Map<EmailLog.EmailStatus, Integer> rows = new java.util.EnumMap<>(EmailLog.EmailStatus.class);
        for (EmailLog.EmailStatus status : EmailLog.EmailStatus.values()) {
            Integer id = persisted(EmailLog.TransactionType.PORTAL_INVITE, old);
            entityManager.find(EmailLog.class, id).setStatus(status);
            rows.put(status, id);
        }
        entityManager.flush();
        assertThat(emailLogDao.findIdsByTransactionTypeChangedBeforeWithOtherBody(
                EmailLog.TransactionType.PORTAL_INVITE, cutoff, "code removed", 0, 200))
                .contains(rows.get(EmailLog.EmailStatus.SUCCESS), rows.get(EmailLog.EmailStatus.BLOCKED))
                .doesNotContain(rows.get(EmailLog.EmailStatus.PENDING), rows.get(EmailLog.EmailStatus.RESOLVED),
                        rows.get(EmailLog.EmailStatus.FAILED));
    }

    @Test
    @DisplayName("should recheck status and cutoff atomically without changing other fields")
    void shouldProtectChangedRows_whenScrubbingSelectedEmails() {
        Date old = new Date(System.currentTimeMillis() / 1000 * 1000 - 60L * 60 * 1000);
        Date cutoff = new Date(System.currentTimeMillis() - 15L * 60 * 1000);
        Integer id = persisted(EmailLog.TransactionType.PORTAL_INVITE, old);
        // The production method uses REQUIRES_NEW; invoke the target in this test's transaction so
        // it can see the uncommitted fixture, while exercising its actual SQL against H2.
        EmailLogDaoImpl target = (EmailLogDaoImpl) org.springframework.test.util.AopTestUtils
                .getUltimateTargetObject(emailLogDao);
        entityManager.find(EmailLog.class, id).setStatus(EmailLog.EmailStatus.RESOLVED);
        entityManager.flush();
        assertThat(target.replaceBodyIfUnchangedBefore(id, EmailLog.TransactionType.PORTAL_INVITE,
                cutoff, "code removed")).isZero();
        EmailLog row = entityManager.find(EmailLog.class, id);
        row.setStatus(EmailLog.EmailStatus.SUCCESS);
        row.setTimestamp(new Date());
        entityManager.flush();
        assertThat(target.replaceBodyIfUnchangedBefore(id, EmailLog.TransactionType.PORTAL_INVITE,
                cutoff, "code removed")).isZero();
        row.setTimestamp(old);
        entityManager.flush();
        assertThat(target.replaceBodyIfUnchangedBefore(id, EmailLog.TransactionType.PORTAL_INVITE,
                cutoff, "code removed")).isOne();
        entityManager.clear();
        row = entityManager.find(EmailLog.class, id);
        assertThat(row.getBody()).isEqualTo("code removed");
        assertThat(row.getStatus()).isEqualTo(EmailLog.EmailStatus.SUCCESS);
        assertThat(row.getTimestamp().getTime()).isEqualTo(old.getTime());
        assertThat(target.replaceBodyIfUnchangedBefore(id, EmailLog.TransactionType.PORTAL_INVITE,
                cutoff, "code removed")).isZero();
    }

    @Test
    @DisplayName("should include a FAILED email only when its invitation attempt recorded a definite not-sent")
    void shouldIncludeFailedEmail_onlyWhenItsAttemptRecordedARefusal() {
        Date old = new Date(System.currentTimeMillis() / 1000 * 1000 - 60L * 60 * 1000);
        Date cutoff = new Date(System.currentTimeMillis() - 15L * 60 * 1000);
        Integer refused = failed(old, null);
        attempt(refused, State.SEND_FAILED, Outcome.SEND_REFUSED);
        Integer abandoned = failed(old, "Staff stopped this delivery; the invitation email was never sent.");
        attempt(abandoned, State.ABANDONED, Outcome.ABANDONED_BY_STAFF);
        // A permission refusal after the gate propagates, and settling leaves the attempt SEND_UNCERTAIN.
        Integer permissionRefused = failed(old, "Failed to send email (authorization failure)");
        attempt(permissionRefused, State.SEND_UNCERTAIN, Outcome.SEND_UNCONFIRMED);
        // The portal refused to activate the code at the gate: the email is FAILED and the attempt, which
        // names the email from QUEUED on, ends ABANDONED.
        Integer commitRefused = failed(old, "Failed to send email (uncategorized delivery failure)");
        attempt(commitRefused, State.ABANDONED, Outcome.COMMIT_REFUSED);
        // An error before the gate (building, archiving, redacting) leaves no attempt naming the email.
        Integer noAttempt = failed(old, "Failed to archive outbound email (I/O failure)");
        // Transport returned but its FAILED write did not land, so the email is still PENDING.
        Integer stillPending = persisted(EmailLog.TransactionType.PORTAL_INVITE, old);
        entityManager.find(EmailLog.class, stillPending).setStatus(EmailLog.EmailStatus.PENDING);
        attempt(stillPending, State.SEND_FAILED, Outcome.SEND_REFUSED);
        Integer recentRefusal = failed(new Date(), null);
        attempt(recentRefusal, State.SEND_FAILED, Outcome.SEND_REFUSED);
        // Guards for states the code does not write today: SEND_FAILED without its outcome, and one
        // email named by an attempt that is still open.
        Integer failedWithoutOutcome = failed(old, null);
        attempt(failedWithoutOutcome, State.SEND_FAILED, null);
        Integer sharedWithOpenAttempt = failed(old, null);
        attempt(sharedWithOpenAttempt, State.SEND_FAILED, Outcome.SEND_REFUSED);
        attempt(sharedWithOpenAttempt, State.COMMITTED, null);
        entityManager.flush();

        assertThat(emailLogDao.findIdsByTransactionTypeChangedBeforeWithOtherBody(
                EmailLog.TransactionType.PORTAL_INVITE, cutoff, "code removed", 0, 200))
                .contains(refused)
                .doesNotContain(abandoned, permissionRefused, commitRefused, noAttempt, stillPending,
                        recentRefusal, failedWithoutOutcome, sharedWithOpenAttempt);

        EmailLogDaoImpl target = (EmailLogDaoImpl) org.springframework.test.util.AopTestUtils
                .getUltimateTargetObject(emailLogDao);
        assertThat(target.replaceBodyIfUnchangedBefore(abandoned, EmailLog.TransactionType.PORTAL_INVITE,
                cutoff, "code removed")).isZero();
        assertThat(target.replaceBodyIfUnchangedBefore(sharedWithOpenAttempt,
                EmailLog.TransactionType.PORTAL_INVITE, cutoff, "code removed")).isZero();
        assertThat(target.replaceBodyIfUnchangedBefore(refused, EmailLog.TransactionType.PORTAL_INVITE,
                cutoff, "code removed")).isOne();
    }

    @Test
    @DisplayName("should clear a refused invitation's code in a sweep and leave abandoned and pending ones alone")
    void shouldClearRefusedInvitation_whenSweeping() {
        Date old = new Date(System.currentTimeMillis() / 1000 * 1000 - 60L * 60 * 1000);
        Integer refused = failed(old, "Failed to send email (SMTP recipient failure)");
        attempt(refused, State.SEND_FAILED, Outcome.SEND_REFUSED);
        Integer abandoned = failed(old, "Staff stopped this delivery; the invitation email was never sent.");
        attempt(abandoned, State.ABANDONED, Outcome.ABANDONED_BY_STAFF);
        Integer stuck = persisted(EmailLog.TransactionType.PORTAL_INVITE, old);
        entityManager.find(EmailLog.class, stuck).setStatus(EmailLog.EmailStatus.PENDING);
        attempt(stuck, State.COMMITTED, null);
        entityManager.flush();
        // The production update runs in its own transaction; use the target so it sees this fixture.
        EmailLogDaoImpl target = (EmailLogDaoImpl) org.springframework.test.util.AopTestUtils
                .getUltimateTargetObject(emailLogDao);

        int cleared = new PortalInviteCodeSweeper(target)
                .forgetLeftoverCodes(PortalInviteDeliveryService.RECOVERY_MIN_AGE);

        assertThat(cleared).isEqualTo(1);
        entityManager.clear();
        EmailLog refusedRow = entityManager.find(EmailLog.class, refused);
        assertThat(refusedRow.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);
        assertThat(refusedRow.getBody()).isEqualTo(EmailLog.PORTAL_INVITE_BODY_FORGOTTEN);
        assertThat(refusedRow.getTimestamp().getTime()).isEqualTo(old.getTime());
        assertThat(entityManager.find(EmailLog.class, abandoned).getBody()).isEqualTo("Body");
        assertThat(entityManager.find(EmailLog.class, stuck).getBody()).isEqualTo("Body");
    }

    private Integer failed(Date timestamp, String errorMessage) {
        Integer id = persisted(EmailLog.TransactionType.PORTAL_INVITE, timestamp);
        EmailLog row = entityManager.find(EmailLog.class, id);
        row.setStatus(EmailLog.EmailStatus.FAILED);
        row.setErrorMessage(errorMessage);
        entityManager.flush();
        return id;
    }

    private void attempt(Integer emailLogId, State state, Outcome outcome) {
        PatientPortalInviteDelivery attempt = new PatientPortalInviteDelivery("inv-" + UUID.randomUUID(), 1,
                "clinic-a", "https://portal.example", Channel.EMAIL, null, "999998");
        attempt.setState(state);
        attempt.setOutcome(outcome);
        attempt.setEmailLogId(emailLogId);
        entityManager.persist(attempt);
        entityManager.flush();
    }

    private Integer persisted(EmailLog.TransactionType type, Date timestamp) {
        EmailLog log = new EmailLog();
        log.setFromEmail("sweep.sender@example.org");
        log.setToEmail(new String[] {"sweep.recipient@example.org"});
        log.setSubject("Sweep window");
        log.setBody("Body");
        log.setStatus(EmailLog.EmailStatus.SUCCESS);
        log.setTransactionType(type);
        log.setTimestamp(timestamp);
        entityManager.persist(log);
        entityManager.flush();
        return log.getId();
    }

    @Test
    void shouldClearBody_withoutOverwritingAConcurrentStatusOrTimestamp() {
        EmailLog log = new EmailLog();
        log.setFromEmail("body.sender@example.org");
        log.setToEmail(new String[] {"body.recipient@example.org"});
        log.setBody("invitation credential");
        log.setStatus(EmailLog.EmailStatus.PENDING);
        log.setTimestamp(new Date(1_700_000_000_000L));
        entityManager.persist(log);
        entityManager.flush();
        Date completed = new Date(1_700_000_060_000L);
        entityManager.createNativeQuery("UPDATE emailLog SET status = 'SUCCESS', timestamp = ?1 WHERE id = ?2")
                .setParameter(1, completed).setParameter(2, log.getId()).executeUpdate();
        // Keep the stale managed PENDING snapshot, as a concurrent writer can leave one. Invoke the
        // DAO target inside this rollback transaction so its normal REQUIRES_NEW does not hide the fixture.
        EmailLogDaoImpl target = new EmailLogDaoImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(target, "entityManager", entityManager);
        assertThat(target.replaceBody(log.getId(), "code removed")).isOne();
        entityManager.flush();
        entityManager.clear();
        EmailLog updated = entityManager.find(EmailLog.class, log.getId());
        assertThat(updated.getStatus()).isEqualTo(EmailLog.EmailStatus.SUCCESS);
        assertThat(updated.getTimestamp().getTime()).isEqualTo(completed.getTime());
        assertThat(updated.getBody()).isEqualTo("code removed");
    }
}
