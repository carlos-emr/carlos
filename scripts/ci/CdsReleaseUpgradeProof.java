/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
import io.github.carlos_emr.carlos.db.FlywaySchemaValidator;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/** Actual release history, retained synthetic clinical data, CLI runner and boot validation. */
public final class CdsReleaseUpgradeProof {
    private static final String BASE = "ef69eea17a4cc3fb288b26327471fce1e20dcd8f";
    private static final String HISTORY =
            "SELECT * FROM flyway_schema_history ORDER BY installed_rank";
    private static final String PATIENT =
            "SELECT * FROM demographic WHERE demographic_no=730000001";
    private static final String CONSENT =
            "SELECT"
                + " id,demographic_no,consent_type_id,explicit,optout,last_entered_by,consent_date,optout_date,edit_date,deleted"
                + " FROM Consent WHERE demographic_no=730000001 ORDER BY id";
    private static final String MESSAGE = "SELECT * FROM messagetbl WHERE messageid=7300001";
    private static final String AUDIT =
            "SELECT"
                + " consent_id,migration,reason,prior_explicit,prior_optout,prior_deleted,recorded_at"
                + " FROM Consent_migration_audit WHERE migration='synthetic_upgrade' ORDER BY"
                + " consent_id";

    private static final String PROPERTY =
            "SELECT * FROM property WHERE name='synthetic_cds_upgrade'";

    /**
     * V1.0.59.44 adds an invisible generated patient key and an index over existing free-text
     * HRMDocumentToDemographic.demographicNo values (review CI-10). The seeded links cover a
     * plain number, a padded signed number, text and SQL NULL; the visible columns must be
     * unchanged and the key derived exactly.
     */
    private static final String HRM_LINKS =
            "SELECT id,demographicNo,hrmDocumentId,timeAssigned FROM HRMDocumentToDemographic"
                    + " WHERE hrmDocumentId LIKE 'SYNTHETIC-7300%' ORDER BY hrmDocumentId";

    private static final String HRM_KEYS =
            "SELECT hrmDocumentId,cds_patient_key FROM HRMDocumentToDemographic"
                    + " WHERE hrmDocumentId LIKE 'SYNTHETIC-7300%' ORDER BY hrmDocumentId";

