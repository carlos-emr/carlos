/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.log;

import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

/** Authoritative Rx audits bypass the async executor and commit in the clinical transaction. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = RxStrictAuditIntegrationTest.Config.class)
@Tag("integration")
@Tag("mariadb-native")
class RxStrictAuditIntegrationTest extends CarlosUnitTestBase {
    @Autowired private OscarLogDao logDao;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext private EntityManager entityManager;

    @AfterEach
    void restoreAuditDependencies() {
        LogAction.resetExecutorServiceForTesting();
        LogAction.setOscarLogDaoForTesting(null);
    }

    @Test
    void shouldCommitAndRollbackAuthoritativeAudit_withClinicalTransaction() throws Exception {
        // The unit base deliberately silences audits; this regression must execute
        // the real logger and verify the physical row instead.
        logActionMock.close();
        logActionMock = null;
        registerMock(PlatformTransactionManager.class, transactions);
        registerMock(io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO.class,
                org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO.class));
        LogAction.setOscarLogDaoForTesting(logDao);
        var transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            LogAction.addLogStrict("999998", "add", "prescription", "synthetic-commit", "127.0.0.1", "1001", "synthetic");
            status.flush();
        });
        Long count = transaction.execute(status -> entityManager.createQuery(
                "select count(l) from OscarLog l where l.contentId = :id", Long.class)
                .setParameter("id", "synthetic-commit").getSingleResult());
        assertThat(count).isEqualTo(1L);
        transaction.executeWithoutResult(status -> {
            LogAction.addLogStrict("999998", "add", "prescription", "synthetic-rollback", "127.0.0.1", "1001", "synthetic");
            status.flush();
            status.setRollbackOnly();
        });
        Long rolledBack = transaction.execute(status -> entityManager.createQuery(
                "select count(l) from OscarLog l where l.contentId = :id", Long.class)
                .setParameter("id", "synthetic-rollback").getSingleResult());
        assertThat(rolledBack).isZero();
        var brokenDao = org.mockito.Mockito.mock(OscarLogDao.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic audit failure")).when(brokenDao).persist(org.mockito.ArgumentMatchers.any());
        LogAction.setOscarLogDaoForTesting(brokenDao);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                LogAction.addLogStrict("999998", "add", "prescription", "synthetic-failure", "127.0.0.1", "1001", "synthetic")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase database() throws Exception {
            return new IsolatedJpaTestDatabase("rx_audit_fallback");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase database) { return database.dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource,
                IsolatedJpaTestDatabase database) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(OscarLog.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(database.jpaProperties());
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean OscarLogDao oscarLogDao() { return new OscarLogDaoImpl(); }
    }
}
