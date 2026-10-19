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
    private boolean evictDuringSave;
    private final MockHttpSession session = new MockHttpSession();
    private final String marker = "submission-" + UUID.randomUUID();
    private final String token = EFormSubmissionGuard.issue(session, "1", "123");

    @AfterEach
    void removeOwnedRows() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> entities.createQuery(
                "delete from EFormData where formName = :marker").setParameter("marker", marker).executeUpdate());
    }

    private void save(boolean rollback, boolean failAfterCommit) {
        try (var claim = EFormSubmissionGuard.attempt(session, token, "1", "123").claim()) {
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
                if (evictDuringSave) {
                    for (int i = 0; i < 64; i++) EFormSubmissionGuard.issue(session, "1", "123");
                }
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
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
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
    void shouldAllowRollbackRetry_whenNewViewsEvictTheActiveIdentity() {
        evictDuringSave = true;
        save(true, false);
        assertThat(rows()).isZero();
        evictDuringSave = false;
        save(false, false);
        assertThat(rows()).isEqualTo(1);
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
    }

    @Test
    void shouldKeepCommittedSaveConsumed_whenNewViewsEvictTheActiveIdentity() {
        evictDuringSave = true;
        save(false, false);
        assertThat(rows()).isEqualTo(1);
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
    }

    @Test
    void shouldRejectReplay_whenCallbackThrowsAfterCommit() {
        assertThatThrownBy(() -> save(false, true)).isInstanceOf(IllegalStateException.class);
        assertThat(rows()).isEqualTo(1);
        assertThat(EFormSubmissionGuard.attempt(session, token, "1", "123").claim()).isNull();
    }
}
