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
package io.github.carlos_emr.carlos.provider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the schedule day-view appointment status icon against regressing to a
 * full-page POST. The status update must use the in-place {@code updateApptStatus}
 * AJAX helper so the schedule does not flash a blank page and browser Back does
 * not replay status transitions.
 *
 * @since 2026-05-29
 */
@DisplayName("Schedule page status update regression")
@Tag("unit")
@Tag("provider")
class SchedulePageStatusUpdateRegressionTest {

    private static final Path SCHEDULE_PAGE_SCRIPT =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "schedulePage.js.jsp");
    private static final Path DAY_VIEW =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "appointmentprovideradminday.jsp");
    private static final Path ADD_STATUS =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "provideraddstatus.jsp");

    private static final Pattern UPDATE_APPT_STATUS_FUNCTION = Pattern.compile(
            "function\\s+updateApptStatus\\s*\\(\\s*url\\s*\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern USES_FETCH_POST = Pattern.compile(
            "fetch\\s*\\([^)]*?\\{[^}]*?method:\\s*['\"]post['\"]", Pattern.DOTALL);
    private static final Pattern SENDS_AJAX_HEADER = Pattern.compile(
            "['\"]X-Requested-With['\"]\\s*:\\s*['\"]XMLHttpRequest['\"]", Pattern.DOTALL);
    private static final Pattern HISTORY_REPLACING_NAVIGATION = Pattern.compile(
            "window\\.location\\.replace\\s*\\(", Pattern.DOTALL);
    private static final Pattern HAS_ERROR_HANDLER = Pattern.compile(
            "\\.catch\\s*\\(.*?apptStatusUpdateErrorMessage", Pattern.DOTALL);

    private static final Pattern DAY_VIEW_USES_HELPER = Pattern.compile(
            "onclick=\"updateApptStatus\\('[^\"]*?displaymode=addstatus", Pattern.DOTALL);
    private static final Pattern DAY_VIEW_STATUS_NOT_FULL_PAGE_POST = Pattern.compile(
            "onclick=\"postViaForm\\('[^\"]*?displaymode=addstatus", Pattern.DOTALL);

    private static final Pattern ADD_STATUS_DETECTS_AJAX = Pattern.compile(
            "\"XMLHttpRequest\"\\.equals\\(request\\.getHeader\\(\"X-Requested-With\"\\)\\)", Pattern.DOTALL);
    private static final Pattern ADD_STATUS_RETURNS_URL_FOR_AJAX = Pattern.compile(
            "if\\s*\\(\\s*ajaxRequest\\s*\\)\\s*\\{.*?out\\.print\\(displaypage\\)", Pattern.DOTALL);

    @Test
    @DisplayName("should update appointment status in place via AJAX helper")
    void shouldUpdateStatusInPlace_viaAjaxHelper() throws Exception {
        String script = Files.readString(SCHEDULE_PAGE_SCRIPT);
        String dayView = Files.readString(DAY_VIEW);
        String addStatus = Files.readString(ADD_STATUS);

        // The in-place helper must exist and use a non-navigating AJAX POST.
        assertThat(UPDATE_APPT_STATUS_FUNCTION.matcher(script).find()).isTrue();
        assertThat(USES_FETCH_POST.matcher(script).find()).isTrue();
        assertThat(SENDS_AJAX_HEADER.matcher(script).find()).isTrue();
        // Success must navigate with a history-replacing GET so Back does not replay.
        assertThat(HISTORY_REPLACING_NAVIGATION.matcher(script).find()).isTrue();
        // Failures must surface a localized error rather than silently failing.
        assertThat(HAS_ERROR_HANDLER.matcher(script).find()).isTrue();

        // The day-view status icon must call the in-place helper, not the full-page POST.
        assertThat(DAY_VIEW_USES_HELPER.matcher(dayView).find()).isTrue();
        assertThat(DAY_VIEW_STATUS_NOT_FULL_PAGE_POST.matcher(dayView).find()).isFalse();

        // The mutation JSP must answer AJAX requests with the refreshed-day URL.
        assertThat(ADD_STATUS_DETECTS_AJAX.matcher(addStatus).find()).isTrue();
        assertThat(ADD_STATUS_RETURNS_URL_FOR_AJAX.matcher(addStatus).find()).isTrue();
    }
}
