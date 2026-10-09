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
package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.commn.model.SystemPreferences;
import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsConfigDaoImpl;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDaoImpl;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRetirementDao;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDaoImpl;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.model.SmsConfig;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect;
import jakarta.persistence.EntityManager;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Production DAOs and Spring propagation, on an owned schema with the shipped InnoDB table definitions. */
@Tag("integration")
@Tag("service")
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "SMS_TEST_DB_URL", matches = ".+")
class SmsProviderRetirementMariaDbIntegrationTest {
    private static final Path COMMON = Path.of("database/mysql/migration/common");
    private static final SmsConsentDecisionDto CONSENT = SmsConsentDecisionDto.permitted(
            SmsConsentStatus.OPT_IN, 4321, Instant.parse("2026-10-01T12:00:00Z"));
    private final String schema = "sms_retirement_" + UUID.randomUUID().toString().replace("-", "");
    private final AtomicBoolean auditFailure = new AtomicBoolean();
    private final AtomicInteger sends = new AtomicInteger();
    private final AtomicInteger lookups = new AtomicInteger();
    private final List<Object> committedEvents = new CopyOnWriteArrayList<>();
    private Connection admin;
    private SessionFactory factory;
    private EntityManager em;
    private JpaTransactionManager manager;
    private SmsConfigService config;
    private SmsTransactionService recorder;
    private SmsProviderRetirementService retirement;
    private SmsProviderClientResolver clients;
    private ApplicationEventPublisher publisher;
    private SmsProviderRateLimitDaoImpl rateDao;
    private String schemaUrl;
    private String user;
    private String password;
    private boolean snapshot = true;

