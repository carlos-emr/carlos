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

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.SmsStatus;
import io.github.carlos_emr.carlos.sms.SmsConsentStatus;
import io.github.carlos_emr.carlos.sms.command.SmsSendCommand;
import io.github.carlos_emr.carlos.sms.dao.SmsTransactionDaoImpl;
import io.github.carlos_emr.carlos.sms.dto.SmsConsentDecisionDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderMessageStatusDto;
import io.github.carlos_emr.carlos.sms.dto.SmsProviderSendResultDto;
import io.github.carlos_emr.carlos.sms.dto.SmsSendResultDto;
import io.github.carlos_emr.carlos.sms.model.SmsTransaction;
import io.github.carlos_emr.carlos.sms.validator.SmsSendValidator;
import io.github.carlos_emr.carlos.sms.dao.SmsProviderRateLimitDaoImpl;
import io.github.carlos_emr.carlos.sms.model.SmsProviderRateLimit;
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
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in MariaDB regression for locks that H2 cannot reproduce. Set SMS_TEST_DB_URL to a server URL
 * using the project's MySQL JDBC driver and ending in '/' (for example jdbc:mysql://localhost:3306/), plus SMS_TEST_DB_USER and
 * SMS_TEST_DB_PASSWORD. The account needs CREATE/DROP DATABASE privileges, and PROCESS to read InnoDB
 * lock waits. Only a unique temporary schema is written, and it is dropped afterwards. Requires MariaDB
 * with snapshot isolation enabled. Runs the production DAOs and Spring transaction boundaries, including
 * direct-send claim release and claim renewal after a long permit wait.
 */
