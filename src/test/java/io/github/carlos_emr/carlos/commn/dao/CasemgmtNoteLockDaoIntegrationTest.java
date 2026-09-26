/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class CasemgmtNoteLockDaoIntegrationTest {
    private SessionFactory factory;

    @BeforeEach
    void setUp() {
        factory = new Configuration().addAnnotatedClass(CasemgmtNoteLock.class)
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:note-lock-" + UUID.randomUUID())
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    private CasemgmtNoteLockDaoImpl dao(EntityManager em) {
        CasemgmtNoteLockDaoImpl dao = new CasemgmtNoteLockDaoImpl();
        dao.entityManager = em;
        return dao;
    }

    private void seed(String provider, int patient, long note, String session) {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var lock = new CasemgmtNoteLock();
            lock.setProviderNo(provider);
            lock.setDemographicNo(patient);
            lock.setNoteId(note);
            lock.setSessionId(session);
            em.persist(lock);
            em.getTransaction().commit();
        }
    }

    @Test
    void shouldPreserveTransferredLock_whenOriginalSessionReleasesFromStalePersistenceContext() {
        seed("1001", 1, 0, "first-session");
        try (var first = factory.createEntityManager(); var second = factory.createEntityManager()) {
            var stale = dao(first).findByNoteDemo(1, 0L);
            second.getTransaction().begin();
            dao(second).findByNoteDemo(1, 0L).setSessionId("second-session");
            second.getTransaction().commit();
            assertThat(stale.getSessionId()).isEqualTo("first-session");

            first.getTransaction().begin();
            assertThat(dao(first).removeForSession("1001", 1, 0L, stale.getSessionId())).isZero();
            first.getTransaction().commit();
            first.clear();
            assertThat(dao(first).findByNoteDemo(1, 0L).getSessionId()).isEqualTo("second-session");
        }
    }

    @Test
    void shouldOnlyRemoveMatchingLock_whenCurrentSessionReleasesTwice() {
        seed("1001", 1, 0, "owner");
        seed("1001", 2, 0, "owner");
        seed("1001", 1, 42, "owner");
        seed("1002", 3, 0, "owner");
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var dao = dao(em);
            assertThat(dao.removeForSession("1002", 1, 0L, "owner")).isZero();
            assertThat(dao.removeForSession("1001", 1, 0L, "owner")).isEqualTo(1);
            assertThat(dao.removeForSession("1001", 1, 0L, "owner")).isZero();
            em.getTransaction().commit();
            assertThat(dao.findByNoteDemo(1, 0L)).isNull();
            assertThat(dao.findByNoteDemo(2, 0L)).isNotNull();
            assertThat(dao.findByNoteDemo(1, 42L)).isNotNull();
            assertThat(dao.findByNoteDemo(3, 0L)).isNotNull();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void shouldPreserveLocks_whenSessionIdentityIsMissing(String session) {
        seed("1001", 1, 0, session);
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            assertThat(dao(em).removeForSession("1001", 1, 0L, session)).isZero();
            em.getTransaction().commit();
            assertThat(dao(em).findByNoteDemo(1, 0L)).isNotNull();
        }
    }
}
