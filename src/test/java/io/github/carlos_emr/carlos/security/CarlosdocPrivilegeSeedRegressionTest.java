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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the default carlosdoc privilege seed and matching migration.
 *
 * @since 2026-05-21
 */
@DisplayName("carlosdoc privilege seed regressions")
@Tag("unit")
@Tag("security")
class CarlosdocPrivilegeSeedRegressionTest {

    private static final Path OSCARDATA = Path.of("database", "mysql", "oscardata.sql");
    private static final Path MIGRATION = Path.of("database", "mysql", "updates",
            "update-2026-05-21-carlosdoc-schedule-group-privilege.sql");
    private static final String ADMIN_GROUP_CREATE_GRANT =
            "insert into `secObjPrivilege` values('admin','_admin.schedule.groupCreate','x',0,'999998');";
    private static final String CARLOSDOC_GROUP_CREATE_OVERRIDE =
            "insert into `secObjPrivilege` values('999998','_admin.schedule.groupCreate','o',1,'999998');";

    @Test
    @DisplayName("should keep carlosdoc in admin role and preserve schedule access")
    void shouldKeepCarlosdocAdmin_whenSeeded() throws IOException {
        String seedSql = Files.readString(OSCARDATA, StandardCharsets.UTF_8);

        assertThat(seedSql).contains(
                "insert into `secUserRole` (`provider_no`,`role_name`,`orgcd`,`activeyn`,lastUpdateDate) values('999998', 'admin', 'R0000001',1,now());",
                "insert into `secObjPrivilege` values('admin', '_admin', 'x', 0, '999998');",
                "insert into `secObjPrivilege` values('admin','_admin.schedule','x',0,'999998');",
                "insert into `secObjPrivilege` values('admin','_appointment','x',0,'999998');");
    }

    @Test
    @DisplayName("should deny carlosdoc schedule group creation in seed")
    void shouldDenyCarlosdocGroupCreation_whenSeeded() throws IOException {
        String seedSql = Files.readString(OSCARDATA, StandardCharsets.UTF_8);

        assertThat(seedSql).contains(
                "('_admin.schedule.groupCreate', 'Create schedule provider groups', 0)",
                ADMIN_GROUP_CREATE_GRANT,
                CARLOSDOC_GROUP_CREATE_OVERRIDE);
        assertThat(seedSql.indexOf(CARLOSDOC_GROUP_CREATE_OVERRIDE))
                .isGreaterThan(seedSql.indexOf(ADMIN_GROUP_CREATE_GRANT));
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
}
