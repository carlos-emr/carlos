/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.Prescription;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/** Actual Rx transaction, medication/header DAOs and physical commit outcomes; text rendering is isolated. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=RxPrescribeMariaDbIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named="CARLOS_TEST_MARIADB_DAO",matches="true")
class RxPrescribeMariaDbIntegrationTest {
    @Autowired CommitFaultDataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    private DrugDaoImpl drugs;
    private PrescriptionDaoImpl headers;
    private RxManager manager;
    private LoggedInInfo actor;
    private JdbcTemplate jdbc;
    private MockedStatic<LogAction> audit;
    @BeforeEach void setup() {
        dataSource.mode.set(0);jdbc=new JdbcTemplate(dataSource);jdbc.update("DELETE FROM drugs");jdbc.update("DELETE FROM prescription");
        var em=SharedEntityManagerCreator.createSharedEntityManager(factory);drugs=spy(new DrugDaoImpl());headers=new PrescriptionDaoImpl();
        ReflectionTestUtils.setField(drugs,"entityManager",em);ReflectionTestUtils.setField(headers,"entityManager",em);
        var target=new RxManagerImpl();target.drugDao=drugs;target.securityInfoManager=mock(SecurityInfoManager.class);
        when(target.securityInfoManager.hasPrivilege(any(),eq("_rx"),eq("w"),eq(42))).thenReturn(true);
        // Bypass only legacy prescription text rendering; the actual header DAO joins the same transaction.
        target.prescriptionManager=mock(PrescriptionManager.class);
        when(target.prescriptionManager.createNewPrescription(any(),anyList(),eq(42))).thenAnswer(call -> {
            var row=new Prescription();row.setDemographicId(42);row.setProviderNo("FAKE");row.setDatePrescribed(new Date());row.setTextView("Synthetic prescription");
            headers.persist(row);return row;
        });
        var proxy=new ProxyFactory(target);proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        manager=(RxManager)proxy.getProxy();actor=mock(LoggedInInfo.class);when(actor.getLoggedInProviderNo()).thenReturn("FAKE");audit=mockStatic(LogAction.class);
    }
    @AfterEach void closeAudit() {dataSource.mode.set(0);if(audit!=null)audit.close();}
    private static Drug drug(String name) {
        var row=new Drug();row.setDemographicId(42);row.setProviderNo("FAKE");row.setGenericName(name);row.setBrandName("Synthetic");
        row.setPosition(1);row.setSpecial("Synthetic dose");row.setRxDate(new Date(1_000_000_000_000L));row.setEndDate(new Date(1_100_000_000_000L));return row;
    }
    private int count(String table) { // Table choice is fixed by these tests, not supplied by a request.
        return jdbc.queryForObject(switch(table){case "drugs" -> "SELECT COUNT(*) FROM drugs";case "prescription" -> "SELECT COUNT(*) FROM prescription";default -> throw new IllegalArgumentException();},Integer.class);
    }
    @Test void shouldCommitPrescriptionAndDrugs_whenNativeWritesSucceed() {
        var result=manager.prescribe(actor,List.of(drug("first"),drug("last")),42);
        assertThat(result.drugs).hasSize(2);assertThat(count("prescription")).isEqualTo(1);assertThat(count("drugs")).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT script_no FROM drugs",Integer.class)).containsOnly(result.prescription.getId());
    }
    @Test void shouldRollbackHeaderEarlierDrugsAndArchive_whenALaterNativeWriteFails() {
        Drug source=drug("existing");new TransactionTemplate(transactions).executeWithoutResult(status -> assertThat(drugs.addNewDrug(source)).isTrue());
        int original=source.getId();
        doAnswer(call -> {Drug row=call.getArgument(0);return "late".equals(row.getGenericName())?false:call.callRealMethod();}).when(drugs).addNewDrug(any());
        assertThatThrownBy(() -> manager.prescribe(actor,List.of(source,drug("late")),42)).isInstanceOf(PrescriptionWriteException.class);
        assertThat(count("prescription")).isZero();assertThat(count("drugs")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT archived FROM drugs WHERE drugid=?",Boolean.class,original)).isFalse();
    }
    @Test void shouldRollbackPhysicalState_whenCommitFailsBeforeSubmission() {
        dataSource.mode.set(1);
        assertThatThrownBy(() -> manager.prescribe(actor,List.of(drug("first"),drug("last")),42)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class).hasRootCauseInstanceOf(SQLException.class);
        assertThat(dataSource.mode.get()).isZero();assertThat(count("prescription")).isZero();assertThat(count("drugs")).isZero();
    }
    @Test void shouldRequireReconciliation_whenCommitSucceedsButAcknowledgementIsLost() {
        dataSource.mode.set(2);
        assertThatThrownBy(() -> manager.prescribe(actor,List.of(drug("first"),drug("last")),42)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class).hasRootCauseInstanceOf(SQLException.class);
        assertThat(dataSource.mode.get()).isZero();assertThat(count("prescription")).isEqualTo(1);assertThat(count("drugs")).isEqualTo(2);
        // The manager does not automatically retry a non-idempotent operation after uncertain commit.
    }
    static final class CommitFaultDataSource extends AbstractDataSource {
        final DataSource delegate;final AtomicInteger mode=new AtomicInteger();
        CommitFaultDataSource(DataSource delegate){this.delegate=delegate;}
        @Override public Connection getConnection() throws SQLException {return wrap(delegate.getConnection());}
        @Override public Connection getConnection(String user,String password) throws SQLException {return wrap(delegate.getConnection(user,password));}
        private Connection wrap(Connection actual) throws SQLException {
            assertThat(actual.getMetaData().getDatabaseProductVersion()).contains("MariaDB");
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args) -> {
                int fault=method.getName().equals("commit")?mode.getAndSet(0):0;
                if(fault==1)throw new SQLException("Synthetic missing commit acknowledgement","08006");
                Object result;try {result=method.invoke(actual,args);}catch(InvocationTargetException failed){throw failed.getCause();}
                if(fault==2)throw new SQLException("Synthetic missing commit acknowledgement","08006");return result;
            });
        }
    }
    @Configuration static class Config {
        @Bean(destroyMethod="close") IsolatedJpaTestDatabase ownedDatabase() throws Exception {return new IsolatedJpaTestDatabase("rx_prescribe");}
        @Bean CommitFaultDataSource dataSource(IsolatedJpaTestDatabase database) {return new CommitFaultDataSource(database.dataSource());}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(CommitFaultDataSource source,IsolatedJpaTestDatabase database) {
            var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(source);bean.setManagedTypes(PersistenceManagedTypes.of(Drug.class.getName(),Prescription.class.getName()));
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());bean.setJpaPropertyMap(database.jpaProperties());return bean;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
    }
}
