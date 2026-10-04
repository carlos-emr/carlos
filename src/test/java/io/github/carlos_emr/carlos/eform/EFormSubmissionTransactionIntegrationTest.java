/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Uses real eForm rows and transaction completion callbacks, with no ambient test transaction. */
@Tag("integration")
@Tag("eform")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EFormSubmissionTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EFormDataDao forms;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entities;
    private final MockHttpSession session = new MockHttpSession();
    private final String marker = "submission-" + UUID.randomUUID();
    private final String token = EFormSubmissionGuard.issue(session, "1", "123");

    @AfterEach
    void removeOwnedRows() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> entities.createQuery(
                "delete from EFormData where formName = :marker").setParameter("marker", marker).executeUpdate());
    }

    private void save(boolean rollback, boolean failAfterCommit) {
        try (var claim = EFormSubmissionGuard.claim(session, token, "1", "123")) {
            assertThat(claim).isNotNull();
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                claim.storageStarted();
                EFormData row = new EFormData();
                row.setDemographicId(123);
                row.setFormId(1);
                row.setFormName(marker);
                row.setSubject("synthetic submission");
                row.setFormDate(new Date());
                row.setFormTime(new Date());
                row.setProviderNo("999001");
                row.setFormData("<form>synthetic</form>");
                row.setCurrent(true);
                row.setShowLatestFormOnly(false);
                row.setPatientIndependent(false);
                row.setRoleType("");
                forms.persist(row);
                entities.flush();
                if (rollback) tx.setRollbackOnly();
                if (failAfterCommit) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { throw new IllegalStateException("synthetic callback failure"); }
                });
            });
        }
    }

    private long rows() {
        return new TransactionTemplate(transactions).execute(tx -> entities.createQuery(
                "select count(e) from EFormData e where e.formName = :marker", Long.class)
                .setParameter("marker", marker).getSingleResult());
    }

    @Test
    void shouldKeepOneCommittedForm_whenPostIsReplayed() {
        save(false, false);
        assertThat(EFormSubmissionGuard.claim(session, token, "1", "123")).isNull();
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void shouldAllowRetry_whenTransactionReallyRollsBack() {
        save(true, false);
        assertThat(rows()).isZero();
        save(false, false);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void shouldRejectReplay_whenCallbackThrowsAfterCommit() {
        assertThatThrownBy(() -> save(false, true)).isInstanceOf(IllegalStateException.class);
        assertThat(rows()).isEqualTo(1);
        assertThat(EFormSubmissionGuard.claim(session, token, "1", "123")).isNull();
    }
}
