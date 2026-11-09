/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.TicklerWebService;
import jakarta.persistence.*;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.transaction.support.TransactionTemplate;

/** Real REST/manager transaction proxies and Tickler queries; audit/converter lookup sinks are isolated. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=TicklerPatientAccessMariaDbIntegrationTest.Config.class)
@Tag("integration") @Tag("mariadb-native")
@EnabledIfEnvironmentVariable(named="CARLOS_TEST_MARIADB_DAO",matches="true")
class TicklerPatientAccessMariaDbIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired EntityManagerFactory factory;
    @Autowired PlatformTransactionManager transactions;
    private EntityManager em;
    private TicklerDaoImpl rows;
    private TicklerManager manager;
    private TicklerWebService resource;
    private SecurityInfoManager security;
    private LoggedInInfo actor;
    private JdbcTemplate jdbc;
    private MockedStatic<LogAction> audit;
    private MockedStatic<SpringUtils> spring;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(dataSource);
        if (!"flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"))) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS admission(client_id INT, admission_status VARCHAR(20), program_id INT)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS health_safety(demographic_no INT)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS lst_gender(code VARCHAR(10), description VARCHAR(100))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS demographic_merged(demographic_no INT, deleted INT, merged_to INT)");
            jdbc.execute("CREATE TABLE IF NOT EXISTS program_queue(program_id INT, status VARCHAR(20))");
            jdbc.execute("CREATE TABLE IF NOT EXISTS Facility(id INT, name VARCHAR(255))");
        }
        jdbc.update("DELETE FROM tickler_update");jdbc.update("DELETE FROM tickler_comments");jdbc.update("DELETE FROM tickler");
        jdbc.update("DELETE FROM demographic WHERE demographic_no IN (42,43)");jdbc.update("DELETE FROM provider WHERE provider_no='FAKE'");
        jdbc.update("INSERT INTO demographic (demographic_no,first_name,last_name) VALUES (42,'Fixture','Patient'),(43,'Blocked','Patient')");
        jdbc.update("INSERT INTO provider (provider_no,first_name,last_name,provider_type) VALUES ('FAKE','Fixture','Clinician','doctor')");
        em=SharedEntityManagerCreator.createSharedEntityManager(factory);rows=spy(new TicklerDaoImpl());
        var updates=new TicklerUpdateDaoImpl();var comments=new TicklerCommentDaoImpl();
        for(Object dao:List.of(rows,updates,comments)) ReflectionTestUtils.setField(dao,"entityManager",em);
        security=mock(SecurityInfoManager.class);actor=mock(LoggedInInfo.class);when(actor.getLoggedInProviderNo()).thenReturn("FAKE");
        when(security.hasPrivilege(eq(actor),eq("_tickler"),anyString(),isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(actor),eq("_tickler"),anyString(),eq("42"))).thenReturn(true);
        when(security.hasPrivilegeReadOnly(actor,"_tickler","r","42")).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(actor,42)).thenReturn(true);
        var target=new TicklerManagerImpl();ReflectionTestUtils.setField(target,"ticklerDao",rows);
        ReflectionTestUtils.setField(target,"ticklerUpdateDao",updates);ReflectionTestUtils.setField(target,"ticklerCommentDao",comments);
        ReflectionTestUtils.setField(target,"securityInfoManager",security);manager=proxy(target,TicklerManager.class);
        var api=new TicklerWebService(){@Override protected LoggedInInfo getLoggedInInfo(){return actor;}};
        ReflectionTestUtils.setField(api,"ticklerManager",manager);ReflectionTestUtils.setField(api,"securityInfoManager",security);
        resource=proxy(api,TicklerWebService.class);
        audit=mockStatic(LogAction.class);spring=mockStatic(SpringUtils.class);
        var patients=mock(DemographicDao.class);var patient=new Demographic();patient.setDemographicNo(42);patient.setFirstName("Fixture");patient.setLastName("Patient");
        when(patients.getDemographicById(42)).thenReturn(patient);
        spring.when(() -> SpringUtils.getBean(DemographicDao.class)).thenReturn(patients);
        var providers=mock(ProviderDao.class);spring.when(() -> SpringUtils.getBean(ProviderDao.class)).thenReturn(providers);
        var documents=mock(TicklerDocsDao.class);spring.when(() -> SpringUtils.getBean(TicklerDocsDao.class)).thenReturn(documents);
        var programs=mock(ProgramDao.class);spring.when(() -> SpringUtils.getBean(ProgramDao.class)).thenReturn(programs);
    }
    @AfterEach void closeMocks(){if(spring!=null)spring.close();if(audit!=null)audit.close();}
    private <T>T proxy(Object target,Class<T> type){var p=new ProxyFactory(target);p.setProxyTargetClass(true);p.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));return type.cast(p.getProxy());}
    private void seed(int id,int patient) throws java.sql.SQLException {
        // Deliberately independent, including when called during the resource's read snapshot.
        try(var connection=dataSource.getConnection();var sql=connection.prepareStatement(
                "INSERT INTO tickler (tickler_no,demographic_no,message,status,priority,creator,task_assigned_to,service_date,creation_date,update_date) VALUES (?,?,'Synthetic clinical text','A','Normal','FAKE','FAKE','2001-09-09','2001-09-09','2001-09-09')")) {
            assertThat(connection.getAutoCommit()).isTrue();sql.setInt(1,id);sql.setInt(2,patient);sql.executeUpdate();
        }
    }
    private com.fasterxml.jackson.databind.JsonNode body(String json)throws Exception{return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);}
    private String status(int id){return jdbc.queryForObject("SELECT status FROM tickler WHERE tickler_no=?",String.class,id);}
    @Test void shouldPageAndCountOnlyPermittedRows_afterNativeDeniedBatches()throws Exception{
        seed(1,42);seed(2,42);for(int id=3;id<=502;id++)seed(id,43);
        doAnswer(call->{assertThat(em.unwrap(Session.class).doReturningWork(Connection::getTransactionIsolation)).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);assertThat((Integer)call.getArgument(2)).isEqualTo(500);return call.callRealMethod();})
                .when(rows).getTicklers(any(),anyInt(),anyInt(),anyBoolean(),anyBoolean(),anyBoolean(),anyBoolean());
        var result=resource.search(body("{}"),1,1);assertThat(result.getTotal()).isEqualTo(2);assertThat(result.getContent()).extracting(t -> t.getId()).containsExactly(1);
    }
    @Test void shouldRetainUpdateOnlyAuthority_withoutAddingReadPermission()throws Exception{
        seed(1,42);when(security.hasPrivilege(actor,"_tickler","r",(String)null)).thenReturn(false);
        assertThat(resource.completeTicklers(body("{\"ticklers\":[1]}"))).isNotNull();assertThat(status(1)).isEqualTo("C");
        verify(security,never()).hasPrivilege(actor,"_tickler","r",(String)null);
    }
    @Test void shouldRollbackEarlierNativeWrites_whenALaterMutationFails()throws Exception{
        seed(1,42);seed(2,42);
        doAnswer(call->{Tickler row=call.getArgument(0);if(row.getId()==2)throw new PersistenceException("Synthetic late write failure");return call.callRealMethod();}).when(rows).merge(any());
        assertThatThrownBy(() -> resource.completeTicklers(body("{\"ticklers\":[1,2]}"))).isInstanceOf(PersistenceException.class);
        assertThat(status(1)).isEqualTo("A");assertThat(status(2)).isEqualTo("A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickler_update",Integer.class)).isZero();
    }
    @Test void shouldDenyTheCurrentStoredPatient_beforeAnyNativeMutation()throws Exception{
        seed(1,42);jdbc.update("UPDATE tickler SET demographic_no=43 WHERE tickler_no=1");
        assertThatThrownBy(() -> resource.completeTicklers(body("{\"ticklers\":[1]}"))).isInstanceOf(io.github.carlos_emr.carlos.commn.exception.AccessDeniedException.class);
        assertThat(status(1)).isEqualTo("A");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickler_update",Integer.class)).isZero();
    }
    @Test void shouldAuthorizeTheStoredAndRequestedPatients_beforeFlushingAManagedEdit() throws Exception {
        seed(1,42);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Tickler candidate=rows.find(1);candidate.setDemographicNo(43);candidate.setMessage("Changed synthetic text");
            manager.updateTickler(actor,candidate);
        })).isInstanceOf(io.github.carlos_emr.carlos.commn.exception.AccessDeniedException.class);
        var order=inOrder(security);
        order.verify(security).hasPrivilege(actor,"_tickler","u","42");
        order.verify(security).hasPrivilege(actor,"_tickler","u","43");
        assertThat(jdbc.queryForObject("SELECT demographic_no FROM tickler WHERE tickler_no=1",Integer.class)).isEqualTo(42);
        assertThat(jdbc.queryForObject("SELECT message FROM tickler WHERE tickler_no=1",String.class)).isEqualTo("Synthetic clinical text");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickler_update",Integer.class)).isZero();
    }
    @Test void shouldRollbackManagedFieldChanges_whenValidationReturnsFalse() throws Exception {
        seed(1,42);
        var result=resource.updateTickler(body("{\"id\":1,\"taskAssignedTo\":\"\",\"status\":\"A\",\"priority\":\"Normal\",\"message\":\"Changed synthetic text\",\"serviceDate\":\"2001-09-09T09:00:00Z\"}"));
        assertThat(result.getStatus().toString()).isEqualTo("ERROR");
        assertThat(jdbc.queryForObject("SELECT message FROM tickler WHERE tickler_no=1",String.class)).isEqualTo("Synthetic clinical text");
        assertThat(status(1)).isEqualTo("A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tickler_update",Integer.class)).isZero();
    }
    @Test void shouldHoldThePhysicalPatientLock_untilTheMutationTransactionCommits() throws Exception {
        seed(1,42);var attempted=new CountDownLatch(1);
        var writerId=new java.util.concurrent.atomic.AtomicLong();var pending=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
        try(var executor=Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                assertThat(manager.getTicklerDemographicNoForUpdate(actor,1)).isEqualTo(42);
                pending.set(executor.submit(() -> {
                    try(var connection=dataSource.getConnection();var sql=connection.createStatement();var result=sql.executeQuery("SELECT CONNECTION_ID()")) {
                        result.next();writerId.set(result.getLong(1));attempted.countDown();
                        return sql.executeUpdate("UPDATE tickler SET demographic_no=43 WHERE tickler_no=1");
                    }
                }));
                try {
                    assertThat(attempted.await(5,TimeUnit.SECONDS)).isTrue();awaitPhysicalLockWait(writerId.get());
                    assertThatThrownBy(() -> pending.get().get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } catch(Exception failed) {throw new IllegalStateException(failed);}
                manager.completeTickler(actor,1,"FAKE");
            });
            assertThat(pending.get().get(5,TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(status(1)).isEqualTo("C");
        assertThat(jdbc.queryForObject("SELECT demographic_no FROM tickler WHERE tickler_no=1",Integer.class)).isEqualTo(43);
    }
    private void awaitPhysicalLockWait(long writer) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        try(var connection=dataSource.getConnection();var query=connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w JOIN information_schema.INNODB_TRX t ON w.requesting_trx_id=t.trx_id WHERE t.trx_mysql_thread_id=?")) {
            query.setLong(1,writer);
            do {try(var result=query.executeQuery()) {result.next();if(result.getInt(1)>0)return;}Thread.sleep(20);} while(System.nanoTime()<deadline);
        }
        throw new AssertionError("The independent native writer did not wait on the held patient lock");
    }
    @Test void shouldKeepDiscoveryRowsAndTotals_inOneNativeReadSnapshot()throws Exception{
        seed(1,42);seed(2,42);for(int id=3;id<=502;id++)seed(id,43);var inserted=new AtomicBoolean();
        when(security.hasPrivilegeReadOnly(actor,"_tickler","r","43")).thenAnswer(call->{if(inserted.compareAndSet(false,true))seed(503,42);return false;});
        assertThat(resource.search(body("{}"),0,1).getTotal()).isEqualTo(2);
        assertThat(resource.search(body("{}"),0,1).getTotal()).isEqualTo(3);
    }
    @Configuration static class Config{
        @Bean(destroyMethod="close")IsolatedJpaTestDatabase ownedDatabase()throws Exception{return new IsolatedJpaTestDatabase("tickler_access");}
        @Bean DataSource dataSource(IsolatedJpaTestDatabase db){return db.dataSource();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source,IsolatedJpaTestDatabase db){
            var bean=new LocalContainerEntityManagerFactoryBean();bean.setDataSource(source);bean.setManagedTypes(PersistenceManagedTypes.of(Tickler.class.getName(),TicklerUpdate.class.getName(),TicklerComment.class.getName(),TicklerCategory.class.getName(),Demographic.class.getName(),DemographicMerged.class.getName(),io.github.carlos_emr.carlos.commn.model.Provider.class.getName(),Program.class.getName()));
            bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());bean.setJpaPropertyMap(db.jpaProperties());return bean;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory){return new JpaTransactionManager(factory);}
    }
}
