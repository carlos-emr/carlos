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
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosInventoryTransactionsUnitTest extends CarlosUnitTestBase {
    private PropertyDao properties;
    private ProviderLabRoutingDao coordination;
    private PortalMyCarlosInventoryTestSupport.Fixture fixture;
    @BeforeEach void setup() {
        properties = mock(PropertyDao.class);
        coordination = mock(ProviderLabRoutingDao.class);
        fixture = PortalMyCarlosInventoryTestSupport.fixture(properties, coordination);
    }
    private static RuntimeException conflict(int code, String state) {
        return new IllegalStateException("FAKE database conflict", new SQLException("FAKE conflict", state, code));
    }
    @Test void shouldRollbackAndCleanBeforeRetryingTheWholeOperationWithAFreshEntityManager() {
        AtomicInteger attempts = new AtomicInteger();
        assertThat(fixture.transactions.execute(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (attempts.incrementAndGet() == 1) throw conflict(1020, "HY000");
            assertThat(fixture.manager.rollbacks).isEqualTo(1);
            return "FAKE result";
        })).isEqualTo("FAKE result");
        assertThat(attempts).hasValue(2);
        assertThat(fixture.manager.entityManagers).hasSize(2).doesNotHaveDuplicates();
        assertThat(fixture.manager.commits).isEqualTo(1);
        assertThat(TransactionSynchronizationManager.hasResource(fixture.factory)).isFalse();
        verify(coordination, times(2)).lockRoutingReport(PortalMyCarlosKeyInventoryService.COORDINATION_KEY);
    }
    @Test void shouldStopAfterThreeKnownRolledBackConflicts() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> fixture.transactions.execute(() -> {
            attempts.incrementAndGet(); throw conflict(1213, "40001");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(attempts).hasValue(3);
        assertThat(fixture.manager.rollbacks).isEqualTo(3);
        assertThat(fixture.manager.commits).isZero();
    }
    @Test void shouldNeverRetryBusinessRefusalsUnknownJdbcOrWrongVendorState() {
        for (RuntimeException error : new RuntimeException[]{new SecurityException("FAKE denied"),
                new IllegalStateException("FAKE stale CAS"), conflict(1062, "23000"), conflict(1020, "40001"),
                new SecurityException("FAKE denied", new SQLException("FAKE", "HY000", 1020)),
                new IllegalArgumentException("FAKE invalid", new SQLException("FAKE", "HY000", 1020))}) {
            int before = fixture.manager.begins;
            assertThatThrownBy(() -> fixture.transactions.execute(() -> { throw error; })).isSameAs(error);
            assertThat(fixture.manager.begins).isEqualTo(before + 1);
        }
    }
    @Test void shouldRefuseAnInheritedTransactionBeforeCoordinationOrCallerEffects() {
        AtomicInteger effects = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> fixture.transactions.execute(effects::incrementAndGet))
                    .isInstanceOf(IllegalStateException.class);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertThat(effects).hasValue(0);assertThat(fixture.manager.begins).isZero();
        verifyNoInteractions(properties, coordination);
    }
    @Test void shouldRefuseAnExternallyBoundPersistenceContextBeforeBeginning() {
        TransactionSynchronizationManager.bindResource(fixture.factory, new EntityManagerHolder(mock(EntityManager.class)));
        try {
            assertThatThrownBy(() -> fixture.transactions.execute(() -> "FAKE"))
                    .isInstanceOf(IllegalStateException.class);
        } finally { TransactionSynchronizationManager.unbindResource(fixture.factory); }
        assertThat(fixture.manager.begins).isZero();verifyNoInteractions(coordination);
    }
    @Test void shouldRejectAReusedEntityManagerInsteadOfClaimingAFreshReadView() {
        fixture.manager.reuseEntityManager = true;AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> fixture.transactions.execute(() -> {
            attempts.incrementAndGet();throw conflict(1020, "HY000");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("fresh transaction-owned");
        assertThat(attempts).hasValue(1);assertThat(fixture.manager.begins).isEqualTo(2);
    }
    @Test void shouldNeverReplayCommittedMutationWhenAfterCommitAuditThrowsASqlLookingException() {
        for (int code : new int[]{1020,1213,1205}) {
            fixture = PortalMyCarlosInventoryTestSupport.fixture(properties, coordination);
            AtomicInteger mutations = new AtomicInteger(), audits = new AtomicInteger();
            RuntimeException failure = conflict(code, code == 1213 ? "40001" : "HY000");
            assertThatThrownBy(() -> fixture.transactions.execute(() -> {
                mutations.incrementAndGet();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { audits.incrementAndGet();throw failure; }
                });
                return "FAKE committed";
            })).isSameAs(failure);
            assertThat(mutations).hasValue(1);assertThat(audits).hasValue(1);
            assertThat(fixture.manager.commits).isEqualTo(1);assertThat(fixture.manager.rollbacks).isZero();
            assertThat(fixture.manager.begins).isEqualTo(1);
        }
    }
    @Test void shouldNeverRetryUnknownCommitEvenWhenItsExceptionLooksLikeASnapshotConflict() {
        fixture.manager.onCommit = () -> { throw conflict(1020,"HY000"); };
        AtomicInteger mutations = new AtomicInteger();
        assertThatThrownBy(() -> fixture.transactions.execute(mutations::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);
        assertThat(mutations).hasValue(1);assertThat(fixture.manager.begins).isEqualTo(1);
        assertThat(fixture.manager.commits).isEqualTo(1);
    }
    @Test void shouldNotRepeatAPreparationWhichThrewBeforeSavingExactBytes() {
        AtomicInteger preparations = new AtomicInteger();
        assertThatThrownBy(() -> fixture.transactions.execute(() -> {
            preparations.incrementAndGet();PortalMyCarlosInventoryTransactions.preparationFailed();
            throw conflict(1020,"HY000");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(preparations).hasValue(1);assertThat(fixture.manager.begins).isEqualTo(1);
    }
}
