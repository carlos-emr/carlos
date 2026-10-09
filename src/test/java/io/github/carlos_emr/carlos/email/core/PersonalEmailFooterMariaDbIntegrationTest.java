/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.email.core;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** Uses production personal service/DAO on its own generated schema; no schema migration changes. */
@Tag("integration") @Isolated @TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="EMAIL_TEST_DB_URL",matches=".+")
class PersonalEmailFooterMariaDbIntegrationTest {
    private final String schema="email_personal_test_"+UUID.randomUUID().toString().replace("-","");
    private Connection admin; private SessionFactory factory; private JpaTransactionManager manager;
    private jakarta.persistence.EntityManager entities; private UserPropertyDAO dao;
    private EmailFooterService service; private boolean created;
    @BeforeAll void setup()throws Exception {
        String url=System.getenv("EMAIL_TEST_DB_URL");assertThat(url).matches("jdbc:mysql://[^/]+/");
        String user=System.getenv("EMAIL_TEST_DB_USER"),password=System.getenv("EMAIL_TEST_DB_PASSWORD");
        admin=DriverManager.getConnection(url,user,password);
        try(var stmt=admin.createStatement();var rows=stmt.executeQuery("SELECT VERSION()")){
            assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).containsIgnoringCase("MariaDB");
        }
        try(var stmt=admin.createStatement()){
            stmt.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");created=true;
        }
        factory=new Configuration().addAnnotatedClass(Provider.class).addAnnotatedClass(UserProperty.class)
                .setProperty("hibernate.connection.driver_class","com.mysql.cj.jdbc.Driver")
                .setProperty("hibernate.connection.url",url+schema)
                .setProperty("hibernate.connection.username",user).setProperty("hibernate.connection.password",password)
                .setProperty("hibernate.dialect",io.github.carlos_emr.carlos.util.persistence.OscarMySQL5Dialect.class.getName())
                .setProperty("hibernate.connection.handling_mode","DELAYED_ACQUISITION_AND_HOLD")
                .setProperty("hibernate.hbm2ddl.auto","create").setProperty("hibernate.show_sql","false")
                .buildSessionFactory();
        entities=SharedEntityManagerCreator.createSharedEntityManager(factory);manager=new JpaTransactionManager(factory);
        manager.setJpaDialect(new org.springframework.orm.jpa.vendor.HibernateJpaDialect());
        var target=new UserPropertyDAOImpl();ReflectionTestUtils.setField(target,"entityManager",entities);
        dao=proxy(target,UserPropertyDAO.class);service=proxy(new EmailFooterService(dao),EmailFooterService.class);
    }
    private <T>T proxy(Object target,Class<T> type){
        var p=new ProxyFactory(target);p.setProxyTargetClass(true);
        p.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return type.cast(p.getProxy());
    }
    @AfterAll void cleanup()throws Exception {
        if(factory!=null)factory.close();if(admin!=null){try{
            if(created)try(var s=admin.createStatement()){s.execute("DROP DATABASE `"+schema+"`");}
        }finally{admin.close();}}
    }
    @BeforeEach void reset(){
        new TransactionTemplate(manager).executeWithoutResult(status->{
            entities.createQuery("delete from UserProperty").executeUpdate();entities.createQuery("delete from Provider").executeUpdate();
            entities.persist(new Provider("101"));entities.persist(new Provider("202"));
            var clinic=new UserProperty();clinic.setName("email_footer_clinic_default");clinic.setValue("Mandatory Clinic");entities.persist(clinic);
        });
    }
    private TransactionTemplate tx(int isolation){var t=new TransactionTemplate(manager);t.setIsolationLevel(isolation);return t;}
    @ParameterizedTest @CsvSource({"true,true","true,false","false,true","false,false"})
    void shouldSerializeFirstPersonalSave_afterAmbientEmptyRead_andAvoidDuplicateRows(boolean commitWinner,boolean strictSnapshot)throws Exception {
        var ready=new CountDownLatch(1);var start=new CountDownLatch(1);var saved=new CountDownLatch(1);
        var finish=new CountDownLatch(1);var connection=new AtomicLong();
        try(var workers=Executors.newFixedThreadPool(2)){
            var reader=workers.submit(()->tx(TransactionDefinition.ISOLATION_REPEATABLE_READ).execute(status->{
                setSnapshotIsolation(strictSnapshot);
                assertThat(dao.getAllProperties("email_footer",List.of("101"))).isEmpty();
                entities.unwrap(org.hibernate.Session.class).doWork(c->{try(var s=c.createStatement();var row=s.executeQuery("SELECT CONNECTION_ID()")){
                    row.next();connection.set(row.getLong(1));}});
                ready.countDown();await(start);service.saveOwnFooter("101","Reader personal");return true;
            }));
            if(!ready.await(10,TimeUnit.SECONDS))reader.get(1,TimeUnit.SECONDS);
            var writer=workers.submit(()->tx(TransactionDefinition.ISOLATION_READ_COMMITTED).execute(status->{
                service.saveOwnFooter("101","Writer personal");saved.countDown();await(finish);
                if(!commitWinner)status.setRollbackOnly();return true;
            }));
            try{
                if(!saved.await(10,TimeUnit.SECONDS))writer.get(1,TimeUnit.SECONDS);
                start.countDown();assertThat(waitingForLock(connection.get())).isTrue();assertThat(reader.isDone()).isFalse();
            }finally{finish.countDown();start.countDown();}
            assertThat(writer.get(10,TimeUnit.SECONDS)).isTrue();
            if(commitWinner && strictSnapshot){
                assertThatThrownBy(()->reader.get(10,TimeUnit.SECONDS))
                        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
            }else{assertThat(reader.get(10,TimeUnit.SECONDS)).isTrue();}
        }
        new TransactionTemplate(manager).executeWithoutResult(s->{
            var rows=dao.getAllProperties("email_footer",List.of("101"));assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getValue()).isEqualTo(commitWinner && strictSnapshot?"Writer personal":"Reader personal");
            assertThat(dao.findClinicEmailFooter().get(0).getValue()).isEqualTo("Mandatory Clinic");
        });
    }
    @Test void shouldNotBlockAnotherOwner_andKeepClinicAndOtherPersonalOnClear()throws Exception {
        var locked=new CountDownLatch(1);var finish=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var first=workers.submit(()->new TransactionTemplate(manager).execute(status->{
                service.saveOwnFooter("101","First personal");locked.countDown();await(finish);return true;
            }));
            try{
                if(!locked.await(10,TimeUnit.SECONDS))first.get(1,TimeUnit.SECONDS);
                var second=workers.submit(()->{service.saveOwnFooter("202","Second personal");return true;});
                assertThat(second.get(5,TimeUnit.SECONDS)).isTrue();
            }finally{finish.countDown();}
            assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();
        }
        service.saveOwnFooter("101","");assertThat(service.ownFooter("101")).isEmpty();
        assertThat(service.ownFooter("202")).isEqualTo("Second personal");
        assertThat(new TransactionTemplate(manager).<String>execute(s->dao.findClinicEmailFooter().get(0).getValue()))
                .isEqualTo("Mandatory Clinic");
    }
    @ParameterizedTest @CsvSource({"true","false"})
    void shouldRespectChangedCachedPersonalRow_andStrictConflict(boolean strictSnapshot)throws Exception {
        service.saveOwnFooter("101","Old personal");
        var cached=new CountDownLatch(1);var changed=new CountDownLatch(1);
        try(var worker=Executors.newSingleThreadExecutor()){
            var reader=worker.submit(()->tx(TransactionDefinition.ISOLATION_REPEATABLE_READ).execute(status->{
                setSnapshotIsolation(strictSnapshot);
                assertThat(service.ownFooter("101")).isEqualTo("Old personal");
                cached.countDown();await(changed);service.saveOwnFooter("101","Reader personal");return true;
            }));
            try{
                if(!cached.await(10,TimeUnit.SECONDS)){reader.get(1,TimeUnit.SECONDS);fail("Reader did not reach barrier");}
                service.saveOwnFooter("101","Winner personal");
            }finally{changed.countDown();}
            if(strictSnapshot){assertThatThrownBy(()->reader.get(10,TimeUnit.SECONDS))
                    .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
            }else{assertThat(reader.get(10,TimeUnit.SECONDS)).isTrue();}
        }
        assertThat(service.ownFooter("101")).isEqualTo(strictSnapshot?"Winner personal":"Reader personal");
    }
    @Test void shouldUpdateDetachedPersonalAndDeleteLegacyDuplicates_onlyForOwner(){
        int retained=seedDuplicatePersonal();service.saveOwnFooter("101","Edited personal");
        assertPersonalRows(retained,"Edited personal",1);assertOtherOwnerAndClinic();
    }
    @Test void shouldRollBackPersonalUpdateAndDuplicateCleanup_withCallerTransaction(){
        int retained=seedDuplicatePersonal();
        tx(TransactionDefinition.ISOLATION_READ_COMMITTED).executeWithoutResult(status->{
            service.saveOwnFooter("101","Rolled back personal");status.setRollbackOnly();
        });
        assertPersonalRows(retained,"Old personal",2);assertOtherOwnerAndClinic();
    }
    @ParameterizedTest @CsvSource({"true,false","false,false","true,true","false,true"})
    void shouldCleanNewDuplicateAfterAmbientRead_orRefuseStrictAndAllowFreshRetry(
            boolean strictSnapshot,boolean clear)throws Exception {
        service.saveOwnFooter("101","Old personal");service.saveOwnFooter("202","Other personal");
        var read=new CountDownLatch(1);var inserted=new CountDownLatch(1);
        try(var worker=Executors.newSingleThreadExecutor()){
            var reader=worker.submit(()->tx(TransactionDefinition.ISOLATION_REPEATABLE_READ).execute(status->{
                setSnapshotIsolation(strictSnapshot);assertThat(service.ownFooter("101")).isEqualTo("Old personal");
                read.countDown();await(inserted);service.saveOwnFooter("101",clear?"":"Edited personal");return true;
            }));
            try{
                if(!read.await(10,TimeUnit.SECONDS)){reader.get(1,TimeUnit.SECONDS);fail("Reader did not reach duplicate barrier");}
                new TransactionTemplate(manager).executeWithoutResult(status->{
                    var row=new UserProperty();row.setName("email_footer");row.setProviderNo("101");
                    row.setValue("New duplicate");entities.persist(row);
                });
            }finally{inserted.countDown();}
            if(strictSnapshot){
                assertThatThrownBy(()->reader.get(10,TimeUnit.SECONDS))
                        .hasCauseInstanceOf(jakarta.persistence.OptimisticLockException.class);
                assertThat(service.ownFooter("101")).isEqualTo("Old personal");
                assertThat(new TransactionTemplate(manager).<Integer>execute(status->dao.getAllProperties("email_footer",List.of("101")).size())).isEqualTo(2);
                service.saveOwnFooter("101",clear?"":"Edited personal");
            }else{assertThat(reader.get(10,TimeUnit.SECONDS)).isTrue();}
        }
        assertThat(new TransactionTemplate(manager).<Integer>execute(status->dao.getAllProperties("email_footer",List.of("101")).size()))
                .isEqualTo(clear?0:1);
        assertThat(service.ownFooter("101")).isEqualTo(clear?"":"Edited personal");assertOtherOwnerAndClinic();
    }
    @Test void shouldAcceptRepeatedSamePersonal_withoutDuplicateOrFalseConflict(){
        service.saveOwnFooter("101","Same personal");service.saveOwnFooter("101","Same personal");
        new TransactionTemplate(manager).executeWithoutResult(status->{
            var rows=dao.getAllProperties("email_footer",List.of("101"));assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getValue()).isEqualTo("Same personal");
        });
    }
    private int seedDuplicatePersonal(){
        service.saveOwnFooter("101","Old personal");service.saveOwnFooter("202","Other personal");
        return new TransactionTemplate(manager).execute(status->{
            int retained=dao.getAllProperties("email_footer",List.of("101")).get(0).getId();
            var row=new UserProperty();row.setName("email_footer");row.setProviderNo("101");row.setValue("Legacy duplicate");
            entities.persist(row);return retained;
        });
    }
    private void assertPersonalRows(int retained,String value,int count){
        new TransactionTemplate(manager).executeWithoutResult(status->{
            var rows=dao.getAllProperties("email_footer",List.of("101"));assertThat(rows).hasSize(count);
            var first=rows.stream().min(Comparator.comparing(UserProperty::getId)).orElseThrow();
            assertThat(first.getId()).isEqualTo(retained);assertThat(first.getValue()).isEqualTo(value);
        });
    }
    private void assertOtherOwnerAndClinic(){
        assertThat(service.ownFooter("202")).isEqualTo("Other personal");
        assertThat(new TransactionTemplate(manager).<String>execute(status->dao.findClinicEmailFooter().get(0).getValue()))
                .isEqualTo("Mandatory Clinic");
    }
    private void setSnapshotIsolation(boolean strict){
        entities.unwrap(org.hibernate.Session.class).doWork(connection->{try(var statement=connection.createStatement()){
            // Owned fixture connection only; no global database change.
            statement.execute("SET SESSION innodb_snapshot_isolation="+(strict?"1":"0"));
        }});
    }
    private boolean waitingForLock(long connection)throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<end){try(var s=admin.prepareStatement("SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w "
                +"JOIN information_schema.INNODB_TRX t ON w.requesting_trx_id=t.trx_id WHERE t.trx_mysql_thread_id=?")){
            s.setLong(1,connection);try(var rows=s.executeQuery()){rows.next();if(rows.getInt(1)>0)return true;}
        }Thread.sleep(250);}return false;
    }
    private static void await(CountDownLatch latch){try{
        if(!latch.await(15,TimeUnit.SECONDS))throw new AssertionError("Owned fixture barrier timed out");
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
