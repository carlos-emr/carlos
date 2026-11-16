/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicSets;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.*;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.util.List;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/** Real resource transaction, demographic enrichment and synchronous audit sink on physical MariaDB. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ReportingPatientListAuditMariaDbIntegrationTest.Config.class)
@Tag("integration")
@Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class ReportingPatientListAuditMariaDbIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    private ReportingService resource;
    private DemographicDao patients;
    private Demographic patient;
    private Object priorAudit;
    private MockedStatic<SpringUtils> spring;

    @BeforeEach void setup() {
        new JdbcTemplate(dataSource).update("DELETE FROM log");
        var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        var audit = new OscarLogDaoImpl();
        ReflectionTestUtils.setField(audit, "entityManager", em);
        priorAudit = ReflectionTestUtils.getField(LogAction.class, "oscarLogDao");
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", audit);

        var actor = mock(LoggedInInfo.class);
        when(actor.getLoggedInProviderNo()).thenReturn("FAKE");
        when(actor.getLoggedInProvider()).thenReturn(new Provider("FAKE", "Fixture", "doctor", "F", "", "Clinician"));
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(actor), anyString(), eq("r"), nullable(String.class))).thenReturn(true);
        when(security.hasPrivilege(eq(actor), eq("_demographic"), eq("r"), anyInt())).thenReturn(true);
        when(security.hasPrivilegeReadOnly(eq(actor), anyString(), eq("r"), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(actor), anyInt())).thenReturn(true);
        spring = mockStatic(SpringUtils.class);
        spring.when(() -> SpringUtils.getBean(ProfessionalSpecialistDao.class)).thenReturn(mock(ProfessionalSpecialistDao.class));
        spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);

        patient = new Demographic(); patient.setDemographicNo(42);
        patient.setFirstName("Fixture"); patient.setLastName("Patient"); patient.setProviderNo("");
        patients = mock(DemographicDao.class);
        when(patients.getDemographicById(42)).thenReturn(patient);
        var demographics = new DemographicManagerImpl();
        ReflectionTestUtils.setField(demographics, "demographicDao", patients);
        ReflectionTestUtils.setField(demographics, "demographicContactDao", mock(DemographicContactDao.class));
        ReflectionTestUtils.setField(demographics, "securityInfoManager", security);
        ReflectionTestUtils.setField(demographics, "appointmentManager", mock(AppointmentManager.class));
        ReflectionTestUtils.setField(demographics, "providerManager", mock(ProviderManager2.class));

        var sets = mock(DemographicSetsManager.class);
        when(sets.getByNamePage(actor, "fixture", 0, 500)).thenAnswer(call -> {
            em.unwrap(Session.class).doWork(connection -> {
                assertThat(connection.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
                assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
            });
            return List.of(member(1, 42), member(2, 43));
        });
        var api = new ReportingService() {
            @Override protected LoggedInInfo getLoggedInInfo() { return actor; }
        };
        api.securityInfoManager = security;
        api.demographicSetsManager = sets;
        api.demographicManager = demographics;
        var proxy = new ProxyFactory(api);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        resource = (ReportingService) proxy.getProxy();
    }

    @AfterEach void restoreAudit() {
        ReflectionTestUtils.setField(LogAction.class, "oscarLogDao", priorAudit);
        if (spring != null) spring.close();
    }

    private static DemographicSets member(int id, int patient) {
        var row = new DemographicSets(); row.setId(id); row.setDemographicNo(patient); return row;
    }

    private int audits() {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM log WHERE action='DemographicManager.getHealthCareTeam'", Integer.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "MISSING"})
    void shouldCommitPatientNameAudit_withAnUnassignedOrMissingProvider(String provider) throws Exception {
        patient.setProviderNo(provider);
        var response = resource.getAsPatientList(new ObjectMapper().readTree("{\"name\":\"fixture\"}"));
        assertThat(response.getPatients()).hasSize(2);
        assertThat(response.getPatients().getFirst().getName()).isEqualTo("Patient, Fixture");
        assertThat(audits()).isEqualTo(1);
    }

    @Test void shouldRollbackEarlierNameAudits_whenLaterPatientEnrichmentFails() throws Exception {
        when(patients.getDemographicById(43)).thenThrow(new IllegalStateException("Synthetic lookup failure"));
        assertThatThrownBy(() -> resource.getAsPatientList(new ObjectMapper().readTree("{\"name\":\"fixture\"}")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(audits()).isZero();
    }

    @Test void shouldRollbackEarlierNameAudits_whenLaterCheckedFailureEscapes() throws Exception {
        when(patients.getDemographicById(43)).thenAnswer(call -> { throw new java.io.IOException("Synthetic checked failure"); });
        assertThatThrownBy(() -> resource.getAsPatientList(new ObjectMapper().readTree("{\"name\":\"fixture\"}")))
                .isInstanceOf(java.lang.reflect.UndeclaredThrowableException.class)
                .hasRootCauseInstanceOf(java.io.IOException.class);
        assertThat(audits()).isZero();
    }

    @Configuration static class Config {
        @Bean(destroyMethod = "close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {
            return new IsolatedJpaTestDatabase("report_name_audit");
        }
        @Bean DataSource dataSource(IsolatedJpaTestDatabase db) { return db.dataSource(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source, IsolatedJpaTestDatabase db) {
            var bean = new LocalContainerEntityManagerFactoryBean(); bean.setDataSource(source);
            bean.setManagedTypes(PersistenceManagedTypes.of(OscarLog.class.getName()));
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter()); bean.setJpaPropertyMap(db.jpaProperties()); return bean;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
    }
}
