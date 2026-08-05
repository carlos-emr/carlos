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
 * Guards the provider-role selectors rendered directly by the admin JSP.
 *
 * @since 2026-08-05
 */
@DisplayName("Provider role JSP regressions")
@Tag("unit")
@Tag("admin")
class ProviderRoleJspRegressionTest {

    private static final Path PROVIDER_ROLE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/providerRole.jsp");

    @Test
    @DisplayName("primary role selector should offer every assignable role")
    void shouldOfferEveryAssignableRole_inPrimaryRoleSelector() throws IOException {
        String jsp = Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);
        int selectStart = jsp.indexOf("<select id=\"primaryRoleRole\"");
        int selectEnd = jsp.indexOf("</select>", selectStart);

        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        assertThat(selectEnd).isGreaterThan(selectStart);

        String primaryRoleSelect = jsp.substring(selectStart, selectEnd);

        // The option list is fed by the full assignable-role collection built from
        // secRoleDao.findAllOrderByRole(), not by the selected provider's own roles.
        assertThat(jsp).containsPattern("vecRoleName\\s*=\\s*new ArrayList");
        assertThat(jsp).contains("secRoleDao.findAllOrderByRole()");
        assertThat(primaryRoleSelect)
                .containsPattern("vecRoleName\\.size\\(\\)")
                .containsPattern("vecRoleName\\.get\\(")
                .contains("context=\"htmlAttribute\"")
                .contains("context=\"html\"");

        // The client-side filter that narrowed the list to the provider's existing
        // roles (issue #3258) must stay gone.
        assertThat(jsp)
                .doesNotContain("primaryRoleChooseProvider")
                .doesNotContain("items[i].providerNo === provider")
                .doesNotContain("items[i].role_id !== \"\"");
    }

    @Test
    @DisplayName("primary role update should validate submitted provider and role")
    void shouldValidateSubmittedValues_beforeUpdatingPrimaryRole() throws IOException {
        String jsp = Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .containsPattern("hasText\\(providerNo\\)[\\s\\S]{0,40}findByProviderNo\\(providerNo\\)")
                .containsPattern("hasText\\(roleName\\)[\\s\\S]{0,40}vecRoleName\\.contains\\(roleName\\)")
                .containsPattern("\"1\"\\.equals\\(provider\\.getStatus\\(\\)\\)")
                .containsPattern("secRole != null[\\s\\S]{0,20}caisiProgram != null")
                .contains("admin.providerrole.msgNotUpdated");
    }

    @Test
    @DisplayName("primary role update should audit the privilege change")
    void shouldAuditThePrivilegeChange_whenPrimaryRoleIsUpdated() throws IOException {
        String jsp = Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);
        int blockStart = jsp.indexOf("//set the primary role");
        int blockEnd = jsp.indexOf("// update the role", blockStart);

        assertThat(blockStart).isGreaterThanOrEqualTo(0);
        assertThat(blockEnd).isGreaterThan(blockStart);

        // program_provider.role_id drives clinical-note access rights, so the
        // successful write must leave both an audit entry and user feedback.
        String primaryRoleBlock = jsp.substring(blockStart, blockEnd);
        assertThat(primaryRoleBlock)
                .containsPattern("LogAction\\.addLog\\([\\s\\S]{0,80}LogConst\\.CON_ROLE")
                .contains("admin.providerrole.msgUpdated");
    }

    @Test
    @DisplayName("page should warn when a primary role is not one of the provider's assigned roles")
    void shouldWarnAboutUnassignedPrimaryRole_whenAuditingProviders() throws IOException {
        String jsp = Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);

        // Offering every role (issue #3258) makes an unheld primary role reachable;
        // the audit loop has to surface it rather than leave the column silently blank.
        assertThat(jsp)
                .contains("which is not one of their assigned roles")
                .containsPattern("roleNamesById\\.get\\(pp\\.getRoleId\\(\\)\\)")
                .containsPattern("assignedEntry\\.getValue\\(\\)\\.contains\\(primaryRoleName\\)");
    }

    @Test
    @DisplayName("page initialization should tolerate a hidden primary role section")
    void shouldTolerateMissingPrimaryRoleControls_duringPageInitialization() throws IOException {
        String jsp = Files.readString(PROVIDER_ROLE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("const primaryRoleProvider = document.getElementById('primaryRoleProvider');")
                .containsPattern("if \\(primaryRoleProvider\\) \\{\\s+primaryRoleProvider\\.value = \\\"\\\";");
    }
}
