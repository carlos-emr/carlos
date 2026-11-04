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
