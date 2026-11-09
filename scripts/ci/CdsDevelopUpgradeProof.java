/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
import io.github.carlos_emr.carlos.db.FlywayReleasedMigrationBackfill;
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
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Actual develop history, retained synthetic clinical data, CLI runner and boot validation. */
public final class CdsDevelopUpgradeProof {
    private static final String BASE = "497896230576b492284b1c29efa77f4c4a2409ea";
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

    private record Snapshot(
            List<Map<String, String>> history,
            List<Map<String, String>> patient,
            List<Map<String, String>> consent,
            List<Map<String, String>> message,
            List<Map<String, String>> audit,
            List<Map<String, String>> property) {
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
                || !url.matches("jdbc:mysql://[^/]+/" + database + "(?:\\?.*)?"))
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
        requireUnknownAuditRefusal(
                source, locations, privateRoot.resolve(province + "-refused.log"), before);
        exerciseInterruptedPropertyStage(source, locations, privateRoot, province);
        verifyConcurrentRecoveryFence(
                source, locations, privateRoot.resolve(province + "-concurrent.log"));
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
                FlywayReleasedMigrationBackfill.load(
                        Flyway.configure()
                                .dataSource(source)
                                .locations(locations.split(","))
                                .ignoreMigrationPatterns(new String[0])
                                .failOnMissingLocations(true)
                                .cleanDisabled(true));
        upgraded.validate();
        require(upgraded.info().pending().length == 0, "PENDING_REMAINS");
        int applied = upgraded.info().applied().length;
        require(applied == (province.equals("on") ? 98 : 96), "HEAD_HISTORY_COUNT");
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
                        + ",\"retainedClinicalRows\":4,\"retainedAuditRows\":2,\"retainedPropertyRows\":1,\"interruptedColumnRecovery\":true,\"concurrentRecoveryFenced\":true,\"unreviewedSchemaRefused\":true,\"appliedHistoryUnchanged\":true,\"cliMigrate\":true,\"cliValidate\":true,\"bootValidate\":true,\"pending\":0}\n");
        System.out.println(
                "CDS develop upgrade passed: "
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
        require(initial == (province.equals("on") ? 68 : 65), "BASE_HISTORY_COUNT");
        require(
                original.info().current().getVersion().toString().equals("1.0.81"),
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
                            + " Consent_migration_audit(consent_id,migration,reason,prior_explicit,prior_optout,prior_deleted,recorded_at)"
                            + " SELECT id,'synthetic_upgrade','preserve',NULL,1,NULL,'2024-01-02"
                            + " 03:04:05' FROM Consent WHERE demographic_no=730000001");
            }
            try (var statement =
                    connection.prepareStatement(
                            "INSERT INTO property(name,value,provider_no)"
                                    + " VALUES('synthetic_cds_upgrade',?,'')")) {
                statement.setString(1, "SYNTHETIC complete Unicode 😀 & footer\r\n".repeat(200));
                require(statement.executeUpdate() == 1, "PROPERTY_FIXTURE_MISSING");
            }
            var before =
                    new Snapshot(
                            rows(connection, HISTORY),
                            rows(connection, PATIENT),
                            rows(connection, CONSENT),
                            rows(connection, MESSAGE),
                            rows(connection, AUDIT),
                            rows(connection, PROPERTY));
            require(before.audit().size() == 2, "AUDIT_FIXTURE_MISSING");
            return before;
        }
    }

    private static void requireUnknownAuditRefusal(
            DriverManagerDataSource source, String locations, Path log, Snapshot before)
            throws Exception {
        try (var connection = source.getConnection();
                var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "ALTER TABLE Consent_migration_audit ADD COLUMN unreviewed_upgrade_probe INT"
                            + " NULL");
            try {
                require(
                        cli("migrate", locations, source, log) != 0
                                && Files.readString(log)
                                        .contains(
                                                "FLYWAY_RELEASED_CONSENT_AUDIT_PREPARATION_REFUSED"),
                        "UNREVIEWED_SCHEMA_ACCEPTED");
                require(
                        rows(connection, HISTORY).equals(before.history()),
                        "REFUSAL_CHANGED_HISTORY");
            } finally {
                sql.executeUpdate(
                        "ALTER TABLE Consent_migration_audit DROP COLUMN unreviewed_upgrade_probe");
            }
        }
    }

    private static void exerciseInterruptedPropertyStage(
            DriverManagerDataSource source, String locations, Path root, String province)
            throws Exception {
        // Reproduce a process interruption after staging, without modifying an original value.
        try (var connection = source.getConnection();
                var sql = connection.createStatement()) {
            sql.executeUpdate(
                    "ALTER TABLE property RENAME COLUMN `value` TO carlos_forward_59_27_value, ADD"
                        + " COLUMN `value` VARCHAR(4000) CHARACTER SET utf8mb4 COLLATE"
                        + " utf8mb4_general_ci NULL DEFAULT NULL AFTER carlos_forward_59_27_value");
            require(
                    sql.executeUpdate(
                                    "UPDATE property SET `value`='SYNTHETIC unexpected write' WHERE"
                                            + " name='synthetic_cds_upgrade'")
                            == 1,
                    "PROPERTY_WRITE_FIXTURE");
        }
        Path refused = root.resolve(province + "-property-refused.log");
        require(
                cli("migrate", locations, source, refused) != 0
                        && Files.readString(refused)
                                .contains("FLYWAY_RELEASED_PROPERTY_PREPARATION_REFUSED"),
                "UNEXPECTED_PROPERTY_WRITE_ACCEPTED");
        try (var connection = source.getConnection();
                var sql = connection.createStatement()) {
            require(
                    sql.executeUpdate(
                                    "UPDATE property SET `value`=NULL WHERE"
                                            + " name='synthetic_cds_upgrade' AND `value`='SYNTHETIC"
                                            + " unexpected write'")
                            == 1,
                    "PROPERTY_WRITE_TEARDOWN");
        }
        Path validation = root.resolve(province + "-property-validation.log");
        require(
                cli("validate", locations, source, validation) != 0
                        && Files.readString(validation)
                                .contains("FLYWAY_RELEASED_PROPERTY_RECOVERY_REQUIRED"),
                "STAGED_PROPERTY_VALIDATION_ACCEPTED");
    }

    private static void verifyConcurrentRecoveryFence(
            DriverManagerDataSource source, String locations, Path log) throws Exception {
        try (var connection = source.getConnection();
                var executor = Executors.newSingleThreadExecutor()) {
            try (var query =
                            connection.prepareStatement(
                                    "SELECT"
                                        + " GET_LOCK(CONCAT('carlos-release:',LEFT(SHA2(DATABASE(),256),40)),0)");
                    var rows = query.executeQuery()) {
                require(rows.next() && rows.getInt(1) == 1, "MIGRATION_LOCK_FIXTURE");
            }
            var upgrading = executor.submit(() -> cli("migrate", locations, source, log));
            try {
                boolean waiting = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (!waiting && System.nanoTime() < deadline) {
                    try (var query =
                                    connection.prepareStatement(
                                            "SELECT COUNT(*) FROM information_schema.PROCESSLIST"
                                                + " WHERE DB=DATABASE() AND STATE='User lock' AND"
                                                + " INFO LIKE '%carlos-release:%'");
                            var rows = query.executeQuery()) {
                        waiting = rows.next() && rows.getInt(1) > 0;
                    }
                    if (!waiting) Thread.sleep(50);
                }
                require(waiting && !upgrading.isDone(), "CONCURRENT_MIGRATION_DID_NOT_WAIT");
                try (var query =
                                connection.prepareStatement(
                                        "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE"
                                            + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='property'"
                                            + " AND COLUMN_NAME='carlos_forward_59_27_value'");
                        var rows = query.executeQuery()) {
                    require(
                            rows.next() && rows.getInt(1) == 1,
                            "RECOVERY_RAN_WITHOUT_EXCLUSIVE_LOCK");
                }
            } finally {
                try (var query =
                                connection.prepareStatement(
                                        "SELECT"
                                            + " RELEASE_LOCK(CONCAT('carlos-release:',LEFT(SHA2(DATABASE(),256),40)))");
                        var rows = query.executeQuery()) {
                    require(rows.next() && rows.getInt(1) == 1, "MIGRATION_LOCK_FIXTURE_RELEASE");
                }
            }
            require(upgrading.get(5, TimeUnit.MINUTES) == 0, "CONCURRENT_RECOVERY_FAILED");
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
        }
    }

    private static int cli(
            String command, String locations, DriverManagerDataSource source, Path log)
            throws Exception {
        var builder =
                new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
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
                    byte[] value = rows.getBytes(index);
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
}
