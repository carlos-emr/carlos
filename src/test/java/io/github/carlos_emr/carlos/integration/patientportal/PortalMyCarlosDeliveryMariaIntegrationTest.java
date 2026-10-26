/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.*;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Owned real Maria rows/transactions only; accepted-state fixture and staff rights are synthetic. */
@Tag("integration")
class PortalMyCarlosDeliveryMariaIntegrationTest {
    private final List<Long> ownedFenceProperties = new ArrayList<>();
    static final ObjectMapper JSON = new ObjectMapper();
    static JsonNode credentials;
    static DriverManagerDataSource source;
    static AnnotationConfigApplicationContext context;
    static PortalMyCarlosDeviceApprovalTransactions tx;
    static PortalMyCarlosDeliveryOutboxDao dao;
    static PortalMyCarlosAcceptedDeviceDao devices;
    static PortalMyCarlosPatientBindingDao bindings;
    static final AtomicLong NOW = new AtomicLong(1792454400L);
    static final Clock CLOCK = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochSecond(NOW.get()); }
    };
    static PatientPortalSettings settings;
    static PortalMyCarlosDeviceApprovalService.Current initial;
    static final AtomicReference<PortalMyCarlosDeviceApprovalService.Current> current = new AtomicReference<>();
    static final SecurityInfoManager STAFF = new SecurityInfoManager() {
        public List<io.github.carlos_emr.carlos.model.security.Secuserrole> getRoles(LoggedInInfo s) { return List.of(); }
        public List<io.github.carlos_emr.carlos.model.security.Secobjprivilege> getSecurityObjects(LoggedInInfo s) { return List.of(); }
        public boolean hasPrivilege(LoggedInInfo s, String o, String p, int d) { return true; }
        public boolean hasPrivilege(LoggedInInfo s, String o, String p, String d) { return true; }
        public boolean isAllowedAccessToPatientRecord(LoggedInInfo s, Integer d) { return true; }
    };

    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class TestContext {
        @Bean static PersistenceAnnotationBeanPostProcessor injection() { return new PersistenceAnnotationBeanPostProcessor(); }
        @Bean DataSource dataSource() { return source; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var unit = new DefaultPersistenceUnitManager();
            unit.setPersistenceXmlLocations(new String[0]); unit.setMappingResources(new String[0]);
            unit.setDefaultPersistenceUnitName("entityManagerFactory");
            unit.setManagedTypes(PersistenceManagedTypes.of(Property.class.getName()));
            unit.setDefaultDataSource(ds);
            unit.setPersistenceUnitPostProcessors(info -> { info.setTransactionType(jakarta.persistence.PersistenceUnitTransactionType.RESOURCE_LOCAL); info.setExcludeUnlistedClasses(true); });
            unit.afterPropertiesSet();
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setPersistenceUnitManager(unit); factory.setPersistenceUnitName("entityManagerFactory");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect",
                    "hibernate.boot.allow_jdbc_metadata_access", "true", "hibernate.hbm2ddl.auto", "none",
                    "jakarta.persistence.schema-generation.database.action", "none", "hibernate.cache.use_second_level_cache", "false"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(EntityManagerFactory f) { return new JpaTransactionManager(f); }
        @Bean PropertyDaoImpl properties() { return new PropertyDaoImpl(); }
        @Bean ProviderLabRoutingDaoImpl coordination() { return new ProviderLabRoutingDaoImpl(); }
        @Bean PortalMyCarlosInventoryTransactions owner(JpaTransactionManager m, EntityManagerFactory f, ProviderLabRoutingDao d) { return new PortalMyCarlosInventoryTransactions(m, f, d); }
        @Bean PortalMyCarlosKeyInventoryWorker inventory(PropertyDao p) { return new PortalMyCarlosKeyInventoryWorker(p); }
        @Bean PortalMyCarlosDeviceApprovalTransactions approval(PortalMyCarlosInventoryTransactions o, PortalMyCarlosKeyInventoryWorker i) { return new PortalMyCarlosDeviceApprovalTransactions(o, i); }
    }

    @BeforeAll static void start() throws Exception {
        String path = System.getProperty("mycarlos.sender.test.credentials");
        Assumptions.assumeTrue(path != null, "owned isolated Maria test credentials required");
        credentials = JSON.readTree(Files.readAllBytes(Path.of(path)));
        assertEquals("a1_send_delete_runtime_20261026", credentials.get("schema").textValue());
        source = new DriverManagerDataSource(credentials.get("url").textValue(), credentials.get("username").textValue(), credentials.get("password").textValue());
        try (var c = source.getConnection(); var q = c.createStatement(); var r = q.executeQuery("SELECT DATABASE()")) {
            assertTrue(r.next()); assertEquals("a1_send_delete_runtime_20261026", r.getString(1));
        }
        context = new AnnotationConfigApplicationContext(TestContext.class);
        var f = context.getBean(EntityManagerFactory.class);
        tx = context.getBean(PortalMyCarlosDeviceApprovalTransactions.class);
        dao = new PortalMyCarlosDeliveryOutboxDao(f); devices = new PortalMyCarlosAcceptedDeviceDao(f); bindings = new PortalMyCarlosPatientBindingDao(f);
        settings = PatientPortalSettings.fromProperties(Map.of(PatientPortalSettings.BASE_URL_KEY, "https://portal.example.test",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a", PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY, PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        initial = PortalMyCarlosDeviceApprovalService.Current.fromValidated(settings, "https://portal.example.test", true, true);
        current.set(initial);
    }
    @AfterAll static void close() { if (context != null) context.close(); }
    @AfterEach void restoreOnlyOwnedAcceptanceFixture() throws Exception {
        NOW.set(1792454400L); current.set(initial);
        for (long id : ownedFenceProperties) {
            try (var c = source.getConnection(); var q = c.prepareStatement("DELETE FROM property WHERE id=? AND name=?")) {
                q.setLong(1, id); q.setString(2, "mycarlos.delivery.removed.Ebz2sH2jVoqcdFhl1BmIOBRm1xuT-zW5bvFBcCeERPY");
                assertEquals(1, q.executeUpdate());
            }
        }
        try (var c = source.getConnection(); var q = c.prepareStatement("UPDATE mycarlos_accepted_device SET removed_at=NULL,removal_id=NULL,removal_reason=NULL,removed_by_provider_no=NULL,version=0 WHERE id=1 AND binding_id=1")) { assertEquals(1, q.executeUpdate()); }
    }

    @Test void shouldRefuseMissingStaffIdentity_beforeStagingAnyOwnedRow() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); var service = service(exchange);
        assertThrows(SecurityException.class, () -> service.stage(null, pair.copy(), pair.snapshot()));
        var missingSecurityId = staff(); missingSecurityId.getLoggedInSecurity().setSecurityNo(null);
        assertThrows(SecurityException.class, () -> service.stage(missingSecurityId, pair.copy(), pair.snapshot()));
        assertEquals(0, count("mycarlos_delivery_original", pair.snapshot().deliveryId()));
        assertEquals(0, count("mycarlos_delivery_outbox", pair.snapshot().deliveryId()));
        assertTrue(exchange.sent.isEmpty());
    }

    @Test void shouldCommitEncryptedOriginalAndIdenticalOutboxOnce_whenTwoThreadsStageSameCopy() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); var service = service(exchange);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Long> stage = () -> { start.await(); return service.stage(staff(), pair.copy(), pair.snapshot()); };
            var a = pool.submit(stage); var b = pool.submit(stage); start.countDown();
            assertEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertEquals(1, count("mycarlos_delivery_original", pair.snapshot().deliveryId()));
            assertEquals(1, count("mycarlos_delivery_outbox", pair.snapshot().deliveryId()));
            var row = tx.withApprovalState(() -> dao.find(pair.snapshot().clinicId(), pair.snapshot().deliveryId(), pair.snapshot().device().kid()).orElseThrow());
            assertFalse(Arrays.equals(pair.snapshot().encode(), row.encryptedSnapshot().copy()));
            assertEquals(pair.snapshot(), service.load(row));
            assertTrue(exchange.sent.isEmpty());
        } finally { pool.shutdownNow(); }
    }

    @Test void shouldRetryExactPersistedBytesAfterRestart_whenUploadReplyIsLost() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); exchange.failFirst = true;
        var service = service(exchange); long id = service.stage(staff(), pair.copy(), pair.snapshot());
        assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.RETRY_PENDING, service.send(id));
        NOW.addAndGet(61);
        var restored = service(exchange); // Same provisioned storage key, fresh service/codec/client objects.
        assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.WAITING, restored.send(id));
        assertEquals(2, exchange.sent.size());
        assertArrayEquals(pair.snapshot().body().copy(), exchange.sent.get(0));
        assertArrayEquals(exchange.sent.get(0), exchange.sent.get(1));
        assertNotEquals(exchange.assertions.get(0), exchange.assertions.get(1));
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.WAITING,
                restored.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
    }

    @Test void shouldFenceQueuedWork_whenLocalAcceptanceIsRemovedOrConfigurationChanges() throws Exception {
        for (boolean remove : List.of(true, false)) {
            var pair = pair(); var exchange = new Exchange(pair.snapshot()); var service = service(exchange);
            long id = service.stage(staff(), pair.copy(), pair.snapshot());
            if (remove) removeDevice();
            else current.set(PortalMyCarlosDeviceApprovalService.Current.fromValidated(settings, "https://other.example.test", true, true));
            assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.NOT_CLAIMED, service.send(id));
            assertTrue(exchange.sent.isEmpty());
            assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.FENCED,
                    service.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
            restoreOnlyOwnedAcceptanceFixture();
        }
    }

    @Test void shouldRejectLateCallback_whenLeaseHasBeenReplaced() throws Exception {
        var pair = pair(); var service = service(new Exchange(pair.snapshot()));
        long id = service.stage(staff(), pair.copy(), pair.snapshot());
        var old = service.claim(id); assertNotNull(old);
        NOW.addAndGet(31); var replacement = service.claim(id); assertNotNull(replacement);
        assertNotEquals(old.token(), replacement.token());
        assertFalse(tx.withApprovalState(() -> dao.finish(id, old.token(), old.version(), "waiting", 0, null)));
        assertEquals(replacement.token(), tx.withApprovalState(() -> dao.find(id).orElseThrow().leaseId()));
    }

    @Test void shouldKeepHistoricalPickupAndFirstExactReceipt_whenCurrentDeviceIsRemoved() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); var service = service(exchange);
        long id = service.stage(staff(), pair.copy(), pair.snapshot()); removeDevice();
        String kept = receipt(pair.snapshot(), UUID.randomUUID().toString());
        tx.withApprovalState(() -> { service.keepReceipt(dao.find(id).orElseThrow(), kept); return null; });
        var first = tx.withApprovalState(() -> dao.find(id).orElseThrow());
        tx.withApprovalState(() -> { service.keepReceipt(dao.find(id).orElseThrow(), kept); return null; });
        assertEquals(first, tx.withApprovalState(() -> dao.find(id).orElseThrow()));
        String conflicting = receipt(pair.snapshot(), UUID.randomUUID().toString());
        assertThrows(IllegalStateException.class, () -> tx.withApprovalState(() -> { service.keepReceipt(dao.find(id).orElseThrow(), conflicting); return null; }));
        tx.withApprovalState(() -> { dao.fenceDevice("clinic-a", pair.snapshot().device().kid()); return null; });
        try (var c = source.getConnection(); var q = c.prepareStatement("SELECT id FROM property WHERE name=?")) {
            q.setString(1, "mycarlos.delivery.removed." + pair.snapshot().device().kid());
            try (var r = q.executeQuery()) { assertTrue(r.next()); ownedFenceProperties.add(r.getLong(1)); }
        }
        assertEquals(first, tx.withApprovalState(() -> dao.find(id).orElseThrow()));
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.PICKED_UP,
                service.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
    }

    @Test void shouldKeepWaiting_whenUnsignedPortalUploadStatusSaysPickedUp() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); exchange.status = "picked_up";
        var service = service(exchange); long id = service.stage(staff(), pair.copy(), pair.snapshot());
        assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.WAITING, service.send(id));
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.WAITING,
                service.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
    }

    @Test void shouldRecheckRemovalAfterClaim_beforeInitiatingAnyHttp() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); var service = service(exchange);
        long id = service.stage(staff(), pair.copy(), pair.snapshot());
        var claimed = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                var lease = service.claim(id); assertNotNull(lease); claimed.countDown();
                assertTrue(release.await(20, TimeUnit.SECONDS));
                return service.transmit(lease);
            });
            assertTrue(claimed.await(20, TimeUnit.SECONDS));
            removeDevice(); // A real separate owning Maria transaction commits after the claim.
            release.countDown();
            assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.FENCED, worker.get(30, TimeUnit.SECONDS));
            assertTrue(exchange.sent.isEmpty());
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test void shouldPreservePickup_whenHttpCompletionArrivesAfterReceiptCommit() throws Exception {
        var pair = pair(); var exchange = new Exchange(pair.snapshot()); var service = service(exchange);
        long id = service.stage(staff(), pair.copy(), pair.snapshot());
        exchange.requestStarted = new CountDownLatch(1); exchange.allowReply = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> service.send(id));
            assertTrue(exchange.requestStarted.await(20, TimeUnit.SECONDS));
            String exact = receipt(pair.snapshot(), UUID.randomUUID().toString());
            tx.withApprovalState(() -> { service.keepReceipt(dao.find(id).orElseThrow(), exact); return null; });
            exchange.allowReply.countDown();
            assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.STALE, worker.get(30, TimeUnit.SECONDS));
            assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.PICKED_UP,
                    service.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
        } finally { exchange.allowReply.countDown(); pool.shutdownNow(); }
    }

    @Test void shouldKeepExplicitTransportTerminalState_whenKnownRetryReturnsExpiredOrRemoved() throws Exception {
        for (String terminal : List.of("expired", "removed")) {
            var pair = pair(); var exchange = new Exchange(pair.snapshot()); exchange.status = terminal;
            var service = service(exchange); long id = service.stage(staff(), pair.copy(), pair.snapshot());
            assertEquals("expired".equals(terminal) ? PortalMyCarlosDeliveryOutboxService.SendResult.EXPIRED
                    : PortalMyCarlosDeliveryOutboxService.SendResult.REMOVED, service.send(id));
            assertEquals("expired".equals(terminal) ? PortalMyCarlosDeliveryOutboxService.DeliveryStatus.EXPIRED
                    : PortalMyCarlosDeliveryOutboxService.DeliveryStatus.REMOVED,
                    service.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
        }
    }

    @Test void shouldReportGenericAuthFailuresAndOnlyFenceKnownDeviceRemoval_whenPortalRefusesUpload() throws Exception {
        for (int code : List.of(403, 404, 409, 413)) {
            var pair = pair(); var exchange = new Exchange(pair.snapshot()); exchange.responseStatus = code;
            if (code == 409) exchange.errorCode = "device_removed";
            var service = service(exchange); long id = service.stage(staff(), pair.copy(), pair.snapshot());
            assertEquals(code == 409 ? PortalMyCarlosDeliveryOutboxService.SendResult.FENCED
                    : PortalMyCarlosDeliveryOutboxService.SendResult.ERROR, service.send(id));
            assertEquals(code == 409 ? PortalMyCarlosDeliveryOutboxService.DeliveryStatus.FENCED
                    : PortalMyCarlosDeliveryOutboxService.DeliveryStatus.ERROR,
                    service.status(staff(), 123, pair.snapshot().deliveryId(), pair.snapshot().device().kid()));
        }
    }

    @Test void shouldRollBackOriginalAndRefuseUnownedDao_whenTransactionFailsBeforeCommit() throws Exception {
        String delivery = UUID.randomUUID().toString();
        assertThrows(IllegalStateException.class, () -> dao.original("clinic-a", delivery));
        assertThrows(IllegalStateException.class, () -> tx.withApprovalState(() -> {
            dao.insertOriginal("clinic-a", delivery, "0".repeat(64), new byte[41], NOW.get());
            throw new IllegalStateException("FAKE injected precommit failure");
        }));
        assertEquals(0, count("mycarlos_delivery_original", delivery));
    }

    static PortalMyCarlosDeliveryOutboxService service(Exchange exchange) {
        var portal = new PatientPortalService(settings, exchange, PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> true);
        var signer = PortalJobAssertionSigner.from(new PortalJobKey(PortalJob.DELIVERY_SYNC, PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-delivery-job"));
        return new PortalMyCarlosDeliveryOutboxService(tx, bindings, devices, dao,
                new PortalMyCarlosDeliveryCipher(1, Map.of(1, new byte[32])), current::get, portal,
                new PortalMyCarlosDeliveryClient(portal, signer), STAFF, CLOCK);
    }
    record Pair(PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) {}
    static Pair pair() throws Exception {
        var f = PortalMyCarlosDeliveryProducerUnitTest.fixture();
        ((ObjectNode) f.at("/sender/manifest_claims")).put("delivery_id", UUID.randomUUID().toString());
        var c = PortalMyCarlosDeliveryProducerUnitTest.compose(f);
        var s = new PortalMyCarlosDeliverySnapshot("clinic-a", 123, c.recipientId(), "https://portal.example.test",
                "https://portal.example.test/internal/carlos/patients/123/mycarlos/deliveries", credentials.get("config_sha256").textValue(),
                1, 0, credentials.get("acceptance_sha256").textValue(), c.deliveryId(), c.device(), c.signerKid(), c.documentSha256(),
                c.ciphertextSha256(), c.createdAt(), c.expiresAt(), c.bodySha256(), c.uploadBody());
        return new Pair(c, s);
    }
    static LoggedInInfo staff() {
        var session = new LoggedInInfo(); var provider = new Provider(); provider.setProviderNo("FAKE");
        var security = new Security(); security.setSecurityNo(1); session.setLoggedInProvider(provider); session.setLoggedInSecurity(security); return session;
    }
    static void removeDevice() {
        tx.withApprovalState(() -> { var d = devices.findByKidForUpdate("Ebz2sH2jVoqcdFhl1BmIOBRm1xuT-zW5bvFBcCeERPY").orElseThrow();
            devices.markRemoved(d, d.removed(UUID.randomUUID(), "FAKE", CLOCK.instant())); return null; });
    }
    static long count(String table, String delivery) throws Exception {
        if (!Set.of("mycarlos_delivery_original", "mycarlos_delivery_outbox").contains(table)) throw new IllegalArgumentException();
        try (var c = source.getConnection(); var q = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE delivery_id=?")) {
            q.setString(1, delivery); try (var r = q.executeQuery()) { assertTrue(r.next()); return r.getLong(1); }
        }
    }
    static String receipt(PortalMyCarlosDeliverySnapshot s, String jti) throws Exception {
        byte[] payload = PortalMyCarlosDeliveryCodec.canonical(Map.of("typ", "mycarlos-delivery-receipt", "aud", "carlos-delivery-receipt",
                "delivery_id", s.deliveryId(), "recipient_id", s.recipientId(), "device_key_id", s.device().kid(),
                "ciphertext_sha256", s.ciphertextSha256(), "document_sha256", s.documentSha256(), "picked_up_at", NOW.get(),
                "vault_head_link", "a".repeat(64), "jti", jti));
        return sign(payload);
    }
    static String sign(byte[] payload) throws Exception {
        byte[] seed = MessageDigest.getInstance("SHA-256").digest("FAKE delivery contract test key: device 1 ed25519".getBytes(StandardCharsets.UTF_8));
        byte[] prefix = HexFormat.of().parseHex("302e020100300506032b657004220420");
        var key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(ByteBuffer.allocate(prefix.length + seed.length).put(prefix).put(seed).array()));
        Signature signer = Signature.getInstance("Ed25519"); signer.initSign(key); signer.update(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }
    static final class Exchange implements PatientPortalHttpExchange {
        final PortalMyCarlosDeliverySnapshot snapshot;
        final List<byte[]> sent = new ArrayList<>(); final List<String> assertions = new ArrayList<>();
        CountDownLatch requestStarted; CountDownLatch allowReply;
        boolean failFirst; String status = "waiting"; int responseStatus = 200; String errorCode;
        Exchange(PortalMyCarlosDeliverySnapshot snapshot) { this.snapshot = snapshot; }
        public PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
            try { assertEquals(1, count("mycarlos_delivery_original", snapshot.deliveryId()));
                assertEquals(1, count("mycarlos_delivery_outbox", snapshot.deliveryId())); }
            catch (Exception failure) { throw new AssertionError("sender must commit before HTTP", failure); }
            sent.add(request.getEntity().getContent().readAllBytes()); assertions.add(request.getFirstHeader(PortalJobAssertionSigner.HEADER).getValue());
            if (requestStarted != null) {
                requestStarted.countDown();
                try { if (!allowReply.await(20, TimeUnit.SECONDS)) throw new IOException("FAKE timed out awaiting reply release"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IOException("FAKE interrupted reply"); }
            }
            if (failFirst && sent.size() == 1) throw new IOException("FAKE lost reply after server acceptance");
            if (responseStatus != 200) return new PatientPortalHttpResponse(responseStatus, errorCode == null ? "{}" : "{\"code\":\"" + errorCode + "\"}");
            return new PatientPortalHttpResponse(200, "{\"delivery_id\":\"" + snapshot.deliveryId() + "\",\"device_key_id\":\"" + snapshot.device().kid() + "\",\"status\":\"" + status + "\"}");
        }
    }
}
