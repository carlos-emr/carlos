/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the default carlosdoc privilege seed and matching migration.
 *
 * <p>The fresh-install seed now lives in the Flyway baseline reference-data migration
 * ({@code migration/on/V1.0.2__on_data.sql}), captured by {@code mysqldump}, so the
 * assertions match the dump's extended-INSERT tuple form ({@code ('col',...)}) rather
 * than the hand-written {@code insert into ... values(...)} statements the old
 * {@code oscardata.sql} used. Row order in a dump follows the primary key, so this test
 * asserts presence of each seed tuple, not their relative ordering.
 *
 * @since 2026-05-21
 */
@DisplayName("carlosdoc privilege seed regressions")
@Tag("unit")
@Tag("security")
class CarlosdocPrivilegeSeedRegressionTest {

    private static final Path DEVELOPMENT_SEED =
            Path.of(".devcontainer", "db", "scripts", "development.sql");
    private static final Path DEVELOPMENT_PRIVILEGES =
            Path.of(".devcontainer", "db", "scripts", "development_privileges.sql");
    private static final Path DATABASE_DOCKERFILE =
            Path.of(".devcontainer", "db", "Dockerfile");
    private static final Path POPULATE_SCRIPT =
            Path.of(".devcontainer", "db", "scripts", "populate_db.sh");
    private static final Path DEVCONTAINER_SEED =
            Path.of(".devcontainer", "development", "setup", "seed_data.sh");
    private static final Path SEED = Path.of("database", "mysql", "migration", "on", "V1.0.2__on_data.sql");
    private static final Path BC_SEED = Path.of("database", "mysql", "migration", "bc", "V1.0.2__bc_data.sql");
    private static final Path MIGRATION = Path.of("database", "mysql", "updates",
            "update-2026-05-21-carlosdoc-schedule-group-privilege.sql");
    private static final Pattern ADMIN_PRIVILEGE = Pattern.compile(
            "\\(\\s*'admin'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,"
                    + "\\s*(\\d+)\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern ADMIN_PRIVILEGE_DELETE = Pattern.compile(
            "DELETE\\s+FROM\\s+`?secObjPrivilege`?\\s+WHERE\\s+`?objectName`?"
                    + "\\s*=\\s*'([^']+)'\\s*;",
            Pattern.CASE_INSENSITIVE);

    /** The seed dump is a multi-MB mysqldump — read once per class, not per test. */
    private static String developmentSeedSql;
    private static String seedSql;
    private static String bcSeedSql;

