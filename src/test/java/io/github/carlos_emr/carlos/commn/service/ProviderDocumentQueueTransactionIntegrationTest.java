/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.QueueDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.daos.security.SecObjectNameDao;
import io.github.carlos_emr.carlos.model.security.Secobjectname;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Actual DAO commits/rollbacks through the production service's transaction annotation.
 * The surrounding test transaction is suspended so it cannot hide an orphaned queue.
 *
 * @since 2026-11-16
 */
@Tag("integration")
@Tag("dao")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ProviderDocumentQueueTransactionIntegrationTest extends CarlosTestBase {
    @Autowired private QueueDao queues;
    @Autowired private SecObjectNameDao security;
    @Autowired private UserPropertyDAO properties;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private javax.sql.DataSource dataSource;
    @PersistenceContext private EntityManager database;
    private TransactionTemplate transactions;
    private String owner;
    private String name;

    @BeforeEach
    void prepareOwnedKeys() {
        transactions = new TransactionTemplate(transactionManager);
        String token = UUID.randomUUID().toString().replace("-", "");
        owner = "Q" + token.substring(0, 5);
        name = "FAKE-Q" + token.substring(0, 20);
        transactions.executeWithoutResult(status -> database.persist(
                new Provider(owner, "Synthetic", "doctor", "F", "", "Queue")));
    }

    private ProviderDocumentQueueService service(SecObjectNameDao securityDao, UserPropertyDAO preferenceDao) {
        ProxyFactory proxy = new ProxyFactory(new ProviderDocumentQueueService(queues, securityDao, preferenceDao));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (ProviderDocumentQueueService) proxy.getProxy();
    }

    private long count(String sql) {
        return ((Number) database.createNativeQuery(sql).setParameter("name", name).getSingleResult()).longValue();
    }

    @AfterEach
    void removeOnlyOwnedRows() {
        transactions.executeWithoutResult(status -> {
            for (String queueName : java.util.List.of(name, name + "-existing", name + "-second")) {
                for (Object id : database.createNativeQuery("SELECT id FROM queue WHERE name=:name")
                        .setParameter("name", queueName).getResultList()) {
                    String object = "_queue." + id;
                    database.createNativeQuery("DELETE FROM secObjPrivilege WHERE objectName=:object")
                            .setParameter("object", object).executeUpdate();
                    database.createNativeQuery("DELETE FROM secObjectName WHERE objectName=:object")
                            .setParameter("object", object).executeUpdate();
                }
                database.createNativeQuery("DELETE FROM secObjectName WHERE description=:name").setParameter("name", queueName).executeUpdate();
                database.createNativeQuery("DELETE FROM queue WHERE name=:name").setParameter("name", queueName).executeUpdate();
            }
            database.createNativeQuery("DELETE FROM property WHERE provider_no=:owner AND name=:property")
                    .setParameter("owner", owner).setParameter("property", UserProperty.DOC_DEFAULT_QUEUE).executeUpdate();
            database.createNativeQuery("DELETE FROM provider WHERE provider_no=:owner").setParameter("owner", owner).executeUpdate();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCommitTheQueueSecurityAndDefaultTogether(boolean previous) {
        if (previous) transactions.executeWithoutResult(status -> properties.saveProp(owner, UserProperty.DOC_DEFAULT_QUEUE, "7"));
        UserProperty result = service(security, properties).createDefaultQueue(owner, name);
        int id = Integer.parseInt(result.getValue());
        transactions.executeWithoutResult(status -> {
            assertThat(queues.getQueueName(id)).isEqualTo(name);
            Secobjectname object = database.find(Secobjectname.class, "_queue." + id);
            assertThat(object).isNotNull();
            assertThat(object.getDescription()).isEqualTo(name);
            assertThat(object.getOrgapplicable()).isZero();
            assertThat(properties.getProp(owner, UserProperty.DOC_DEFAULT_QUEUE).getValue()).isEqualTo(result.getValue());
            assertThat(((Number) database.createNativeQuery("SELECT COUNT(*) FROM secObjPrivilege WHERE objectName=:object")
                    .setParameter("object", object.getObjectname()).getSingleResult()).longValue()).isZero();
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldKeepOneDefault_whenFirstTimeSavesOverlapForTheSameProvider(boolean firstSelectsExisting) throws Exception {
        String existingQueueId = firstSelectsExisting
                ? transactions.execute(status -> queues.addNewQueueAndGetId(name + "-existing")).toString() : null;
        boolean maria;
        try (var connection = dataSource.getConnection()) {
            maria = connection.getMetaData().getDatabaseProductName().contains("MariaDB");
        }
        var firstRead = new java.util.concurrent.CountDownLatch(1);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);
        var followerEntered = new java.util.concurrent.CountDownLatch(1);
        var followerRead = new java.util.concurrent.CountDownLatch(1);
        var followerConnection = new java.util.concurrent.atomic.AtomicLong();
        UserPropertyDAO firstPreferences = mock(UserPropertyDAO.class, org.mockito.AdditionalAnswers.delegatesTo(properties));
        doAnswer(call -> {
            UserProperty result = properties.getProp(owner, UserProperty.DOC_DEFAULT_QUEUE);
            firstRead.countDown();
            assertThat(releaseFirst.await(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return result;
        }).when(firstPreferences).getProp(owner, UserProperty.DOC_DEFAULT_QUEUE);
        UserPropertyDAO followerPreferences = mock(UserPropertyDAO.class, org.mockito.AdditionalAnswers.delegatesTo(properties));
        doAnswer(call -> {
            UserProperty result = properties.getProp(owner, UserProperty.DOC_DEFAULT_QUEUE);
            followerRead.countDown();
            return result;
        }).when(followerPreferences).getProp(owner, UserProperty.DOC_DEFAULT_QUEUE);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                ProviderDocumentQueueService firstService = service(security, firstPreferences);
                return firstSelectsExisting ? firstService.selectDefaultQueue(owner, existingQueueId)
                        : firstService.createDefaultQueue(owner, name);
            });
            assertThat(firstRead.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var follower = pool.submit(() -> transactions.execute(status -> {
                followerConnection.set(database.unwrap(org.hibernate.Session.class).doReturningWork(connection -> {
                    try (var statement = connection.createStatement();
                         var rows = statement.executeQuery(maria ? "SELECT CONNECTION_ID()" : "SELECT SESSION_ID()")) {
                        rows.next(); return rows.getLong(1);
                    }
                }));
                followerEntered.countDown();
                return service(security, followerPreferences).createDefaultQueue(owner, name + "-second");
            }));
            assertThat(followerEntered.await(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
            while (followerRead.getCount() != 0) {
                String query = maria
                        ? "SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w JOIN information_schema.INNODB_TRX t ON t.trx_id=w.requesting_trx_id WHERE t.trx_mysql_thread_id=?"
                        : "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID=? AND BLOCKER_ID IS NOT NULL";
                if (jdbc.queryForObject(query, Long.class, followerConnection.get()) > 0) break;
                assertThat(System.nanoTime()).as("Follower neither read the preference nor waited for its owner lock")
                        .isLessThan(deadline);
                Thread.sleep(20);
            }
            // Before repair the follower commits a second first-time preference here. With the
            // owner lock it cannot reach that read until the first transaction commits.
            if (followerRead.getCount() == 0) follower.get(15, java.util.concurrent.TimeUnit.SECONDS);
            releaseFirst.countDown();
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            UserProperty latest = follower.get(15, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM property WHERE provider_no=? AND name=?",
                    Long.class, owner, UserProperty.DOC_DEFAULT_QUEUE)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT value FROM property WHERE provider_no=? AND name=?",
                    String.class, owner, UserProperty.DOC_DEFAULT_QUEUE)).isEqualTo(latest.getValue());
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldRefuseMissingPreferenceOwners_withoutCreatingQueueRows() {
        for (String missing : new String[]{null, owner + "-missing"}) {
            assertThatThrownBy(() -> service(security, properties).createDefaultQueue(missing, name))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Unknown document queue preference owner");
        }
        transactions.executeWithoutResult(status -> {
            assertThat(count("SELECT COUNT(*) FROM queue WHERE name=:name")).isZero();
            assertThat(count("SELECT COUNT(*) FROM secObjectName WHERE description=:name")).isZero();
            assertThat(properties.getProp(owner, UserProperty.DOC_DEFAULT_QUEUE)).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"security", "preference"})
    void shouldRollbackEveryNewRow_andRetainThePreviousDefaultWhenALaterWriteFails(String stage) {
        transactions.executeWithoutResult(status -> properties.saveProp(owner, UserProperty.DOC_DEFAULT_QUEUE, "7"));
        SecObjectNameDao securityDao = security;
        UserPropertyDAO preferenceDao = properties;
        if (stage.equals("security")) {
            securityDao = object -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                throw new IllegalStateException("FAKE security refusal");
            };
        } else {
            preferenceDao = mock(UserPropertyDAO.class, org.mockito.AdditionalAnswers.delegatesTo(properties));
            doAnswer(call -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                properties.saveProp((UserProperty) call.getArgument(0));
                throw new IllegalStateException("FAKE preference refusal");
            }).when(preferenceDao).saveProp(any(UserProperty.class));
        }
        ProviderDocumentQueueService failing = service(securityDao, preferenceDao);
        assertThatThrownBy(() -> failing.createDefaultQueue(owner, name)).isInstanceOf(IllegalStateException.class);
        transactions.executeWithoutResult(status -> {
            assertThat(count("SELECT COUNT(*) FROM queue WHERE name=:name")).isZero();
            assertThat(count("SELECT COUNT(*) FROM secObjectName WHERE description=:name")).isZero();
            assertThat(properties.getProp(owner, UserProperty.DOC_DEFAULT_QUEUE).getValue()).isEqualTo("7");
        });
    }
}
