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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the devcontainer database build against losing migration-added security objects
 * (issue #4369).
 *
 * <p>{@code development.sql} truncate-reloads the security tables after the Flyway chain has
 * run, so {@code populate_db.sh} must re-apply every forward migration that touches them. The
 * re-apply is discovered by content, so a future migration is covered without editing the
 * script; this test pins that mechanism and the idempotency the re-run depends on.
 *
 * @since 2026-10-08
 */
@DisplayName("devcontainer security migration re-apply regressions")
@Tag("unit")
@Tag("security")
class DevcontainerSecurityMigrationReapplyRegressionTest {

    private static final Path POPULATE_SCRIPT =
            Path.of(".devcontainer", "db", "scripts", "populate_db.sh");
    private static final Path MIGRATIONS = Path.of("database", "mysql", "migration");
    private static final Pattern SECURITY_TABLES = Pattern.compile(
            "secObjectName|secObjPrivilege|secPrivilege|secRole|secUserRole");
    private static final Pattern PLAIN_INSERT = Pattern.compile(
            "^\\s*INSERT\\s+INTO\\s+`?sec", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    @Test
    @DisplayName("should reapply security migrations after the demo dump and before privilege repair")
    void shouldReapplySecurityMigrations_afterDemoDump() throws IOException {
        String script = Files.readString(POPULATE_SCRIPT, StandardCharsets.UTF_8);

        int demoLoad = script.indexOf("$SQL carlos < /scripts/development.sql");
        int reapply = script.indexOf("for SEC_MIGRATION in ${FORWARD}");
        int repair = script.indexOf("$SQL carlos < /scripts/development_privileges.sql");

        assertThat(demoLoad).isPositive();
        assertThat(reapply).isGreaterThan(demoLoad);
        assertThat(repair).isGreaterThan(reapply);
        assertThat(script).contains(
                "secObjectName|secObjPrivilege|secPrivilege|secRole|secUserRole",
                "$SQL carlos < \"${SEC_MIGRATION}\"");
    }

    @Test
    @DisplayName("should keep every forward security migration idempotent for re-application")
    void shouldKeepSecurityMigrationsIdempotent_forReapply() throws IOException {
        try (Stream<Path> files = Stream.of("common", "on")
                .flatMap(dir -> {
                    try {
                        return Files.list(MIGRATIONS.resolve(dir));
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                })) {
            files.filter(p -> p.getFileName().toString().matches("V1\\.0\\.(?!1__|2__)\\d+.*\\.sql"))
                    .filter(p -> !p.getFileName().toString().startsWith("V1__"))
                    .forEach(p -> {
                        String sql = read(p);
                        if (SECURITY_TABLES.matcher(sql).find()) {
                            for (String statement : sql.split(";")) {
                                if (PLAIN_INSERT.matcher(statement).find()) {
                                    assertThat(statement)
                                            .as("%s re-applies against a populated security table, so "
                                                    + "plain inserts must be guarded", p.getFileName())
                                            .matches("(?is).*(WHERE\\s+NOT\\s+EXISTS|ON\\s+DUPLICATE\\s+KEY).*");
                                }
                            }
                        }
                    });
        }
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
