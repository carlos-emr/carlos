/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.CarlosProperties;
import ca.ontario.health.edt.EDTDelegate;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilderConfig;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real committed transactions in a unique, owned H2 or explicitly disposable MariaDB database. */
@Tag("integration")
@Isolated("Temporary application encryption key and delegate cache")
@Execution(ExecutionMode.SAME_THREAD)
class McedtCredentialServiceIntegrationTest {
    private static final String AUDIT_ACTOR = "611";
    static class OwnedDataSource extends DriverManagerDataSource implements AutoCloseable {
        final boolean mariaDb;
        private final String database="mcedt_credential_test_"+UUID.randomUUID().toString().replace("-","");
        private final String adminUrl;
        private boolean created;
        final java.util.Queue<String> jdbcFailures=new ConcurrentLinkedQueue<>();
        private Object observed(Object target,Class<?> contract) {
            return Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
                try {
                    Object result=method.invoke(target,args);
                    if(result instanceof java.sql.PreparedStatement)return observed(result,java.sql.PreparedStatement.class);
                    return result;
                } catch(InvocationTargetException failure) {
                    if(failure.getCause() instanceof SQLException sql)jdbcFailures.add(sql.getClass().getSimpleName()+":"+sql.getSQLState()+":"+sql.getErrorCode());
                    throw failure.getCause();
                }
            });
        }
        @Override public Connection getConnection() throws SQLException {return (Connection)observed(super.getConnection(),Connection.class);}
        OwnedDataSource() throws SQLException {
            adminUrl=System.getenv("CARLOS_TEST_MCEDT_MARIADB_URL");mariaDb=adminUrl!=null;
            if(mariaDb) {
                if(!"jdbc:mariadb://127.0.0.1:13360/".equals(adminUrl)
                        || !"true".equals(System.getenv("CARLOS_TEST_MCEDT_MARIADB_DISPOSABLE")))throw new IllegalStateException("MCEDT physical tests require their explicit disposable loopback server");
                try(Connection connection=DriverManager.getConnection(adminUrl,"root","");var statement=connection.createStatement()) {
                    statement.execute("CREATE DATABASE `"+database+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");created=true;
                }catch(SQLException failure){if(created)close();throw failure;}
                setUrl(adminUrl+database);setUsername("root");setPassword("");
            } else {setUrl("jdbc:h2:mem:"+database+";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000;NON_KEYWORDS=VALUE");setUsername("sa");setPassword("");}
        }
        @Override public void close() throws SQLException {
            if(mariaDb) {if(created)try(Connection connection=DriverManager.getConnection(adminUrl,"root","");var statement=connection.createStatement()){statement.execute("DROP DATABASE `"+database+"`");created=false;}}
            else try(Connection connection=getConnection();var statement=connection.createStatement()){statement.execute("SHUTDOWN");}
        }
    }
    @Configuration
    @EnableTransactionManagement
    static class Database {
        @Bean(destroyMethod="close") OwnedDataSource dataSource() throws SQLException {return new OwnedDataSource();}
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory=new LocalContainerEntityManagerFactoryBean();factory.setDataSource(source);
            factory.setPersistenceUnitName("testPersistenceUnit");factory.setManagedTypes(PersistenceManagedTypes.of(UserProperty.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop","hibernate.hbm2ddl.halt_on_error","true","hibernate.show_sql","false"));return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {return new JpaTransactionManager(factory);}
        @Bean McedtCredentialService credentials() {return new McedtCredentialService();}
    }
    private AnnotationConfigApplicationContext context;
    private McedtCredentialService credentials;
    private EntityManager em;
    private TransactionTemplate tx;
    private DataSource source;
    private Field keyField;
    private Object originalKey;

    @BeforeEach
    void prepare() throws Exception {
        keyField=EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");keyField.setAccessible(true);originalKey=keyField.get(null);keyField.set(null,new SecretKeySpec(new byte[32],"AES"));
        context=new AnnotationConfigApplicationContext(Database.class);credentials=context.getBean(McedtCredentialService.class);
        em=SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));tx=new TransactionTemplate(context.getBean(PlatformTransactionManager.class));source=context.getBean(DataSource.class);
        if (((OwnedDataSource) source).mariaDb) {
            try (Connection connection = source.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                        "database/mysql/migration/common/V1.0.59.51__mcedt_encrypted_credential_storage.sql"));
            }
            return;
        }
        tx.executeWithoutResult(status->{
            em.createNativeQuery("CREATE TABLE mcedt_credential (id TINYINT PRIMARY KEY CHECK(id=1), encrypted_password LONGTEXT,"
                    + " lastUpdateUser VARCHAR(100) NOT NULL DEFAULT 'system',"
                    + " lastUpdateDate TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)").executeUpdate();
            em.createNativeQuery("INSERT INTO mcedt_credential(id, encrypted_password, lastUpdateUser) VALUES(1,NULL,'migration')")
                    .executeUpdate();
            em.createNativeQuery("CREATE INDEX idx_property_name ON property(name)").executeUpdate();
        });
    }
    @AfterEach
    void cleanup() throws Exception {
        try {
            if(context!=null)context.close();
            // Closing the context closes only its UUID datasource, after its JPA factory.
        } finally {if(keyField!=null)keyField.set(null,originalKey);}
    }
    private int legacy(String value) {return tx.execute(status->{var p=new UserProperty();p.setName(UserProperty.MCEDT_ACCOUNT_PASSWORD);p.setValue(value);em.persist(p);em.flush();return p.getId();});}
    private long legacyCount() {return tx.execute(status->em.createQuery("SELECT COUNT(p) FROM UserProperty p WHERE p.name=:name",Long.class).setParameter("name",UserProperty.MCEDT_ACCOUNT_PASSWORD).getSingleResult());}
    private String stored() {return tx.execute(status->(String)em.createNativeQuery("SELECT encrypted_password FROM mcedt_credential WHERE id=1",String.class).getSingleResult());}
    private Object[] audit() {
        return tx.execute(status -> (Object[]) em.createNativeQuery(
                "SELECT lastUpdateUser,lastUpdateDate FROM mcedt_credential WHERE id=1").getSingleResult());
    }
    private void cipher(String value) {tx.executeWithoutResult(status->em.createNativeQuery("UPDATE mcedt_credential SET encrypted_password=:value WHERE id=1").setParameter("value",value).executeUpdate());}
    private void assertStaticFailure(Runnable work) {var error=assertThrows(IllegalStateException.class,work::run);assertNull(error.getCause());assertFalse(error.getMessage().contains("FAKE"));}

    @Test
    void shouldCommitEncryptedCredential_whenCreatedAndReplaced() throws Exception {
        var seed = audit();
        assertEquals("migration", seed[0]);
        assertNotNull(seed[1]);
        tx.executeWithoutResult(status -> em.createNativeQuery(
                "UPDATE mcedt_credential SET lastUpdateDate='2000-01-01 00:00:00' WHERE id=1").executeUpdate());
        var priorTime = audit()[1];
        credentials.savePassword("FAKE-first", AUDIT_ACTOR);String first=stored();assertNotEquals("FAKE-first",first);assertEquals("FAKE-first",EncryptionUtils.decrypt(first));
        credentials.savePassword("FAKE-second", AUDIT_ACTOR);assertNotEquals(first,stored());assertEquals("FAKE-second",credentials.getPassword("fallback"));assertEquals(0,legacyCount());
        var replacement = audit();
        assertEquals(AUDIT_ACTOR, replacement[0]);
        assertNotEquals(priorTime, replacement[1]);
    }
    @ParameterizedTest @ValueSource(strings={"FAKE-legacy","{ENC}literal-legacy","{ENC}AAAA"})
    void shouldMigrateOpaqueLegacyPassword_withoutInterpretingItsPrefix(String password) throws Exception {legacy(password);legacy("FAKE-duplicate");assertEquals(password,credentials.getPassword("fallback"));assertEquals(password,EncryptionUtils.decrypt(stored()));assertEquals(0,legacyCount());assertEquals("system",audit()[0]);}
    @Test
    void shouldRemoveEveryLegacyDuplicate_whenSavingReplacement() throws Exception {legacy("FAKE-first");legacy("FAKE-second");credentials.savePassword("FAKE-replacement", AUDIT_ACTOR);assertEquals(0,legacyCount());assertEquals("FAKE-replacement",EncryptionUtils.decrypt(stored()));}
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"  "})
    void shouldKeepConfiguredFallback_withBlankLegacyPassword(String value) {legacy(value);assertEquals("FAKE-fallback",credentials.getPassword("FAKE-fallback"));assertEquals(0,legacyCount());}
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"  "})
    void shouldKeepConfiguredFallback_withBlankReplacement(String value) {legacy("FAKE-old");credentials.savePassword(value, AUDIT_ACTOR);assertEquals("FAKE-fallback",credentials.getPassword("FAKE-fallback"));assertEquals(0,legacyCount());}
    @Test
    void shouldKeepConfiguredFallback_whenNoLegacyPasswordExists() {assertEquals("FAKE-fallback",credentials.getPassword("FAKE-fallback"));assertNull(stored());}
    @Test
    void shouldKeepExistingCiphertext_whenAlreadyMigrated() throws Exception {String encrypted=EncryptionUtils.encrypt("FAKE-current");cipher(encrypted);legacy("FAKE-obsolete");assertEquals("FAKE-current",credentials.getPassword("fallback"));assertEquals(encrypted,stored());assertEquals(0,legacyCount());}
    @ParameterizedTest @ValueSource(strings={"{ENC}invalid!","FAKE-wrong-format"})
    void shouldFailClosed_withoutTreatingCorruptCiphertextAsPlaintext(String encrypted) {cipher(encrypted);legacy("FAKE-survive");assertStaticFailure(()->credentials.getPassword("fallback"));assertEquals(encrypted,stored());assertEquals(1,legacyCount());}
    @Test
    void shouldPreserveLegacyRows_whenEncryptionKeyIsUnavailable() throws Exception {legacy("FAKE-legacy");legacy("FAKE-duplicate");keyField.set(null,null);assertStaticFailure(()->credentials.savePassword("FAKE-replacement", AUDIT_ACTOR));assertStaticFailure(()->credentials.getPassword("fallback"));assertNull(stored());assertEquals(2,legacyCount());}
    @Test
    void shouldRollBackEncryptedWrite_whenLegacyRemovalFails() {int id=legacy("FAKE-parent");tx.executeWithoutResult(status->{em.createNativeQuery("CREATE TABLE owned_reference (property_id INT REFERENCES property(id))").executeUpdate();em.createNativeQuery("INSERT INTO owned_reference VALUES(:id)").setParameter("id",id).executeUpdate();});assertStaticFailure(()->credentials.getPassword("fallback"));assertNull(stored());assertEquals(1,legacyCount());}
    @ParameterizedTest @ValueSource(strings={"x","😀"})
    void shouldRetainFullLegacyCapacity_withExpandedCiphertext(String character) throws Exception {String password=character.repeat(4000);credentials.savePassword(password, AUDIT_ACTOR);assertTrue(stored().length()>4000);assertEquals(password,credentials.getPassword("fallback"));}
    @Test
    void shouldRejectOverlongInput_beforeMutatingStorage() {legacy("FAKE-before");assertStaticFailure(()->credentials.savePassword("x".repeat(4001), AUDIT_ACTOR));assertNull(stored());assertEquals(1,legacyCount());}
    @Test
    void shouldFailClosed_whenSingletonLockRecordIsMissing() {tx.executeWithoutResult(status->em.createNativeQuery("DELETE FROM mcedt_credential").executeUpdate());assertStaticFailure(()->credentials.savePassword("FAKE-value", AUDIT_ACTOR));assertStaticFailure(()->credentials.getPassword("fallback"));}

    private void awaitDatabaseBlock(int blocker, AtomicInteger contender, Future<?> waiting) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        String sql=((OwnedDataSource)source).mariaDb
                ? "SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS waits"
                    + " JOIN information_schema.INNODB_TRX writer ON writer.trx_id=waits.blocking_trx_id"
                    + " JOIN information_schema.INNODB_TRX reader ON reader.trx_id=waits.requesting_trx_id"
                    + " WHERE writer.trx_mysql_thread_id=? AND reader.trx_mysql_thread_id=?"
                : "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID=? AND SESSION_ID=?";
        try(Connection connection=source.getConnection();var query=connection.prepareStatement(sql)) {
            query.setInt(1,blocker);
            while(System.nanoTime()<deadline) {
                query.setInt(2, contender.get());
                try(var rows=query.executeQuery()) {
                    rows.next();
                    if(rows.getInt(1)>0) {
                        // The contender has reached the actual database lock operation.
                        // A scheduling delay before its call cannot satisfy this signal.
                        assertThrows(TimeoutException.class,()->waiting.get(200,TimeUnit.MILLISECONDS));
                        return;
                    }
                }
                assertFalse(waiting.isDone(),"contender completed before the actual database lock was observed");
                // The InnoDB information-schema views reuse a recent lock snapshot.
                TimeUnit.MILLISECONDS.sleep(200);
            }
        }
        StringBuilder diagnostic=new StringBuilder("No database session was observed blocked by writer ").append(blocker);
        if(((OwnedDataSource)source).mariaDb)try(Connection connection=source.getConnection();var query=connection.createStatement()) {
            // Metadata only: never record SQL text or credential values.
            for(String probe:new String[]{"SELECT trx_id,trx_mysql_thread_id,trx_state FROM information_schema.INNODB_TRX","SELECT requesting_trx_id,blocking_trx_id FROM information_schema.INNODB_LOCK_WAITS","SELECT ID,COMMAND,STATE FROM information_schema.PROCESSLIST WHERE DB=DATABASE()"}) {
                try(var rows=query.executeQuery(probe)){diagnostic.append(" [");while(rows.next()){for(int i=1;i<=rows.getMetaData().getColumnCount();i++)diagnostic.append(rows.getString(i)).append(':');diagnostic.append(';');}diagnostic.append(']');}
            }
        }
        fail(diagnostic.toString());
    }
    private int databaseSession() {
        return ((Number) em.createNativeQuery(((OwnedDataSource) source).mariaDb
                ? "SELECT CONNECTION_ID()" : "SELECT SESSION_ID()").getSingleResult()).intValue();
    }

    private <T> T observeContender(AtomicInteger session, java.util.function.Supplier<T> operation) {
        return tx.execute(status -> {
            session.set(databaseSession());
            return operation.get();
        });
    }

    private void hold(CountDownLatch locked,CountDownLatch release,AtomicInteger session) {
        session.set(databaseSession());locked.countDown();
        try {assertTrue(release.await(15,TimeUnit.SECONDS));}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
    }
    private <T> T completed(Future<T> operation) throws Exception {
        try {return operation.get(15,TimeUnit.SECONDS);}
        catch(ExecutionException failure) {throw new AssertionError("Credential operation failed; JDBC classes/states/codes only: "+((OwnedDataSource)source).jdbcFailures,failure);}
    }
    @Test
    void shouldSerializeFirstSaves_usingTheSeededDatabaseLock() throws Exception {
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var session=new AtomicInteger();
        var contender = new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->tx.executeWithoutResult(status->{credentials.savePassword("FAKE-first", AUDIT_ACTOR);hold(locked,release,session);}));
            try {
                assertTrue(locked.await(15, TimeUnit.SECONDS));
                var second = pool.submit(() -> observeContender(contender, () -> {
                    credentials.savePassword("FAKE-second", AUDIT_ACTOR);
                    return null;
                }));
                awaitDatabaseBlock(session.get(), contender, second);
                release.countDown();
                completed(first);
                completed(second);
                assertEquals("FAKE-second", credentials.getPassword("fallback"));
                assertEquals(0, legacyCount());
                assertEquals(1L, tx.execute(status -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM mcedt_credential").getSingleResult()).longValue()).longValue());
            }
            finally {release.countDown();}
        }
    }
    @Test
    void shouldWaitForLegacyWriter_withoutRestoringAStalePassword() throws Exception {
        int id=legacy("FAKE-old");var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var session=new AtomicInteger();
        var contender = new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var writer=pool.submit(()->tx.executeWithoutResult(status->{var row=em.find(UserProperty.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);row.setValue("FAKE-new");em.flush();hold(locked,release,session);}));
            try {
                assertTrue(locked.await(15, TimeUnit.SECONDS));
                var reader = pool.submit(() -> observeContender(contender, () -> credentials.getPassword("fallback")));
                awaitDatabaseBlock(session.get(), contender, reader);
                release.countDown();
                completed(writer);
                assertEquals("FAKE-new", completed(reader));
                assertEquals("FAKE-new", EncryptionUtils.decrypt(stored()));
                assertEquals(0, legacyCount());
            }
            finally {release.countDown();}
        }
    }
    @SuppressWarnings("unchecked")
    private static <T> T cache(String name) throws Exception {
        Field field=DelegateFactory.class.getDeclaredField(name);field.setAccessible(true);return (T)field.get(null);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void shouldReplaceCachedClient_afterCommittedPasswordRotationIncludingAnotherInstance(boolean otherInstance) throws Exception {
        credentials.savePassword("FAKE-first", AUDIT_ACTOR);
        List<EdtClientBuilderConfig> observed=new ArrayList<>();
        try(var spring=mockStatic(SpringUtils.class);var properties=mockStatic(CarlosProperties.class);
                var builders=mockConstruction(EdtClientBuilder.class,(builder,construction)->{
                    observed.add((EdtClientBuilderConfig)construction.arguments().get(0));
                    when(builder.build(EDTDelegate.class)).thenReturn(mock(EDTDelegate.class));
                })) {
            spring.when(()->SpringUtils.getBean(McedtCredentialService.class)).thenReturn(credentials);
            spring.when(()->SpringUtils.getBean(UserPropertyDAO.class)).thenReturn(mock(UserPropertyDAO.class));
            properties.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
            Map<String,EDTDelegate> delegates=cache("edtDelegates");
            Map<String,byte[]> fingerprints=cache("credentialFingerprints");
            var previousDelegates=new HashMap<>(delegates);var previousFingerprints=new HashMap<>(fingerprints);
            try {
                delegates.clear();fingerprints.clear();
                var first=DelegateFactory.getEDTDelegateInstance("FAKE-local-service");
                assertEquals("FAKE-first",observed.getFirst().getUserNameTokenPassword());
                tx.executeWithoutResult(status->{credentials.savePassword("FAKE-rolled-back", AUDIT_ACTOR);status.setRollbackOnly();});
                assertSame(first,DelegateFactory.getEDTDelegateInstance("FAKE-local-service"));
                assertEquals(1,builders.constructed().size());
                if(otherInstance)cipher(EncryptionUtils.encrypt("FAKE-second"));
                else credentials.savePassword("FAKE-second", AUDIT_ACTOR);
                var second=DelegateFactory.getEDTDelegateInstance("FAKE-local-service");
                assertNotSame(first,second);
                assertEquals("FAKE-second",observed.getLast().getUserNameTokenPassword());
                assertSame(second,DelegateFactory.getEDTDelegateInstance("FAKE-local-service"));
                assertEquals(2,builders.constructed().size());
            } finally {
                delegates.clear();delegates.putAll(previousDelegates);
                fingerprints.clear();fingerprints.putAll(previousFingerprints);
            }
        }
    }
    @Test
    void shouldAllowUnrelatedPreferenceUpdate_whileCredentialTransactionHoldsItsLocks() throws Exception {
        legacy("FAKE-legacy");
        int preference=tx.execute(status->{
            var row=new UserProperty();row.setName("FAKE-unrelated-preference");row.setValue("FAKE-before");
            em.persist(row);em.flush();return row.getId();
        });
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var session=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var reader=pool.submit(()->tx.executeWithoutResult(status->{
                assertEquals("FAKE-legacy",credentials.getPassword("fallback"));hold(locked,release,session);
            }));
            try {
                assertTrue(locked.await(15,TimeUnit.SECONDS));
                var unrelated=pool.submit(()->tx.executeWithoutResult(status->{
                    em.find(UserProperty.class,preference).setValue("FAKE-after");em.flush();
                }));
                // The unrelated commit must complete while MCEDT locks remain held.
                unrelated.get(5,TimeUnit.SECONDS);
                assertEquals(1L,release.getCount());
                release.countDown();completed(reader);
                assertEquals("FAKE-after",tx.execute(status->em.find(UserProperty.class,preference).getValue()));
            } finally {release.countDown();}
        }
    }

    @ParameterizedTest @ValueSource(strings={"FAKE-current","  "})
    void shouldAllowNewPreferenceInsert_whileMigratedCredentialReadRemainsOpen(String password) throws Exception {
        credentials.savePassword(password, AUDIT_ACTOR);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var session=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var reader=pool.submit(()->tx.executeWithoutResult(status->{
                assertEquals(password.isBlank()?"FAKE-fallback":password,credentials.getPassword("FAKE-fallback"));hold(locked,release,session);
            }));
            try {
                assertTrue(locked.await(15,TimeUnit.SECONDS));
                var inserted=pool.submit(()->tx.execute(status->{
                    if(((OwnedDataSource)source).mariaDb)em.createNativeQuery("SET innodb_lock_wait_timeout=2").executeUpdate();
                    var row=new UserProperty();row.setProviderNo("FAKE");row.setName("provider.language");row.setValue("FAKE-language");em.persist(row);em.flush();return row.getId();
                }));
                int id=inserted.get(5,TimeUnit.SECONDS);
                assertEquals(1L,release.getCount(),"the independent insert must commit before the credential reader releases its transaction");
                assertEquals("FAKE-language",tx.execute(status->em.find(UserProperty.class,id).getValue()));
                release.countDown();completed(reader);
            } finally {release.countDown();}
        }
    }
    @Test
    void shouldKeepCurrentCredentialAndRemoveLegacy_whenAnOldWriterCommitsDuringRead() throws Exception {
        obsoleteLegacyRead(false);
    }
    @Test
    void shouldPreserveARegularPreference_whenTheLegacyWriterRenamesItsRow() throws Exception {
        obsoleteLegacyRead(true);
    }
    private void obsoleteLegacyRead(boolean renamed) throws Exception {
        credentials.savePassword("FAKE-current", AUDIT_ACTOR);int id=legacy("FAKE-old");
        Integer snapshotIsolation=((OwnedDataSource)source).mariaDb?tx.execute(status->((Number)em.createNativeQuery("SELECT @@innodb_snapshot_isolation").getSingleResult()).intValue()):null;
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var session=new AtomicInteger();
        var contender = new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var writer=pool.submit(()->tx.executeWithoutResult(status->{
                var row=em.find(UserProperty.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
                if(renamed)row.setName("provider.language");else row.setValue("FAKE-new");
                em.flush();hold(locked,release,session);
            }));
            try {
                assertTrue(locked.await(15,TimeUnit.SECONDS));var reader=pool.submit(()->observeContender(contender,()->{
                    String password=credentials.getPassword("FAKE-fallback");
                    if(snapshotIsolation!=null)assertEquals(snapshotIsolation,((Number)em.createNativeQuery("SELECT @@innodb_snapshot_isolation").getSingleResult()).intValue(),"the cleanup statement must restore this same connection's setting");
                    return password;
                }));
                awaitDatabaseBlock(session.get(),contender,reader);release.countDown();completed(writer);
                assertEquals("FAKE-current",completed(reader));assertEquals(0,legacyCount());
                if(renamed)assertEquals("provider.language",tx.execute(status->em.find(UserProperty.class,id).getName()));
                if(snapshotIsolation!=null)assertEquals(snapshotIsolation,tx.execute(status->((Number)em.createNativeQuery("SELECT @@innodb_snapshot_isolation").getSingleResult()).intValue()));
            } finally {release.countDown();}
        }
    }

    @Test
    void shouldAllowPreferenceInsert_whileMigratedCredentialReadRemainsOpen() throws Exception {
        credentials.savePassword("FAKE-current", AUDIT_ACTOR);
        assertEquals(0, legacyCount());
        var read = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var readerSession = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var reader = pool.submit(() -> tx.executeWithoutResult(status -> {
                assertEquals("FAKE-current", credentials.getPassword("fallback"));
                hold(read, release, readerSession);
            }));
            try {
                assertTrue(read.await(15, TimeUnit.SECONDS));
                var insert = pool.submit(() -> tx.execute(status -> {
                    var preference = new UserProperty();
                    preference.setName("provider.language");
                    preference.setValue("FAKE-language");
                    em.persist(preference);
                    em.flush();
                    return preference.getId();
                }));
                int inserted = insert.get(5, TimeUnit.SECONDS);
                assertEquals(1L, release.getCount());
                release.countDown();
                completed(reader);
                assertEquals("FAKE-language", tx.execute(status -> em.find(UserProperty.class, inserted).getValue()));
            } finally {
                release.countDown();
            }
        }
    }

}
