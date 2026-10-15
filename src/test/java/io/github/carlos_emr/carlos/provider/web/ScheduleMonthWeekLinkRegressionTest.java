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
package io.github.carlos_emr.carlos.provider.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the week-number cells of the schedule month view (issue #4540).
 *
 * <p>The month JSP is only rendered by a running server, so these checks read its source.
 * The browser-level behaviour is pinned by {@code scripts/schedule-views-playwright-checks.js}.
 *
 * @since 2026-10-14
 */
@DisplayName("Schedule month view week-number regressions")
@Tag("unit")
@Tag("provider")
class ScheduleMonthWeekLinkRegressionTest {

    private static final Path MONTH_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "appointmentprovideradminmonth.jsp");
    private static final Path PROVIDER_CONTROL_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "providercontrol.jsp");

    /** One week-number cell: the first column of each calendar row ({@code j == -1}). */
    private static final Pattern WEEK_NUMBER_CELL = Pattern.compile(
            "if \\(j == -1\\) \\{.*?<%=\\(i \\+ 1\\)%>", Pattern.DOTALL);

    @Test
    @DisplayName("should not link week numbers to a display mode the dispatcher does not know")
    void shouldNotLinkWeekNumbers_toRetiredWeekMode() throws IOException {
        String month = Files.readString(MONTH_JSP, StandardCharsets.UTF_8);
        String dispatcher = Files.readString(PROVIDER_CONTROL_JSP, StandardCharsets.UTF_8);

        // Premise of the bug: the dispatcher has no "week" entry, so any link to it is a 404.
        assertThat(dispatcher).doesNotContain("{\"week\",");
        assertThat(month)
                .doesNotContain("displaymode=week")
                .doesNotContain("searchapptweek");
    }

    @Test
    @DisplayName("should build every week-number link with ScheduleWeekLink")
    void shouldBuildWeekNumberLinks_withScheduleWeekLink() throws IOException {
        String month = Files.readString(MONTH_JSP, StandardCharsets.UTF_8);

        Matcher cells = WEEK_NUMBER_CELL.matcher(month);
        int found = 0;
        while (cells.find()) {
            found++;
            assertThat(cells.group())
                    .as("week-number cell #%d", found)
                    .contains("ScheduleWeekLink.href(")
                    .contains("weekHref != null");
        }
        // The page renders the calendar twice (previous and next month tables).
        assertThat(found).as("week-number cells in the month JSP").isEqualTo(2);
    }

    @Test
    @DisplayName("should resolve the week target from the viewer's saved schedule view and real providers")
    void shouldResolveWeekTarget_fromSavedViewAndExistingProviders() throws IOException {
        String month = Files.readString(MONTH_JSP, StandardCharsets.UTF_8);

        // The sheet a week number opens lists the viewer's SAVED view, not the month's providerview,
        // so the target must know the saved view, and a provider number must belong to a real provider.
        assertThat(month)
                .contains("ScheduleWeekLink.weekTarget(providerview, providerPreference.getMyGroupNo(),")
                .contains("providerDao.getProvider(")
                // The day sheet's selector hides what the viewer is restricted from; the week link must too.
                .contains("myGroupAccessRestrictionDao.findByProviderNo(curUser_no)")
                .contains("restrictedNo.equals(restriction.getMyGroupNo())");
    }
}
