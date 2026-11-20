/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real database transactions serialize first saves, including rollback of the first writer. */
@Tag("integration")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderLinkingRulesConcurrencyIntegrationTest extends CarlosTestBase {
    private static final String KEY = "provider_linking_rules";
    @Autowired private PropertyDao properties;
    @Autowired private ProviderLabRoutingDao coordination;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    private TransactionTemplate tx;
    private ProviderLinkingRulesService service;
    private LoggedInInfo admin;
    private CountDownLatch writersAtLock;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactions);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        tx.executeWithoutResult(status -> {
            // V1.0.21's coordination table has no entity for Hibernate's test DDL.
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)")
                    .executeUpdate();
            assertThat(properties.findByName(KEY)).isEmpty();
        });
        var security = mock(SecurityInfoManager.class);
        admin = mock(LoggedInInfo.class);
        when(security.hasPrivilege(admin, "_admin", "w", null)).thenReturn(true);
        // Checkpoint at the lock boundary: count each writer as it reaches lockRoutingReport,
        // then delegate to the real database lock.
        writersAtLock = new CountDownLatch(2);
        var checkpoint = mock(ProviderLabRoutingDao.class);
        doAnswer(call -> {
            writersAtLock.countDown();
            coordination.lockRoutingReport(call.getArgument(0));
            return null;
        }).when(checkpoint).lockRoutingReport(anyInt());
        service = new ProviderLinkingRulesService(properties, security, checkpoint);
    }

    @AfterEach
    void tearDown() {
        tx.executeWithoutResult(status -> {
            properties.removeByName(KEY);
            em.createNativeQuery("delete from providerLabRoutingLock where lab_no=?1")
                    .setParameter(1, ProviderLabRoutingDao.PROVIDER_LINKING_RULES_LOCK).executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldStoreOnlyLastSavedGlobalValue_whenFirstSavesOverlap(boolean rollBackFirst) throws Exception {
        var firstSaved = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                try (var audit = mockStatic(LogAction.class)) {
                    tx.executeWithoutResult(status -> {
                        assertThat(service.setEnabled(admin, true)).isTrue();
                        em.flush();
                        firstSaved.countDown();
                        await(releaseFirst);
                        if (rollBackFirst) status.setRollbackOnly();
                    });
                }
            });
            try {
                assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> {
                    try (var audit = mockStatic(LogAction.class)) {
                        return tx.execute(status -> service.setEnabled(admin, false));
                    }
                });
                // The second writer has reached the lock, not merely started its transaction.
                assertThat(writersAtLock.await(10, TimeUnit.SECONDS)).isTrue();
                // Writer two cannot return success before writer one's transaction finishes.
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);
                assertThat(second.get(10, TimeUnit.SECONDS)).isFalse();
                tx.executeWithoutResult(status -> {
                    assertThat(properties.findByName(KEY)).singleElement()
                            .satisfies(row -> {
                                assertThat(row.getProviderNo()).isNull();
                                assertThat(row.getValue()).isEqualTo("false");
                            });
                    assertThat(service.isEnabled()).isFalse();
                });
            } finally {
                releaseFirst.countDown();
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Writer release timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Writer interrupted", e);
        }
    }
}
