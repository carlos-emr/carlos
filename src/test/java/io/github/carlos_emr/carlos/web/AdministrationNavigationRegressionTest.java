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
package io.github.carlos_emr.carlos.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for Administration navigation entries that should not be
 * visible unless the linked action can authorize the current user.
 *
 * @since 2026-05-19
 */
@DisplayName("Administration navigation regression tests")
@Tag("unit")
@Tag("admin")
class AdministrationNavigationRegressionTest {

    private static final Path LEFT_NAV = Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");
    private static final Path ADMIN_HOME = Path.of("src/main/webapp/WEB-INF/jsp/administration/index.jsp");

    private static final List<Path> ADMIN_NAV_FILES = List.of(
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf"),
            Path.of("src/main/webapp/WEB-INF/jsp/admin/admin.jsp"));

    private static final List<String> ADMIN_BILLING_WRITE_LINKS = List.of(
            "/billing/CA/ON/ViewBenefitScheduleUpload",
            "/billing/CA/ON/AddEditServiceCode",
            "/billing/CA/ON/ViewBillingONEditPrivateCode",
            "/admin/GstControl",
            "/admin/GstReport",
            "/billing/CA/ON/ManageBillingLocation",
            "/billing/CA/ON/BillingONUpload",
            "/billing/CA/ON/moveMOHFiles");

    @Test
    @DisplayName("Ontario billing admin links should require billing write privilege")
    void shouldRequireBillingWritePrivilege_forOntarioBillingAdminLinks() throws IOException {
        for (Path navFile : ADMIN_NAV_FILES) {
            String jsp = Files.readString(navFile);

            for (String link : ADMIN_BILLING_WRITE_LINKS) {
                assertLinkGuardedBy(jsp, link, "_admin.billing", "w");
            }
        }
    }

    @Test
    @DisplayName("Administration navigation should not link the removed Survey Manager route")
    void shouldNotLinkSurveyManager_inAdministrationNavigation() throws IOException {
        for (Path navFile : ADMIN_NAV_FILES) {
            assertThat(Files.readString(navFile))
                    .as("%s must not link the unmapped /SurveyManager route (issue #4563)", navFile)
                    .doesNotContain("SurveyManager");
        }
    }

    @Test
    @DisplayName("Lab forwarding link should require lab write privilege")
    void shouldRequireLabWritePrivilege_forLabForwardingLink() throws IOException {
        for (Path navFile : ADMIN_NAV_FILES) {
            assertLinkGuardedBy(Files.readString(navFile), "/admin/labForwardingRules", "_lab", "w");
        }
    }

    @Test
    @DisplayName("Group preference link should use mapped Struts route")
    void shouldUseMappedStrutsRoute_forGroupPreferenceLink() throws IOException {
        for (Path navFile : ADMIN_NAV_FILES) {
            String jsp = Files.readString(navFile);

            assertThat(jsp)
                    .as(navFile + " should not link to the unmapped lowercase plural route")
                    .doesNotContain("/admin/groupPreferences")
                    .contains("/admin/GroupPreference");
        }

        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/classes/struts-scheduling.xml")))
                .contains("name=\"admin/GroupPreference\"");
    }

    @Test
    @DisplayName("Administration index should not render Help or About header controls")
    void shouldNotRenderHelpOrAboutControls_forAdministrationIndex() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/administration/index.jsp"));

