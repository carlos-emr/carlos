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

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path PROVIDER_ROLE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/providerRole.jsp");

    /**
     * Resolves a project-relative path from the Maven {@code basedir} property or
     * current working directory, walking parent directories for IDE and CLI runs.
     *
     * @param relativePath path relative to the project root
     * @return resolved regular file or directory path
     */
    private static Path resolveProjectPath(Path relativePath) {
        String searchRoot = System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir"));
        Path current = Path.of(searchRoot).toAbsolutePath().normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                String.format("Unable to locate %s starting at %s", relativePath, searchRoot));
    }

    @Test
    @DisplayName("primary role selector should offer only roles the provider holds")
    void shouldOfferOnlyHeldRoles_inPrimaryRoleSelector() throws IOException {
        String jsp = Files.readString(resolveProjectPath(PROVIDER_ROLE_JSP), StandardCharsets.UTF_8);
        int selectStart = jsp.indexOf("<select id=\"primaryRoleRole\"");
        int selectEnd = jsp.indexOf("</select>", selectStart);

        assertThat(selectStart).isGreaterThanOrEqualTo(0);
        assertThat(selectEnd).isGreaterThan(selectStart);

        String primaryRoleSelect = jsp.substring(selectStart, selectEnd);

        /* The selector holds only a placeholder; options are filled in from the chosen
         * provider's own roles (issue #3258 is by design). The full assignable-role list,
         * vecRoleName, belongs to the table's add/switch dropdown, not here.
         */
        assertThat(primaryRoleSelect)
                .contains("admin.providerupdateprovider.selectBelow")
                .doesNotContain("vecRoleName");

        // Held roles reach the browser as encoded JSON on the provider option.
        assertThat(jsp)
                .contains("heldRolesByProvider")
                .contains("data-roles=\"<carlos:encode")
                // Serialized by Jackson, not hand-escaped: a control character in a role name
                // would emit invalid JSON and leave the selector silently empty.
                .containsPattern("HELD_ROLES_MAPPER\\s*\\.\\s*writeValueAsString")
                /* Active assignments only, on both sides. An inactive role is ignored by
                 * authorization but would still drive note access through
                 * program_provider.role_id if it could be made primary.
                 */
                .contains("secUserRoleDao.findActiveByProviderNo(providerNo)")
                .doesNotContain("secUserRoleDao.findByProviderNo(providerNo)")
                .containsPattern("!\"1\"\\.equals\\(prop\\.getProperty\\(\"activeyn\"")
                /* The multioffice role is withheld from the selector and refused server-side:
                 * promoting it activates its note access, so an administrator who may not
                 * assign that role must not be able to make it primary either.
                 */
                .containsPattern("heldRoleName\\.equals\\(omit\\)")
                .containsPattern("!roleName\\.equals\\(omit\\)");

        // The old array interpolated provider_no and role_id into a script block unencoded.
        assertThat(jsp)
                .doesNotContain("items.push(item)")
                .doesNotContain("role_id: \"<%=prop.get(\"role_id\")%>\"");
    }

    @Test
    @DisplayName("primary role selector should filter its options by the chosen provider")
    void shouldFilterOptionsByChosenProvider_inPrimaryRoleSelector() throws IOException {
        String jsp = Files.readString(resolveProjectPath(PROVIDER_ROLE_JSP), StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("function primaryRoleChooseProvider()")
                .contains("onchange=\"primaryRoleChooseProvider()\"")
                .contains("JSON.parse(selectedProvider.dataset.roles)")
                // Rebuilt, not hidden: repeating a role value per provider would make
                // selection by value ambiguous. Labels use textContent, never innerHTML.
                .containsPattern("while \\(roleSelect\\.options\\.length > 1\\)")
                .containsPattern("option\\.textContent\\s*=\\s*heldRole")
                .doesNotContain("option.innerHTML");
    }

    @Test
    @DisplayName("primary role update should validate submitted provider and role")
    void shouldValidateSubmittedValues_beforeUpdatingPrimaryRole() throws IOException {
        String jsp = Files.readString(resolveProjectPath(PROVIDER_ROLE_JSP), StandardCharsets.UTF_8);

        // A POST can carry any role name, so the server re-checks that the provider actually
        // holds the submitted role rather than trusting the filtered selector.
        assertThat(jsp)
                .containsPattern("hasText\\(providerNo\\)[\\s\\S]{0,40}providerDao\\.findByProviderNo\\(providerNo\\)")
                .contains("secUserRoleDao.findActiveByProviderNo(providerNo)")
                .containsPattern("providerHoldsRole = true")
                .containsPattern("secRole = providerHoldsRole \\? secRoleDao\\.findByName\\(roleName\\) : null")
                .containsPattern("\"1\"\\.equals\\(provider\\.getStatus\\(\\)\\)")
                .containsPattern("secRole != null[\\s\\S]{0,20}caisiProgram != null")
                .contains("admin.providerrole.msgNotUpdated");
    }

    @Test
    @DisplayName("primary role update should audit the privilege change")
    void shouldAuditThePrivilegeChange_whenPrimaryRoleIsUpdated() throws IOException {
        String jsp = Files.readString(resolveProjectPath(PROVIDER_ROLE_JSP), StandardCharsets.UTF_8);
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
    @DisplayName("role column should show a withheld role on the row that holds it")
    void shouldShowWithheldRole_onItsOwnRow() throws IOException {
        String jsp = Files.readString(resolveProjectPath(PROVIDER_ROLE_JSP), StandardCharsets.UTF_8);

        /* Without an option for it the select falls back to "-", so the row reads blank while
         * "Primary EMR Role" says Yes. Added only to its own row, so the multioffice guard
         * still prevents conferring the role on anyone else.
         */
        assertThat(jsp)
                .containsPattern("!currentRoleName\\.isEmpty\\(\\)\\s*&&\\s*!vecRoleName\\.contains\\(currentRoleName\\)")
                // data-org feeds enableAddRoleButton and must not be raw DB text.
                .containsPattern("data-org=\"<carlos:encode")
                .doesNotContain("data-org=\"<%= item.getProperty(\"role_name\", \"\") %>\"");

        // One role select per row, so each carries its own id and a hidden label; without them
        // a screen reader cannot tell the rows apart.
        assertThat(jsp)
                .containsPattern("<label[^>]*for=\"roleNew-<%= i %>\"")
                .containsPattern("<select id=\"roleNew-<%= i %>\" name=\"roleNew\"");
    }

    @Test
    @DisplayName("page initialization should tolerate a hidden primary role section")
    void shouldTolerateMissingPrimaryRoleControls_duringPageInitialization() throws IOException {
        String jsp = Files.readString(resolveProjectPath(PROVIDER_ROLE_JSP), StandardCharsets.UTF_8);

        // Separate anchors for the guard and the assignment: pinning them as one pattern
        // would fail on reindentation alone, with no behaviour change.
        assertThat(jsp)
                .contains("const primaryRoleProvider = document.getElementById('primaryRoleProvider');")
                .containsPattern("if \\(primaryRoleProvider\\)")
                .containsPattern("primaryRoleProvider\\.value\\s*=\\s*\"\"");
    }
}
