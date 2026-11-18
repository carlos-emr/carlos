/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@Isolated("Owned credential rows and temporary encryption key")
@ContextConfiguration(locations = "classpath:test-context-mcedt-credentials.xml")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class McedtCredentialsIntegrationTest extends CarlosTestBase {
    @Autowired private McedtCredentials credentials;
    @Autowired private PlatformTransactionManager transactions;
    @PersistenceContext(unitName="entityManagerFactory") private EntityManager em;
    private TransactionTemplate tx;
    private Field keyField;
    private Object originalKey;
    private static final String NAME = UserProperty.MCEDT_ACCOUNT_PASSWORD;

    @BeforeEach
    void prepare() throws Exception {
        tx = new TransactionTemplate(transactions);
        long existing = tx.execute(status -> em.createQuery("SELECT COUNT(p) FROM UserProperty p WHERE p.name = :name", Long.class).setParameter("name", NAME).getSingleResult());
        assertEquals(0L, existing);
        keyField=EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");keyField.setAccessible(true);originalKey=keyField.get(null);keyField.set(null,new SecretKeySpec(new byte[32],"AES"));
    }

    @AfterEach
    void cleanup() throws Exception {
        try { tx.executeWithoutResult(status -> em.createQuery("DELETE FROM UserProperty p WHERE p.name = :name").setParameter("name", NAME).executeUpdate()); }
        finally { if (keyField != null) keyField.set(null,originalKey); }
    }

    private int seed(String value) { return tx.execute(status -> { UserProperty p=new UserProperty();p.setName(NAME);p.setValue(value);em.persist(p);em.flush();return p.getId(); }); }
    private String value(int id) { return tx.execute(status -> em.find(UserProperty.class,id).getValue()); }

    @Test
    void committedCreateAndReplacementStayEncrypted() throws Exception {
        credentials.savePassword("FAKE-first");
        int id=tx.execute(status -> em.createQuery("SELECT p.id FROM UserProperty p WHERE p.name=:name",Integer.class).setParameter("name",NAME).getSingleResult());
        assertNotEquals("FAKE-first",value(id));assertEquals("FAKE-first",EncryptionUtils.decrypt(value(id)));
        credentials.savePassword("FAKE-second");assertEquals("FAKE-second",EncryptionUtils.decrypt(value(id)));
        assertEquals("FAKE-second",credentials.getPassword("fallback"));
    }

    @Test
    void plaintextAndDuplicateMigrationCommitsWithoutChangingTheCredential() throws Exception {
        int first=seed("FAKE-legacy-first"),second=seed("FAKE-legacy-second");
        assertEquals("FAKE-legacy-first",credentials.getPassword("fallback"));
        assertTrue(EncryptionUtils.isEncrypted(value(first)));assertTrue(EncryptionUtils.isEncrypted(value(second)));
        assertEquals("FAKE-legacy-first",EncryptionUtils.decrypt(value(first)));assertEquals("FAKE-legacy-second",EncryptionUtils.decrypt(value(second)));
    }

    @Test
    void unchangedCiphertextAndMissingPropertyPreserveCompatibility() throws Exception {
        assertEquals("FAKE-fallback",credentials.getPassword("FAKE-fallback"));
        String cipher=EncryptionUtils.encrypt("FAKE-cipher");int id=seed(cipher);
        assertEquals("FAKE-cipher",credentials.getPassword("fallback"));assertEquals(cipher,value(id));
    }

    @Test
    void invalidKeyLeavesTheExistingRowUnchanged() throws Exception {
        int id=seed("FAKE-legacy");keyField.set(null,null);
        assertThrows(IllegalStateException.class,()->credentials.savePassword("FAKE-replacement"));
        assertThrows(IllegalStateException.class,()->credentials.getPassword("fallback"));
        assertEquals("FAKE-legacy",value(id));
    }

    @Test
    void decryptFailureRollsBackOtherRowsMigratedInTheSameTransaction() {
        int first=seed("{ENC}invalid!"),second=seed("FAKE-still-legacy");
        assertThrows(IllegalStateException.class,()->credentials.getPassword("fallback"));
        assertEquals("{ENC}invalid!",value(first));assertEquals("FAKE-still-legacy",value(second));
    }

    @Test
    void migrationWaitsForAConcurrentWriterAndCannotRestoreAStalePassword() throws Exception {
        int id=seed("FAKE-old");
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1),started=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        Future<?> writer=pool.submit(()->tx.executeWithoutResult(status->{
            UserProperty p=em.find(UserProperty.class,id,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);p.setValue("FAKE-new");em.flush();locked.countDown();
            try { assertTrue(release.await(15,TimeUnit.SECONDS)); } catch(InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
        }));
        try {
            assertTrue(locked.await(15,TimeUnit.SECONDS));
            Future<String> reader=pool.submit(()->{started.countDown();return credentials.getPassword("fallback");});
            assertTrue(started.await(15,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->reader.get(200,TimeUnit.MILLISECONDS));
            release.countDown();writer.get(15,TimeUnit.SECONDS);
            assertEquals("FAKE-new",reader.get(15,TimeUnit.SECONDS));assertEquals("FAKE-new",EncryptionUtils.decrypt(value(id)));
        } finally { release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(15,TimeUnit.SECONDS)); }
    }
}
