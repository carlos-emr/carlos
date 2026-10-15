/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Episode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.apache.struts2.ActionContext;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual Hibernate rows, Spring JPA transactions and database locks; only authentication,
 * servlet transport and the Spring bean lookup are substituted. Like FrmDataShortcutLatestIntegrationTest,
 * this intentionally uses a self-contained isolated harness instead of the full CarlosTestBase
 * application context: each database is unique and destroyed when its factory closes. The production spring_jpa.xml
 * likewise binds its JpaTransactionManager to the primary entityManagerFactory. */
@Tag("integration")
class PregnancyTransactionIntegrationTest {
    private static EntityManagerFactory factory;
    private static JpaTransactionManager transactions;
    private int patient;
    private int other;

    @BeforeAll static void openDatabase() {
        factory = new Configuration().addAnnotatedClass(Demographic.class).addAnnotatedClass(Episode.class)
                .addAnnotatedClass(io.github.carlos_emr.carlos.commn.model.Provider.class)
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty("hibernate.connection.url", "jdbc:h2:mem:pregnancy_" + UUID.randomUUID() + ";MODE=MySQL;LOCK_TIMEOUT=10000")
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .setProperty("hibernate.show_sql", "false")
                .buildSessionFactory();
        transactions = new JpaTransactionManager(factory);

    }

    @AfterAll static void closeDatabase() { if (factory != null) factory.close(); }

    @BeforeEach void seedOwnedPatients() {
        write(em -> {
            em.createNativeQuery("ALTER TABLE Episode DROP CONSTRAINT IF EXISTS pregnancy_failure").executeUpdate();
            em.createQuery("DELETE FROM Episode").executeUpdate();
            em.createNativeQuery("DELETE FROM demographic_merged").executeUpdate();
            em.createNativeQuery("DELETE FROM demographic").executeUpdate();
            // Use real mapped columns but no entity getters: gender/pronoun remain NULL,
            // detecting accidental Demographic hydration during the pregnancy row lock.
            for (String name : List.of("Synthetic A", "Synthetic B")) {
                em.createNativeQuery("INSERT INTO demographic (first_name,last_name,sex,year_of_birth,month_of_birth,date_of_birth,patient_status) "
                        + "VALUES (:name,'Pregnancy fixture','F','1980','01','02','AC')")
                        .setParameter("name", name).executeUpdate();
            }
            patient = ((Number) em.createNativeQuery("SELECT demographic_no FROM demographic WHERE first_name='Synthetic A'").getSingleResult()).intValue();
            other = ((Number) em.createNativeQuery("SELECT demographic_no FROM demographic WHERE first_name='Synthetic B'").getSingleResult()).intValue();
            return null;
        });
    }

    private static <T> T write(Function<EntityManager, T> action) {
        return new TransactionTemplate(transactions).execute(tx -> action.apply(
                EntityManagerFactoryUtils.getTransactionalEntityManager(factory)));
    }

    private int submit(String operation, int patientId, Integer episodeId, String notes) throws Exception {
        try (var spring = mockStatic(SpringUtils.class)) {
            SecurityInfoManager security = mock(SecurityInfoManager.class);
            when(security.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(EpisodeDao.class)).thenReturn(mock(EpisodeDao.class));
            spring.when(() -> SpringUtils.getBean(EntityManagerFactory.class)).thenReturn(factory);
            spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
            var request = new MockHttpServletRequest();
            var response = new MockHttpServletResponse();
            request.setMethod("POST");
            request.setParameter("method", operation);
            request.setParameter("demographicNo", Integer.toString(patientId));
            request.setParameter("code", "72892002");
            request.setParameter("startDate", "2026-01-02");
            request.setParameter("endDate", "2026-02-03");
            request.setParameter("notes", notes);
            if (episodeId != null) request.setParameter("episodeId", episodeId.toString());
            LoggedInInfo user = mock(LoggedInInfo.class);
            when(user.getLoggedInProviderNo()).thenReturn("999001");
            request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", user);
            ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
            List<?> patientsBefore = patientRows();
            try {
                Pregnancy2Action action = new Pregnancy2Action();
                String result = action.execute();
                // This invokes the action only. Native Playwright verifies that the fixed
                // Struts result turns a successful commit into an actual HTTP 302 redirect.
                if (response.getStatus() == 200) {
                    assertThat(result).isEqualTo("saved");
                    assertThat(action.getDemographicNo()).isEqualTo(patientId);
                    assertThat(response.getRedirectedUrl()).isNull();
                } else {
                    assertThat(result).isEqualTo("none");
                }
                return response.getStatus();
            } finally {
                assertThat(patientRows()).usingRecursiveComparison().isEqualTo(patientsBefore);
            }
        } finally {
            ActionContext.clear();
        }
    }