    @BeforeAll
    static void loadSeed() throws IOException {
        developmentSeedSql = Files.readString(DEVELOPMENT_SEED, StandardCharsets.UTF_8);
        seedSql = Files.readString(SEED, StandardCharsets.UTF_8);
        bcSeedSql = Files.readString(BC_SEED, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should keep carlosdoc in admin role and preserve schedule access")
    void shouldKeepCarlosdocAdmin_whenSeeded() throws IOException {
        assertThat(seedSql).contains(
                "'999998','admin','R0000001',1,",
                "('admin','_admin','x',0,'999998')",
                "('admin','_admin.schedule','x',0,'999998')",
                "('admin','_appointment','x',0,'999998')");
    }

    @Test
    @DisplayName("should grant carlosdoc admin billing access in development seed")
    void shouldGrantCarlosdocAdminBillingAccess_whenDevelopmentSeeded() {
        assertThat(developmentSeedSql).contains(
                "INSERT INTO `secObjPrivilege` VALUES ('admin','_admin.billing','x',0,'999998');",
                "(2,'999998','admin','R0000001',1,");
    }

    @Test
    @DisplayName("should grant carlosdoc admin billing access in Flyway seeds")
    void shouldGrantCarlosdocAdminBillingAccess_whenFlywaySeeded() {
        assertThat(seedSql).contains(
                "('admin','_admin.billing','x',0,'999998')",
                "'999998','admin','R0000001',1,");
        assertThat(bcSeedSql).contains(
                "('admin','_admin.billing','x',0,'999998')",
                "'999998','admin','R0000001',1,");
    }

    @Test
    @DisplayName("should restore baseline admin privileges after development snapshot")
    void shouldRestoreBaselineAdminPrivileges_afterDevelopmentSnapshot() throws IOException {
        String privilegeRepairSql = Files.readString(DEVELOPMENT_PRIVILEGES, StandardCharsets.UTF_8);
        Map<String, String> baselinePrivileges = adminPrivileges(seedSql);
        Map<String, String> repairedPrivileges = effectiveAdminPrivileges(
                developmentSeedSql, privilegeRepairSql);

        assertThat(repairedPrivileges)
                .isEqualTo(baselinePrivileges)
                .doesNotContainKey("_admin.traceability");
        assertThat(privilegeRepairSql).contains(
                "('admin', '_admin.schedule', 'x', 0, '999998')",
                "('999998', '_admin.schedule.groupCreate', 'o', 1, '999998')",
                "ON DUPLICATE KEY UPDATE");
    }

    @Test
    @DisplayName("should apply development privilege repair to fresh and existing databases")
    void shouldApplyDevelopmentPrivilegeRepair_toFreshAndExistingDatabases() throws IOException {
        String databaseDockerfile = Files.readString(DATABASE_DOCKERFILE, StandardCharsets.UTF_8);
        String populateScript = Files.readString(POPULATE_SCRIPT, StandardCharsets.UTF_8);
        String devcontainerSeed = Files.readString(DEVCONTAINER_SEED, StandardCharsets.UTF_8);

        assertThat(databaseDockerfile).contains(
                "COPY ./.devcontainer/db/scripts/development_privileges.sql /scripts/development_privileges.sql");
        assertThat(populateScript)
                .contains("$SQL oscar < /scripts/development_privileges.sql")
                .satisfies(script -> assertThat(script.indexOf("/scripts/development_privileges.sql"))
                        .isGreaterThan(script.indexOf("/scripts/development.sql")));
        assertThat(devcontainerSeed).contains(
                "mariadb -h db -u root oscar \\",
                "/workspace/.devcontainer/db/scripts/development_privileges.sql");
    }

    @Test
    @DisplayName("should deny carlosdoc schedule group creation in seed")
    void shouldDenyCarlosdocGroupCreation_whenSeeded() throws IOException {
        assertThat(seedSql).contains(
                "('_admin.schedule.groupCreate','Create schedule provider groups',0)",
                "('admin','_admin.schedule.groupCreate','x',0,'999998')",
                "('999998','_admin.schedule.groupCreate','o',1,'999998')");
    }

    @Test
    @DisplayName("should force password reset for default carlosdoc seed")
    void shouldForcePasswordResetForDefaultCarlosdocSeed() {
        assertThat(seedSql)
                .contains("(128,'carlosdoc'")
                .contains(",'999998','2026',1,'2100-01-01'");
        assertThat(bcSeedSql)
                .contains("(128,'carlosdoc'")
                .contains(",'999998','2026',1,'2100-01-01'");
    }

    @Test
    @DisplayName("should apply carlosdoc group creation override in migration")
    void shouldApplyCarlosdocOverride_whenMigrationRuns() throws IOException {
        String migrationSql = Files.readString(MIGRATION, StandardCharsets.UTF_8);

        assertThat(migrationSql).contains(
                "('_admin.schedule.groupCreate', 'Create schedule provider groups', 0)",
                "('admin', '_admin.schedule.groupCreate', 'x', 0, '999998')",
                "('999998', '_admin.schedule.groupCreate', 'o', 1, '999998')");
    }

    private static Map<String, String> adminPrivileges(String sql) {
        Map<String, String> privileges = new LinkedHashMap<>();
        Matcher matcher = ADMIN_PRIVILEGE.matcher(sql);
        while (matcher.find()) {
            privileges.put(matcher.group(1),
                    matcher.group(2) + '|' + matcher.group(3) + '|' + matcher.group(4));
        }
        return privileges;
    }

    private static Map<String, String> effectiveAdminPrivileges(String seed, String repair) {
        Map<String, String> privileges = adminPrivileges(seed);
        privileges.putAll(adminPrivileges(repair));

        Matcher deletedPrivilege = ADMIN_PRIVILEGE_DELETE.matcher(repair);
        while (deletedPrivilege.find()) {
            privileges.remove(deletedPrivilege.group(1));
        }
        return privileges;
    }
}
