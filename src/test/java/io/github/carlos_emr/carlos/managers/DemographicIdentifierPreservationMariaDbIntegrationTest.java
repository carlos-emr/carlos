/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.DemographicService;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real production JPA reads/locks/refresh against generated and unmodified ON/BC Flyway schemas. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DemographicMergeMariaDbIntegrationTest.Config.class)
@Tag("integration")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class DemographicIdentifierPreservationMariaDbIntegrationTest {
    public interface Writes { DemographicTo1 updateDemographicData(DemographicTo1 data); }
    private static final class Resource extends DemographicService implements Writes {
        private final LoggedInInfo actor;
        Resource(DemographicDao records, LoggedInInfo actor) {
            super(mock(CdsLegacyProjectionService.class), records); this.actor = actor;
        }
        @Override protected LoggedInInfo getLoggedInInfo() { return actor; }
    }
    @Autowired private DemographicDao patients;
    @Autowired private DataSource source;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager em;
    @Autowired private jakarta.persistence.EntityManagerFactory factory;
    private Writes api;
    private JdbcTemplate jdbc;
    private int patientId;

    @BeforeEach
    void setup() throws Exception {
        jdbc = new JdbcTemplate(source);
        if (!"flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"))) {
            try (var connection = source.getConnection(); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE IF NOT EXISTS admission(client_id INT, admission_status VARCHAR(20), program_id INT)");
                sql.execute("CREATE TABLE IF NOT EXISTS program(id INT, type VARCHAR(20))");
                sql.execute("CREATE TABLE IF NOT EXISTS health_safety(demographic_no INT)");
                sql.execute("CREATE TABLE IF NOT EXISTS lst_gender(code VARCHAR(10), description VARCHAR(100))");
            }
        }
        patientId = new TransactionTemplate(transactions).execute(status -> {
            var patient = new Demographic();
            patient.setFirstName("Original"); patient.setLastName("Identifier"); patient.setSex("M"); patient.setPrefName("");
            patient.setSin("FAKE-SIN"); patient.setHin("FAKE-HIN"); em.persist(patient);
            return patient.getDemographicNo();
        });
        var actor = new LoggedInInfo();
        var policy = mock(SecurityInfoManager.class);
        when(policy.hasPrivilege(actor, "_demographic", "u", patientId)).thenReturn(true);
        var manager = mock(DemographicManager.class);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            Demographic patient = call.getArgument(1);
            jdbc.update("update demographic set sin=?, hin=?, first_name=? where demographic_no=?",
                    patient.getSin(), patient.getHin(), patient.getFirstName(), patientId);
            if ("fail".equals(patient.getFirstName())) throw new IllegalStateException("late native write failure");
            return null;
        }).when(manager).updateDemographic(eq(actor), any(Demographic.class));
        var target = new Resource(patients, actor);
        ReflectionTestUtils.setField(target, "securityInfoManager", policy);
        ReflectionTestUtils.setField(target, "demographicManager", manager);
        var factory = new ProxyFactory(target); factory.setInterfaces(Writes.class);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        api = (Writes) factory.getProxy();
    }

    private DemographicTo1 payload(String name) {
        var data = new DemographicTo1(); data.setDemographicNo(patientId);
        data.setFirstName(name); data.setLastName("Identifier");
        data.setDobYear("1980"); data.setDobMonth("06"); data.setDobDay("15");
        return data;
    }

    @Test
    void shouldStartItsOwnJpaTransaction_andPreserveUndisclosedIdentifiers() {
        var result = api.updateDemographicData(payload("Changed"));
        assertThat(jdbc.queryForMap("select sin, hin, first_name from demographic where demographic_no=?", patientId))
                .containsEntry("sin", "FAKE-SIN").containsEntry("hin", "FAKE-HIN").containsEntry("first_name", "Changed");
        assertThat(result.getSin()).isNull();
    }

    @Test
    void shouldRefreshPreviouslyCachedIdentifiers_afterTakingTheWriteLock() {
        EntityManager context = factory.createEntityManager();
        assertThat(TransactionSynchronizationManager.hasResource(factory)).isFalse();
        TransactionSynchronizationManager.bindResource(factory, new org.springframework.orm.jpa.EntityManagerHolder(context));
        try {
            Demographic cached = context.find(Demographic.class, patientId);
            assertThat(cached.getSin()).isEqualTo("FAKE-SIN");
            // A separate committed connection changes the row after this context cached it.
            Long currentConnection = jdbc.queryForObject("select connection_id()", Long.class);
            try (var connection = source.getConnection();
                 var update = connection.prepareStatement("update demographic set sin=?, hin=? where demographic_no=?")) {
                assertThat(connection.getAutoCommit()).isTrue();
                try (var statement = connection.createStatement(); var id = statement.executeQuery("select connection_id()")) {
                    assertThat(id.next()).isTrue(); assertThat(id.getLong(1)).isNotEqualTo(currentConnection);
                }
                update.setString(1, "NEW-SIN"); update.setString(2, "NEW-HIN"); update.setInt(3, patientId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            assertThat(cached.getSin()).isEqualTo("FAKE-SIN");
            api.updateDemographicData(payload("Changed"));
            assertThat(cached.getSin()).isEqualTo("NEW-SIN");
        } finally {
            TransactionSynchronizationManager.unbindResource(factory);
            context.close();
        }
        assertThat(jdbc.queryForMap("select sin, hin from demographic where demographic_no=?", patientId))
                .containsEntry("sin", "NEW-SIN").containsEntry("hin", "NEW-HIN");
    }

    @Test
    void shouldReportConflict_withoutOverwritingAnOlderTransactionSnapshot() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Demographic cached = patients.getDemographicById(patientId);
            assertThat(cached.getSin()).isEqualTo("FAKE-SIN");
            try (var connection = source.getConnection();
                 var update = connection.prepareStatement("update demographic set sin=?, hin=? where demographic_no=?")) {
                assertThat(connection.getAutoCommit()).isTrue();
                update.setString(1, "NEW-SIN"); update.setString(2, "NEW-HIN"); update.setInt(3, patientId);
                assertThat(update.executeUpdate()).isEqualTo(1);
            } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
            api.updateDemographicData(payload("Changed"));
        })).isInstanceOfSatisfying(jakarta.ws.rs.WebApplicationException.class, failure ->
                assertThat(failure.getResponse().getStatus()).isEqualTo(409));
        assertThat(jdbc.queryForMap("select sin, hin, first_name from demographic where demographic_no=?", patientId))
                .containsEntry("sin", "NEW-SIN").containsEntry("hin", "NEW-HIN").containsEntry("first_name", "Original");
    }

    @Test
    void shouldRollbackTheNativeWrite_whenALaterFailureOccurs() {
        var data = payload("fail");
        assertThatThrownBy(() -> api.updateDemographicData(data)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForMap("select sin, hin, first_name from demographic where demographic_no=?", patientId))
                .containsEntry("sin", "FAKE-SIN").containsEntry("hin", "FAKE-HIN").containsEntry("first_name", "Original");
    }

    @Test
    void shouldRetainExplicitEmptyStringClears_inTheNativeTransaction() {
        var data = payload("Changed"); data.setSin(""); data.setHin("");
        api.updateDemographicData(data);
        assertThat(jdbc.queryForMap("select sin, hin from demographic where demographic_no=?", patientId))
                .containsEntry("sin", "").containsEntry("hin", "");
    }
}
