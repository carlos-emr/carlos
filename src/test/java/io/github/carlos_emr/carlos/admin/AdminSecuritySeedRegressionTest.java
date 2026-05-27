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
}
