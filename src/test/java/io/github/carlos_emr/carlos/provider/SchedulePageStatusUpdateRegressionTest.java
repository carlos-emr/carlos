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
    private static final Pattern AJAX_RESPONSE_BRANCH = Pattern.compile(
            "if\\s*\\(\\s*ajaxRequest\\s*\\)\\s*\\{", Pattern.DOTALL);
    private static final Pattern USES_FETCH_POST = Pattern.compile(
            "fetch\\s*\\([^)]*?\\{[^}]*?method:\\s*['\"]post['\"]", Pattern.DOTALL);
    private static final Pattern SENDS_AJAX_HEADER = Pattern.compile(
            "['\"]X-Requested-With['\"]\\s*:\\s*['\"]XMLHttpRequest['\"]", Pattern.DOTALL);
    private static final Pattern SENDS_SCROLL_POSITION = Pattern.compile(
            "body\\.append\\s*\\(\\s*['\"]x['\"]\\s*,\\s*X\\s*\\)\\s*;.*?"
                    + "body\\.append\\s*\\(\\s*['\"]y['\"]\\s*,\\s*Y\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern SENDS_CSRF_TOKEN = Pattern.compile(
            "body\\.append\\s*\\(\\s*csrfInput\\.name\\s*,\\s*csrfInput\\.value\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern SENDS_CSRF_HEADER = Pattern.compile(
            "['\"]CSRF-TOKEN['\"]\\s*:\\s*csrfInput\\.value",
            Pattern.DOTALL);
    private static final Pattern USES_BUSY_CURSOR = Pattern.compile(
            "previousCursor\\s*=\\s*document\\.body\\.style\\.cursor\\s*;.*?"
                    + "document\\.body\\.style\\.cursor\\s*=\\s*['\"]wait['\"]",
            Pattern.DOTALL);
    private static final Pattern RESTORES_CURSOR_ON_FAILURE = Pattern.compile(
            "\\.catch\\s*\\(.*?document\\.body\\.style\\.cursor\\s*=\\s*previousCursor",
            Pattern.DOTALL);
    private static final Pattern FETCH_UNAVAILABLE_FALLBACK = Pattern.compile(
            "typeof\\s+window\\.fetch\\s*!==\\s*['\"]function['\"].*?"
                    + "postViaForm\\s*\\(\\s*url\\s*\\).*?return\\s+false",
            Pattern.DOTALL);
    private static final Pattern IN_FLIGHT_GUARD_DECLARATION = Pattern.compile(
            "var\\s+apptStatusUpdateInFlight\\s*=\\s*false\\s*;");
    private static final Pattern IN_FLIGHT_GUARD = Pattern.compile(
            "if\\s*\\(\\s*apptStatusUpdateInFlight\\s*\\)\\s*\\{\\s*return\\s+false\\s*;",
            Pattern.DOTALL);
    private static final Pattern SETS_IN_FLIGHT_GUARD = Pattern.compile(
            "apptStatusUpdateInFlight\\s*=\\s*true\\s*;");
    private static final Pattern CLEARS_IN_FLIGHT_GUARD_ON_FAILURE = Pattern.compile(
            "\\.catch\\s*\\(.*?apptStatusUpdateInFlight\\s*=\\s*false\\s*;",
            Pattern.DOTALL);
    private static final Pattern HISTORY_REPLACING_NAVIGATION = Pattern.compile(
            "window\\.location\\.replace\\s*\\(", Pattern.DOTALL);
    private static final Pattern HAS_ERROR_HANDLER = Pattern.compile(
            "\\.catch\\s*\\(.*?apptStatusUpdateErrorMessage", Pattern.DOTALL);

    private static final Pattern DAY_VIEW_USES_HELPER = Pattern.compile(
            "onclick=\"return\\s+updateApptStatus\\('[^\"]*?displaymode=addstatus", Pattern.DOTALL);
    private static final Pattern DAY_VIEW_SENDS_CURRENT_STATUS = Pattern.compile(
            "currentstatus=<%=SafeEncode\\.forUriComponent\\(status\\)%>", Pattern.DOTALL);
    private static final Pattern DAY_VIEW_STATUS_NOT_FULL_PAGE_POST = Pattern.compile(
            "onclick=\"postViaForm\\('[^\"]*?displaymode=addstatus", Pattern.DOTALL);

    private static final Pattern ADD_STATUS_DETECTS_AJAX = Pattern.compile(
            "\"XMLHttpRequest\"\\.equals\\(request\\.getHeader\\(\"X-Requested-With\"\\)\\)", Pattern.DOTALL);
    private static final Pattern ADD_STATUS_RETURNS_URL_FOR_AJAX = Pattern.compile(
            "out\\.print\\s*\\(\\s*displaypage\\s*\\)", Pattern.DOTALL);
    private static final Pattern ADD_STATUS_EMPTY_AJAX_FAILURE = Pattern.compile(
            "out\\.clear\\s*\\(\\s*\\)\\s*;\\s*return\\s*;", Pattern.DOTALL);
    private static final Pattern ADD_STATUS_REJECTS_STALE_STATUS = Pattern.compile(
            "matchesCurrentStatus\\s*\\(\\s*appt\\.getStatus\\(\\)\\s*,\\s*submittedCurrentStatus\\s*\\)"
                    + ".*?SC_CONFLICT",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_VALIDATES_CALCULATED_TRANSITION = Pattern.compile(
            "apptStatusData\\.getNextStatus\\s*\\(\\s*\\).*?"
                    + "matchesCalculatedNextStatus\\s*\\(\\s*calculatedNextStatus\\s*,\\s*appointmentStatus\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_VALIDATES_PROVIDER = Pattern.compile(
            "!appointmentProviderNo\\.equals\\s*\\(\\s*providerNoParam\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_PUBLISHES_AUTHORITATIVE_EVENT = Pattern.compile(
            "appointmentStatusChanged\\s*\\(\\s*this\\s*,\\s*String\\.valueOf\\(appointmentNo\\)\\s*,"
                    + "\\s*appointmentProviderNo\\s*,\\s*appointmentStatus\\s*\\)",
            Pattern.DOTALL);

    @Test
    @DisplayName("should update appointment status in place via AJAX helper")
    void shouldUpdateStatusInPlace_viaAjaxHelper() throws Exception {
        String script = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);
        String dayView = Files.readString(DAY_VIEW, StandardCharsets.UTF_8);
        String addStatus = Files.readString(ADD_STATUS, StandardCharsets.UTF_8);
        String updateApptStatusBody = extractFunctionBody(script, UPDATE_APPT_STATUS_FUNCTION);
        List<String> ajaxResponseBranches = extractBlockBodies(addStatus, AJAX_RESPONSE_BRANCH);

        // The in-place helper must exist and use a non-navigating AJAX POST.
        assertThat(matches(updateApptStatusBody, USES_FETCH_POST)).isTrue();
        assertThat(matches(updateApptStatusBody, SENDS_AJAX_HEADER)).isTrue();
        assertThat(matches(updateApptStatusBody, SENDS_SCROLL_POSITION)).isTrue();
        assertThat(matches(updateApptStatusBody, SENDS_CSRF_TOKEN)).isTrue();
        assertThat(matches(updateApptStatusBody, SENDS_CSRF_HEADER)).isTrue();
        assertThat(matches(updateApptStatusBody, USES_BUSY_CURSOR)).isTrue();
        assertThat(matches(updateApptStatusBody, RESTORES_CURSOR_ON_FAILURE)).isTrue();
        assertThat(matches(updateApptStatusBody, FETCH_UNAVAILABLE_FALLBACK)).isTrue();

        // A shared guard must prevent duplicate status mutations and allow retries after failure.
        assertThat(matches(script, IN_FLIGHT_GUARD_DECLARATION)).isTrue();
        assertThat(matches(updateApptStatusBody, IN_FLIGHT_GUARD)).isTrue();
        assertThat(matches(updateApptStatusBody, SETS_IN_FLIGHT_GUARD)).isTrue();
        assertThat(matches(updateApptStatusBody, CLEARS_IN_FLIGHT_GUARD_ON_FAILURE)).isTrue();

        // Success must navigate with a history-replacing GET so Back does not replay.
        assertThat(matches(updateApptStatusBody, HISTORY_REPLACING_NAVIGATION)).isTrue();
        // Failures must surface a localized error rather than silently failing.
        assertThat(matches(updateApptStatusBody, HAS_ERROR_HANDLER)).isTrue();

        // The day-view status icon must call the in-place helper, not the full-page POST.
        assertThat(matches(dayView, DAY_VIEW_USES_HELPER)).isTrue();
        assertThat(matches(dayView, DAY_VIEW_SENDS_CURRENT_STATUS)).isTrue();
        assertThat(matches(dayView, DAY_VIEW_STATUS_NOT_FULL_PAGE_POST)).isFalse();

        // The mutation JSP must reject stale/forged transitions and publish authoritative values.
        assertThat(matches(addStatus, ADD_STATUS_REJECTS_STALE_STATUS)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_VALIDATES_CALCULATED_TRANSITION)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_VALIDATES_PROVIDER)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_PUBLISHES_AUTHORITATIVE_EVENT)).isTrue();

        // The mutation JSP must return the refreshed URL on success and an empty body on failure.
        assertThat(matches(addStatus, ADD_STATUS_DETECTS_AJAX)).isTrue();
        assertThat(ajaxResponseBranches).hasSize(2);
        assertThat(matches(ajaxResponseBranches.get(0), ADD_STATUS_RETURNS_URL_FOR_AJAX)).isTrue();
        assertThat(matches(ajaxResponseBranches.get(1), ADD_STATUS_EMPTY_AJAX_FAILURE)).isTrue();
        assertThat(matches(ajaxResponseBranches.get(1), ADD_STATUS_RETURNS_URL_FOR_AJAX)).isFalse();
    }

    private static boolean matches(String source, Pattern pattern) {
        return pattern.matcher(source).find();
    }

    private static String extractFunctionBody(String source, Pattern functionPattern) {
        Matcher matcher = functionPattern.matcher(source);
        assertThat(matcher.find())
                .as("expected to find updateApptStatus in schedulePage.js.jsp")
                .isTrue();

        return extractBracedBody(source, matcher.end() - 1);
    }

    private static List<String> extractBlockBodies(String source, Pattern blockPattern) {
        Matcher matcher = blockPattern.matcher(source);
        List<String> bodies = new ArrayList<>();
        while (matcher.find()) {
            bodies.add(extractBracedBody(source, matcher.end() - 1));
        }
        return bodies;
    }

    private static String extractBracedBody(String source, int openingBrace) {
        assertThat(source.charAt(openingBrace))
                .as("expected the matched block to end with an opening brace")
                .isEqualTo('{');

        int depth = 0;
        for (int i = openingBrace; i < source.length(); i++) {
            char current = source.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openingBrace + 1, i);
                }
            }
        }

        throw new IllegalStateException("Unable to find end of matched block");
    }
}