    private record Snapshot(
            List<Map<String, String>> history,
            List<Map<String, String>> patient,
            List<Map<String, String>> consent,
            List<Map<String, String>> message,
            List<Map<String, String>> audit,
            List<Map<String, String>> property,
            List<Map<String, String>> hrmLinks) {
        @Override
        public String toString() {
            return "Snapshot[protected]";
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !List.of("on", "bc").contains(args[2]))
            throw new IllegalArgumentException("CDS_UPGRADE_ARGUMENTS_INVALID");
        Properties settings = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(args[0]))) {
            settings.load(input);
        }
        String province = args[2], url = settings.getProperty("jdbcUrl", "");
        String database = province.equals("on") ? "cds_export_test" : "cds_full_test";
        if (!"true".equals(settings.getProperty("ownedDisposableDatabase"))
                || !url.matches("jdbc:mariadb://[^/]+/" + database + "(?:\\?.*)?"))
            throw new IllegalArgumentException("CDS_UPGRADE_OWNED_DATABASE_REQUIRED");
        var source =
                new DriverManagerDataSource(
                        url,
                        settings.getProperty("jdbcUser"),
                        settings.getProperty("jdbcPassword"));
        int initial = migrateBase(source, Path.of(args[1]), province);
        var before = seedAndCapture(source);
        String locations = "classpath:db/migration/common,classpath:db/migration/" + province;
        boolean behind = false;
        try {
            new FlywaySchemaValidator(source, "validate", locations, province).afterPropertiesSet();
        } catch (IllegalStateException expected) {
            behind = expected.getMessage().contains("BEHIND");
        }
        require(behind, "BOOT_MUST_REFUSE_PENDING");
        Path privateRoot = Path.of(args[0]).getParent();
        require(
                cli("migrate", locations, source, privateRoot.resolve(province + "-migrate.log"))
                        == 0,
                "CLI_MIGRATE_FAILED");
        require(
                cli("validate", locations, source, privateRoot.resolve(province + "-validate.log"))
                        == 0,
                "CLI_VALIDATE_FAILED");
        new FlywaySchemaValidator(source, "validate", locations, province).afterPropertiesSet();
        var upgraded =
                Flyway.configure()
                        .dataSource(source)
                        .locations(locations.split(","))
                        .ignoreMigrationPatterns(new String[0])
                        .failOnMissingLocations(true)
                        .cleanDisabled(true)
                        .load();
        upgraded.validate();
        require(upgraded.info().pending().length == 0, "PENDING_REMAINS");
        int applied = upgraded.info().applied().length;
        // Exactly the checked-out migration set, counted from the packaged classpath rather than a
        // number that the next merged migration would silently invalidate (review CI-10).
        require(applied == packagedMigrations(locations), "HEAD_HISTORY_COUNT");
        verifyRetained(source, before);
        removeSyntheticFixture(source);
        Files.writeString(
                Path.of(args[3]),
                "{\"status\":\"CLI_AND_RETENTION_PASSED\",\"baseCommit\":\""
                        + BASE
                        + "\",\"province\":\""
                        + province
                        + "\",\"baseMigrations\":"
                        + initial
                        + ",\"headMigrations\":"
                        + applied
                        + ",\"retainedClinicalRows\":4,\"retainedAuditRows\":2,\"retainedPropertyRows\":1,\"retainedHrmLinks\":4,\"appliedHistoryUnchanged\":true,\"cliMigrate\":true,\"cliValidate\":true,\"bootValidate\":true,\"pending\":0}\n");
        System.out.println(
                "CDS release upgrade passed: "
                        + province
                        + "; retained clinical/audit rows and original Flyway history unchanged.");
    }

    private static int migrateBase(DriverManagerDataSource source, Path base, String province) {
        var original =
                Flyway.configure()
                        .dataSource(source)
                        .locations(
                                "filesystem:" + base.resolve("common"),
                                "filesystem:" + base.resolve(province))
                        .validateOnMigrate(true)
                        .ignoreMigrationPatterns(new String[0])
                        .failOnMissingLocations(true)
                        .cleanDisabled(true)
                        .load();
        int initial = original.migrate().migrationsExecuted;
        original.validate();
        require(initial == (province.equals("on") ? 78 : 77), "BASE_HISTORY_COUNT");
        require(
                original.info().current().getVersion().toString().equals("1.0.59.37"),
                "BASE_HIGH_WATER");
        return initial;
    }

    private static Snapshot seedAndCapture(DriverManagerDataSource source) throws Exception {
        try (var connection = source.getConnection()) {
            try (var sql = connection.createStatement()) {
                sql.executeUpdate(
                        "INSERT INTO"
                            + " demographic(demographic_no,last_name,first_name,sex,lastUpdateDate)"
                            + " VALUES(730000001,'SYNTHETIC Upgrade','Preserved 😀','F','2026-01-02"
                            + " 03:04:05')");
                sql.executeUpdate(
                        "INSERT INTO"
                            + " Consent(demographic_no,consent_type_id,explicit,optout,deleted,consent_date)"
                            + " VALUES(730000001,1,1,0,0,'2026-01-02"
                            + " 03:04:05'),(730000001,1,0,1,1,'2025-01-02 03:04:05')");
                sql.executeUpdate(
                        "INSERT INTO messagetbl(messageid,themessage,pdfattachment)"
                                + " VALUES(7300001,'SYNTHETIC preserved"
                                + " message',UNHEX('00010203FEFF'))");
                sql.executeUpdate(
                        "INSERT INTO"
                                + " HRMDocumentToDemographic(demographicNo,hrmDocumentId,timeAssigned)"
                                + " VALUES('730000001','SYNTHETIC-73001','2026-01-02 03:04:05'),"
                                + "(' +730000001','SYNTHETIC-73002','2026-01-02 03:04:06'),"
                                + "('SYNTHETIC text','SYNTHETIC-73003',NULL),"
                                + "(NULL,'SYNTHETIC-73004','2026-01-02 03:04:07')");
                sql.executeUpdate(
                        "INSERT INTO"
                            + " Consent_migration_audit(consent_id,migration,reason,prior_explicit,prior_optout,prior_deleted,recorded_at)"
                            + " SELECT id,'synthetic_upgrade','preserve',NULL,1,NULL,'2024-01-02"
                            + " 03:04:05' FROM Consent WHERE demographic_no=730000001");
            }
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO property(name,value,provider_no)"
                                    + " VALUES('synthetic_cds_upgrade',?,'')")) {
                statement.setString(1, "SYNTHETIC complete Unicode 😀 & footer\r\n".repeat(3));
                require(statement.executeUpdate() == 1, "PROPERTY_FIXTURE_MISSING");
            }
            var before =
                    new Snapshot(
                            rows(connection, HISTORY),
                            rows(connection, PATIENT),
                            rows(connection, CONSENT),
                            rows(connection, MESSAGE),
                            rows(connection, AUDIT),
                            rows(connection, PROPERTY),
                            rows(connection, HRM_LINKS));
            require(before.audit().size() == 2, "AUDIT_FIXTURE_MISSING");
            require(before.hrmLinks().size() == 4, "HRM_FIXTURE_MISSING");
            return before;
        }
    }

    private static void verifyRetained(DriverManagerDataSource source, Snapshot before)
            throws Exception {
        try (var connection = source.getConnection()) {
            require(
                    rows(connection, HISTORY)
                            .subList(0, before.history().size())
                            .equals(before.history()),
                    "APPLIED_HISTORY_CHANGED");
            require(
                    preservesOriginalPatient(rows(connection, PATIENT), before.patient()),
                    "PATIENT_CHANGED");
            require(rows(connection, CONSENT).equals(before.consent()), "CONSENT_CHANGED");
            require(rows(connection, MESSAGE).equals(before.message()), "MESSAGE_CHANGED");
            require(rows(connection, AUDIT).equals(before.audit()), "AUDIT_VALUES_CHANGED");
            require(
                    rows(connection, PROPERTY).equals(before.property()),
                    "PROPERTY_VALUES_CHANGED");
            require(rows(connection, HRM_LINKS).equals(before.hrmLinks()), "HRM_LINKS_CHANGED");
            var keys = new ArrayList<String>();
            for (var row : rows(connection, HRM_KEYS)) {
                String key = row.get("cds_patient_key");
                keys.add(
                        key == null
                                ? null
                                : new String(
                                        HexFormat.of().parseHex(key),
                                        java.nio.charset.StandardCharsets.UTF_8));
            }
            require(
                    java.util.Arrays.asList("730000001", "730000001", null, null).equals(keys),
                    "HRM_PATIENT_KEY_INVALID");
        }
    }

    private static boolean preservesOriginalPatient(
            List<Map<String, String>> actual, List<Map<String, String>> original) {
        return original.size() == 1
                && actual.size() == 1
                && actual.getFirst().entrySet().containsAll(original.getFirst().entrySet());
    }

    /**
     * Retention was verified above; remove only this owned test fixture before the full schema/seed
     * oracle.
     */
    private static void removeSyntheticFixture(DriverManagerDataSource source) throws Exception {
        try (var connection = source.getConnection();
                var sql = connection.createStatement()) {
            require(
                    sql.executeUpdate(
                                    "DELETE FROM Consent_migration_audit WHERE"
                                            + " migration='synthetic_upgrade'")
                            == 2,
                    "AUDIT_TEARDOWN");
            require(
                    sql.executeUpdate("DELETE FROM Consent WHERE demographic_no=730000001") == 2,
                    "CONSENT_TEARDOWN");
            require(
                    sql.executeUpdate("DELETE FROM demographic WHERE demographic_no=730000001")
                            == 1,
                    "PATIENT_TEARDOWN");
            require(
                    sql.executeUpdate("DELETE FROM messagetbl WHERE messageid=7300001") == 1,
                    "MESSAGE_TEARDOWN");
            require(
                    sql.executeUpdate("DELETE FROM property WHERE name='synthetic_cds_upgrade'")
                            == 1,
                    "PROPERTY_TEARDOWN");
            require(
                    sql.executeUpdate(
                                    "DELETE FROM HRMDocumentToDemographic WHERE hrmDocumentId"
                                            + " LIKE 'SYNTHETIC-7300%'")
                            == 4,
                    "HRM_TEARDOWN");
        }
    }

    private static int cli(
            String command, String locations, DriverManagerDataSource source, Path log)
            throws Exception {
        require(List.of("migrate", "validate").contains(command), "CLI_COMMAND_INVALID");
        require(
                List.of(
                                "classpath:db/migration/common,classpath:db/migration/on",
                                "classpath:db/migration/common,classpath:db/migration/bc")
                        .contains(locations),
                "CLI_LOCATIONS_INVALID");
        Path java = Path.of(System.getProperty("java.home"), "bin", "java").toRealPath();
        require(
                java.isAbsolute() && Files.isRegularFile(java) && Files.isExecutable(java),
                "CLI_EXECUTABLE_INVALID");
        var builder =
                // The executable is the resolved current JVM, not caller input or a PATH lookup.
                // The runner is fixed, arguments are allowlisted, and no shell interprets them.
                // nosemgrep: command-injection-process-builder
                new ProcessBuilder(
                        java.toString(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        "io.github.carlos_emr.carlos.deb.FlywayRunner",
                        command,
                        locations);
        builder.environment().put("FLYWAY_URL", source.getUrl());
        builder.environment().put("FLYWAY_USER", source.getUsername());
        builder.environment().put("FLYWAY_PASSWORD", source.getPassword());
        var process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            require(process.waitFor(5, TimeUnit.MINUTES), "CLI_TIMEOUT");
            return process.exitValue();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static List<Map<String, String>> rows(Connection connection, String query)
            throws Exception {
        var result = new ArrayList<Map<String, String>>();
        try (var statement = connection.prepareStatement(query);
                var rows = statement.executeQuery()) {
            int columns = rows.getMetaData().getColumnCount();
            while (rows.next()) {
                var row = new LinkedHashMap<String, String>();
                for (int index = 1; index <= columns; index++) {
                    int type = rows.getMetaData().getColumnType(index);
                    byte[] value;
                    if (type == java.sql.Types.BINARY
                            || type == java.sql.Types.VARBINARY
                            || type == java.sql.Types.LONGVARBINARY
                            || type == java.sql.Types.BLOB) {
                        value = rows.getBytes(index);
                    } else {
                        String scalar = rows.getString(index);
                        value =
                                scalar == null
                                        ? null
                                        : scalar.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    }
                    String name = rows.getMetaData().getColumnName(index);
                    require(!row.containsKey(name), "AMBIGUOUS_COLUMN");
                    row.put(name, value == null ? null : HexFormat.of().formatHex(value));
                }
                result.add(row);
            }
        }
        return result;
    }

    private static void require(boolean condition, String code) {
        if (!condition) throw new IllegalStateException("CDS_UPGRADE_" + code);
    }

    private static int packagedMigrations(String locations) throws java.io.IOException {
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        int count = 0;
        for (String location : locations.split(",")) {
            String folder = location.substring("classpath:".length());
            count += resolver.getResources("classpath*:" + folder + "/V*.sql").length;
        }
        require(count > 0, "HEAD_MIGRATIONS_UNRESOLVED");
        return count;
    }
}
