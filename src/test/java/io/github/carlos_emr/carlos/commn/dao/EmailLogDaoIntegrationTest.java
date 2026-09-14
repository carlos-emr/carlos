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
}
