/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAuthEnvelopedData;
import org.bouncycastle.cms.jcajce.JceKeyTransAuthEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientId;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Disposable installed CDS export-scaling fixture; external to the packaged WAR.
 *
 * <p>It runs only as root in a disposable container, after {@code CdsCliEngineFixture prepare}
 * has written the installed import configuration and native-baseline key, against the exclusively
 * owned {@code carlos_cds_test} database. {@code prepare} adds the export policy, keys, recipient
 * certificate and audit arrangement, and appends the export settings to the same protected
 * configuration file. {@code seed} enters ordinary native charts of synthetic FAKE- data the way
 * the CARLOS encounter and lab-upload screens store them: an admitted demographic, signed
 * encounter notes scoped to the provider's program and reporter role, and uploaded HL7 results
 * routed to the patient and acknowledged by the provider. {@code imported} binds an export
 * selection to the charts of one completed import run. {@code decrypt} opens a downloaded package
 * with the fixture's own recipient key.
 *
 * <p>All SQL is parameterized. Output is one JSON object of counts and private fixture paths;
 * patient identifiers stay in protected selection files and never reach standard output.
 */
public final class CdsExportScalingFixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Path CONFIG = Path.of("/etc/carlos-emr/carlos.properties");
    private static final Path ROOT = Path.of("/opt/cds-export-scaling");
    private static final Path PRIVATE = Path.of("/run/cds-export-scaling");
    /** The import fixture's synthetic provider ({@code CdsCliEngineFixture.PROVIDER}). */
    private static final String PROVIDER = "999998";
    private static final String KEY = "scaling";
    private static final int MAXIMUM_PATIENTS = 64;
    private static final int MAXIMUM_RECORDS = 4096;
    /** A decrypted package is read whole by the recipient library; bound it explicitly. */
    private static final int MAXIMUM_PACKAGE_BYTES = 256 << 20;

    private CdsExportScalingFixture() {}

    public static void main(String[] args) {
        try {
            if (args.length < 2
                    || !Files.isRegularFile(Path.of("/.dockerenv"))
                    || ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).intValue()
                            != 0) {
                throw new IllegalStateException("Disposable root container required");
            }
            if (args[0].equals("decrypt")) {
                if (args.length != 3) throw new IllegalArgumentException("Decrypt arguments");
                System.out.println(JSON.writeValueAsString(decrypt(Path.of(args[1]), Path.of(args[2]))));
                return;
            }
            Path credentials = Path.of(args[1]);
            if (!Files.isRegularFile(credentials, LinkOption.NOFOLLOW_LINKS)
                    || (((Number) Files.getAttribute(credentials, "unix:mode")).intValue() & 0077)
                            != 0) {
                throw new IllegalStateException("Protected synthetic JDBC configuration required");
            }
            Properties jdbc = new Properties();
            try (var input = Files.newInputStream(credentials)) {
                jdbc.load(input);
            }
            String url = jdbc.getProperty("jdbcUrl", "");
            if (!url.matches("jdbc:mariadb://[^/?]+/carlos_cds_test"))
                throw new IllegalStateException("Owned CI database required");
            Class.forName("org.mariadb.jdbc.Driver");
            try (Connection db =
                    DriverManager.getConnection(
                            url, jdbc.getProperty("jdbcUser"), jdbc.getProperty("jdbcPassword"))) {
                if (!"carlos_cds_test".equals(text(db, "SELECT DATABASE()")))
                    throw new IllegalStateException("Database mismatch");
                Object result =
                        switch (args[0]) {
                            case "prepare" -> {
                                if (args.length != 2) throw new IllegalArgumentException("Prepare");
                                yield prepare(db);
                            }
                            case "seed" -> {
                                if (args.length != 5) throw new IllegalArgumentException("Seed");
                                yield seed(
                                        db,
                                        label(args[2]),
                                        bounded(args[3], MAXIMUM_PATIENTS),
                                        bounded(args[4], MAXIMUM_RECORDS));
                            }
                            case "imported" -> {
                                if (args.length != 5) throw new IllegalArgumentException("Imported");
                                yield imported(
                                        db,
                                        label(args[2]),
                                        UUID.fromString(args[3]).toString(),
                                        bounded(args[4], MAXIMUM_PATIENTS));
                            }
                            default -> throw new IllegalArgumentException("Unknown operation");
                        };
                System.out.println(JSON.writeValueAsString(result));
            }
        } catch (Exception failure) {
            // Operational evidence only: never print exception messages, SQL, paths or values.
            // The frames are read to name this fixture's own method and line, nothing else; the
            // fixture runs only in the CI runtime and is never packaged.
            String location = "unknown";
            // nosemgrep: java.lang.security.audit.active-debug-code-getstacktrace.active-debug-code-getstacktrace
            for (StackTraceElement frame : failure.getStackTrace()) {
                if (frame.getClassName().equals(CdsExportScalingFixture.class.getName())) {
                    location = frame.getMethodName() + ":" + frame.getLineNumber();
                    break;
                }
            }
            String sql =
                    failure instanceof java.sql.SQLException database
                            ? ":sqlState="
                                    + database.getSQLState()
                                    + ":vendorCode="
                                    + database.getErrorCode()
                            : "";
            System.err.println(
                    "CDS_EXPORT_SCALING_FIXTURE_FAILED:"
                            + failure.getClass().getSimpleName()
                            + ":"
                            + location
                            + sql);
            System.exit(1);
        }
    }

    /**
     * Adds export to the installed import fixture without replacing it: every existing setting is
     * kept byte for byte and only new {@code cds.export.*} keys are appended.
     */
    private static Map<String, Object> prepare(Connection db) throws Exception {
        if (!Files.isRegularFile(CONFIG, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(ROOT, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(PRIVATE, LinkOption.NOFOLLOW_LINKS)
                || count(db, "SELECT COUNT(*) FROM cds_export_run") != 0
                || count(
                                db,
                                "SELECT COUNT(*) FROM provider WHERE provider_no=? AND status='1'",
                                PROVIDER)
                        != 1) {
            throw new IllegalStateException("Prepared import fixture without export work required");
        }
        byte[] original = Files.readAllBytes(CONFIG);
        Properties installed = new Properties();
        installed.load(new java.io.ByteArrayInputStream(original));
        if (installed.stringPropertyNames().stream().anyMatch(key -> key.startsWith("cds.export.")))
            throw new IllegalStateException("Existing export configuration is never replaced");
        // The provider's first program membership is the program its encounter screen records on
        // a note, with the membership role as the note's reporter role.
        long program =
                count(
                        db,
                        "SELECT COALESCE(MIN(program_id),0) FROM program_provider WHERE"
                                + " provider_no=?",
                        PROVIDER);
        if (program < 1
                || count(
                                db,
                                "SELECT COUNT(DISTINCT role_id) FROM program_provider WHERE"
                                        + " provider_no=? AND program_id=?",
                                PROVIDER,
                                program)
                        != 1) throw new IllegalStateException("One program membership required");
        long role =
                count(
                        db,
                        "SELECT MIN(role_id) FROM program_provider WHERE provider_no=? AND"
                                + " program_id=?",
                        PROVIDER,
                        program);
        long facility = count(db, "SELECT facilityId FROM program WHERE id=?", program);
        if (role < 1
                || facility < 1
                || count(db, "SELECT COUNT(*) FROM Facility WHERE id=? AND disabled=0", facility)
                        != 1) throw new IllegalStateException("Active program facility required");
        db.setAutoCommit(false);
        try {
            if (count(
                            db,
                            "SELECT COUNT(*) FROM provider_facility WHERE provider_no=? AND"
                                    + " facility_id=?",
                            PROVIDER,
                            facility)
                    == 0)
                update(
                        db,
                        "INSERT INTO provider_facility(provider_no,facility_id) VALUES (?,?)",
                        PROVIDER,
                        facility);
            // The migrated sensitive-laboratory capability has no default grant, including for
            // admin; laboratory export requires it, so this synthetic operator receives it.
            if (count(
                            db,
                            "SELECT COUNT(*) FROM secObjPrivilege WHERE roleUserGroup=? AND"
                                    + " objectName='_lab_sensitive'",
                            PROVIDER)
                    == 0)
                update(
                        db,
                        "INSERT INTO"
                                + " secObjPrivilege(roleUserGroup,objectName,privilege,priority,provider_no)"
                                + " VALUES(?,'_lab_sensitive','r',0,?)",
                        PROVIDER,
                        PROVIDER);
            db.commit();
        } catch (Exception failure) {
            db.rollback();
            throw failure;
        } finally {
            db.setAutoCommit(true);
        }
        directory(PRIVATE, false, false);
        directory(ROOT, false, true);
        for (String child : List.of("store", "delivery")) directory(ROOT.resolve(child), true, false);
        for (String child : List.of("selection", "policy", "keys"))
            directory(ROOT.resolve(child), false, true);
        for (String child : List.of("recipients", "certificates", "audit"))
            directory(ROOT.resolve("policy").resolve(child), false, true);
        byte[] master = new byte[32];
        new SecureRandom().nextBytes(master);
        try {
            writePrivate(ROOT.resolve("keys/" + KEY + ".key"), master, true);
        } finally {
            Arrays.fill(master, (byte) 0);
        }
        policy();
        Properties export = new Properties();
        export.setProperty("cds.export.enabled", "true");
        export.setProperty("cds.export.worker.enabled", "true");
        export.setProperty("cds.export.clinicalZone", "America/Toronto");
        export.setProperty("cds.export.operator.root.providers", PROVIDER);
        export.setProperty("cds.export.policyRoot", ROOT.resolve("policy").toString());
        export.setProperty("cds.export.masterKeyRoot", ROOT.resolve("keys").toString());
        export.setProperty("cds.export.masterKey.active", KEY);
        export.setProperty("cds.export.store.active", KEY);
        export.setProperty("cds.export.store." + KEY + ".root", ROOT.resolve("store").toString());
        export.setProperty("cds.export.selectionRoot", ROOT.resolve("selection").toString());
        // No web worker runs in this runtime, so a foreground job that selects Reports would only
        // wait out the renderer hand-off window before running here; the scaling measurement
        // starts the work at once instead (docs/cds-export.md, "Installed export scaling lane").
        export.setProperty("cds.export.cliRendererHandoffSeconds", "0");
        StringWriter appended = new StringWriter();
        export.store(appended, null);
        String block =
                appended.toString()
                        .lines()
                        .filter(line -> !line.startsWith("#"))
                        .reduce("", (left, line) -> left + line + "\n");
        byte[] next =
                (new String(original, StandardCharsets.ISO_8859_1)
                                + (original.length > 0 && original[original.length - 1] != '\n'
                                        ? "\n"
                                        : "")
                                + "# Owned synthetic CDS export-scaling fixture\n"
                                + block)
                        .getBytes(StandardCharsets.ISO_8859_1);
        Path pending = CONFIG.resolveSibling(".carlos.properties.export-scaling-" + UUID.randomUUID());
        Files.write(pending, next, StandardOpenOption.CREATE_NEW);
        try {
            var source = Files.getFileAttributeView(CONFIG, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes();
            var target = Files.getFileAttributeView(pending, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
            target.setOwner(source.owner());
            target.setGroup(source.group());
            target.setPermissions(source.permissions());
            Files.move(pending, CONFIG, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(pending);
        }
        Properties reloaded = new Properties();
        try (var input = Files.newInputStream(CONFIG)) {
            reloaded.load(input);
        }
        for (String key : installed.stringPropertyNames())
            if (!installed.getProperty(key).equals(reloaded.getProperty(key)))
                throw new IllegalStateException("Installed setting changed");
        for (String key : export.stringPropertyNames())
            if (!export.getProperty(key).equals(reloaded.getProperty(key)))
                throw new IllegalStateException("Export setting not recorded");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prepared", true);
        result.put("provider", PROVIDER);
        result.put("facility", facility);
        result.put("recipient", KEY);
        result.put("auditPlan", KEY);
        result.put("profile", "omd-5.2");
        result.put("selectionRoot", ROOT.resolve("selection").toString());
        result.put("deliveryRoot", ROOT.resolve("delivery").toString());
        result.put("privateRoot", PRIVATE.toString());
        result.put("nativeProgram", program);
        return result;
    }

    /**
     * Enters {@code patients} ordinary native charts, each with {@code records} signed encounter
     * notes and {@code records} uploaded, acknowledged HL7 results, and writes their selection.
     */
    private static Map<String, Object> seed(Connection db, String label, int patients, int records)
            throws Exception {
        if (patients < 1 || records < 1) throw new IllegalArgumentException("Empty chart");
        Path selection = ROOT.resolve("selection/native-" + label + ".json");
        if (Files.exists(selection, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("Selection is never replaced");
        long program =
                count(db, "SELECT COALESCE(MIN(program_id),0) FROM program_provider WHERE provider_no=?", PROVIDER);
        long role =
                count(
                        db,
                        "SELECT MIN(role_id) FROM program_provider WHERE provider_no=? AND program_id=?",
                        PROVIDER,
                        program);
        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        List<Integer> created = new ArrayList<>();
        // One record a day at local noon: a clinic records notes and results in its own wall
        // clock, and noon is never inside a daylight-saving change, where a local time is missing
        // or ambiguous and the export rightly holds the note for review. HL7 times carry the
        // clinic zone's real offset on that date (the fixture configuration's clinical zone).
        LocalDateTime start = LocalDateTime.of(2018, 1, 8, 12, 0);
        ZoneId clinic = ZoneId.of("America/Toronto");
        DateTimeFormatter hl7 = DateTimeFormatter.ofPattern("yyyyMMddHHmmssxx");
        db.setAutoCommit(false);
        try (PreparedStatement note =
                        db.prepareStatement(
                                "INSERT INTO casemgmt_note(update_date,observation_date,demographic_no,"
                                        + "provider_no,note,signed,include_issue_innote,signing_provider_no,"
                                        + "encounter_type,billing_code,program_no,reporter_caisi_role,"
                                        + "reporter_program_team,history,locked,archived,position,uuid)"
                                        + " VALUES (?,?,?,?,?,1,0,?,'face to face encounter with client','',?,?,'0',?,'0',0,0,?)");
                PreparedStatement upload =
                        db.prepareStatement(
                                "INSERT INTO fileUploadCheck(provider_no,filename,md5sum,date_time) VALUES (?,?,?,?)",
                                Statement.RETURN_GENERATED_KEYS);
                PreparedStatement message =
                        db.prepareStatement(
                                "INSERT INTO hl7TextMessage(message,type,created,fileUploadCheck_id,serviceName)"
                                        + " VALUES (?,'GENERIC',?,?,'FAKE Scaling Lab')",
                                Statement.RETURN_GENERATED_KEYS);
                PreparedStatement info =
                        db.prepareStatement(
                                "INSERT INTO hl7TextInfo(lab_no,accessionNum,first_name,last_name,health_no,sex,"
                                        + "sending_facility,result_status,final_result_count,obr_date,report_status,"
                                        + "discipline) VALUES (?,?,'Synthetic',?,'','F','FAKE Scaling Lab','N',1,?,'F','CHEM')");
                PreparedStatement patientRoute =
                        db.prepareStatement(
                                "INSERT INTO patientLabRouting(demographic_no,lab_no,lab_type,created,dateModified)"
                                        + " VALUES (?,?,'HL7',?,?)");
                PreparedStatement providerRoute =
                        db.prepareStatement(
                                "INSERT INTO providerLabRouting(provider_no,lab_no,status,comment,lab_type)"
                                        + " VALUES (?,?,'A','','HL7')")) {
            for (int p = 0; p < patients; p++) {
                String last = "FAKE-SCALING-" + token + "-" + p;
                update(
                        db,
                        "INSERT INTO demographic(last_name,first_name,year_of_birth,month_of_birth,"
                                + "date_of_birth,sex,patient_status,address,city,province,postal,phone,"
                                + "provider_no,lastUpdateDate) VALUES (?,'Synthetic','1971','03','04','F','AC',"
                                + "'1 FAKE Scaling Road','Ottawa','ON','K1A0B1','613-555-0100',?,NOW())",
                        last,
                        PROVIDER);
                int patient = (int) count(db, "SELECT LAST_INSERT_ID()");
                created.add(patient);
                // The registration screen admits a new patient to the provider's program.
                update(
                        db,
                        "INSERT INTO admission(client_id,program_id,provider_no,admission_date,"
                                + "admission_from_transfer,discharge_from_transfer,admission_status,lastUpdateDate)"
                                + " VALUES (?,?,?,?,0,0,'current',NOW())",
                        patient,
                        program,
                        PROVIDER,
                        java.sql.Timestamp.valueOf(start));
                for (int r = 0; r < records; r++) {
                    LocalDateTime at = start.plusDays(r);
                    String time = hl7.format(at.atZone(clinic));
                    var stamp = java.sql.Timestamp.valueOf(at);
                    String body =
                            "FAKE synthetic encounter note "
                                    + (r + 1)
                                    + " of "
                                    + records
                                    + ". Subjective: routine follow-up. Plan: continue current care.";
                    note.setTimestamp(1, stamp);
                    note.setTimestamp(2, stamp);
                    note.setInt(3, patient);
                    note.setString(4, PROVIDER);
                    note.setString(5, body);
                    note.setString(6, PROVIDER);
                    note.setString(7, Long.toString(program));
                    note.setString(8, Long.toString(role));
                    note.setString(9, body);
                    note.setString(10, UUID.randomUUID().toString());
                    note.addBatch();
                    String accession = "FAKE-" + token + "-" + p + "-" + r;
                    String hl7Message =
                            "MSH|^~\\&|LABAPP|FAKE Scaling Lab|CARLOS|Clinic|"
                                    + time
                                    + "||ORU^R01|"
                                    + accession
                                    + "|P|2.5\r"
                                    + "PID|1||"
                                    + accession
                                    + "^^^LAB^MR||"
                                    + last
                                    + "^Synthetic||19710304|F\r"
                                    + "OBR|1||"
                                    + accession
                                    + "|PANEL^Synthetic panel^L||"
                                    + time
                                    + "|"
                                    + time
                                    + "\r"
                                    + "OBX|1|NM|2345-7^Glucose^LN||"
                                    + (4 + r % 4)
                                    + "."
                                    + (r % 10)
                                    + "|mmol/L|3.5-7.8|N|||F|||"
                                    + time
                                    + "\r";
                    byte[] bytes = hl7Message.getBytes(StandardCharsets.UTF_8);
                    upload.setString(1, PROVIDER);
                    upload.setString(2, accession + ".hl7");
                    upload.setString(3, sha(bytes));
                    upload.setTimestamp(4, stamp);
                    upload.executeUpdate();
                    int uploadId = generated(upload);
                    message.setString(1, Base64.getEncoder().encodeToString(bytes));
                    message.setTimestamp(2, stamp);
                    message.setInt(3, uploadId);
                    message.executeUpdate();
                    int lab = generated(message);
                    info.setInt(1, lab);
                    info.setString(2, accession);
                    info.setString(3, last);
                    info.setString(4, at.toLocalDate() + " " + at.toLocalTime() + ":00");
                    info.addBatch();
                    patientRoute.setInt(1, patient);
                    patientRoute.setInt(2, lab);
                    patientRoute.setTimestamp(3, stamp);
                    patientRoute.setTimestamp(4, stamp);
                    patientRoute.addBatch();
                    providerRoute.setString(1, PROVIDER);
                    providerRoute.setInt(2, lab);
                    providerRoute.addBatch();
                }
                for (PreparedStatement batch : List.of(note, info, patientRoute, providerRoute))
                    for (int changed : batch.executeBatch())
                        if (changed != 1 && changed != Statement.SUCCESS_NO_INFO)
                            throw new IllegalStateException("Exact fixture batch required");
            }
            db.commit();
        } catch (Exception failure) {
            db.rollback();
            throw failure;
        } finally {
            db.setAutoCommit(true);
        }
        writePrivate(
                selection,
                JSON.writeValueAsBytes(Map.of("version", 1, "patients", created)),
                true);
        return counted("native", label, created, selection);
    }

    /** Binds an export selection to every chart one completed import run created. */
    private static Map<String, Object> imported(
            Connection db, String label, String run, int expectedPatients) throws Exception {
        Path selection = ROOT.resolve("selection/imported-" + label + ".json");
        if (Files.exists(selection, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalStateException("Selection is never replaced");
        if (count(
                        db,
                        "SELECT COUNT(*) FROM cds_import_run WHERE id=? AND state='COMPLETE' AND"
                                + " dry_run=FALSE AND cancel_requested=FALSE",
                        run)
                != 1) throw new IllegalStateException("Completed import run required");
        List<Integer> created = new ArrayList<>();
        try (var statement =
                db.prepareStatement(
                        "SELECT DISTINCT target_demographic_no FROM cds_import_patient WHERE run_id=?"
                                + " AND held=FALSE AND target_demographic_no IS NOT NULL ORDER BY 1")) {
            statement.setString(1, run);
            statement.setMaxRows(MAXIMUM_PATIENTS + 1);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) created.add(rows.getInt(1));
            }
        }
        if (created.size() != expectedPatients
                || count(db, "SELECT COUNT(*) FROM cds_import_patient WHERE run_id=? AND held=TRUE", run)
                        != 0) throw new IllegalStateException("Unexpected imported chart inventory");
        writePrivate(
                selection,
                JSON.writeValueAsBytes(Map.of("version", 1, "patients", created)),
                true);
        return counted("imported", label, created, selection);
    }

    /** The public result of a bound selection: its origin, case and chart count, never an ID. */
    private static Map<String, Object> counted(
            String origin, String label, List<Integer> patients, Path selection) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("origin", origin);
        result.put("label", label);
        result.put("patients", patients.size());
        result.put("selection", selection.toString());
        return result;
    }

    private static Map<String, Object> decrypt(Path encrypted, Path output) throws Exception {
        if (!encrypted.isAbsolute()
                || !output.isAbsolute()
                || !encrypted.normalize().getParent().equals(ROOT.resolve("delivery"))
                || !output.normalize().getParent().equals(PRIVATE)
                || !Files.isRegularFile(encrypted, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(output, LinkOption.NOFOLLOW_LINKS)
                || Files.size(encrypted) > MAXIMUM_PACKAGE_BYTES)
            throw new IllegalStateException("Bounded owned artifact required");
        X509Certificate certificate;
        try (var input = Files.newInputStream(PRIVATE.resolve("recipient.der"))) {
            certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
        var key =
                KeyFactory.getInstance("RSA")
                        .generatePrivate(
                                new PKCS8EncodedKeySpec(
                                        Files.readAllBytes(PRIVATE.resolve("recipient.pk8"))));
        var cms = new CMSAuthEnvelopedData(Files.readAllBytes(encrypted));
        var recipients = cms.getRecipientInfos();
        if (recipients.size() != 1) throw new IllegalStateException("One pinned recipient required");
        var recipient = recipients.get(new JceKeyTransRecipientId(certificate));
        if (recipient == null) throw new IllegalStateException("Pinned recipient absent");
        byte[] plain =
                recipient.getContent(
                        new JceKeyTransAuthEnvelopedRecipient(key)
                                .setProvider(new BouncyCastleProvider()));
        try {
            if (plain.length > MAXIMUM_PACKAGE_BYTES)
                throw new IllegalStateException("Package bound exceeded");
            writePrivate(output, plain, false);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("decrypted", true);
            result.put("bytes", plain.length);
            result.put("sha256", sha(plain));
            return result;
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    private static void policy() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var now = Instant.now();
        var name = new X500Name("CN=CDS SYNTHETIC EXPORT SCALING RECIPIENT,O=example.invalid");
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
                                "protocol", 1,
                                "enabled", true,
                                "namespace", "export-scaling",
                                "physicianGroup", "Synthetic export scaling",
                                "groupContactInformation", "ci@example.invalid"));
        byte[] recipient =
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "id", KEY,
                                "certificateId", KEY,
                                "certificateSha256", sha(der),
                                "profiles", List.of("omd-5.2")));
        byte[] audit =
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "id", KEY,
                                "description", "Owned synthetic export scaling audit arrangement",
                                "recipientIds", List.of(KEY),
                                "providerNos", List.of(PROVIDER),
                                "profiles", List.of("omd-5.2")));
        writePrivate(policy.resolve("sender.json"), sender, true);
        writePrivate(policy.resolve("recipients/" + KEY + ".json"), recipient, true);
        writePrivate(policy.resolve("certificates/" + KEY + ".der"), der, true);
        writePrivate(policy.resolve("audit/" + KEY + ".json"), audit, true);
        writePrivate(
                policy.resolve("policy.json"),
                JSON.writeValueAsBytes(
                        Map.of(
                                "protocol", 1,
                                "enabled", true,
                                "senderSha256", sha(sender),
                                "recipients", Map.of(KEY, sha(recipient)),
                                "certificates", Map.of(KEY, sha(der)),
                                "auditPlans", Map.of(KEY, sha(audit)))),
                true);
    }

    private static String label(String value) {
        if (!value.matches("[a-z]+-[0-9]{1,4}")) throw new IllegalArgumentException("Case label");
        return value;
    }

    private static int bounded(String value, int maximum) {
        if (!value.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Count");
        int parsed = Integer.parseInt(value);
        if (parsed < 1 || parsed > maximum) throw new IllegalArgumentException("Count");
        return parsed;
    }

    private static int generated(PreparedStatement statement) throws Exception {
        try (var keys = statement.getGeneratedKeys()) {
            if (!keys.next()) throw new IllegalStateException("Generated key absent");
            return keys.getInt(1);
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
        var lookup = path.getFileSystem().getUserPrincipalLookupService();
        if (serviceOwned) Files.setOwner(path, lookup.lookupPrincipalByName("carlos"));
        Files.getFileAttributeView(path, PosixFileAttributeView.class)
                .setGroup(lookup.lookupPrincipalByGroupName("carlos"));
        Files.setPosixFilePermissions(
                path, PosixFilePermissions.fromString(groupRead ? "rwxr-x---" : "rwx------"));
    }

    private static void writePrivate(Path path, byte[] value, boolean groupRead) throws Exception {
        Files.write(path, value, StandardOpenOption.CREATE_NEW);
        if (groupRead) {
            Files.getFileAttributeView(path, PosixFileAttributeView.class)
                    .setGroup(
                            path.getFileSystem()
                                    .getUserPrincipalLookupService()
                                    .lookupPrincipalByGroupName("carlos"));
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r-----"));
        } else {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }
    }
}