@Tag("integration")
@Tag("service")
@Isolated
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "SMS_TEST_DB_URL", matches = ".+")
class JpaSmsSendRateLimitMariaDbIntegrationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
    private static final int LIMIT = 5;
    private static final int WORKERS = 12;
    private static final Path COMMON_MIGRATIONS = Path.of("database", "mysql", "migration", "common");
    private final String schema = "sms_limiter_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection admin;
    private SessionFactory entityManagerFactory;
    private EntityManager entityManager;
    private JpaTransactionManager transactionManager;
    private String schemaUrl;
    private String user;
    private String password;

    @BeforeAll
    void createDatabase() throws Exception {
        String serverUrl = System.getenv("SMS_TEST_DB_URL");
        assertThat(serverUrl).matches("jdbc:mysql://[^/]+/");
        user = System.getenv("SMS_TEST_DB_USER");
        password = System.getenv("SMS_TEST_DB_PASSWORD");
        admin = DriverManager.getConnection(serverUrl, user, password);
        try (var statement = admin.createStatement(); var result = statement.executeQuery("SELECT VERSION()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).containsIgnoringCase("MariaDB");
        }
        try (var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");

        }
        schemaUrl = serverUrl + schema;
        try (Connection fixture = DriverManager.getConnection(schemaUrl, user, password)) {
            executeMigrationStatement(fixture, "add_sms_system_of_record", "CREATE TABLE sms_transaction (");
            executeMigrationStatement(fixture, "add_sms_system_of_record", "CREATE TABLE sms_provider_rate_limit (");
            executeMigrationStatement(fixture, "add_sms_consent", "ALTER TABLE sms_transaction");
        }
        entityManagerFactory = new Configuration().addAnnotatedClass(SmsProviderRateLimit.class)
                .addAnnotatedClass(SmsTransaction.class)
                .setProperty("hibernate.connection.url", schemaUrl)
                .setProperty("hibernate.connection.username", user)
                .setProperty("hibernate.connection.password", password)
                .setProperty("hibernate.connection.pool_size", "16")
                .setProperty("hibernate.connection.isolation", Integer.toString(Connection.TRANSACTION_REPEATABLE_READ))
                .setProperty("hibernate.dialect", OscarMySQL5Dialect.class.getName())
                .setProperty("hibernate.hbm2ddl.auto", "none")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
        transactionManager = new JpaTransactionManager(entityManagerFactory);
    }

    @AfterAll
    void dropDatabase() throws Exception {
        try {
            if (entityManagerFactory != null) entityManagerFactory.close();
        } finally {
            if (admin != null) {
                try (Connection connection = admin; var statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE IF EXISTS `" + schema + "`");
                }
            }
        }
    }

    @BeforeEach
    void clearLimiter() throws Exception {
        try (var statement = admin.createStatement()) {
            statement.executeUpdate("DELETE FROM `" + schema + "`.sms_provider_rate_limit");
            statement.executeUpdate("DELETE FROM `" + schema + "`.sms_transaction");
        }
    }

    @Test
    void shouldEnforceCap_whenSeededRowIsContended() throws Exception {
        contend(true);
    }

    @Test
    void shouldEnforceCap_whenMissingRowIsContended() throws Exception {
        contend(false);
    }

    @Test
    void shouldKeepPermit_whenOuterTransactionRollsBack() throws Exception {
        SmsSendRateLimitService service = service(new SmsProviderRateLimitDaoImpl());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(service.tryAcquire(SmsProviderType.STUB)).isTrue();
            status.setRollbackOnly();
        });
        assertCount(1);
    }

    @Test
    void shouldAllowRetry_whenPermitTransactionRollsBack() throws Exception {
        AtomicBoolean failOnce = new AtomicBoolean(true);
        SmsSendRateLimitService service = service(new SmsProviderRateLimitDaoImpl() {
            @Override
            public void flush() {
                super.flush();
                if (failOnce.getAndSet(false)) throw new IllegalStateException("synthetic failure after flush");
            }
        });
        assertThatThrownBy(() -> service.tryAcquire(SmsProviderType.STUB))
                .isInstanceOf(IllegalStateException.class).hasMessage("synthetic failure after flush");
        assertCount(0);
        assertThat(service.tryAcquire(SmsProviderType.STUB)).isTrue();
        assertCount(1);
    }

    @Test
    void shouldWaitForCreator_whenMissingRowCreationCommits() throws Exception {
        waitForCreator(true);
    }

    @Test
    void shouldCreateRow_whenConcurrentCreatorRollsBack() throws Exception {
        waitForCreator(false);
    }

    @Test
    void shouldConfirmQueuedSend_whenLimiterThrowsAndClaimIsReleased() throws Exception {
        AtomicInteger sends = new AtomicInteger();
        SmsSendService service = sendService(type -> {
            throw new IllegalStateException("synthetic limiter failure");
        }, sends);

        SmsSendResultDto result = service.send(syntheticCommand());

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
        assertThat(sends.get()).isZero();
        assertStoredSend(SmsStatus.QUEUED, 0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveCurrentClaim_whenReleaseLosesVersionRace(boolean limiterThrows) throws Exception {
        AtomicInteger sends = new AtomicInteger();
        SmsSendService service = sendService(type -> {
            mutateStoredSend("UPDATE `" + schema + "`.sms_transaction SET version = version + 1");
            if (limiterThrows) throw new IllegalStateException("synthetic limiter failure");
            return false;
        }, sends);

        SmsSendResultDto result = service.send(syntheticCommand());

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(result.messages()).containsExactly(
                "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
        assertThat(sends.get()).isZero();
        assertStoredSend(SmsStatus.SENDING, 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveDeliveryOutcome_whenCallbackWinsReleaseRace(boolean limiterThrows) throws Exception {
        AtomicInteger sends = new AtomicInteger();
        SmsSendService service = sendService(type -> {
            mutateStoredSend("UPDATE `" + schema + "`.sms_transaction SET version = version + 1, "
                    + "status = 'DELIVERED', provider_message_id = 'synthetic-delivered'");
            if (limiterThrows) throw new IllegalStateException("synthetic limiter failure");
            return false;
        }, sends);

        SmsSendResultDto result = service.send(syntheticCommand());

        assertThat(result.accepted()).isTrue();
        assertThat(result.status()).isEqualTo(SmsStatus.DELIVERED);
        assertThat(result.providerMessageId()).isEqualTo("synthetic-delivered");
        assertThat(sends.get()).isZero();
        assertStoredSend(SmsStatus.DELIVERED, 1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldAvoidConfirmedQueue_whenClaimedRowDisappears(boolean limiterThrows) throws Exception {
        AtomicInteger sends = new AtomicInteger();
        SmsSendService service = sendService(type -> {
            mutateStoredSend("DELETE FROM `" + schema + "`.sms_transaction");
            if (limiterThrows) throw new IllegalStateException("synthetic limiter failure");
            return false;
        }, sends);

        SmsSendResultDto result = service.send(syntheticCommand());

        assertThat(result.accepted()).isFalse();
        assertThat(result.status()).isEqualTo(SmsStatus.SENDING);
        assertThat(result.messages()).containsExactly(
                "SMS send outcome is unknown; awaiting provider status lookup. Do not resend manually.");
        assertThat(sends.get()).isZero();
        try (var statement = admin.createStatement(); var rows = statement.executeQuery(
                "SELECT COUNT(*) FROM `" + schema + "`.sms_transaction")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isZero();
        }
    }

    @Test
    void shouldNotSendTwice_whenStaleRecoveryTakesOverDuringPermitWait() throws Exception {
        SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        SmsTransactionService recorder = (SmsTransactionService) transactional(
                new JpaSmsTransactionService(dao, event -> { }, transactionManager) {
                    @Override
                    public SmsTransaction markSending(SmsTransaction transaction, Date attemptAt) {
                        // Stands in for a permit wait longer than the five-minute stale-send timeout.
                        return super.markSending(transaction, new Date(attemptAt.getTime() - 360_000));
                    }
                });
        AtomicInteger sends = new AtomicInteger();
        StubSmsProviderClient provider = new StubSmsProviderClient() {
            @Override
            public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId, SmsProviderSettings settings) {
                sends.incrementAndGet();
                return super.send(command, clientReferenceId, settings);
            }

            @Override
            public SmsProviderMessageStatusDto lookupMessageStatus(String clientReferenceId, String messageId,
                                                               SmsProviderSettings settings) {
                return SmsProviderMessageStatusDto.notFound();
            }
        };
        SmsProviderClientResolver resolver = new SmsProviderClientResolver(List.of(provider));
        SmsConsentService consent = command -> SmsConsentDecisionDto.permitted(
                SmsConsentStatus.OPT_IN, 4321, CLOCK.instant());
        SmsSendRateLimitService limiter = service(new SmsProviderRateLimitDaoImpl());
        assertThat(limiter.tryAcquire(SmsProviderType.STUB)).isTrue();
        SmsSendService direct = new SmsSendService(new SmsSendValidator(), consent, resolver, recorder, limiter,
                new SmsDefaultProviderResolver(() -> "STUB"));
        SmsQueueProcessingService worker = new SmsQueueProcessingService(recorder, resolver,
                new SmsRetryCalculator(), limiter, consent);

        // This connection holds the limiter row, so the direct send waits for its permit after claiming.
        try (Connection holder = DriverManager.getConnection(schemaUrl, user, password)) {
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) {
                statement.executeUpdate("UPDATE sms_provider_rate_limit SET send_count = send_count");
            }
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<SmsSendResultDto> waiting = pool.submit(() -> direct.send(syntheticCommand()));
                SmsSendResultDto result;
                try {
                    awaitLockWaitOn(holder, waiting);
                    // Another run's stale recovery finds the claim unsent at the provider and requeues it.
                    assertThat(worker.processDueMessages()).isZero();
                    assertThat(storedStatus()).isEqualTo(SmsStatus.QUEUED.name());
                } finally {
                    holder.commit();
                }
                result = waiting.get(15, TimeUnit.SECONDS);
                assertThat(result.accepted()).isTrue();
                assertThat(result.status()).isEqualTo(SmsStatus.QUEUED);
                // The permit was granted, so the claim renewal, not a limiter timeout, stopped the send.
                assertCount(2);
            }
        }
        assertThat(sends.get()).isZero();

        mutateStoredSend("UPDATE `" + schema + "`.sms_transaction SET next_attempt_at = NULL");
        assertThat(worker.processDueMessages()).isEqualTo(1);
        assertThat(sends.get()).isEqualTo(1);
        assertThat(storedStatus()).isEqualTo(SmsStatus.SENT.name());
    }

    private String storedStatus() throws Exception {
        try (var statement = admin.createStatement(); var rows = statement.executeQuery(
                "SELECT status FROM `" + schema + "`.sms_transaction")) {
            assertThat(rows.next()).isTrue();
            String status = rows.getString(1);
            assertThat(rows.next()).isFalse();
            return status;
        }
    }

    /** Waits until InnoDB reports another transaction blocked by the given connection's locks. */
    private void awaitLockWaitOn(Connection blocker, Future<?> waiter) throws Exception {
        long blockerId;
        try (var statement = blocker.createStatement(); var rows = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertThat(rows.next()).isTrue();
            blockerId = rows.getLong(1);
        }
        String sql = "SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w "
                + "JOIN information_schema.INNODB_TRX b ON b.trx_id = w.blocking_trx_id "
                + "WHERE b.trx_mysql_thread_id = ?";
        try (var statement = admin.prepareStatement(sql)) {
            statement.setLong(1, blockerId);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                if (waiter.isDone()) {
                    // Surfaces the waiter's own result or exception instead of a bare timeout.
                    throw new AssertionError("The waiting call finished without blocking: " + waiter.get());
                }
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    if (rows.getInt(1) > 0) {
                        return;
                    }
                }
                // InnoDB refreshes its lock tables only when they have not been read for 100 ms.
                Thread.sleep(200);
            }
        }
        throw new AssertionError("No InnoDB lock wait on connection " + blockerId);
    }

    private SmsSendCommand syntheticCommand() {
        return SmsSendCommand.patientMessage(123, "416-555-1212", "synthetic release regression", "999998");
    }

    private SmsSendService sendService(SmsSendRateLimitService limiter, AtomicInteger sends) {
        SmsTransactionDaoImpl dao = new SmsTransactionDaoImpl();
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        SmsTransactionService recorder = (SmsTransactionService) transactional(
                new JpaSmsTransactionService(dao, event -> { }, transactionManager));
        StubSmsProviderClient provider = new StubSmsProviderClient() {
            @Override
            public SmsProviderSendResultDto send(SmsSendCommand command, String clientReferenceId, SmsProviderSettings settings) {
                sends.incrementAndGet();
                return super.send(command, clientReferenceId, settings);
            }
        };
        return new SmsSendService(new SmsSendValidator(),
                command -> SmsConsentDecisionDto.permitted(SmsConsentStatus.OPT_IN, 4321, CLOCK.instant()),
                new SmsProviderClientResolver(List.of(provider)), recorder, limiter,
                new SmsDefaultProviderResolver(() -> "STUB"));
    }

    private void mutateStoredSend(String sql) {
        try (var statement = admin.createStatement()) {
            assertThat(statement.executeUpdate(sql)).isEqualTo(1);
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("synthetic concurrent update failed", failure);
        }
    }

    private void assertStoredSend(SmsStatus status, int attempts) throws Exception {
        try (var statement = admin.createStatement(); var rows = statement.executeQuery(
                "SELECT status, attempt_count, next_attempt_at FROM `" + schema + "`.sms_transaction")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(status.name());
            assertThat(rows.getInt(2)).isEqualTo(attempts);
            if (status == SmsStatus.QUEUED) {
                assertThat(rows.getTimestamp(3)).isNotNull().isBeforeOrEqualTo(new Date());
            }
            assertThat(rows.next()).isFalse();
        }
    }

    private void executeMigrationStatement(Connection fixture, String migrationName, String marker) throws Exception {
        String sql = Files.readString(commonMigration(migrationName), StandardCharsets.UTF_8);
        int start = sql.indexOf(marker);
        assertThat(start).isNotNegative();
        try (var statement = fixture.createStatement()) {
            statement.execute(sql.substring(start, sql.indexOf(';', start)));
        }
    }

    /** The one common migration named {@code V1.0.<n>__<name>.sql}; the number is not pinned, since it is set at merge. */
    private static Path commonMigration(String name) throws IOException {
        try (Stream<Path> files = Files.list(COMMON_MIGRATIONS)) {
            List<Path> candidates = files
                    .filter(p -> p.getFileName().toString().matches("V1\\.0\\.\\d+__" + Pattern.quote(name) + "\\.sql"))
                    .toList();
            assertThat(candidates).as("exactly one %s migration", name).hasSize(1);
            return candidates.get(0);
        }
    }

    private void contend(boolean seeded) throws Exception {
        SmsSendRateLimitService service = service(new SmsProviderRateLimitDaoImpl());
        try (var pool = Executors.newFixedThreadPool(WORKERS)) {
            for (int round = 0; round < 20; round++) {
                clearLimiter();
                int initialCount = seeded ? 1 : 0;
                if (seeded) assertThat(service.tryAcquire(SmsProviderType.STUB)).isTrue();
                CountDownLatch ready = new CountDownLatch(WORKERS);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Boolean>> attempts = new ArrayList<>();
                try {
                    for (int caller = 0; caller < WORKERS; caller++) {
                        attempts.add(pool.submit(() -> {
                            ready.countDown();
                            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                            return service.tryAcquire(SmsProviderType.STUB);
                        }));
                    }
                    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                } finally {
                    start.countDown();
                }
                int permitted = initialCount;
                for (Future<Boolean> attempt : attempts) {
                    if (attempt.get(15, TimeUnit.SECONDS)) permitted++;
                }
                assertThat(permitted).isEqualTo(LIMIT);
                assertCount(LIMIT);
            }
        }
    }

    private void waitForCreator(boolean commit) throws Exception {
        SmsSendRateLimitService service = service(new SmsProviderRateLimitDaoImpl());
        // This connection keeps the new key uncommitted while another transaction requests a permit.
        try (Connection holder = DriverManager.getConnection(schemaUrl, user, password)) {
            holder.setAutoCommit(false);
            try (var statement = holder.createStatement()) {
                statement.executeUpdate("INSERT INTO sms_provider_rate_limit VALUES "
                        + "('STUB', 0, '2026-10-01 12:00:00', '2026-10-01 12:00:00', '2026-10-01 12:00:00')");
            }
            try (var pool = Executors.newSingleThreadExecutor()) {
                Future<Boolean> waiting = pool.submit(() -> service.tryAcquire(SmsProviderType.STUB));
                try {
                    // Proves the permit request reached the database and is blocked by the creator's row.
                    awaitLockWaitOn(holder, waiting);
                } finally {
                    if (commit) holder.commit();
                    else holder.rollback();
                }
                assertThat(waiting.get(15, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertCount(1);
    }

    private SmsSendRateLimitService service(SmsProviderRateLimitDaoImpl dao) {
        ReflectionTestUtils.setField(dao, "entityManager", entityManager);
        JpaSmsSendRateLimitService target = new JpaSmsSendRateLimitService(
                dao, LIMIT, Duration.ofMinutes(5), CLOCK);
        return (SmsSendRateLimitService) transactional(target);
    }

    private Object transactional(Object target) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactionManager);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        // This advice runs inside the production annotation's transaction, on its actual connection.
        factory.addAdvice((MethodInterceptor) invocation -> {
            Object[] settings = (Object[]) entityManager.createNativeQuery(
                    "SELECT @@tx_isolation, @@innodb_snapshot_isolation").getSingleResult();
            assertThat(settings[0].toString()).isEqualTo("REPEATABLE-READ");
            assertThat(((Number) settings[1]).intValue()).isEqualTo(1);
            entityManager.createNativeQuery("SET SESSION innodb_lock_wait_timeout=5").executeUpdate();
            return invocation.proceed();
        });
        return factory.getProxy();
    }

    private void assertCount(int expected) throws Exception {
        try (var statement = admin.createStatement(); var result = statement.executeQuery(
                "SELECT COUNT(*), COALESCE(SUM(send_count), 0) FROM `" + schema + "`.sms_provider_rate_limit")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(expected == 0 ? 0 : 1);
            assertThat(result.getInt(2)).isEqualTo(expected);
        }
    }
}