    private static List<?> patientRows() {
        return write(em -> em.createNativeQuery("SELECT * FROM demographic ORDER BY demographic_no").getResultList());
    }

    private List<Episode> rows() {
        return write(em -> em.createQuery("SELECT e FROM Episode e ORDER BY e.id", Episode.class).getResultList());
    }

    @Test void shouldPersistLifecycle_whenRealJpaTransactionCommits() throws Exception {
        assertThat(submit("create", patient, null, "Original <literal> & ' notes")).isEqualTo(200);
        Episode saved = rows().get(0);
        assertThat(saved.getDemographicNo()).isEqualTo(patient);
        assertThat(saved.getStatus()).isEqualTo("Current");
        assertThat(saved.getLastUpdateUser()).isEqualTo("999001");
        assertThat(saved.getStartDateStr()).isEqualTo("2026-01-02");
        assertThat(submit("complete", patient, saved.getId(), "Completed")).isEqualTo(200);
        Episode complete = rows().get(0);
        assertThat(complete.getStatus()).isEqualTo("Complete");
        assertThat(complete.getEndDateStr()).isEqualTo("2026-02-03");
        assertThat(complete.getNotes()).isEqualTo("Completed");
        assertThat(complete.getLastUpdateTime()).isNotNull();
    }

    @Test void shouldRollBackAndPermitRetry_whenCompletionFlushFails() throws Exception {
        submit("create", patient, null, "Original");
        Episode before = rows().get(0);
        write(em -> em.createNativeQuery("ALTER TABLE Episode ADD CONSTRAINT pregnancy_failure CHECK (notes <> 'fail')").executeUpdate());
        assertThatThrownBy(() -> submit("complete", patient, before.getId(), "fail")).isInstanceOf(RuntimeException.class);
        Episode after = rows().get(0);
        assertThat(after).usingRecursiveComparison().ignoringFields("formatter").isEqualTo(before);
        assertThat(submit("complete", patient, before.getId(), "Retry")).isEqualTo(200);
        assertThat(rows().get(0).getNotes()).isEqualTo("Retry");
    }

    @Test void shouldLeaveNoEpisodeAndPermitRetry_whenCreateFlushFails() throws Exception {
        write(em -> em.createNativeQuery("ALTER TABLE Episode ADD CONSTRAINT pregnancy_failure CHECK (notes <> 'fail')").executeUpdate());
        assertThatThrownBy(() -> submit("create", patient, null, "fail")).isInstanceOf(RuntimeException.class);
        assertThat(rows()).isEmpty();
        assertThat(submit("create", patient, null, "Retry")).isEqualTo(200);
        assertThat(rows()).hasSize(1);
    }

    @Test void shouldKeepOneCurrentEpisode_whenConcurrentCreatesRace() throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Integer> submit = () -> { start.await(10, TimeUnit.SECONDS); return submit("create", patient, null, "Concurrent"); };
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(submit);
            var second = executor.submit(submit);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
            assertThat(rows()).hasSize(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void shouldPreserveOtherPatient_whenEpisodeIdIsForged() throws Exception {
        submit("create", other, null, "Other patient");
        Episode before = rows().get(0);
        assertThat(submit("complete", patient, before.getId(), "Tampered")).isEqualTo(404);
        assertThat(rows().get(0)).usingRecursiveComparison().ignoringFields("formatter").isEqualTo(before);
    }

    @Test void shouldPreserveCompletion_whenOldPostReplayed() throws Exception {
        submit("create", patient, null, "Original");
        int id = rows().get(0).getId();
        submit("complete", patient, id, "Final");
        Episode before = rows().get(0);
        assertThat(submit("complete", patient, id, "Overwrite")).isEqualTo(409);
        assertThat(rows().get(0)).usingRecursiveComparison().ignoringFields("formatter").isEqualTo(before);
    }
}
