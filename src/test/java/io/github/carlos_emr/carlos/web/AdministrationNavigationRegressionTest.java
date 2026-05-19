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
