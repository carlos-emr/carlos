/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicMergedDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual production DAOs, annotations, commits, server faults and contending connections. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = DemographicMergeMariaDbIntegrationTest.Config.class)
@Tag("integration")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class DemographicMergeMariaDbIntegrationTest {
    @Autowired private DataSource dataSource;
    @Autowired private DemographicDao patients;
    @Autowired private DemographicMergedDao merges;
    @Autowired private OscarLogDao auditLogs;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager em;
    private DemographicManager manager;
    private LoggedInInfo actor;
    private int parent;
    private int otherParent;
    private int first;
    private int second;
    private Object previousAuditDao;

    @BeforeEach
    void prepareParticipants() throws Exception {
        previousAuditDao = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", auditLogs);
        if (!"flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"))) {
            // Demographic's read-only formulas refer to these legacy lookup tables.
            // The Flyway lane uses the shipped definitions without alteration.
            try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE IF NOT EXISTS admission(client_id INT, admission_status VARCHAR(20), program_id INT)");
                sql.execute("CREATE TABLE IF NOT EXISTS program(id INT, type VARCHAR(20))");
                sql.execute("CREATE TABLE IF NOT EXISTS health_safety(demographic_no INT)");
                sql.execute("CREATE TABLE IF NOT EXISTS lst_gender(code VARCHAR(10), description VARCHAR(100))");
            }
        }
        actor = mock(LoggedInInfo.class);
        var target = new DemographicManagerImpl();
        ReflectionTestUtils.setField(target, "demographicDao", patients);
        ReflectionTestUtils.setField(target, "demographicMergedDao", merges);
        var proxy = new ProxyFactory(target);
        proxy.setInterfaces(DemographicManager.class);
        // Read the production annotations: deleting @Transactional must break the rollback proof.
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        manager = (DemographicManager) proxy.getProxy();
        var ids = new TransactionTemplate(transactions).execute(status -> {
            var rows = new java.util.ArrayList<Integer>();
            for (int i = 0; i < 4; i++) {
                var patient = new Demographic();
                patient.setFirstName("Synthetic"); patient.setLastName("Merge"); patient.setPrefName(""); patient.setSex("M");
                em.persist(patient);
                rows.add(patient.getDemographicNo());
            }
            return rows;
        });
        parent = ids.get(0); otherParent = ids.get(1); first = ids.get(2); second = ids.get(3);
    }

    private long active(int child) throws Exception {
        // A different physical connection observes only committed state.
        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM demographic_merged WHERE demographic_no=? AND deleted=0")) {
            query.setInt(1, child);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private long audits(String action) throws Exception {
        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM log WHERE action=?")) {
            query.setString(1, action);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private long patientAudits(String action, int child) throws Exception {
        try (var connection = dataSource.getConnection();
             var query = connection.prepareStatement("SELECT COUNT(*) FROM log WHERE action=? AND demographic_no=?")) {
            query.setString(1, action);
            query.setInt(2, child);
            try (var rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    @Test
    void shouldPersistMergeAndUnmergeAudits_whenObservedAfterCommit() throws Exception {
        String merge = "DemographicManager.mergeDemographics";
        String unmerge = "DemographicManager.unmergeDemographics";
        long beforeMerge = audits(merge);
        long beforeUnmerge = audits(unmerge);
        manager.mergeDemographics(actor, parent, List.of(first));
        assertThat(active(first)).isEqualTo(1);
        assertThat(audits(merge)).isEqualTo(beforeMerge + 1);
        assertThat(audits(unmerge)).isEqualTo(beforeUnmerge);
        assertThat(patientAudits(merge, first)).isEqualTo(1);
        assertThat(patientAudits(merge, parent)).isZero();
        manager.unmergeDemographics(actor, parent, List.of(first));
        assertThat(active(first)).isZero();
        assertThat(audits(merge)).isEqualTo(beforeMerge + 1);
        assertThat(audits(unmerge)).isEqualTo(beforeUnmerge + 1);
        assertThat(patientAudits(unmerge, first)).isEqualTo(1);
        assertThat(patientAudits(unmerge, parent)).isZero();
    }

    @AfterEach
    void restoreAuditDao() {
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", previousAuditDao);
    }

    private void fault(String event, String condition) throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER carlos_merge_late_fault BEFORE " + event + " ON demographic_merged FOR EACH ROW "
                    + "BEGIN IF NEW.demographic_no=" + second + " " + condition + " THEN "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Synthetic late merge failure'; END IF; END");
        }
    }

    private void removeFault() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS carlos_merge_late_fault");
        }
    }

    @Test
    void shouldRollbackEveryMergeAndAudit_whenSecondInsertFails() throws Exception {
        long beforeAudit = audits("DemographicManager.mergeDemographics");
        fault("INSERT", "");
        try {
            assertThatThrownBy(() -> manager.mergeDemographics(actor, parent, List.of(first, second)))
                    .rootCause().hasMessageContaining("Synthetic late merge failure");
            assertThat(active(first)).isZero(); assertThat(active(second)).isZero();
            assertThat(audits("DemographicManager.mergeDemographics")).isEqualTo(beforeAudit);
        } finally { removeFault(); }
        manager.mergeDemographics(actor, parent, List.of(first, second));
        assertThat(active(first)).isEqualTo(1); assertThat(active(second)).isEqualTo(1);
    }

    @Test
    void shouldRollbackEveryUnmerge_whenSecondUpdateFails() throws Exception {
        manager.mergeDemographics(actor, parent, List.of(first, second));
        long beforeAudit = audits("DemographicManager.unmergeDemographics");
        fault("UPDATE", "AND NEW.deleted=1");
        try {
            assertThatThrownBy(() -> manager.unmergeDemographics(actor, parent, List.of(first, second)))
                    .rootCause().hasMessageContaining("Synthetic late merge failure");
            assertThat(active(first)).isEqualTo(1); assertThat(active(second)).isEqualTo(1);
            assertThat(audits("DemographicManager.unmergeDemographics")).isEqualTo(beforeAudit);
        } finally { removeFault(); }
        manager.unmergeDemographics(actor, parent, List.of(first, second));
        assertThat(active(first)).isZero(); assertThat(active(second)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldCommitOnlyOneAcyclicMerge_whenRequestsContend(boolean reciprocal) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Boolean> one = () -> contend(barrier, parent, first);
            java.util.concurrent.Callable<Boolean> two = () -> contend(barrier, reciprocal ? first : otherParent,
                    reciprocal ? parent : first);
            var a = workers.submit(one); var b = workers.submit(two);
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(active(first) + (reciprocal ? active(parent) : 0)).isEqualTo(1);
    }

    private boolean contend(CyclicBarrier barrier, int destination, int child) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            manager.mergeDemographics(actor, destination, List.of(child));
            return true;
        } catch (IllegalArgumentException refused) {
            return false;
        }
    }

    @Test
    void shouldKeepOneLink_whenARequestRepeatsTheSameChild() throws Exception {
        manager.mergeDemographics(actor, parent, List.of(first, first));
        assertThat(active(first)).isEqualTo(1);
        assertThatThrownBy(() -> manager.mergeDemographics(actor, first, List.of(parent)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(active(parent)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRollbackEveryChangeAndAudit_whenSecondAuditFails(boolean unmerge) throws Exception {
        if (unmerge) manager.mergeDemographics(actor, parent, List.of(first, second));
        String operation = unmerge ? "DemographicManager.unmergeDemographics" : "DemographicManager.mergeDemographics";
        long beforeAudit = audits(operation);
        var failingDao = mock(OscarLogDao.class);
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(invocation -> {
            if (writes.incrementAndGet() == 2) throw new IllegalStateException("Synthetic audit failure");
            auditLogs.persist(invocation.getArgument(0));
            return null;
        }).when(failingDao).persist(any(OscarLog.class));
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", failingDao);
        assertThatThrownBy(() -> {
            if (unmerge) manager.unmergeDemographics(actor, parent, List.of(first, second));
            else manager.mergeDemographics(actor, parent, List.of(first, second));
        }).isInstanceOf(IllegalStateException.class).hasMessage("Synthetic audit failure");
        assertThat(writes.get()).isEqualTo(2);
        assertThat(active(first)).isEqualTo(unmerge ? 1 : 0);
        assertThat(active(second)).isEqualTo(unmerge ? 1 : 0);
        assertThat(audits(operation)).isEqualTo(beforeAudit);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {
            return new IsolatedJpaTestDatabase("patient_merge");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase database) { return database.dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, IsolatedJpaTestDatabase database) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(Demographic.class.getName(), DemographicMerged.class.getName(),
                    io.github.carlos_emr.carlos.commn.model.Provider.class.getName(), OscarLog.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(database.jpaProperties());
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean DemographicDao demographicDao() { return new DemographicDaoImpl(); }
        @Bean DemographicMergedDao demographicMergedDao() { return new DemographicMergedDaoImpl(); }
        @Bean OscarLogDao auditLogDao() { return new OscarLogDaoImpl(); }
    }
}