    @BeforeAll
    void createOwnedSchema() throws Exception {
        String url = System.getenv("SMS_TEST_DB_URL");
        assertThat(url).matches("jdbc:mysql://[^/]+/");
        user = System.getenv("SMS_TEST_DB_USER");
        password = System.getenv("SMS_TEST_DB_PASSWORD");
        admin = DriverManager.getConnection(url, user, password);
        try (var s = admin.createStatement(); var r = s.executeQuery("SELECT VERSION()")) {
            assertThat(r.next()).isTrue();
            assertThat(r.getString(1)).containsIgnoringCase("MariaDB");
        }
        try (var s = admin.createStatement()) {
            s.execute("CREATE DATABASE `" + schema + "`");
        }
        schemaUrl = url + schema;
        try (Connection c = DriverManager.getConnection(schemaUrl, user, password)) {
            install(c, migration("add_sms_system_of_record"), "CREATE TABLE sms_transaction (");
            install(c, migration("add_sms_system_of_record"), "CREATE TABLE sms_provider_rate_limit (");
            install(c, migration("add_sms_consent"), "ALTER TABLE sms_transaction");
            install(c, migration("add_sms_config"), "CREATE TABLE IF NOT EXISTS sms_config (");
            install(c, COMMON.resolve("V1__baseline_schema.sql"), "CREATE TABLE `SystemPreferences` (");
        }
        try (var s = admin.createStatement(); var r = s.executeQuery(
                "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "'")) {
            int count = 0;
            while (r.next()) {
                assertThat(r.getString(1)).isEqualToIgnoringCase("InnoDB");
                count++;
            }
            assertThat(count).isEqualTo(4);
        }
        factory = new Configuration().addAnnotatedClass(SmsTransaction.class).addAnnotatedClass(SmsConfig.class)
                .addAnnotatedClass(SmsProviderRateLimit.class).addAnnotatedClass(SystemPreferences.class)
                .setProperty("hibernate.connection.url", schemaUrl)
                .setProperty("hibernate.connection.username", user)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.connection.pool_size", "12")
                .setProperty("hibernate.connection.isolation", Integer.toString(Connection.TRANSACTION_REPEATABLE_READ))
                .setProperty("hibernate.dialect", OscarMySQL5Dialect.class.getName())
                .setProperty("hibernate.hbm2ddl.auto", "none").setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        manager = new JpaTransactionManager(factory);
        SmsConfigDaoImpl configDao = wired(new SmsConfigDaoImpl());
        rateDao = wired(new SmsProviderRateLimitDaoImpl());
        SmsProviderRetirementDao retirementDao = transactional(wired(new SmsProviderRetirementDao()));
        retirement = transactional(new SmsProviderRetirementService(rateDao, configDao, retirementDao));
        publisher = event -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedEvents.add(event);
                }
            });
        };
        SmsConfigAuditRecorder audit = mock(SmsConfigAuditRecorder.class);
        doAnswer(call -> {
            if (auditFailure.get()) {
                throw new IllegalStateException("FAKE audit failure");
            }
            return null;
        }).when(audit).recordSaved(any(), any(), any());
        clients = new SmsProviderClientResolver(List.of(provider(SmsProviderType.STUB), provider(SmsProviderType.CLOUDLI)));
        config = transactional(new SmsConfigService(configDao, clients, publisher, audit, retirement));
        recorder = transactional(new JpaSmsTransactionService(wired(new SmsTransactionDaoImpl()), publisher,
                manager, retirement));
    }

    @AfterAll
    void dropOnlyOwnedSchema() throws Exception {
        try {
            if (factory != null) factory.close();
        } finally {
            if (admin != null) {
                try (Connection c = admin; var s = c.createStatement()) {
                    s.execute("DROP DATABASE IF EXISTS `" + schema + "`");
                }
            }
        }
    }

    @BeforeEach
    void resetOwnedFixtures() throws Exception {
        snapshot = true;
        auditFailure.set(false);
        sends.set(0);
        lookups.set(0);
        committedEvents.clear();
        try (var s = admin.createStatement()) {
            for (String table : List.of("sms_transaction", "sms_config", "SystemPreferences", "sms_provider_rate_limit")) {
                s.executeUpdate("DELETE FROM `" + schema + "`." + table);
            }
        }
    }

    @Test
    void shouldKeepOldBacklogRetired_whenSwitchedAwayAndBackBeforeWorker() throws Exception {
        save(SmsProviderType.STUB, true);
        List<SmsTransaction> old = new ArrayList<>();
        for (int i = 0; i < 5; i++) old.add(admit(SmsProviderType.STUB));
        try (var s = admin.createStatement()) {
            s.executeUpdate("UPDATE `" + schema + "`.sms_transaction SET next_attempt_at='2099-01-01 00:00:00'");
        }
        save(SmsProviderType.STUB, false);
        save(SmsProviderType.CLOUDLI, false);
        save(SmsProviderType.STUB, false);
        assertThat(worker().processDueMessages(2)).isZero();
        save(SmsProviderType.STUB, true);
        SmsTransaction fresh = admit(SmsProviderType.STUB);
        SmsQueueProcessingService worker = worker();
        worker.processDueMessages(2);
        assertThat(stored(fresh).getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(old.stream().map(this::stored).filter(t -> t.getStatus() == SmsStatus.FAILED)).hasSize(2);
        worker.processDueMessages(2);
        worker.processDueMessages(2);
        for (SmsTransaction row : old) {
            assertThat(stored(row)).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                    .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
        }
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    void shouldRetireUnsentClaimsAndRetries_butKeepStartedProviderResults() {
        save(SmsProviderType.STUB, true);
        admit(SmsProviderType.STUB);
        admit(SmsProviderType.STUB);
        admit(SmsProviderType.STUB);
        List<SmsTransaction> held = recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 3);
        assertThat(held).hasSize(3);
        save(SmsProviderType.CLOUDLI, true);
        save(SmsProviderType.STUB, true);
        assertThatThrownBy(() -> recorder.renewClaim(held.get(0), new Date()))
                .isInstanceOf(SmsTransactionClaimConflictException.class);
        assertThat(recorder.releaseClaim(held.get(0), new Date()).getStatus()).isEqualTo(SmsStatus.FAILED);
        assertThat(recorder.markRetryScheduled(held.get(1),
                SmsProviderSendResultDto.failed("FAKE_TEMPORARY", "FAKE retry"), new Date()).getStatus())
                .isEqualTo(SmsStatus.FAILED);
        assertThat(config.statusLookupSettings(held.get(2))).isEmpty();
        assertThat(recorder.markProviderResult(held.get(2),
                SmsProviderSendResultDto.accepted("FAKE_ACCEPTED", SmsStatus.SENT)).getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 10)).isEmpty();
    }

    @Test
    void shouldRetireOtherLegacyProviders_whenRegistryIsInitializedForExistingConfig() throws Exception {
        save(SmsProviderType.STUB, true);
        SmsTransaction selected = admit(SmsProviderType.STUB);
        SmsTransaction former = new TransactionTemplate(manager).execute(tx -> {
            SmsTransaction row = SmsTransaction.outboundAttempt(
                    SmsSendCommand.patientMessage(123, "+16135550100", "FAKE legacy backlog", "999998"),
                    SmsProviderType.CLOUDLI);
            row.recordConsentDecision(CONSENT);
            em.persist(row); // a fixture admitted by the previous binary, before retirement tracking existed
            return row;
        });
        try (var statement = admin.createStatement()) {
            statement.executeUpdate("DELETE FROM `" + schema + "`.SystemPreferences");
        }
        save(SmsProviderType.STUB, true);
        List<SmsTransaction> eligible = recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1);
        assertThat(eligible).extracting(SmsTransaction::getId).containsExactly(selected.getId());
        recorder.releaseClaim(eligible.get(0), new Date());
        save(SmsProviderType.CLOUDLI, true);
        SmsTransaction fresh = admit(SmsProviderType.CLOUDLI);
        worker().processDueMessages(1);
        assertThat(stored(former)).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, "QUEUE_PROVIDER_NOT_ACTIVE");
        assertThat(stored(fresh).getStatus()).isEqualTo(SmsStatus.SENT);
        assertThat(sends.get()).isEqualTo(1);
    }

    @Test
    void shouldRequireManualReconciliation_withoutLookingUpRetiredUncertainSend() throws Exception {
        save(SmsProviderType.STUB, true);
        SmsTransaction old = admit(SmsProviderType.STUB);
        recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1);
        try (var statement = admin.createStatement()) {
            statement.executeUpdate("UPDATE `" + schema + "`.sms_transaction SET last_attempt_at='2020-01-01' "
                    + "WHERE id=" + old.getId());
        }
        save(SmsProviderType.CLOUDLI, true);
        save(SmsProviderType.STUB, true);
        worker().processDueMessages(1);
        assertThat(stored(old)).extracting(SmsTransaction::getStatus, SmsTransaction::getErrorCode)
                .containsExactly(SmsStatus.FAILED, "QUEUE_STALE_STATUS_LOOKUP_UNAVAILABLE");
        assertThat(lookups.get()).isZero();
        assertThat(sends.get()).isZero();
        worker().processDueMessages(1);
        assertThat(sends.get()).isZero();
    }

    @Test
    void shouldStopCleanupAfterTwoFailedWrites_withoutPublishingRolledBackEvents() {
        save(SmsProviderType.STUB, true);
        List<SmsTransaction> rows = List.of(admit(SmsProviderType.STUB), admit(SmsProviderType.STUB),
                admit(SmsProviderType.STUB));
        save(SmsProviderType.CLOUDLI, true);
        AtomicInteger attemptedWrites = new AtomicInteger();
        SmsTransactionDaoImpl dao = wired(new SmsTransactionDaoImpl() {
            @Override
            public void flush() {
                attemptedWrites.incrementAndGet();
                throw new IllegalStateException("FAKE persistent row write failure");
            }
        });
        SmsTransactionService cleanup = transactional(new JpaSmsTransactionService(dao, publisher, manager, retirement));
        committedEvents.clear();
        assertThat(cleanup.failRetiredOutboundQueue(SmsProviderType.STUB, 10)).isZero();
        assertThat(attemptedWrites.get()).isEqualTo(2);
        assertThat(rows.stream().map(this::stored)).extracting(SmsTransaction::getStatus)
                .containsOnly(SmsStatus.QUEUED);
        assertThat(committedEvents).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldBlockFirstSaveBeforeReadingCutoff_whenAdmissionIsUncommitted(boolean enabled) throws Exception {
        snapshot = enabled;
        CountDownLatch inserted = new CountDownLatch(1);
        AtomicLong blocker = new AtomicLong();
        CountDownLatch commit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<SmsTransaction> admission = pool.submit(() -> new TransactionTemplate(manager).execute(tx -> {
                sessionMode();
                SmsTransaction row = admit(SmsProviderType.STUB);
                blocker.set(((Number) em.createNativeQuery("SELECT CONNECTION_ID()").getSingleResult()).longValue());
                inserted.countDown();
                await(commit);
                return row;
            }));
            assertThat(inserted.await(20, TimeUnit.SECONDS)).isTrue();
            Future<SmsConfig> firstSave = pool.submit(() -> save(SmsProviderType.CLOUDLI, true));
            try {
                awaitSelectionLockWait(firstSave, blocker.get());
                assertThat(firstSave.isDone()).isFalse();
            } finally {
                commit.countDown();
            }
            SmsTransaction row = admission.get(20, TimeUnit.SECONDS);
            firstSave.get(20, TimeUnit.SECONDS);
            save(SmsProviderType.STUB, true);
            assertThat(recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1)).isEmpty();
            assertThat(recorder.failRetiredOutboundQueue(SmsProviderType.STUB, 1)).isEqualTo(1);
            assertThat(stored(row).getStatus()).isEqualTo(SmsStatus.FAILED);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUseCurrentHighestId_whenCallerHasEarlierRepeatableReadSnapshot(boolean enabled) throws Exception {
        snapshot = enabled;
        save(SmsProviderType.STUB, true);
        AtomicReferenceBox<SmsTransaction> admitted = new AtomicReferenceBox<>();
        AtomicReferenceBox<List<String>> committedState = new AtomicReferenceBox<>();
        AtomicInteger committedEventCount = new AtomicInteger();
        try {
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                sessionMode();
                config.current(); // managed config entity and a pre-existing consistent-read snapshot
                em.createNativeQuery("SELECT MAX(id) FROM sms_transaction").getSingleResult();
                try (var pool = Executors.newSingleThreadExecutor()) {
                    admitted.value = pool.submit(() -> admit(SmsProviderType.STUB)).get(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                committedState.value = durableState();
                committedEventCount.set(committedEvents.size());
                em.persist(new SystemPreferences("sms.fake.caller.work", "FAKE ambient write"));
                save(SmsProviderType.CLOUDLI, true);
            });
        } catch (RuntimeException e) {
            if (!enabled || !hasSnapshotConflict(e)) throw new AssertionError("Unexpected retirement rejection", e);
            assertThat(durableState()).isEqualTo(committedState.value);
            assertThat(committedEvents).hasSize(committedEventCount.get());
            save(SmsProviderType.CLOUDLI, true); // restart the whole failed transaction with fresh state
        }
        save(SmsProviderType.STUB, true);
        assertThat(recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1)).isEmpty();
        assertThat(recorder.failRetiredOutboundQueue(SmsProviderType.STUB, 1)).isEqualTo(1);
        assertThat(stored(admitted.value).getStatus()).isEqualTo(SmsStatus.FAILED);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRefreshCachedSelectionAndCutoffs_orAbortWholeStaleTransaction(boolean enabled) throws Exception {
        snapshot = enabled;
        save(SmsProviderType.STUB, true);
        AtomicReferenceBox<SmsTransaction> old = new AtomicReferenceBox<>();
        AtomicReferenceBox<SmsConfig> changed = new AtomicReferenceBox<>();
        AtomicReferenceBox<List<String>> committedState = new AtomicReferenceBox<>();
        AtomicInteger committedEventCount = new AtomicInteger();
        try {
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                sessionMode();
                config.current();
                em.createQuery("SELECT p FROM SystemPreferences p", SystemPreferences.class).getResultList();
                try (var pool = Executors.newSingleThreadExecutor()) {
                    changed.value = pool.submit(() -> {
                        old.value = admit(SmsProviderType.STUB);
                        return save(SmsProviderType.CLOUDLI, true);
                    }).get(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                committedState.value = durableState();
                committedEventCount.set(committedEvents.size());
                em.persist(new SystemPreferences("sms.fake.caller.work", "FAKE ambient write"));
                config.save(update(SmsProviderType.STUB, true, changed.value.getVersion()), "999998");
            });
        } catch (RuntimeException e) {
            if (!enabled || !hasSnapshotConflict(e)) throw new AssertionError("Unexpected rejection while refreshing cached state", e);
            assertThat(durableState()).isEqualTo(committedState.value);
            assertThat(committedEvents).hasSize(committedEventCount.get());
            save(SmsProviderType.STUB, true); // fresh transaction, not an in-transaction retry
        }
        assertThat(recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1)).isEmpty();
        recorder.failRetiredOutboundQueue(SmsProviderType.STUB, 1);
        assertThat(stored(old.value).getStatus()).isEqualTo(SmsStatus.FAILED);
    }

    @Test
    void shouldRollbackCutoffAndConfigTogether_whenAuditFails() {
        save(SmsProviderType.STUB, true);
        SmsTransaction queued = admit(SmsProviderType.STUB);
        committedEvents.clear();
        List<String> before = durableState();
        auditFailure.set(true);
        assertThatThrownBy(() -> save(SmsProviderType.CLOUDLI, true)).hasMessage("FAKE audit failure");
        auditFailure.set(false);
        assertThat(durableState()).isEqualTo(before);
        assertThat(committedEvents).isEmpty();
        assertThat(recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1))
                .extracting(SmsTransaction::getId).containsExactly(queued.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "invalid", "9223372036854775808", ""})
    void shouldRefuseAdmissionAndDispatch_whenRetirementValueIsMalformed(String value) throws Exception {
        save(SmsProviderType.STUB, true);
        SmsTransaction queued = admit(SmsProviderType.STUB);
        try (var s = admin.prepareStatement("UPDATE `" + schema + "`.SystemPreferences SET `value`=? WHERE name=?")) {
            s.setString(1, value);
            s.setString(2, "sms.provider.retiredThrough.STUB");
            assertThat(s.executeUpdate()).isEqualTo(1);
        }
        assertThatThrownBy(() -> admit(SmsProviderType.STUB)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.dispatchSettings(queued)).isInstanceOf(IllegalStateException.class);
        assertThat(stored(queued).getStatus()).isEqualTo(SmsStatus.QUEUED);
    }

    @Test
    void shouldRefusePartialOrDuplicateRegistry() throws Exception {
        save(SmsProviderType.STUB, true);
        try (var s = admin.createStatement()) {
            s.executeUpdate("INSERT INTO `" + schema + "`.SystemPreferences (name,`value`) "
                    + "VALUES ('sms.provider.retiredThrough.STUB','0')");
        }
        assertThatThrownBy(() -> admit(SmsProviderType.STUB)).isInstanceOf(IllegalStateException.class);
        try (var s = admin.createStatement()) {
            s.executeUpdate("DELETE FROM `" + schema + "`.SystemPreferences WHERE name='sms.provider.retiredThrough.STUB'");
        }
        assertThatThrownBy(() -> admit(SmsProviderType.STUB)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldLeaveLimiterCounterAndWindowAlone_whenSerializingSelection() throws Exception {
        SmsSendRateLimitService limiter = transactional(new JpaSmsSendRateLimitService(rateDao, 5,
                Duration.ofHours(1), Clock.systemUTC()));
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        String before = limiterState();
        save(SmsProviderType.STUB, true);
        SmsTransaction row = admit(SmsProviderType.STUB);
        SmsTransaction claimed = recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1).get(0);
        recorder.releaseClaim(claimed, new Date());
        config.dispatchSettings(row);
        assertThat(limiterState()).isEqualTo(before);
    }

    @Test
    void shouldApplyDistinctProviderCaps_underConcurrentRequests() throws Exception {
        SmsSendRateLimitService limiter = transactional(new JpaSmsSendRateLimitService(rateDao,
                type -> new SmsSendRateLimit(type == SmsProviderType.STUB ? 2 : 4, Duration.ofHours(1)),
                Clock.systemUTC()));
        try (var pool = Executors.newFixedThreadPool(12)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> stub = new ArrayList<>();
            List<Future<Boolean>> cloud = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                stub.add(pool.submit(() -> { await(start); return limiter.tryAcquire(SmsProviderType.STUB); }));
                cloud.add(pool.submit(() -> { await(start); return limiter.tryAcquire(SmsProviderType.CLOUDLI); }));
            }
            start.countDown();
            assertThat(permitted(stub)).isEqualTo(2);
            assertThat(permitted(cloud)).isEqualTo(4);
        }
    }

    @Test
    void shouldSerializeCleanupWithActivation_andKeepNewlyAdmittedRows() throws Exception {
        save(SmsProviderType.STUB, true);
        SmsTransaction old = admit(SmsProviderType.STUB);
        save(SmsProviderType.CLOUDLI, true);
        CountDownLatch cleanupLocked = new CountDownLatch(1);
        AtomicLong blocker = new AtomicLong();
        CountDownLatch finishCleanup = new CountDownLatch(1);
        SmsTransactionDaoImpl dao = wired(new SmsTransactionDaoImpl() {
            @Override
            public List<SmsTransaction> findRetiredQueuedForUpdate(SmsProviderType type, long cutoff,
                    boolean inactive, Date now, List<Long> excluded) {
                List<SmsTransaction> rows = super.findRetiredQueuedForUpdate(type, cutoff, inactive, now, excluded);
                blocker.set(((Number) em.createNativeQuery("SELECT CONNECTION_ID()").getSingleResult()).longValue());
                cleanupLocked.countDown();
                await(finishCleanup);
                return rows;
            }
        });
        SmsTransactionService cleanup = transactional(new JpaSmsTransactionService(dao, publisher, manager, retirement));
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> failing = pool.submit(() -> cleanup.failRetiredOutboundQueue(SmsProviderType.STUB, 1));
            assertThat(cleanupLocked.await(20, TimeUnit.SECONDS)).isTrue();
            Future<SmsTransaction> activation = pool.submit(() -> {
                save(SmsProviderType.STUB, true);
                return admit(SmsProviderType.STUB);
            });
            try {
                awaitSelectionLockWait(activation, blocker.get());
                assertThat(activation.isDone()).isFalse();
            } finally {
                finishCleanup.countDown();
            }
            assertThat(failing.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            SmsTransaction fresh = activation.get(20, TimeUnit.SECONDS);
            assertThat(stored(old).getStatus()).isEqualTo(SmsStatus.FAILED);
            worker().processDueMessages(2);
            assertThat(stored(fresh).getStatus()).isEqualTo(SmsStatus.SENT);
        }
    }

    @Test
    void shouldSkipOneFailedCleanupWrite_andCommitNextRowAndItsEvent() {
        save(SmsProviderType.STUB, true);
        SmsTransaction faulty = admit(SmsProviderType.STUB);
        SmsTransaction healthy = admit(SmsProviderType.STUB);
        save(SmsProviderType.CLOUDLI, true);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        SmsTransactionDaoImpl dao = wired(new SmsTransactionDaoImpl() {
            @Override
            public void flush() {
                if (failOnce.getAndSet(false)) throw new IllegalStateException("FAKE row write failure");
                super.flush();
            }
        });
        SmsTransactionService cleanup = transactional(new JpaSmsTransactionService(dao, publisher, manager, retirement));
        committedEvents.clear();
        assertThat(cleanup.failRetiredOutboundQueue(SmsProviderType.STUB, 10)).isEqualTo(1);
        assertThat(stored(faulty).getStatus()).isEqualTo(SmsStatus.QUEUED);
        assertThat(stored(healthy).getStatus()).isEqualTo(SmsStatus.FAILED);
        assertThat(committedEvents).hasSize(1);
    }

    @Test
    void shouldReturnUnsentClaim_whenSendingIsTurnedOffDuringPermit() {
        save(SmsProviderType.STUB, true);
        SmsSendService direct = new SmsSendService(new io.github.carlos_emr.carlos.sms.validator.SmsSendValidator(),
                command -> CONSENT, clients, recorder, type -> {
                    save(SmsProviderType.STUB, false);
                    return true;
                }, new SmsDefaultProviderResolver(config), config);
        assertThat(direct.send(SmsSendCommand.patientMessage(123, "+16135550100", "FAKE direct", "999998"))
                .status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(sends.get()).isZero();
        assertThat(recorder.claimDueOutboundQueue(SmsProviderType.STUB, new Date(), 1)).isEmpty();
    }

    @Test
    void shouldAllowInitialAdminStubTest_whenOtherProviderIsSelectedAndSendingOff() {
        save(SmsProviderType.CLOUDLI, false);
        SmsSendService direct = new SmsSendService(new io.github.carlos_emr.carlos.sms.validator.SmsSendValidator(),
                command -> CONSENT, clients, recorder, type -> true, new SmsDefaultProviderResolver(config), config);
        assertThat(direct.sendSystemTest("+16135550100", "999998", 1).status()).isEqualTo(SmsStatus.SENT);
        assertThat(sends.get()).isEqualTo(1);
    }

    private SmsConfig save(SmsProviderType type, boolean enabled) {
        Integer version = config.current().map(SmsConfig::getVersion).orElse(null);
        return config.save(update(type, enabled, version), "999998");
    }

    private static SmsConfigUpdateDto update(SmsProviderType type, boolean enabled, Integer version) {
        return new SmsConfigUpdateDto(type, enabled, false, "", "", false, Map.of(), version);
    }

    private SmsTransaction admit(SmsProviderType type) {
        return recorder.recordOutboundAttempt(SmsSendCommand.patientMessage(123, "+16135550100", "FAKE SMS", "999998"),
                type, CONSENT);
    }

    private SmsTransaction stored(SmsTransaction row) {
        return new TransactionTemplate(manager).execute(tx -> em.find(SmsTransaction.class, row.getId()));
    }

    private SmsQueueProcessingService worker() {
        return new SmsQueueProcessingService(recorder, clients, new SmsRetryCalculator(), type -> true,
                command -> CONSENT, new SmsDefaultProviderResolver(config), config);
    }

    private StubSmsProviderClient provider(SmsProviderType type) {
        return new StubSmsProviderClient() {
            @Override
            public SmsProviderType providerType() { return type; }
            @Override
            public SmsProviderMessageStatusDto lookupMessageStatus(String reference, String id,
                    SmsProviderSettings settings) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                lookups.incrementAndGet();
                return SmsProviderMessageStatusDto.notFound();
            }
            @Override
            public SmsProviderSendResultDto send(SmsSendCommand command, String reference, SmsProviderSettings settings) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                sends.incrementAndGet();
                return SmsProviderSendResultDto.accepted("FAKE_" + reference, SmsStatus.SENT);
            }
        };
    }

    private <T> T wired(T dao) {
        ReflectionTestUtils.setField(dao, "entityManager", em);
        return dao;
    }

    @SuppressWarnings("unchecked")
    private <T> T transactional(T target) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(interceptor);
        proxy.addAdvice((MethodInterceptor) invocation -> {
            if (TransactionSynchronizationManager.isActualTransactionActive()) sessionMode();
            return invocation.proceed();
        });
        return (T) proxy.getProxy();
    }

    private void sessionMode() {
        em.createNativeQuery("SET SESSION innodb_snapshot_isolation=" + (snapshot ? "1" : "0")).executeUpdate();
        em.createNativeQuery("SET SESSION innodb_lock_wait_timeout=30").executeUpdate();
        Object[] mode = (Object[]) em.createNativeQuery("SELECT @@tx_isolation, @@innodb_snapshot_isolation")
                .getSingleResult();
        assertThat(mode[0].toString()).isEqualTo("REPEATABLE-READ");
        assertThat(((Number) mode[1]).intValue()).isEqualTo(snapshot ? 1 : 0);
    }

    private void awaitSelectionLockWait(Future<?> waiting, long blocker) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        String sql = "SELECT r.trx_query FROM information_schema.INNODB_LOCK_WAITS w "
                + "JOIN information_schema.INNODB_TRX b ON b.trx_id=w.blocking_trx_id "
                + "JOIN information_schema.INNODB_TRX r ON r.trx_id=w.requesting_trx_id "
                + "WHERE b.trx_mysql_thread_id=?";
        try (var s = admin.prepareStatement(sql)) {
            s.setLong(1, blocker);
            while (System.nanoTime() < until) {
                if (waiting.isDone()) {
                    waiting.get(); // expose an actual implementation failure rather than hide it as a timeout
                    throw new AssertionError("selection change completed while its mutex was still held");
                }
                try (var r = s.executeQuery()) {
                    if (r.next()) {
                        assertThat(r.getString(1)).contains("sms_provider_rate_limit");
                        return;
                    }
                }
                // MariaDB refreshes its InnoDB lock telemetry only after more than 100 ms without a read.
                Thread.sleep(250);
            }
        }
        throw new AssertionError("selection change did not wait on the holder's database mutex");
    }

    /** Compare every committed config/version, cutoff, limiter and message column after a refused transaction. */
    private List<String> durableState() {
        List<String> state = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(schemaUrl, user, password);
             var statement = connection.createStatement()) {
            for (String table : List.of("sms_config", "SystemPreferences", "sms_transaction", "sms_provider_rate_limit")) {
                String key = table.equals("sms_provider_rate_limit") ? "provider_type" : "id";
                try (var rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY " + key)) {
                    int columns = rows.getMetaData().getColumnCount();
                    while (rows.next()) {
                        StringBuilder row = new StringBuilder(table);
                        for (int column = 1; column <= columns; column++) row.append('|').append(rows.getString(column));
                        state.add(row.toString());
                    }
                }
            }
            return state;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot inspect the owned FAKE SMS schema", e);
        }
    }

    private String limiterState() throws Exception {
        try (var s = admin.createStatement(); var r = s.executeQuery("SELECT send_count, window_started_at, updated_at "
                + "FROM `" + schema + "`.sms_provider_rate_limit WHERE provider_type='STUB'")) {
            assertThat(r.next()).isTrue();
            return r.getInt(1) + ":" + r.getString(2) + ":" + r.getString(3);
        }
    }

    private static boolean hasSnapshotConflict(Throwable failure) {
        for (int i = 0; failure != null && i < 20; i++, failure = failure.getCause()) {
            if (failure instanceof SQLException sql && sql.getErrorCode() == 1020) return true;
        }
        return false;
    }

    private static int permitted(List<Future<Boolean>> attempts) throws Exception {
        int count = 0;
        for (Future<Boolean> attempt : attempts) if (attempt.get(20, TimeUnit.SECONDS)) count++;
        return count;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Path migration(String name) throws Exception {
        try (var paths = Files.list(COMMON)) {
            List<Path> matches = paths.filter(p -> p.getFileName().toString().endsWith("__" + name + ".sql")).toList();
            assertThat(matches).hasSize(1);
            return matches.get(0);
        }
    }

    private static void install(Connection connection, Path path, String marker) throws Exception {
        String sql = Files.readString(path);
        int start = sql.indexOf(marker);
        assertThat(start).isNotNegative();
        try (var s = connection.createStatement()) {
            s.execute(sql.substring(start, sql.indexOf(';', start)));
        }
    }

    private static final class AtomicReferenceBox<T> { private T value; }
}