        assertThat(jsp)
                .doesNotContain("global.help")
                .doesNotContain("global.about")
                .doesNotContain("UserPropertyDAO")
                .doesNotContain("resource_helpHtml")
                .doesNotContain("resourcebaseurl")
                .doesNotContain("/encounter/ViewAbout");
        assertThat(jsp)
                .contains("id=\"dynamic-content\"")
                .contains("admin.admin.unlockAcct");
    }

    @Test
    @DisplayName("Administration home link should preserve focused schedule navigation")
    void shouldPreserveScheduleNavigation_forAdministrationHomeLink() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf"));

        assertThat(jsp)
                .contains("href=\"${ctx}/administration${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}\"")
                .doesNotContain("href=\"${ctx}/administration?scheduleNav=1\"");
    }

    @Test
    @DisplayName("Manage eForms links should preserve focused schedule navigation")
    void shouldPreserveScheduleNavigation_forManageEFormsLinks() throws IOException {
        for (Path jspPath : List.of(
                Path.of("src/main/webapp/WEB-INF/jsp/administration/index.jsp"),
                Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf"))) {
            String jsp = Files.readString(jspPath);

            assertThat(jsp)
                    .contains("/eform/efmformmanager${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}")
                    .doesNotContain("/eform/efmformmanager?scheduleNav=1\"");
        }
    }

    @Test
    @DisplayName("Manage Flowsheets link should follow the action's per-object read gate")
    void shouldGuardManageFlowsheetsLink_withPerObjectReadChecks() throws IOException {
        // ManageFlowsheets2Action opens the list for read on any of these (issue #4623); the page
        // itself offers enable/disable and upload only to a writer.
        assertEntryGuardedByEachObject(Files.readString(LEFT_NAV), "/admin/ManageFlowsheets",
                "canViewFlowsheets", "r", "_admin", "_admin.misc", "_admin.flowsheet");
    }

    @Test
    @DisplayName("Assign Role/Rights to Object entries should follow the action's per-object write gate")
    void shouldGuardRoleRightsEntries_withPerObjectWriteChecks() throws IOException {
        assertEntryGuardedByEachObject(Files.readString(LEFT_NAV), "/admin/ProviderPrivilege",
                "canManageRoleRights", "w", "_admin", "_admin.userAdmin");
        assertEntryGuardedByEachObject(Files.readString(ADMIN_HOME), "/admin/ProviderPrivilege",
                "canAssignRoleRights", "w", "_admin", "_admin.userAdmin");
    }

    @Test
    @DisplayName("System Management section should open for read on any of its objects, each checked alone")
    void shouldGateSystemManagementSection_withPerObjectReadChecks() throws IOException {
        assertEntryGuardedByEachObject(Files.readString(LEFT_NAV), "admin.admin.SystemManagement",
                "canSeeSystemManagement", "r", "_admin", "_admin.measurements", "_admin.document",
                "_admin.flowsheet", "_admin.misc", "_admin.userAdmin");
    }

    @Test
    @DisplayName("Assign Role/Rights to Object page should admit the same write grants as its action")
    void shouldAdmitRoleRightsPage_forWriteOnEitherObject() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/providerPrivilege.jsp"));
        String gate = jsp.substring(0, jsp.indexOf("if (!authed)"));

        // Deny by default; each object's write grant admits on its own, as in ProviderPrivilege2Action.
        assertThat(gate)
                .contains("boolean authed = false;")
                .contains("<security:oscarSec roleName=\"<%=roleName$%>\" objectName=\"_admin\" rights=\"w\">")
                .contains("<security:oscarSec roleName=\"<%=roleName$%>\" objectName=\"_admin.userAdmin\" rights=\"w\">")
                .doesNotContain("rights=\"*\"")
                .doesNotContainPattern("objectName=\"[^\"]*,");
    }

    @Test
    @DisplayName("Schedule Administration link should check each admin object on its own")
    void shouldGateScheduleAdministrationLink_withPerObjectReadChecks() throws IOException {
        String gate = Files.readString(Path.of("src/main/webapp/WEB-INF/jspf/administration-menu-gate.jspf"));
        String checks = gate.substring(gate.indexOf("<c:set var=\"canOpenAdministration\" value=\"${false}\" />"));
        assertThat(checks).doesNotContainPattern("objectName=\"[^\"]*,");

        // Everyone the Administration shell admits must be offered the link that opens it.
        String action = Files.readString(Path.of(
                "src/main/java/io/github/carlos_emr/carlos/administration/gate/ViewAdministrationIndex2Action.java"));
        String admitted = action.substring(action.indexOf("ADMIN_PRIVS = {"), action.indexOf("};", action.indexOf("ADMIN_PRIVS = {")));
        Matcher objects = Pattern.compile("\"(_admin[^\"]*)\"").matcher(admitted);
        int count = 0;
        while (objects.find()) {
            count++;
            assertThat(checks).contains("<security:oscarSec roleName=\"<%=roleName$%>\" objectName=\""
                    + objects.group(1) + "\" rights=\"r\"><c:set var=\"canOpenAdministration\" value=\"${true}\" />");
        }
        assertThat(count).as("objects admitted by ViewAdministrationIndex2Action").isPositive();

        for (String page : List.of("src/main/webapp/WEB-INF/jsp/provider/appointmentprovideradminday.jsp",
                "src/main/webapp/WEB-INF/jsp/provider/mainMenu.jsp")) {
            String jsp = Files.readString(Path.of(page));
            int link = jsp.indexOf("id=\"admin-panel\"");
            assertThat(link).as(page + " should offer the Administration link").isNotNegative();
            int include = jsp.lastIndexOf("<%@ include file=\"/WEB-INF/jspf/administration-menu-gate.jspf\" %>", link);
            int test = jsp.lastIndexOf("<c:if test=\"${canOpenAdministration}\">", link);
            assertThat(include).as(page + " should include the per-object gate").isNotNegative();
            assertThat(test).as(page + " should wrap the link in the gate's flag").isGreaterThan(include);
            assertThat(jsp.indexOf("</c:if>", link)).as(page + " should close the gate after the link").isPositive();
            assertThat(jsp).as(page + " should not keep the combined thirteen-object tag")
                    .doesNotContain("objectName=\"_admin,_admin.userAdmin,_admin.schedule");
        }
    }

    /**
     * Asserts that {@code entry} sits inside {@code <c:if test="${flag}">} and that the flag is set only
     * by one single-object {@code oscarSec} check per object. A comma-separated objectName list keeps
     * ONE privilege per role across its objects, so one object's row could mask another's grant.
     */
    private static void assertEntryGuardedByEachObject(String jsp, String entry, String flag, String rights,
                                                       String... objectNames) {
        int entryIndex = jsp.indexOf(entry);
        assertThat(entryIndex).as(entry + " should be present").isNotNegative();
        int reset = jsp.lastIndexOf("<c:set var=\"" + flag + "\" value=\"${false}\" />", entryIndex);
        int test = jsp.lastIndexOf("<c:if test=\"${" + flag + "}\">", entryIndex);
        assertThat(reset).as(entry + " should be preceded by a reset of " + flag).isNotNegative();
        assertThat(test).as(entry + " should be inside <c:if test=\"${" + flag + "}\">").isGreaterThan(reset);
        assertThat(jsp.indexOf("</c:if>", test)).as(entry + " should be inside the " + flag + " block")
                .isGreaterThan(entryIndex);

        String gate = jsp.substring(reset, test);
        for (String objectName : objectNames) {
            assertThat(gate).contains("<security:oscarSec roleName=\"<%=roleName$%>\" objectName=\""
                    + objectName + "\" rights=\"" + rights + "\">");
        }
        assertThat(gate.split("<security:oscarSec", -1)).as(flag + " checks").hasSize(objectNames.length + 1);
        assertThat(gate).doesNotContainPattern("objectName=\"[^\"]*,");
    }

    private static void assertLinkGuardedBy(String jsp, String link, String objectName, String rights) {
        int linkIndex = jsp.indexOf(link);
        assertThat(linkIndex).as(link + " should be present in the Administration navigation").isNotNegative();

        int guardStart = jsp.lastIndexOf("<security:oscarSec", linkIndex);
        int guardEnd = jsp.indexOf("</security:oscarSec>", guardStart);

        assertThat(guardStart).as(link + " should have a preceding security guard").isNotNegative();
        assertThat(guardEnd).as(link + " should be inside a security guard").isGreaterThan(linkIndex);
        assertThat(jsp.substring(guardStart, linkIndex))
                .contains("objectName=\"" + objectName + "\"")
                .contains("rights=\"" + rights + "\"");
    }
}
