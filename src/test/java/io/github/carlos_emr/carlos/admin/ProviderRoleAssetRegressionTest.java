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

    @Test
    @DisplayName("should write the primary-role script's provider and role values through the JavaScript encoder")
    void shouldEncodeScriptItemValues_inPrimaryRoleScript() throws IOException {
        String jsp = readProviderRoleJsp();

        // Each row becomes a JavaScript string literal in the items array, so every value needs the block encoder.
        assertThat(jsp)
                .contains("providerNo: \"<carlos:encode value='<%= (String)prop.get(\"provider_no\") %>' context=\"javaScriptBlock\"/>\"")
                .contains("role_id: \"<carlos:encode value='<%= (String)prop.get(\"role_id\") %>' context=\"javaScriptBlock\"/>\"")
                .contains("roleName: \"<carlos:encode value='<%= (String)prop.get(\"role_name\") %>' context=\"javaScriptBlock\"/>\"")
                .doesNotContainPattern("(providerNo|role_id|roleName): \"<%=");
    }

    @Test
    @DisplayName("should build the primary-role options with DOM APIs so a role name stays text")
    void shouldBuildPrimaryRoleOptions_withDomApis() throws IOException {
        String jsp = readProviderRoleJsp();

        // roleName is decoded back to its stored characters in the script, so it must not be pasted into HTML.
        assertThat(jsp)
                .contains("$(\"#primaryRoleRole\").append($('<option>').val(items[i].roleName).text(items[i].roleName));")
                .doesNotContain("'<option value=\"' + items[i].roleName");
    }

    private static String readProviderRoleJsp() throws IOException {
        return Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);
    }
}
