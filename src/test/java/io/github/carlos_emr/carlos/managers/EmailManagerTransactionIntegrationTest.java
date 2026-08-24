/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.managers;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.model.EmailLog;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailStatus;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Verifies the compliance-critical transaction boundary around manual email resolution. The test
 * invokes the real Spring proxy and real DAOs, while a temporary database constraint forces the
 * resolution audit insert to fail after the status compare-and-set has executed. The email status
 * must roll back with that failed audit insert.
 */
@Tag("integration")
@Tag("manager")
@Tag("email")
@Isolated("temporarily alters the shared log table schema")
@DisplayName("EmailManager transactional integration tests")
class EmailManagerTransactionIntegrationTest extends CarlosTestBase {

    private static final String REJECT_AUDIT_CONSTRAINT = "reject_email_resolution_audit";
    private static final String RESOLUTION_ACTION = "EmailManager.resolveEmailStatus";

    @Autowired
    private EmailManager emailManager;
    @Autowired
    private EmailLogDao emailLogDao;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private DataSource dataSource;

    private TransactionTemplate transactionTemplate;
    private JdbcTemplate jdbcTemplate;
    private Integer emailLogId;

    @BeforeEach
    void setUpIntegrationFixture() throws Exception {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate = new JdbcTemplate(dataSource);

        assertThat(AopUtils.isAopProxy(emailManager))
                .as("the test must invoke EmailManager through its Spring transaction proxy")
                .isTrue();

        emailLogId = transactionTemplate.execute(status -> {
            EmailLog emailLog = new EmailLog();
            emailLog.setFromEmail("transaction.sender@example.org");
            emailLog.setToEmail(new String[] {"transaction.recipient@example.org"});
            emailLog.setSubject("Resolution audit rollback regression");
            emailLog.setBody("Body");
            emailLog.setStatus(EmailStatus.FAILED);
            emailLog.setErrorMessage("SMTP rejected message");
            emailLogDao.persist(emailLog);
            return emailLog.getId();
        });

        jdbcTemplate.execute("ALTER TABLE log DROP CONSTRAINT IF EXISTS "
                + REJECT_AUDIT_CONSTRAINT);
        jdbcTemplate.execute("ALTER TABLE log ADD CONSTRAINT " + REJECT_AUDIT_CONSTRAINT
                + " CHECK (action <> '" + RESOLUTION_ACTION + "')");
    }

    @AfterEach
    void tearDownIntegrationFixture() {
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.execute("ALTER TABLE log DROP CONSTRAINT IF EXISTS "
                        + REJECT_AUDIT_CONSTRAINT);
            } finally {
                if (emailLogId != null) {
                    jdbcTemplate.update("DELETE FROM emailLog WHERE id = ?", emailLogId);
                }
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should roll back resolution when its audit insert fails")
    void shouldRollBackResolution_whenAuditInsertFails() {
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        assertThatThrownBy(() -> emailManager.resolveEmailStatus(loggedInInfo, emailLogId))
                .isInstanceOf(RuntimeException.class)
                .hasStackTraceContaining(REJECT_AUDIT_CONSTRAINT);

        EmailStatus persistedStatus = transactionTemplate.execute(status ->
                emailLogDao.find(emailLogId).getStatus());
        assertThat(persistedStatus).isEqualTo(EmailStatus.FAILED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM log WHERE action = ? AND contentId = ?",
                Integer.class, RESOLUTION_ACTION, String.valueOf(emailLogId))).isZero();
    }
}
