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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
    private static final Set<String> ADMIN_ROLE_GROUPS = Set.of("admin", "999998");
    private static final Pattern SEC_OBJ_PRIVILEGE_INSERT = Pattern.compile(
            "INSERT\\s+INTO\\s+`?secObjPrivilege`?(?:\\s*\\([^)]*\\))?"
                    + "\\s+VALUES\\s+([^;]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PRIVILEGE_TUPLE = Pattern.compile(
            "\\(\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,\\s*'([^']+)'\\s*,"
                    + "\\s*(\\d+)\\s*,\\s*'([^']+)'\\s*\\)");
    private static final Pattern PRIVILEGE_DELETE = Pattern.compile(
            "DELETE\\s+FROM\\s+`?secObjPrivilege`?\\s+WHERE\\s+`?objectName`?"
                    + "\\s*=\\s*'([^']+)'\\s*;",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVILEGE_KEY_DELETE = Pattern.compile(
            "DELETE\\s+FROM\\s+`?secObjPrivilege`?\\s+WHERE\\s+`?roleUserGroup`?"
                    + "\\s*=\\s*'([^']+)'\\s+AND\\s+`?objectName`?"
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
        Map<PrivilegeKey, String> baselinePrivileges = administrationPrivileges(privileges(seedSql));
        Map<PrivilegeKey, String> bcBaselinePrivileges = administrationPrivileges(privileges(bcSeedSql));
        Map<PrivilegeKey, String> repairPrivileges =
                administrationPrivileges(privileges(privilegeRepairSql));
        Map<PrivilegeKey, String> allRepairedPrivileges =
                effectivePrivileges(developmentSeedSql, privilegeRepairSql);
        Map<PrivilegeKey, String> repairedPrivileges =
                administrationPrivileges(allRepairedPrivileges);
        Map<PrivilegeKey, String> repairedExistingBaselinePrivileges = administrationPrivileges(
                effectivePrivileges(seedSql, privilegeRepairSql));
        PrivilegeKey carlosdocGroupCreation =
                new PrivilegeKey("999998", "_admin.schedule.groupCreate");
        PrivilegeKey doctorTraceability =
                new PrivilegeKey("doctor", "_admin.traceability");
        Map<PrivilegeKey, String> expectedDevelopmentPrivileges =
                new LinkedHashMap<>(baselinePrivileges);
        expectedDevelopmentPrivileges.remove(carlosdocGroupCreation);

        assertThat(baselinePrivileges).isNotEmpty();
        assertThat(bcBaselinePrivileges).isNotEmpty();
        assertThat(repairPrivileges).hasSize(7);
        assertThat(repairedPrivileges).isEqualTo(expectedDevelopmentPrivileges);
        assertThat(baselinePrivileges).containsAllEntriesOf(repairPrivileges);
        assertThat(bcBaselinePrivileges).containsAllEntriesOf(repairPrivileges);
        assertThat(repairedPrivileges.keySet())
                .isNotEmpty()
                .doesNotContain(carlosdocGroupCreation)
                .noneMatch(key -> key.objectName().equals("_admin.traceability"));
        assertThat(repairedExistingBaselinePrivileges.keySet())
                .isNotEmpty()
                .doesNotContain(carlosdocGroupCreation);
        assertThat(privileges(developmentSeedSql)).containsKey(doctorTraceability);
        assertThat(allRepairedPrivileges.keySet())
                .isNotEmpty()
                .noneMatch(key -> key.objectName().equals("_admin.traceability"));
        assertThat(privilegeTupleCount(privilegeRepairSql))
                .isEqualTo(privileges(privilegeRepairSql).size());
        assertThat(privilegeRepairSql)
                .contains(
                        "('admin', '_admin.schedule', 'x', 0, '999998')",
                        "WHERE `roleUserGroup` = '999998'",
                        "AND `objectName` = '_admin.schedule.groupCreate'",
                        "DELETE FROM `secObjPrivilege`\n"
                                + "WHERE `objectName` = '_admin.traceability';",
                        "ON DUPLICATE KEY UPDATE")
                .doesNotContain("('999998', '_admin.schedule.groupCreate', 'o', 1, '999998')");
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
                "mariadb -h db -u root oscar \\\n"
                        + "    < /workspace/.devcontainer/db/scripts/development_privileges.sql");
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

    private static Map<PrivilegeKey, String> privileges(String sql) {
        Map<PrivilegeKey, String> privileges = new LinkedHashMap<>();
        Matcher insert = SEC_OBJ_PRIVILEGE_INSERT.matcher(sql);
        while (insert.find()) {
            privileges.putAll(privilegeTuples(insert.group(1)));
        }
        return privileges;
    }

    private static Map<PrivilegeKey, String> privilegeTuples(String sql) {
        Map<PrivilegeKey, String> privileges = new LinkedHashMap<>();
        Matcher tuple = PRIVILEGE_TUPLE.matcher(sql);
        while (tuple.find()) {
            // The table key is (roleUserGroup, objectName), so later repair upserts
            // replace the effective tuple for the same role and security object.
            privileges.put(new PrivilegeKey(tuple.group(1), tuple.group(2)),
                    tuple.group(3) + '|' + tuple.group(4) + '|' + tuple.group(5));
        }
        return privileges;
    }

    private static Map<PrivilegeKey, String> administrationPrivileges(
            Map<PrivilegeKey, String> allPrivileges) {
        Map<PrivilegeKey, String> administrationPrivileges = new LinkedHashMap<>();
        allPrivileges.forEach((key, value) -> {
            if (ADMIN_ROLE_GROUPS.contains(key.roleUserGroup())) {
                administrationPrivileges.put(key, value);
            }
        });
        return administrationPrivileges;
    }

    private static long privilegeTupleCount(String sql) {
        long count = 0;
        Matcher insert = SEC_OBJ_PRIVILEGE_INSERT.matcher(sql);
        while (insert.find()) {
            count += PRIVILEGE_TUPLE.matcher(insert.group(1)).results().count();
        }
        return count;
    }

    private static Map<PrivilegeKey, String> effectivePrivileges(String... scripts) {
        Map<PrivilegeKey, String> privileges = new LinkedHashMap<>();
        for (String sql : scripts) {
            List<PrivilegeMutation> mutations = new ArrayList<>();
            Matcher insert = SEC_OBJ_PRIVILEGE_INSERT.matcher(sql);
            while (insert.find()) {
                Map<PrivilegeKey, String> insertedPrivileges = privilegeTuples(insert.group(1));
                mutations.add(new PrivilegeMutation(
                        insert.start(), current -> current.putAll(insertedPrivileges)));
            }

            Matcher deletedPrivilege = PRIVILEGE_DELETE.matcher(sql);
            while (deletedPrivilege.find()) {
                String objectName = deletedPrivilege.group(1);
                mutations.add(new PrivilegeMutation(deletedPrivilege.start(), current ->
                        current.keySet().removeIf(key -> key.objectName().equals(objectName))));
            }

            Matcher deletedPrivilegeKey = PRIVILEGE_KEY_DELETE.matcher(sql);
            while (deletedPrivilegeKey.find()) {
                PrivilegeKey key = new PrivilegeKey(
                        deletedPrivilegeKey.group(1), deletedPrivilegeKey.group(2));
                mutations.add(new PrivilegeMutation(
                        deletedPrivilegeKey.start(), current -> current.remove(key)));
            }

            mutations.sort(Comparator.comparingInt(PrivilegeMutation::position));
            mutations.forEach(mutation -> mutation.applyTo(privileges));
        }
        return privileges;
    }

    private record PrivilegeMutation(
            int position, Consumer<Map<PrivilegeKey, String>> operation) {
        void applyTo(Map<PrivilegeKey, String> privileges) {
            operation.accept(privileges);
        }
    }

    private record PrivilegeKey(String roleUserGroup, String objectName) {}
}
