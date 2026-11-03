/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.AppointmentArchive;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.AppointmentUpdateException;
import io.github.carlos_emr.carlos.managers.ScheduleManager;
import io.github.carlos_emr.carlos.managers.ScheduleManagerImpl;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import java.sql.DriverManager;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses the CI MariaDB DAO lane when configured, and H2 for the ordinary test lane. */
@Tag("integration")
@Tag("mariadb-native")
class AppointmentUpdateDaoIntegrationTest {
    private SessionFactory factory;
    private JpaTransactionManager transactions;
    private ScheduleManager manager;
    private OscarAppointmentDaoImpl appointments;
    private AppointmentArchiveDaoImpl archives;
    private String database;
    private String server;
    private final Properties credentials = new Properties();
    private final LoggedInInfo clientA = mock(LoggedInInfo.class);
    private final LoggedInInfo clientB = mock(LoggedInInfo.class);
    private int id;

    @BeforeEach
    void setup() throws Exception {
        database = "carlos_test_5300_" + UUID.randomUUID().toString().replace("-", "");
        server = System.getenv("CARLOS_TEST_MARIADB_URL");
        String url = "jdbc:h2:mem:" + database + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000";
        credentials.setProperty("user", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_USER", "root"));
        credentials.setProperty("password", System.getenv().getOrDefault("CARLOS_TEST_MARIADB_PASSWORD", ""));
        if (server != null) {
            try (var connection = DriverManager.getConnection(server, credentials); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
                statement.executeUpdate("CREATE DATABASE " + database);
            }
            url = server + database;
        }
        var configuration = new Configuration().addAnnotatedClass(Appointment.class).addAnnotatedClass(AppointmentArchive.class)
                .setProperty("hibernate.connection.driver_class", server == null ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver")
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.dialect", server == null ? "org.hibernate.dialect.H2Dialect"
                        : "io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect")
                .setProperty("hibernate.connection.username", server == null ? "sa" : credentials.getProperty("user"))
                .setProperty("hibernate.connection.password", server == null ? "" : credentials.getProperty("password"))
                .setProperty("hibernate.connection.isolation", server == null ? "2" : "4") // MariaDB REPEATABLE READ
                .setProperty("hibernate.connection.pool_size", "4")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop");
        var vendor = new org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter();
        configuration.getProperties().putAll(vendor.getJpaPropertyMap());
        factory = configuration.buildSessionFactory();
        if (server != null) {
            // Match the production column, rather than Hibernate's fractional TIMESTAMP default.
            try (var connection = DriverManager.getConnection(url, credentials); var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE appointment MODIFY updatedatetime DATETIME NULL");
            }
        }
        transactions = new JpaTransactionManager(factory);
        transactions.setJpaDialect(vendor.getJpaDialect());
        appointments = spy(new OscarAppointmentDaoImpl());
        archives = spy(new AppointmentArchiveDaoImpl());
        var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        ReflectionTestUtils.setField(appointments, "entityManager", em);
        ReflectionTestUtils.setField(archives, "entityManager", em);
        var target = new ScheduleManagerImpl();
        ReflectionTestUtils.setField(target, "oscarAppointmentDao", appointments);
        ReflectionTestUtils.setField(target, "appointmentArchiveDao", archives);
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_appointment"), eq("w"), isNull())).thenReturn(true);
        ReflectionTestUtils.setField(target, "securityInfoManager", security);
        when(clientA.getLoggedInProviderNo()).thenReturn("FAKE-A");
        when(clientB.getLoggedInProviderNo()).thenReturn("FAKE-B");
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        manager = (ScheduleManager) proxy.getProxy();
        id = tx(entityManager -> {
            Appointment row = new Appointment();
            row.setReason("FAKE-original");
            row.setCreator("FAKE-creator");
            row.setCreatorSecurityId(7);
            row.setBookingSource(Appointment.BookingSource.OSCAR);
            row.setCreateDateTime(new Date(1_000_000_000_000L));
            entityManager.persist(row);
            return row.getId();
        });
    }

    @AfterEach
    void cleanup() throws Exception {
        if (factory != null) factory.close();
        if (server != null && database != null) {
            try (var connection = DriverManager.getConnection(server, credentials); var statement = connection.createStatement()) {
                statement.executeUpdate("DROP DATABASE IF EXISTS " + database);
            }
        }
    }

    private <T> T tx(Function<EntityManager, T> action) {
        return new TransactionTemplate(transactions).execute(status -> {
            var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
            return action.apply(em);
        });
    }

    private Appointment read() {
        return tx(em -> em.find(Appointment.class, id));
    }

    private Appointment save(LoggedInInfo client, String version, Consumer<Appointment> edit) {
        // Audit logging is separate from the appointment persistence exercised here.
        try (var audit = mockStatic(LogAction.class)) {
            return manager.updateAppointment(client, id, version, edit);
        }
    }

    private void assertState(String reason, long archiveCount) {
        tx(em -> {
            Appointment row = em.find(Appointment.class, id);
            assertThat(row.getReason()).isEqualTo(reason);
            assertThat(row.getCreator()).isEqualTo("FAKE-creator");
            assertThat(row.getCreatorSecurityId()).isEqualTo(7);
            assertThat(row.getBookingSource()).isEqualTo(Appointment.BookingSource.OSCAR);
            assertThat(row.getCreateDateTime().getTime()).isEqualTo(1_000_000_000_000L);
            assertThat(em.createQuery("select count(a) from AppointmentArchive a", Long.class).getSingleResult())
                    .isEqualTo(archiveCount);
            return null;
        });
    }

    @Test
    void shouldRejectSecondClient_andKeepOriginalArchive() {
        String a = read().getEditVersion();
        String b = read().getEditVersion();
        Appointment saved = save(clientA, a, row -> row.setReason("FAKE-first"));
        assertThat(saved.getEditVersion()).isNotEqualTo(a).isEqualTo(read().getEditVersion());
        assertThat(read().getLastUpdateUser()).isEqualTo("FAKE-A");
        assertThatThrownBy(() -> save(clientB, b, row -> row.setReason("FAKE-stale")))
                .isInstanceOfSatisfying(AppointmentUpdateException.class,
                        e -> assertThat(e.getReason()).isEqualTo(AppointmentUpdateException.Reason.STALE_VERSION));
        assertState("FAKE-first", 1);
        tx(em -> {
            assertThat(em.createQuery("from AppointmentArchive", AppointmentArchive.class).getSingleResult().getReason())
                    .isEqualTo("FAKE-original");
            return null;
        });
        save(clientB, saved.getEditVersion(), row -> row.setReason("FAKE-fresh"));
        assertState("FAKE-fresh", 2);
    }

    @Test
    void shouldSerializeConcurrentEdits_andRejectTheWaitingStaleWriter() throws Exception {
        String version = read().getEditVersion();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempted = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> save(clientA, version, row -> {
                locked.countDown();
                try {
                    assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                row.setReason("FAKE-winner");
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> {
                attempted.countDown();
                return catchThrowable(() -> save(clientB, version, row -> row.setReason("FAKE-loser")));
            });
            assertThat(attempted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isInstanceOf(AppointmentUpdateException.class);
            assertState("FAKE-winner", 1);
        } finally {
            release.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void shouldAdvanceStoredVersion_whenWritesShareAClockSecond() {
        Date future = new Date((System.currentTimeMillis() / 1000 + 60) * 1000);
        tx(em -> {
            em.createNativeQuery("update appointment set updatedatetime=:version where appointment_no=:id")
                    .setParameter("version", future).setParameter("id", id).executeUpdate();
            return null;
        });
        String version = read().getEditVersion();
        Appointment first = save(clientA, version, row -> {
            row.setReason("FAKE-first");
            row.setUpdateDateTime(new Date(0));
        });
        assertThat(Long.parseLong(first.getEditVersion())).isEqualTo(future.getTime() + 1000);
        Appointment second = save(clientB, first.getEditVersion(), row -> row.setReason("FAKE-second"));
        assertThat(Long.parseLong(second.getEditVersion())).isEqualTo(future.getTime() + 2000);
        assertThat(read().getEditVersion()).isEqualTo(second.getEditVersion());
        assertState("FAKE-second", 2);
    }

    @Test
    void shouldRollbackArchiveAndMutation_whenFlushFails() {
        String version = read().getEditVersion();
        doThrow(new IllegalStateException("synthetic flush failure")).when(appointments).flush();
        assertThatThrownBy(() -> save(clientA, version, row -> row.setReason("FAKE-rejected")))
                .isInstanceOf(IllegalStateException.class);
        assertState("FAKE-original", 0);
        assertThat(read().getEditVersion()).isEqualTo(version);
    }

    @Test
    void shouldRequireReadToken_andSupportLegacyNullTimestamps() {
        assertThatThrownBy(() -> save(clientA, null, row -> row.setReason("FAKE-rejected")))
                .isInstanceOf(AppointmentUpdateException.class);
        tx(em -> {
            em.createNativeQuery("update appointment set updatedatetime=null where appointment_no=:id")
                    .setParameter("id", id).executeUpdate();
            return null;
        });
        assertThat(read().getEditVersion()).isEqualTo("unversioned");
        save(clientA, "unversioned", row -> row.setReason("FAKE-upgraded"));
        assertState("FAKE-upgraded", 1);
        assertThatThrownBy(() -> save(clientB, "unversioned", row -> row.setReason("FAKE-stale")))
                .isInstanceOf(AppointmentUpdateException.class);
    }
}
