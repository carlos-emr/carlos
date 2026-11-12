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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saving a day in the Schedule Setting calendar must not resubmit the week setting (issue #4880).
 * The calendar is the POST result of Next, so reloading it re-posted the week setting and
 * regenerated every rule-made day in the range on each save. The save page now asks the calendar
 * to redisplay its month with the display-only request the month links use.
 *
 * @since 2026-10-17
 */
@DisplayName("Schedule Setting date save JSP regressions")
@Tag("unit")
@Tag("schedule")
class ScheduleDateSaveJspRegressionTest {

    private static final Path SAVE = Path.of("src/main/webapp/WEB-INF/jsp/schedule/scheduledatesave.jsp");
    private static final Path CALENDAR = Path.of("src/main/webapp/WEB-INF/jsp/schedule/schedulecreatedate.jsp");

    @Test
    void shouldNotReloadTheCalendar_afterADateIsSaved() throws IOException {
        String save = Files.readString(SAVE, StandardCharsets.UTF_8);

        assertThat(save).doesNotContain("location.reload(");
        assertThat(save).contains("opener.showCalendarMonth()");
    }

    @Test
    void shouldRedisplayTheMonth_withTheDisplayOnlyRequest() throws IOException {
        String calendar = Files.readString(CALENDAR, StandardCharsets.UTF_8);

        Matcher function = Pattern.compile("function showCalendarMonth\\(\\)\\s*\\{(.*?)\\n\\s*}", Pattern.DOTALL).matcher(calendar);
        assertThat(function.find()).as("the calendar defines showCalendarMonth()").isTrue();
        String body = function.group(1);
        assertThat(body).contains("/schedule/CreateDate?provider_no=")
                .contains("&provider_name=")
                .contains("&delta=0&bFirstDisp=0");
        // A bare reload or a request without bFirstDisp=0 would regenerate the range again.
        assertThat(body).doesNotContain("reload(").doesNotContain("bFirstDisp=1");
    }
}
