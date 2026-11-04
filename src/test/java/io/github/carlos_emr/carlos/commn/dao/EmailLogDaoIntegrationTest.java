/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

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
    @DisplayName("should reload a stored status even when the persistence context holds the previous status")
    void shouldReloadStatus_whenPersistenceContextIsStale() {
        EmailLog log = new EmailLog();
        log.setStatus(EmailLog.EmailStatus.FAILED);
        entityManager.persist(log);
        entityManager.flush();
        entityManager.createQuery("UPDATE EmailLog e SET e.status = :status WHERE e.id = :id")
                .setParameter("status", EmailLog.EmailStatus.RESOLVED).setParameter("id", log.getId()).executeUpdate();
        assertThat(log.getStatus()).isEqualTo(EmailLog.EmailStatus.FAILED);

        assertThat(emailLogDao.findFresh(log.getId()).getStatus()).isEqualTo(EmailLog.EmailStatus.RESOLVED);
        assertThat(emailLogDao.findFresh(-1)).isNull();
    }

    @Test
    @DisplayName("should preserve the stored send time when resolution receives an older snapshot")
    void shouldPreserveStoredSendTime_whenResolutionSnapshotIsStale() {
        Date originalTime = new Date(1760000000000L);
        Date latestTime = new Date(originalTime.getTime() + 60000);
        EmailLog log = new EmailLog();
        log.setStatus(EmailLog.EmailStatus.FAILED);
        log.setTimestamp(originalTime);
        log.setErrorMessage("FAKE delivery failure");
        entityManager.persist(log);
        entityManager.flush();
        // A transport failure records its time after the resolver loaded the log.
        entityManager.createQuery("UPDATE EmailLog e SET e.timestamp = :ts WHERE e.id = :id")
                .setParameter("ts", latestTime).setParameter("id", log.getId()).executeUpdate();

        assertThat(emailLogDao.updateEmailStatus(log.getId(), EmailLog.EmailStatus.RESOLVED, null, originalTime))
                .isEqualTo(1);
        entityManager.clear();

        EmailLog stored = entityManager.find(EmailLog.class, log.getId());
        assertThat(stored.getStatus()).isEqualTo(EmailLog.EmailStatus.RESOLVED);
        assertThat(stored.getErrorMessage()).isNull();
        assertThat(stored.getTimestamp().getTime()).isEqualTo(latestTime.getTime());
    }

    @ParameterizedTest
    @EnumSource(EmailLog.EmailStatus.class)
    @DisplayName("should resolve only failed rows, preserving their original send time")
    void shouldResolveOnlyFailedRows_preservingSendTime(EmailLog.EmailStatus initialStatus) {
        EmailLog log = new EmailLog();
        log.setStatus(initialStatus);
        log.setErrorMessage("FAKE original message");
        Date timestamp = new Date(1760000000000L);
        log.setTimestamp(timestamp);
        entityManager.persist(log);
        entityManager.flush();
        entityManager.clear();

        int updated = emailLogDao.updateEmailStatus(log.getId(), EmailLog.EmailStatus.RESOLVED, null, timestamp);
        entityManager.clear();

        EmailLog stored = entityManager.find(EmailLog.class, log.getId());
        assertThat(updated).isEqualTo(initialStatus == EmailLog.EmailStatus.FAILED ? 1 : 0);
        assertThat(stored.getStatus()).isEqualTo(initialStatus == EmailLog.EmailStatus.FAILED
                ? EmailLog.EmailStatus.RESOLVED : initialStatus);
        assertThat(stored.getErrorMessage()).isEqualTo(initialStatus == EmailLog.EmailStatus.FAILED
                ? null : "FAKE original message");
        assertThat(stored.getTimestamp().getTime()).isEqualTo(timestamp.getTime());
    }

    @ParameterizedTest
    @EnumSource(value = EmailLog.EmailStatus.class, names = {"SUCCESS", "FAILED"})
    @DisplayName("should record the supplied send time for actual transport outcomes")
    void shouldRecordSendTime_whenTransportOutcomeChanges(EmailLog.EmailStatus status) {
        EmailLog log = new EmailLog();
        log.setStatus(EmailLog.EmailStatus.FAILED);
        log.setTimestamp(new Date(1760000000000L));
        entityManager.persist(log);
        entityManager.flush();
        Date latestTime = new Date(1760000060000L);

        assertThat(emailLogDao.updateEmailStatus(log.getId(), status, "FAKE transport result", latestTime)).isEqualTo(1);
        entityManager.clear();

        EmailLog stored = entityManager.find(EmailLog.class, log.getId());
        assertThat(stored.getStatus()).isEqualTo(status);
        assertThat(stored.getTimestamp().getTime()).isEqualTo(latestTime.getTime());
    }

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
}
