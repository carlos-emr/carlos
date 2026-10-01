package io.github.carlos_emr.carlos.sms.service;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Opt-in MariaDB regression for locks that H2 cannot reproduce. Set SMS_TEST_DB_URL to a server URL
 * ending in '/' (for example jdbc:mysql://localhost:3306/), plus SMS_TEST_DB_USER and
 * SMS_TEST_DB_PASSWORD. The account needs CREATE/DROP DATABASE privileges. Only a unique temporary
 * schema is written, and it is dropped afterwards. Requires MariaDB with snapshot isolation enabled.
 * Runs the production DAO and the service's Spring REQUIRES_NEW transaction boundary.
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
        assertThat(serverUrl).matches("jdbc:(mysql|mariadb)://[^/]+/");
        user = System.getenv("SMS_TEST_DB_USER");
        password = System.getenv("SMS_TEST_DB_PASSWORD");
        admin = DriverManager.getConnection(serverUrl, user, password);
        try (var statement = admin.createStatement(); var result = statement.executeQuery("SELECT VERSION()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).containsIgnoringCase("MariaDB");
        }
        try (var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE `" + schema + "`");
            statement.execute("CREATE TABLE `" + schema + "`.sms_provider_rate_limit ("
                    + "provider_type VARCHAR(16) NOT NULL PRIMARY KEY, send_count INT NOT NULL DEFAULT 0, "
                    + "window_started_at DATETIME NOT NULL, created_at DATETIME NOT NULL, "
                    + "updated_at DATETIME NOT NULL) ENGINE=InnoDB");
        }
        schemaUrl = serverUrl + schema;
        entityManagerFactory = new Configuration().addAnnotatedClass(SmsProviderRateLimit.class)
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
        CountDownLatch enteringUpsert = new CountDownLatch(1);
        SmsSendRateLimitService service = service(new SmsProviderRateLimitDaoImpl() {
            @Override
            public void ensureExists(SmsProviderType type, Date now) {
                enteringUpsert.countDown();
                super.ensureExists(type, now);
            }
        });
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
                    assertThat(enteringUpsert.await(10, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> waiting.get(200, TimeUnit.MILLISECONDS))
                            .isInstanceOf(TimeoutException.class);
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
        return (SmsSendRateLimitService) factory.getProxy();
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
