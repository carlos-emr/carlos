/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Admin security seed regressions")
@Tag("unit")
@Tag("admin")
class AdminSecuritySeedRegressionTest {

    private static final String DOCTOR_EFORM_WRITE_GRANT =
            "insert into `secObjPrivilege` values('doctor','_eform','w',0,'999998');";

    @Test
    @DisplayName("should grant site-access privacy to admin role in fresh and migrated databases")
    void shouldGrantSiteAccessPrivacy_toAdminRole() throws IOException {
        String freshSeed = Files.readString(Path.of("database/mysql/oscardata.sql"));
        String migration = Files.readString(Path.of(
                "database/mysql/updates/update-2026-05-19-admin-site-access-privacy.sql"));

        assertThat(freshSeed)
                .as("fresh dev databases should let carlosdoc's admin role reach site-aware admin pages")
                .contains("insert into `secObjPrivilege` values('admin','_site_access_privacy','x',0,'999998')");
        assertThat(migration)
                .as("existing production databases should receive the same admin privilege")
                .contains("('_site_access_privacy', 'restrict access to only the assigned sites of a provider', 0)")
                .contains("('admin', '_site_access_privacy', 'x', 0, '999998')");
    }

    @Test
    @DisplayName("should seed doctor role with eForm write privilege in fresh seed")
    void shouldSeedDoctorEFormWritePrivilege_inFreshSeed() throws IOException {
        String freshSeed = Files.readString(Path.of("database/mysql/oscardata.sql"), StandardCharsets.UTF_8);

        assertThat(freshSeed)
                .as("fresh dev databases should seed doctor with _eform write (not full-access)")
                .contains(DOCTOR_EFORM_WRITE_GRANT)
                .as("admin deletion rights come from the existing _admin.eform grant, not a new _eform:d row")
                .doesNotContain("'admin','_eform','d'");
    }

    @Test
    @DisplayName("should downgrade doctor _eform privilege using correct column names in migration")
    void shouldDowngradeDoctorPrivilege_usingCorrectColumnNamesInMigration() throws IOException {
        String migration = Files.readString(Path.of(
                "database/mysql/updates/update-2026-06-15-eform-delete-privilege.sql"), StandardCharsets.UTF_8);

        assertThat(migration)
                .as("migration must use the real column name 'roleUserGroup', not the non-existent 'roleName'")
                .contains("roleUserGroup")
                .doesNotContain("roleName")
                .as("migration should downgrade doctor from x to w")
                .contains("SET privilege = 'w'")
                .contains("'doctor'")
                .contains("'_eform'")
                .contains("privilege = 'x'")
                .as("admin deletion rights come from the existing _admin.eform grant; no INSERT needed")
                .doesNotContain("'admin', '_eform', 'd'")
                .doesNotContain("'admin','_eform','d'");
    }
}
