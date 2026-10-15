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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.schedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the visibility gate of the Holiday Setting and Template Code Setting links on Schedule
 * Setting (issue #4528): the seeded admin role carries {@code _site_access_privacy}, which must
 * not hide install-wide settings; the links follow {@code _admin.schedule} write access.
 *
 * @since 2026-10-15
 */
@DisplayName("Schedule template setting JSP regressions")
@Tag("unit")
@Tag("schedule")
class ScheduleTemplateSettingJspRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/schedule/scheduletemplatesetting.jsp");

    @Test
    @DisplayName("global setting links should not be hidden by site or team access privacy")
    void shouldNotGateGlobalSettingLinks_bySiteOrTeamPrivacy() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("isSiteAccessPrivacy").doesNotContain("isTeamAccessPrivacy");
        assertThat(jsp).doesNotContain("objectName=\"_site_access_privacy\"");
    }

    @Test
    @DisplayName("global setting links should follow _admin.schedule write access")
    void shouldGateGlobalSettingLinks_byAdminSchedulePrivilege() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern("objectName=\"_admin\\.schedule\"\\s+rights=\"w\"");

        // Both links must sit inside the gated block, not merely coexist with the condition somewhere.
        String gate = "if (canEditGlobalScheduleSettings && !grantOnlyCurProviderScheduleData)";
        int gateStart = jsp.indexOf(gate);
        assertThat(gateStart).as("gate condition present").isGreaterThanOrEqualTo(0);
        int gateEnd = jsp.indexOf("<%}%>", gateStart);
        assertThat(gateEnd).as("gate block closed").isGreaterThan(gateStart);
        String gated = jsp.substring(gateStart, gateEnd);

        assertThat(gated).contains("/schedule/HolidaySetting").contains("/schedule/TemplateCodeSetting");
        String ungated = jsp.substring(0, gateStart) + jsp.substring(gateEnd);
        assertThat(ungated).doesNotContain("/schedule/HolidaySetting").doesNotContain("/schedule/TemplateCodeSetting");
    }
}
