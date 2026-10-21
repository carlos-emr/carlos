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
package io.github.carlos_emr.carlos.schedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking from the waiting list keeps the selected patient across Flip View navigation (issue
 * #4994). Make Appointment opens Flip View with demographic_no and demographic_name; the month
 * links and the provider change dropped them, so the slot then opened Add Appointment with an
 * empty patient.
 *
 * @since 2026-10-21
 */
@DisplayName("Flip View patient carry JSP regressions")
@Tag("unit")
@Tag("schedule")
class ScheduleFlipViewPatientCarryJspRegressionTest {

    private static final Path FLIP_VIEW = Path.of("src/main/webapp/WEB-INF/jsp/schedule/scheduleflipview.jsp");

    private static String flipView() throws IOException {
        return Files.readString(FLIP_VIEW, StandardCharsets.UTF_8);
    }

    private static String functionBody(String page, String name) {
        Matcher function = Pattern.compile("function " + name + "\\([^)]*\\)\\s*\\{(.*?)\\n\\s*}", Pattern.DOTALL)
                .matcher(page);
        assertThat(function.find()).as("Flip View defines " + name + "()").isTrue();
        return function.group(1);
    }

    @Test
    void shouldCarryThePatient_whenTheProviderChanges() throws IOException {
        String body = functionBody(flipView(), "changePro");

        assertThat(body).contains("/schedule/FlipView?originalpage=")
                .contains("curDemoNo.isEmpty() ? \"\" : \"&demographic_no=\"")
                .contains("\"&demographic_name=\" + SafeEncode.forJavaScript(SafeEncode.forUriComponent(curDemoName))");
    }

    @Test
    void shouldBuildAValidUrl_whenNoStartDateIsGiven() throws IOException {
        String body = functionBody(flipView(), "changePro");

        // The old expression wrote a lone quote when startDate was absent, an unterminated string.
        assertThat(body).doesNotContain(":\"\\\"\"%>")
                .contains("request.getParameter(\"startDate\") == null ? \"\" : \"&startDate=\"");
    }

    @Test
    void shouldUseChangePro_whenAProviderIsSelected() throws IOException {
        String body = functionBody(flipView(), "selectprovider");

        assertThat(body).contains("changePro(s.options[s.selectedIndex].value)")
                .doesNotContain("/schedule/FlipView?");
    }

    @Test
    void shouldCarryThePatient_onEveryMonthLink() throws IOException {
        // Each link sits on one line; its href holds JSP tags with their own double quotes.
        List<String> monthLinks = new ArrayList<>();
        for (String line : flipView().split("\n")) {
            if (line.contains("href=\"${pageContext.request.contextPath}/schedule/FlipView?")) {
                monthLinks.add(line);
            }
        }

        // Last and next month, at the top and at the bottom of the page.
        assertThat(monthLinks).hasSize(4);
        assertThat(monthLinks).allSatisfy(link -> assertThat(link)
                .contains("<% if (!curDemoNo.isEmpty()) { %>&amp;demographic_no=<carlos:encode value='<%= curDemoNo %>' context=\"uriComponent\"/>")
                .contains("&amp;demographic_name=<carlos:encode value='<%= curDemoName %>' context=\"uriComponent\"/><% } %>"));
    }
}
