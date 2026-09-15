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

package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("integration")
@DisplayName("Archive read audit transaction")
class OutboundEmailArchiveReadAuditIntegrationTest extends CarlosTestBase {
    @Autowired
    private OutboundEmailArchiveReadAuditService auditService;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Test
    void shouldRetainAudit_whenCallerTransactionRollsBack() {
        LoggedInInfo caller = mock(LoggedInInfo.class);
        when(caller.getLoggedInProviderNo()).thenReturn("999998");
        when(caller.getIp()).thenReturn("127.0.0.1");
        String contentId = "archiveId=93484 documentNo=93485";
        auditService.record(caller, 93484, 93485, 123,
                OutboundEmailArchiveReadAuditService.Event.INTEGRITY_FAILURE);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        try {
            var entries = entityManager.createQuery(
                    "SELECT entry FROM OscarLog entry WHERE entry.contentId = :id", OscarLog.class)
                    .setParameter("id", contentId).getResultList();
            assertThat(entries).hasSize(1);
            assertThat(entries.get(0).getProviderNo()).isEqualTo("999998");
            assertThat(entries.get(0).getDemographicId()).isEqualTo(123);
            assertThat(entries.get(0).getAction()).endsWith(".integrityFailure");
            assertThat(entries.get(0).getIp()).isEqualTo("127.0.0.1");
        } finally {
            // The production log is append-only; remove only this committed test fixture.
            entityManager.createNativeQuery("DELETE FROM log WHERE contentId = ?1")
                    .setParameter(1, contentId).executeUpdate();
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }
}
