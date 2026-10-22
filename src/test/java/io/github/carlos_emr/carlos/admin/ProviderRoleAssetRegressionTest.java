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
package io.github.carlos_emr.carlos.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards Assign Role JSP behavior that is otherwise only exercised when the
 * container renders the page (JSPs compile under the {@code jspc} profile, not
 * in the default build).
 *
 * @since 2026-09-04
 */
@DisplayName("Provider role asset regressions")
@Tag("unit")
@Tag("admin")
class ProviderRoleAssetRegressionTest {

    private static final Path PROVIDER_ROLE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "admin", "providerRole.jsp");

    @Test
    @DisplayName("should build the role dropdown through the assignable-roles guards")
    void shouldBuildRoleDropdown_throughAssignableRolesGuards() throws IOException {
        String jsp = readProviderRoleJsp();

        assertThat(jsp)
                .contains("<%@ page import=\"io.github.carlos_emr.carlos.admin.support.AssignableRoles\" %>")
                .contains("AssignableRoles.filter(")
                // The multisites gate is the whole fix: without it the seeded `admin`
                // role, which holds `_site_access_privacy`, hides itself from the only
                // account able to grant it.
                .contains("IsPropertiesOn.isMultisitesEnable()")
                // The unguarded narrowing hid the seeded `admin` role from the only
                // account able to grant it on a standalone install (2026.08 alpha).
                .doesNotContainPattern("if \\(isSiteAccessPrivacy\\) \\{\\s+"
                        + "omit = CarlosProperties\\.getInstance\\(\\)")
                .doesNotContain("secRole.getName().equals(omit)");
    }

    private static String readProviderRoleJsp() throws IOException {
        return Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);
    }
}
