/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDaoImpl;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Scope;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import jakarta.persistence.EntityManagerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * The device-code job (clinic code v1, mycarlos#141) on real MariaDB rows: one run issues and emails
 * a code, the next settles what the device sent. The portal is a FAKE in-process exchange, the email
 * a recorder, and the keys are the shared contract fixture's FAKE keys. Runs only against an owned
 * scratch schema named by {@code -Dmycarlos.code.test.credentials} (JSON: url, username, password,
 * schema); otherwise it is skipped.
 */
@Tag("integration")
class PortalMyCarlosDeviceCodeSyncMariaIntegrationTest {
    private static final String SCHEMA = "claude_scratch_c2_code_it_20261028";
    private static final ObjectMapper JSON = new ObjectMapper();
    static DriverManagerDataSource source;
    static AnnotationConfigApplicationContext context;
    static JsonNode data;
    static Scope scope;
    static PrivateKey clinicKey;
    static final AtomicLong NOW = new AtomicLong();
    static final Clock CLOCK = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochSecond(NOW.get()); }
    };

    @Configuration @EnableTransactionManagement(proxyTargetClass = true)
    static class TestContext {
        @Bean static PersistenceAnnotationBeanPostProcessor injection() { return new PersistenceAnnotationBeanPostProcessor(); }
        @Bean DataSource dataSource() { return source; }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var unit = new DefaultPersistenceUnitManager();
            unit.setPersistenceXmlLocations(new String[0]);
            unit.setMappingResources(new String[0]);
            unit.setDefaultPersistenceUnitName("entityManagerFactory");
            unit.setManagedTypes(PersistenceManagedTypes.of(Property.class.getName()));
            unit.setDefaultDataSource(ds);
            unit.setPersistenceUnitPostProcessors(info -> { info.setTransactionType(jakarta.persistence.PersistenceUnitTransactionType.RESOURCE_LOCAL);
                info.setExcludeUnlistedClasses(true); });
            unit.afterPropertiesSet();
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setPersistenceUnitManager(unit);
            factory.setPersistenceUnitName("entityManagerFactory");
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
        String path = System.getProperty("mycarlos.code.test.credentials");
        Assumptions.assumeTrue(path != null, "owned scratch MariaDB schema credentials required");
        JsonNode credentials = JSON.readTree(Files.readAllBytes(Path.of(path)));
        assertEquals(SCHEMA, credentials.get("schema").textValue());
        source = new DriverManagerDataSource(credentials.get("url").textValue(), credentials.get("username").textValue(),
                credentials.get("password").textValue());
        try (var c = source.getConnection(); var q = c.createStatement(); var r = q.executeQuery("SELECT DATABASE()")) {
            assertTrue(r.next());
            assertEquals(SCHEMA, r.getString(1));
        }
        context = new AnnotationConfigApplicationContext(TestContext.class);
        data = PortalMyCarlosDeviceApprovalStateTestSupport.fixture();
        scope = new Scope(data.path("clinic_id").asText(), data.path("recipient_id").asText(), data.path("public_portal_base_url").asText());
        clinicKey = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(data.path("keys").path("clinic"), "FAKE_private_pkcs8_b64url", "EC");
    }

    @AfterAll static void close() { if (context != null) context.close(); }

    /** Every test starts from empty approval rows and the fixture's published clinic key. */
    @BeforeEach void reset() throws Exception {
        NOW.set(data.path("now").longValue());
        try (var c = source.getConnection(); var q = c.createStatement()) {
            for (String table : new String[] {"mycarlos_device_approval_code", "mycarlos_device_approval_outbox",
                    "mycarlos_accepted_device", "mycarlos_device_approval_challenge", "mycarlos_patient_binding", "property"}) {
                q.executeUpdate("DELETE FROM " + table);
            }
        }
        var key = data.path("keys").path("clinic");
        var inventory = PortalMyCarlosKeyInventory.create(scope.clinicId(), List.of(new PatientPortalClinicKeysPublishRequest.Key(
                key.path("kid").asText(), key.path("spki").asText(), "active", null, null)));
        var root = JSON.createObjectNode();
        root.put("schema_version", 1);
        root.set("catalogue", JSON.readTree(inventory.toStoredJson()));
        root.putNull("publication");
        try (var c = source.getConnection(); var q = c.prepareStatement("INSERT INTO property (name,value,provider_no) VALUES (?,?,'')")) {
            q.setString(1, PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
            q.setString(2, root.toString());
            q.executeUpdate();
        }
    }

    /** A FAKE portal: one first-device request, the codes the test queues, and every call it got. */
    static final class FakePortal {
        final List<String> calls = new ArrayList<>();
        final List<String> codes = new ArrayList<>();
        final List<JsonNode> decisions = new ArrayList<>();
        String intent;
        /** Requests after the first are an endless backlog for patient 2, 20 a page. */
        boolean backlog;
        /** "Consumed" answers wrongly, as if the call failed. */
        boolean failConsumed;

        String call(String permission, String method, String path, byte[] body, int status) {
            calls.add(method + " " + path);
            try {
                if (path.contains("device-approval-requests?")) {
                    long after = Long.parseLong(path.replaceAll(".*after=([0-9]+).*", "$1"));
                    var page = JSON.createObjectNode();
                    var rows = page.putArray("requests");
                    long last = after;
                    if (after == 0) {
                        rows.addObject().put("sequence", "1").put("demographic_no", 1).put("intent", intent);
                        last = 1;
                    } else if (backlog) {
                        for (long sequence = after + 1; sequence <= after + 20; sequence++) {
                            rows.addObject().put("sequence", Long.toString(sequence)).put("demographic_no", 2)
                                    .put("intent", intent);
                            last = sequence;
                        }
                    }
                    page.put("last_sequence", Long.toString(last));
                    return backlog && last > after ? page.put("next_cursor", Long.toString(last)).toString()
                            : page.putNull("next_cursor").toString();
                }
                if (path.contains("device-approval-codes?")) {
                    long after = Long.parseLong(path.replaceAll(".*after=([0-9]+).*", "$1"));
                    var page = JSON.createObjectNode();
                    var rows = page.putArray("codes");
                    long last = after;
                    for (int i = (int) after; i < codes.size(); i++) {
                        rows.add(JSON.readTree(codes.get(i)));
                        last = i + 1;
                    }
                    return page.put("last_sequence", Long.toString(last)).putNull("next_cursor").toString();
                }
                if (path.endsWith("/decision")) decisions.add(JSON.readTree(body));
                if (path.endsWith("/consumed")) return failConsumed ? "{\"status\":\"not-this\"}" : "{\"status\":\"consumed\"}";
                return "{\"status\":\"stored\"}";
            } catch (java.io.IOException failure) {
                throw new IllegalStateException(failure);
            }
        }

        void send(UUID request, String challengeId, String codeR) {
            int sequence = codes.size() + 1;
            long attempt = codes.stream().filter(row -> row.contains(request.toString())).count() + 1;
            codes.add("{\"sequence\":\"" + sequence + "\",\"request_id\":\"" + request + "\",\"challenge_id\":\""
                    + challengeId + "\",\"attempt\":" + attempt + ",\"code_r\":\"" + codeR + "\",\"submitted_at\":" + NOW.get() + "}");
        }
    }

    /** Records the one email per code; the address is FAKE. */
    static final class FakeMail implements PortalMyCarlosStaffApprovalService.CodeMail {
        final List<String> sent = new ArrayList<>();
        int reached;
        int tries;
        boolean reachable = true;
        boolean failing;
        public PortalMyCarlosStaffApprovalService.CodeReach reach(int patient) {
            reached++;
            return reachable && patient == 1 ? PortalMyCarlosStaffApprovalService.CodeReach.to("fake.patient@example.com")
                    : PortalMyCarlosStaffApprovalService.CodeReach.staff(PortalMyCarlosStaffApprovalService.NO_USABLE_EMAIL);
        }
        public boolean send(int patient, String address, String displayCode, long minutes) {
            assertTrue(minutes >= 1 && minutes <= 10);
            tries++;
            if (failing) return false;
            sent.add(displayCode);
            return true;
        }
    }

    private PortalMyCarlosStaffApprovalService service(FakePortal portal) {
        var factory = context.getBean(EntityManagerFactory.class);
        var transactions = context.getBean(PortalMyCarlosDeviceApprovalTransactions.class);
        var settings = PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://localhost:8455",
                PatientPortalSettings.CLINIC_ID_KEY, scope.clinicId(),
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff-key",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        var current = PortalMyCarlosDeviceApprovalService.Current.fromValidated(settings, scope.canonicalPublicBase(), true, true);
        var security = mock(SecurityInfoManager.class);
        var key = data.path("keys").path("clinic");
        var signer = new PortalMyCarlosDeviceApprovalService.Signer() {
            public PortalMyCarlosDeviceApprovalService.SigningIdentity currentIdentity() {
                return new PortalMyCarlosDeviceApprovalService.SigningIdentity(key.path("kid").asText(), key.path("spki").asText());
            }
            public String sign(Kind kind, Map<String, Object> claims, Scope signedScope,
                    PortalMyCarlosDeviceApprovalService.SigningIdentity identity) {
                return PortalMyCarlosDeviceApprovalCrypto.sign(kind, claims, signedScope, clinicKey);
            }
        };
        var random = new SecureRandom();
        return new PortalMyCarlosStaffApprovalService(transactions, factory, security, context.getBean(PropertyDao.class),
                () -> current,
                (sourceAuthorization, transactional) -> new PortalMyCarlosDeviceApprovalService(transactions,
                        new PortalMyCarlosPatientBindingDao(factory), new PortalMyCarlosDeviceApprovalChallengeDao(factory),
                        new PortalMyCarlosAcceptedDeviceDao(factory), new PortalMyCarlosDeviceApprovalOutboxDao(factory),
                        security, transactional, sourceAuthorization, signer, () -> {
                            byte[] id = new byte[32];
                            random.nextBytes(id);
                            return new Bytes(id);
                        }, UUID::randomUUID, CLOCK, audit -> {}),
                ignored -> new PortalMyCarlosApprovalClient(portal::call), CLOCK);
    }

    private static PatientPortalService portalService() {
        var settings = PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://localhost:8455",
                PatientPortalSettings.CLINIC_ID_KEY, scope.clinicId(),
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff-key",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        return new PatientPortalService(settings, request -> {
            throw new AssertionError("no real HTTP in this test");
        }, PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> true);
    }

    private static FakePortal portal() {
        var portal = new FakePortal();
        portal.intent = data.path("flows").path("first").path("intent").path("compact").asText();
        return portal;
    }

    private static String sql(String query) throws Exception {
        try (var c = source.getConnection(); var q = c.createStatement(); var r = q.executeQuery(query)) {
            if (!r.next()) return null;
            Object value = r.getObject(1);
            return value == null ? null : String.valueOf(value);
        }
    }

    private static UUID request() {
        return UUID.fromString(data.path("flows").path("first").path("intent").path("claims").path("request_id").asText());
    }

    private static String challengeId() throws Exception {
        try (var c = source.getConnection(); var q = c.createStatement();
                var r = q.executeQuery("SELECT challenge_id FROM mycarlos_device_approval_challenge")) {
            assertTrue(r.next());
            return PortalMyCarlosDeviceApprovalCodec.base64(r.getBytes(1));
        }
    }

    @Test
    void shouldEmailCodeThenAcceptDevice_whenRightCodeFollowsWrongOne() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        var first = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(1, first.challenged());
        assertEquals(1, first.emailed());
        assertEquals(1, mail.sent.size());
        assertEquals("code", sql("SELECT approval_kind FROM mycarlos_device_approval_challenge"));
        assertNotNull(sql("SELECT emailed_at FROM mycarlos_device_approval_code"));
        assertTrue(portal.calls.stream().anyMatch(call -> call.startsWith("PUT ") && call.endsWith("/challenge")));

        // The emailed code's check digits are the clinic kid's, for this device and request.
        String code = mail.sent.getFirst().replace(" ", "");
        var intentClaims = data.path("flows").path("first").path("intent").path("claims");
        assertEquals(code, PortalMyCarlosClinicCode.code(data.path("keys").path("clinic").path("kid").asText(),
                intentClaims.path("candidate_device_key_id").asText(), request().toString(), code.substring(0, 6)));
        // Only a keyed hash is stored, never the code.
        assertNull(sql("SELECT 1 FROM mycarlos_device_approval_code WHERE INSTR(code_mac, '" + code.substring(0, 6) + "') > 0"));

        String wrong = code.startsWith("000000") ? "000001" : "000000";
        portal.send(request(), challengeId(), wrong);
        portal.send(request(), challengeId(), code.substring(0, 6));
        NOW.addAndGet(30);
        var second = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(1, second.wrong());
        assertEquals(1, second.matched());
        assertEquals(1, second.accepted());
        assertEquals("code", sql("SELECT approval_kind FROM mycarlos_accepted_device"));
        assertNull(sql("SELECT approved_by_provider_no FROM mycarlos_accepted_device"));
        assertEquals("1", sql("SELECT wrong_attempts FROM mycarlos_device_approval_code"));
        assertEquals("accepted", portal.decisions.getLast().path("status").asText());
        var acceptance = PortalMyCarlosDeviceApprovalCodec.decode(Kind.ACCEPTANCE,
                portal.decisions.getLast().path("acceptance").asText(), scope).claims();
        assertEquals("code", acceptance.text("approval_kind"));
        assertNull(acceptance.values().get("approval_sha256"));
        assertEquals(2, portal.calls.stream().filter(call -> call.endsWith("/consumed")).count());
        assertEquals("2", sql("SELECT value FROM property WHERE name LIKE 'mycarlos.approval.code.cursor.%'"));

        // A third run finds nothing new: no second email, no second device.
        var third = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(0, third.emailed() + third.accepted() + third.challenged());
        assertEquals(1, mail.sent.size());
        assertEquals("1", sql("SELECT COUNT(*) FROM mycarlos_accepted_device"));
    }

    @Test
    void shouldRefuseRequestForGood_afterFiveWrongCodes() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        String code = mail.sent.getFirst().replace(" ", "").substring(0, 6);
        for (int i = 0; i < 5; i++) {
            String wrong = String.format("%06d", (Integer.parseInt(code) + 1 + i) % 1_000_000);
            portal.send(request(), challengeId(), wrong);
        }
        var run = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(5, run.wrong());
        assertEquals(1, run.refused());
        assertEquals("code_attempts", sql("SELECT cancellation_reason FROM mycarlos_device_approval_challenge"));
        assertEquals("5", sql("SELECT wrong_attempts FROM mycarlos_device_approval_code"));
        assertNotNull(sql("SELECT refused_at FROM mycarlos_device_approval_code"));
        assertEquals("refused", portal.decisions.getLast().path("status").asText());
        assertEquals("code_attempts", portal.decisions.getLast().path("reason").asText());
        assertEquals("0", sql("SELECT COUNT(*) FROM mycarlos_accepted_device"));
    }

    @Test
    void shouldSettleCodeAndMoveOn_whenPatientWasLinkedAnotherWayMeanwhile() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        // Staff linked another device for this patient before the right code came in.
        try (var c = source.getConnection(); var q = c.prepareStatement("INSERT INTO mycarlos_patient_binding "
                + "(clinic_id,demographic_no,recipient_id,public_portal_base_url,internal_portal_origin,"
                + "config_binding_sha256,root_device_key_id,created_at) VALUES (?,1,'FAKE-other-recipient',?,"
                + "'https://localhost:8455',?,?,'2026-10-28 10:00:00')")) {
            q.setString(1, scope.clinicId());
            q.setString(2, scope.canonicalPublicBase());
            q.setBytes(3, new byte[32]);
            // A FAKE device key id: 43 characters that decode to 32 zero bytes.
            q.setString(4, "A".repeat(43));
            q.executeUpdate();
        }
        portal.send(request(), challengeId(), mail.sent.getFirst().replace(" ", "").substring(0, 6));
        var run = assertDoesNotThrow(() -> job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail));
        assertEquals(1, run.matched());
        assertEquals(0, run.accepted());
        assertEquals("0", sql("SELECT COUNT(*) FROM mycarlos_accepted_device"));
        // The code is marked used and the cursor moves past it, so later codes are not held back.
        assertEquals(1, portal.calls.stream().filter(call -> call.endsWith("/consumed")).count());
        assertEquals("1", sql("SELECT value FROM property WHERE name LIKE 'mycarlos.approval.code.cursor.%'"));
    }

    @Test
    void shouldWaitForStaff_whenPolicyIsCodeAndStaff() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE_AND_STAFF, mail);
        assertEquals("code_and_staff", sql("SELECT policy FROM mycarlos_device_approval_code"));
        portal.send(request(), challengeId(), mail.sent.getFirst().replace(" ", "").substring(0, 6));
        var run = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE_AND_STAFF, mail);
        assertEquals(1, run.matched());
        assertEquals(0, run.accepted());
        assertNotNull(sql("SELECT matched_at FROM mycarlos_device_approval_code"));
        assertEquals("0", sql("SELECT COUNT(*) FROM mycarlos_accepted_device"));
        assertTrue(portal.decisions.isEmpty());
    }

    @Test
    void shouldLeaveRequestForStaffAndSaySoOnce_whenPolicyIsStaffOnly() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        try (var log = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(PortalMyCarlosStaffApprovalService.class)) {
            job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.STAFF_ONLY, mail);
            job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.STAFF_ONLY, mail);
            assertEquals(List.of("myCarlos device approval goes to staff: policy staff_only"), log.messages());
        }
        assertEquals("0", sql("SELECT COUNT(*) FROM mycarlos_device_approval_challenge"));
        assertEquals(0, mail.reached);
        assertTrue(mail.sent.isEmpty());
    }

    @Test
    void shouldLeaveUnreachablePatientForStaff_askingOnlyOnce() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        mail.reachable = false;
        var job = service(portal);
        try (var log = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(PortalMyCarlosStaffApprovalService.class)) {
            job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
            job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
            // Fixed text and reason only: no address, patient or request in it.
            assertEquals(List.of("myCarlos device approval goes to staff: no usable chart email"), log.messages());
        }
        // The chart and consent are looked at once, not every run.
        assertEquals(1, mail.reached);
        assertEquals("0", sql("SELECT COUNT(*) FROM mycarlos_device_approval_challenge"));
    }

    @Test
    void shouldEndRequestAsExpiredWithoutCountingCode_whenCodeWasMadeBeforeRestart() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        String right = mail.sent.getFirst().replace(" ", "").substring(0, 6);
        // The code was hashed under a key this server no longer has, as after a restart.
        try (var c = source.getConnection(); var q = c.createStatement()) {
            q.executeUpdate("UPDATE mycarlos_device_approval_code SET code_key_id=UNHEX(REPEAT('00',16))");
        }
        portal.send(request(), challengeId(), right);
        var run = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(0, run.wrong());
        assertEquals(0, run.matched());
        assertEquals(1, run.ended());
        assertEquals("0", sql("SELECT wrong_attempts FROM mycarlos_device_approval_code"));
        assertEquals("challenge_expired", sql("SELECT cancellation_reason FROM mycarlos_device_approval_challenge"));
        assertEquals("expired", portal.decisions.getLast().path("status").asText());
        assertEquals("challenge_expired", portal.decisions.getLast().path("reason").asText());
        assertEquals(1, portal.calls.stream().filter(call -> call.endsWith("/consumed")).count());
        assertEquals("0", sql("SELECT COUNT(*) FROM mycarlos_accepted_device"));
    }

    @Test
    void shouldEndOpenRequestsAfterRestart_beforeAnyCodeIsTyped() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        try (var c = source.getConnection(); var q = c.createStatement()) {
            q.executeUpdate("UPDATE mycarlos_device_approval_code SET code_key_id=UNHEX(REPEAT('00',16))");
        }
        var run = job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(1, run.ended());
        assertEquals("expired", portal.decisions.getLast().path("status").asText());
        assertEquals(1, mail.sent.size());
    }

    @Test
    void shouldCountWrongCodeOnce_whenMarkingItUsedFails() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        String code = mail.sent.getFirst().replace(" ", "").substring(0, 6);
        portal.send(request(), challengeId(), code.startsWith("000000") ? "000001" : "000000");
        portal.failConsumed = true;
        assertEquals(1, job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail).wrong());
        portal.failConsumed = false;
        // Read again by a later run (the portal still lists it): not counted again.
        assertEquals(0, job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail).wrong());
        assertEquals("1", sql("SELECT wrong_attempts FROM mycarlos_device_approval_code"));
        assertEquals("1", sql("SELECT value FROM property WHERE name LIKE 'mycarlos.approval.code.cursor.%'"));
    }

    @Test
    void shouldSettleCodesFirst_whileRequestBacklogIsReadPageByPage() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        portal.backlog = true;
        portal.send(request(), challengeId(), mail.sent.getFirst().replace(" ", "").substring(0, 6));
        var run = assertDoesNotThrow(() -> job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail));
        assertEquals(1, run.accepted());
        int pages = PortalMyCarlosStaffApprovalService.PAGES_PER_RUN;
        // One run reads its page budget of the backlog, the next run the next part.
        assertEquals(Long.toString(1 + 20L * pages), sql("SELECT value FROM property WHERE name LIKE 'mycarlos.approval.code.requests.%'"));
        assertDoesNotThrow(() -> job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail));
        assertEquals(Long.toString(1 + 40L * pages), sql("SELECT value FROM property WHERE name LIKE 'mycarlos.approval.code.requests.%'"));
    }

    @Test
    void shouldBackOffFailingSend_thenSendNewCode() throws Exception {
        var portal = portal();
        var mail = new FakeMail();
        mail.failing = true;
        var job = service(portal);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(1, mail.tries);
        assertNull(sql("SELECT emailed_at FROM mycarlos_device_approval_code"));
        // No challenge yet: the device is not asked for a code that never went out.
        assertTrue(portal.calls.stream().noneMatch(call -> call.endsWith("/challenge")));
        NOW.addAndGet(10);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(1, mail.tries);
        mail.failing = false;
        NOW.addAndGet(25);
        job.syncCodes(portalService(), PortalMyCarlosApprovalPolicy.CODE, mail);
        assertEquals(2, mail.tries);
        assertEquals(1, mail.sent.size());
        assertNotNull(sql("SELECT emailed_at FROM mycarlos_device_approval_code"));
        assertTrue(portal.calls.stream().anyMatch(call -> call.endsWith("/challenge")));
    }
}
