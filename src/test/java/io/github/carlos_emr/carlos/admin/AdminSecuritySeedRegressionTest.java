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

    private static final String ADMIN_EFORM_DELETE_GRANT =
            "insert into `secObjPrivilege` values('admin','_eform','d',0,'999998');";
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
    @DisplayName("should grant admin delete and doctor write for eForms in fresh seed")
    void shouldGrantAdminDeleteAndDoctorWrite_forEFormsInFreshSeed() throws IOException {
        String freshSeed = Files.readString(Path.of("database/mysql/oscardata.sql"), StandardCharsets.UTF_8);

        assertThat(freshSeed)
                .contains(ADMIN_EFORM_DELETE_GRANT, DOCTOR_EFORM_WRITE_GRANT);
        assertThat(freshSeed.indexOf(DOCTOR_EFORM_WRITE_GRANT))
                .isGreaterThan(freshSeed.indexOf(ADMIN_EFORM_DELETE_GRANT));
    }

    @Test
    @DisplayName("should preserve admin eForm delete privilege while downgrading doctor in migration")
    void shouldPreserveAdminEFormDeletePrivilege_whileDowngradingDoctorInMigration() throws IOException {
        String migration = Files.readString(Path.of(
                "database/mysql/updates/update-2026-06-15-eform-delete-privilege.sql"), StandardCharsets.UTF_8);

        assertThat(migration)
                .contains("SELECT 'admin', '_eform', 'd', 0, '999998'")
                .contains("WHERE roleName = 'admin'")
                .contains("AND objectName = '_eform'")
                .contains("AND privilege IN ('d', 'x')")
                .contains("SET privilege = 'w'")
                .contains("WHERE roleName = 'doctor'")
                .contains("AND objectName = '_eform'")
                .contains("AND privilege = 'x'");
    }
}
