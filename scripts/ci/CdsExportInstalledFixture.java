/*
 * Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.login;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.OutboundEmailArchiveKeyringService;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import java.math.BigInteger;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.security.cert.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * An ordinary native chart in an exclusively owned, freshly migrated CI database.
 * No import metadata, mock session, lease or publication is inserted. Production
 * prerequisites run their real zero-census guards before creating missing keys.
 */
public final class CdsExportInstalledFixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path CONFIG = Path.of("/etc/carlos-emr/carlos.properties");
    private static final Path ROOT = Path.of("/opt/cds-native-export-ci");
    private static final Path PRIVATE = Path.of("/run/cds-export-ci");
    private static final String PROVIDER = "989873";
    private static final String NOTE = "Native café 😀 </unotes> literal patient note";

    public static void main(String[] args) {
        try {
            if (args.length < 2
                    || !Files.isRegularFile(Path.of("/.dockerenv"))
                    || ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).intValue()
                            != 0) {
                throw new IllegalStateException("Disposable root container required");
            }
            Properties jdbc = new Properties();
            Path file = Path.of(args[1]);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || (((Number) Files.getAttribute(file, "unix:mode")).intValue() & 0777)
                            != 0600) {
                throw new IllegalStateException("Private JDBC configuration required");
            }
            try (var input = Files.newInputStream(file)) {
                jdbc.load(input);
            }
            String url = jdbc.getProperty("jdbcUrl", "");
            if (!url.matches("jdbc:mysql://[^/]+/carlos_cds_test"))
                throw new IllegalStateException("Owned CI database required");
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection db =
                    DriverManager.getConnection(
                            url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"))) {
                if (!"carlos_cds_test".equals(text(db, "SELECT DATABASE()")))
                    throw new IllegalStateException("Database mismatch");
                switch (args[0]) {
                    case "prepare" -> prepare(db, jdbc, url, args[2]);
                    case "verify" -> verify(db, args[2], args[3]);
                    case "decrypt" -> decrypt(Path.of(args[2]), Path.of(args[3]));
                    default -> throw new IllegalArgumentException("Unknown fixture operation");
                }
            }
        } catch (Exception failure) {
            // Never render database/config/patient values or exception messages.
            String at =
                    Arrays.stream(failure.getStackTrace())
                            .filter(
                                    f ->
                                            f.getClassName()
                                                    .equals(
                                                            CdsExportInstalledFixture.class
                                                                    .getName()))
                            .map(f -> f.getMethodName() + ":" + f.getLineNumber())
                            .findFirst()
                            .orElse("unknown");
            // Disposable CI helper emits only a fixed code, class and its own method/line;
            // no message, value or full stack is rendered.
            // nosemgrep: java.lang.security.audit.active-debug-code-getstacktrace.active-debug-code-getstacktrace
            System.err.println(
                    "CDS_EXPORT_INSTALLED_FIXTURE_FAILED:"
                            + failure.getClass().getSimpleName()
                            + ":"
                            + at);
            System.exit(1);
        }
    }

    private static void prepare(Connection db, Properties jdbc, String url, String publicBase)
            throws Exception {
        if (!publicBase.matches("https://127\\.0\\.0\\.1:[0-9]+/carlos"))
            throw new IllegalStateException("Owned loopback front door required");
        if (Files.exists(CONFIG, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(PRIVATE, LinkOption.NOFOLLOW_LINKS)
                || count(db, "SELECT COUNT(*) FROM cds_import_run") != 0
                || count(db, "SELECT COUNT(*) FROM cds_export_run") != 0
                || count(db, "SELECT COUNT(*) FROM provider WHERE provider_no=?", PROVIDER) != 0) {
            throw new IllegalStateException("Existing runtime or accepted work is never replaced");
        }
        directory(PRIVATE, false, false);
        directory(ROOT, false, true);
        for (String child : List.of("store", "import", "documents", "delivery", "data"))
            directory(ROOT.resolve(child), true, false);
        for (String child : List.of("selection", "policy", "keys"))
            directory(ROOT.resolve(child), false, true);
        for (String child : List.of("recipients", "certificates", "audit"))
            directory(ROOT.resolve("policy").resolve(child), false, true);
        for (String child :
                List.of(
                        "carlos",
                        "carlos/incomingdocs",
                        "carlos/billing",
                        "carlos/billing/invoices",
                        "carlos/fax-incoming"))
            directory(ROOT.resolve("data").resolve(child), true, false);
        String username =
                "cdsnative" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String password = UUID.randomUUID() + "PublicSynthetic!";
        String pin = Integer.toString(1000 + new SecureRandom().nextInt(9000));
        db.setAutoCommit(false);
        int patient;
        try {
            update(
                    db,
                    "INSERT INTO provider(provider_no,last_name,first_name,provider_type,specialty,sex,status,ohip_no,lastUpdateDate) VALUES (?,'Native Export','Synthetic','doctor','','U','1','999891',NOW())",
                    PROVIDER);
            for (String role : List.of("doctor", "admin")) {
                if (count(db, "SELECT COUNT(*) FROM secRole WHERE role_name=?", role) != 1)
                    throw new IllegalStateException("Migrated role required");
                update(
                        db,
                        "INSERT INTO secUserRole(provider_no,role_name,activeyn,lastUpdateDate) VALUES (?,?,1,NOW())",
                        PROVIDER,
                        role);
            }
            int facility =
                    (int) count(db, "SELECT COALESCE(MIN(id),0) FROM Facility WHERE disabled=0");
            if (facility == 0) {
                update(
                        db,
                        "INSERT INTO Facility(name,hic,disabled,orgId,sectorId,enableHealthNumberRegistry,enableDigitalSignatures,enableOcanForms,enableCbiForm,enableAnonymous,enablePhoneEncounter,lastUpdated,enableEncounterTime,enableEncounterTransportationTime,rxInteractionWarningLevel,displayAllVacancies) VALUES('CDS native CI',0,0,0,0,1,0,0,0,0,0,NOW(),0,0,0,0)");
                facility = (int) count(db, "SELECT LAST_INSERT_ID()");
            }
            update(
                    db,
                    "INSERT INTO provider_facility(provider_no,facility_id) VALUES (?,?)",
                    PROVIDER,
                    facility);
            update(
                    db,
                    "INSERT INTO security(user_name,password,pin,provider_no,b_ExpireSet,b_LocalLockSet,b_RemoteLockSet,forcePasswordReset,passwordUpdateDate,pinUpdateDate) VALUES (?,?,?,?,0,0,0,0,NOW(),NOW())",
                    username,
                    EncryptionUtils.hash(password),
                    EncryptionUtils.hash(pin),
                    PROVIDER);
            int security = (int) count(db, "SELECT LAST_INSERT_ID()");
            update(
                    db,
                    "INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,date_of_birth,sex,patient_status,address,city,province,postal,phone,provider_no,lastUpdateDate) VALUES ('SYNTHETIC Native Export','Native','2000','01','02','F','AC','17 Native Fixture Road','Ottawa','ON','K1A0B1','613-555-0198',?,NOW())",
                    PROVIDER);
            patient = (int) count(db, "SELECT LAST_INSERT_ID()");
            update(
                    db,
                    "INSERT INTO demographiccust(demographic_no,content) VALUES (?,?)",
                    patient,
                    "<unotes>" + NOTE + "</unotes>");
            db.commit();
            writePrivate(
                    PRIVATE.resolve("fixture.json"),
                    JSON.writeValueAsBytes(
                            Map.of(
                                    "provider",
                                    PROVIDER,
                                    "patient",
                                    patient,
                                    "security",
                                    security,
                                    "username",
                                    username,
                                    "password",
                                    password,
                                    "pin",
                                    pin)),
                    false);
        } catch (Exception failure) {
            db.rollback();
            throw failure;
        } finally {
            db.setAutoCommit(true);
        }

        writePrivate(
                ROOT.resolve("selection/patients.json"),
                JSON.writeValueAsBytes(Map.of("version", 1, "patients", List.of(patient))),
                true);
        byte[] master = new byte[32];
        new SecureRandom().nextBytes(master);
        try {
            writePrivate(ROOT.resolve("keys/native.key"), master, true);
        } finally {
            Arrays.fill(master, (byte) 0);
        }
        policy();
        Properties config = new Properties();
        int slash = url.indexOf('/', "jdbc:mysql://".length());
        config.setProperty("db_uri", url.substring(0, slash + 1));
        config.setProperty(
                "db_name",
                url.substring(slash + 1)
                        + "?zeroDateTimeBehavior=round&useOldAliasMetadataBehavior=true"
                        + "&jdbcCompliantTruncation=false&characterEncoding=UTF-8&connectionCollation=utf8mb4_general_ci");
        config.setProperty("db_type", "mysql");
        config.setProperty("db_driver", "com.mysql.cj.jdbc.Driver");
        config.setProperty("db_username", jdbc.getProperty("jdbcUser"));
        config.setProperty("db_password", jdbc.getProperty("jdbcPassword"));
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        config.setProperty("billregion", "ON");
        config.setProperty("carlos.flyway.onBoot", "validate");
        config.setProperty("db_default_use_information_schema", "false");
        config.setProperty("cds.export.enabled", "true");
        config.setProperty("cds.export.worker.enabled", "true");
        config.setProperty("cds.import.worker.enabled", "false");
        config.setProperty("cds.import.enabled", "false");
        config.setProperty("cds.import.clinicalZone", "America/Toronto");
        config.setProperty("cds.export.clinicalZone", "America/Toronto");
        config.setProperty("cds.export.operator.root.providers", PROVIDER);
        config.setProperty("cds.export.policyRoot", ROOT.resolve("policy").toString());
        config.setProperty("cds.export.masterKeyRoot", ROOT.resolve("keys").toString());
        config.setProperty("cds.export.masterKey.active", "native");
        config.setProperty("cds.export.store.active", "native");
        config.setProperty("cds.export.store.native.root", ROOT.resolve("store").toString());
        config.setProperty("cds.export.selectionRoot", ROOT.resolve("selection").toString());
        config.setProperty("cds.import.stagingRoot", ROOT.resolve("import").toString());
        // Same deployment keys initialized by the actual paired CLI package.
        config.setProperty("BASE_DOCUMENT_DIR", ROOT.resolve("data") + "/");
        config.setProperty("DOCUMENT_DIR", ROOT.resolve("documents") + "/");
        config.setProperty(
                "INCOMINGDOCUMENT_DIR", ROOT.resolve("data/carlos/incomingdocs").toString());
        config.setProperty("INVOICE_DIR", ROOT.resolve("data/carlos/billing/invoices").toString());
        config.setProperty("FAX_INCOMING_DIR", ROOT.resolve("data/carlos/fax-incoming").toString());
        config.setProperty("log.purge.outputdir", ROOT.resolve("documents") + "/");
        config.setProperty("project_home", "carlos");
        config.setProperty("tomcat_path", "/opt/cds-tomcat-ci/");
        config.setProperty("ws_endpoint_url_base", publicBase);
        config.setProperty(
                "carlos.flyway.locations",
                "classpath:db/migration/common,classpath:db/migration/on");
        config.setProperty(
                "email.archive.keyring.file",
                ROOT.resolve("documents/email-keyring.json").toString());
        Files.createDirectories(CONFIG.getParent());
        try (var out = Files.newOutputStream(CONFIG, StandardOpenOption.CREATE_NEW)) {
            config.store(out, "Owned ordinary native CDS export CI");
        }
        groupReadable(CONFIG);
        CarlosProperties properties = CarlosProperties.getInstance();
        properties.applyParsedDeploymentProperties(config, CONFIG.toString());
        String generated =
                properties.getOrCreateEncryptionKey(
                        () -> {
                            var census = EncryptedDataCountLoader.fromProperties(properties).load();
                            if (!census.complete() || census.total() != 0)
                                throw new IllegalStateException(
                                        "Complete empty encryption census required");
                        });
        properties.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, generated);
        var source =
                new DriverManagerDataSource(
                        url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"));
        var keyring = new OutboundEmailArchiveKeyringService(source);
        keyring
                .afterPropertiesSet(); // Real orphan-protection census, no acknowledgement or
                                       // rotation.
        Path emailKey = ROOT.resolve("documents/email-keyring.json");
        Files.setOwner(
                emailKey,
                emailKey.getFileSystem()
                        .getUserPrincipalLookupService()
                        .lookupPrincipalByName("carlos"));
        Files.setPosixFilePermissions(emailKey, PosixFilePermissions.fromString("rw-------"));
        System.out.println(
                "{\"prepared\":true,\"nativePatients\":1,\"importRuns\":0,\"guardedFreshKeys\":true}");
    }

    private static void policy() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        var now = Instant.now();
        var name = new X500Name("CN=CDS SYNTHETIC NATIVE CI RECIPIENT,O=example.invalid");
        var builder =
                new JcaX509v3CertificateBuilder(
                        name,
                        new BigInteger(128, new SecureRandom()),
                        java.util.Date.from(now.minusSeconds(3600)),
                        java.util.Date.from(now.plusSeconds(7 * 86400)),
                        name,
                        pair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyEncipherment));
        var certificate =
                new JcaX509CertificateConverter()
                        .setProvider(new BouncyCastleProvider())
                        .getCertificate(
                                builder.build(
                                        new JcaContentSignerBuilder("SHA256withRSA")
                                                .build(pair.getPrivate())));
        byte[] der = certificate.getEncoded();
        writePrivate(PRIVATE.resolve("recipient.pk8"), pair.getPrivate().getEncoded(), false);
        writePrivate(PRIVATE.resolve("recipient.der"), der, false);
        Path policy = ROOT.resolve("policy");
        byte[] sender =
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol",
                                1,
                                "enabled",
                                true,
                                "namespace",
                                "native-ci",
                                "physicianGroup",
                                "Synthetic native CI",
                                "groupContactInformation",
                                "ci@example.invalid"));
        byte[] recipient =
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol",
                                1,
                                "enabled",
                                true,
                                "id",
                                "native",
                                "certificateId",
                                "native",
                                "certificateSha256",
                                sha(der),
                                "profiles",
                                List.of("omd-5.2")));
        byte[] audit =
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol",
                                1,
                                "enabled",
                                true,
                                "id",
                                "native",
                                "description",
                                "Owned synthetic transfer audit arrangement",
                                "recipientIds",
                                List.of("native"),
                                "providerNos",
                                List.of(PROVIDER),
                                "profiles",
                                List.of("omd-5.2")));
        writePrivate(policy.resolve("sender.json"), sender, true);
        writePrivate(policy.resolve("recipients/native.json"), recipient, true);
        writePrivate(policy.resolve("certificates/native.der"), der, true);
        writePrivate(policy.resolve("audit/native.json"), audit, true);
        writePrivate(
                policy.resolve("policy.json"),
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol",
                                1,
                                "enabled",
                                true,
                                "senderSha256",
                                sha(sender),
                                "recipients",
                                Map.of("native", sha(recipient)),
                                "certificates",
                                Map.of("native", sha(der)),
                                "auditPlans",
                                Map.of("native", sha(audit)))),
                true);
    }

    private static void verify(Connection db, String cliRun, String browserRun) throws Exception {
        UUID.fromString(cliRun);
        UUID.fromString(browserRun);
        var fixture = JSON.readTree(PRIVATE.resolve("fixture.json").toFile());
        int patient = fixture.path("patient").intValue();
        int security = fixture.path("security").intValue();
        if (cliRun.equals(browserRun)
                || count(db, "SELECT COUNT(*) FROM cds_export_run") != 2
                || count(db, "SELECT COUNT(*) FROM cds_import_run") != 0
                || count(db, "SELECT COUNT(*) FROM cds_import_patient") != 0
                || count(db, "SELECT COUNT(*) FROM cds_import_field") != 0)
            throw new IllegalStateException("Independent native jobs required");
        String cliCompatibility = null, browserCompatibility = null;
        for (String run : List.of(cliRun, browserRun)) {
            if (count(
                            db,
                            "SELECT COUNT(*) FROM cds_export_run WHERE id=? AND state='READY' AND expected_patients=1 AND processed_patients=1 AND blocked_patients=0 AND cancel_requested=0 AND artifact_bytes>0 AND owner_token IS NULL",
                            run)
                    != 1) throw new IllegalStateException("Real complete ledger required");
            String operator = text(db, "SELECT operator FROM cds_export_run WHERE id=?", run);
            if (run.equals(cliRun)) {
                if (!operator.equals("unix:root"))
                    throw new IllegalStateException("Root delegation required");
                cliCompatibility =
                        text(db, "SELECT compatibility_digest FROM cds_export_run WHERE id=?", run);
            } else {
                if (!operator.equals("web:" + security))
                    throw new IllegalStateException("Authenticated browser actor required");
                browserCompatibility =
                        text(db, "SELECT compatibility_digest FROM cds_export_run WHERE id=?", run);
            }
        }
        if (!Objects.equals(cliCompatibility, browserCompatibility))
            throw new IllegalStateException("CLI and browser compatibility differ");
        if (count(
                                db,
                                "SELECT COUNT(*) FROM demographic WHERE demographic_no=? AND BINARY first_name=BINARY 'Native' AND BINARY last_name=BINARY 'SYNTHETIC Native Export' AND year_of_birth='2000' AND month_of_birth='01' AND date_of_birth='02' AND sex='F' AND patient_status='AC' AND address='17 Native Fixture Road' AND city='Ottawa' AND province='ON' AND postal='K1A0B1' AND phone='613-555-0198' AND provider_no=?",
                                patient,
                                PROVIDER)
                        != 1
                || !("<unotes>" + NOTE + "</unotes>")
                        .equals(
                                text(
                                        db,
                                        "SELECT content FROM demographiccust WHERE demographic_no=?",
                                        patient)))
            throw new IllegalStateException("Native chart changed");
        System.out.println(
                JSON.writeValueAsString(
                        Map.of(
                                "verified",
                                true,
                                "realReadyJobs",
                                2,
                                "cliAndBrowserCompatibilityEqual",
                                true,
                                "compatibilitySha256",
                                cliCompatibility,
                                "nativeChartUnchanged",
                                true,
                                "importMetadataRows",
                                0)));
    }

    private static void decrypt(Path encrypted, Path output) throws Exception {
        if (!encrypted.toAbsolutePath().normalize().startsWith(PRIVATE)
                || !output.toAbsolutePath().normalize().startsWith(PRIVATE)
                || !Files.isRegularFile(encrypted, LinkOption.NOFOLLOW_LINKS)
                || Files.size(encrypted) > (2 << 20))
            throw new IllegalStateException("Bounded owned artifact required");
        var certificate =
                (X509Certificate)
                        CertificateFactory.getInstance("X.509")
                                .generateCertificate(
                                        Files.newInputStream(PRIVATE.resolve("recipient.der")));
        var key =
                KeyFactory.getInstance("RSA")
                        .generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        Files.readAllBytes(PRIVATE.resolve("recipient.pk8"))));
        var cms = new CMSAuthEnvelopedData(Files.readAllBytes(encrypted));
        var recipients = cms.getRecipientInfos();
        if (recipients.size() != 1)
            throw new IllegalStateException("One pinned recipient required");
        var recipient = recipients.get(new JceKeyTransRecipientId(certificate));
        if (recipient == null) throw new IllegalStateException("Pinned recipient absent");
        byte[] plain =
                recipient.getContent(
                        new JceKeyTransAuthEnvelopedRecipient(key)
                                .setProvider(new BouncyCastleProvider()));
        if (plain.length > (2 << 20)) throw new IllegalStateException("Fixture archive bound");
        try {
            writePrivate(output, plain, false);
            System.out.println(
                    JSON.writeValueAsString(
                            Map.of(
                                    "decrypted",
                                    true,
                                    "bytes",
                                    plain.length,
                                    "sha256",
                                    sha(plain))));
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static long count(Connection db, String sql, Object... args) throws Exception {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Count absent");
                return rows.getLong(1);
            }
        }
    }

    private static String text(Connection db, String sql, Object... args) throws Exception {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Value absent");
                return rows.getString(1);
            }
        }
    }

    private static void update(Connection db, String sql, Object... args) throws Exception {
        try (var statement = db.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            if (statement.executeUpdate() != 1)
                throw new IllegalStateException("Exact fixture write required");
        }
    }

    private static void directory(Path path, boolean serviceOwned, boolean groupRead)
            throws Exception {
        Files.createDirectory(path);
        if (serviceOwned)
            Files.setOwner(
                    path,
                    path.getFileSystem()
                            .getUserPrincipalLookupService()
                            .lookupPrincipalByName("carlos"));
        Files.getFileAttributeView(path, PosixFileAttributeView.class)
                .setGroup(
                        path.getFileSystem()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByGroupName("carlos"));
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString(groupRead ? "rwxr-x---" : "rwx------"));
    }

    private static void writePrivate(Path path, byte[] value, boolean groupRead) throws Exception {
        Files.write(path, value, StandardOpenOption.CREATE_NEW);
        if (groupRead) groupReadable(path);
        else Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    private static void groupReadable(Path path) throws Exception {
        Files.getFileAttributeView(path, PosixFileAttributeView.class)
                .setGroup(
                        path.getFileSystem()
                                .getUserPrincipalLookupService()
                                .lookupPrincipalByGroupName("carlos"));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"));
    }
}
