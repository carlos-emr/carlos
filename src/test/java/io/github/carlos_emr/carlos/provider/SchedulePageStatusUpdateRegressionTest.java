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
    private static final Path PROVIDER_CONTROL =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "providercontrol.jsp");

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
    private static final Pattern SKIPS_EMPTY_QUERY_SEGMENTS = Pattern.compile(
            "if\\s*\\(\\s*!pairs\\[i\\]\\s*\\)\\s*\\{\\s*continue\\s*;\\s*\\}",
            Pattern.DOTALL);

    private static final Pattern DAY_VIEW_USES_HELPER = Pattern.compile(
            "onclick=\"return\\s+updateApptStatus\\('[^\"]*?displaymode=addstatus", Pattern.DOTALL);
    private static final Pattern DAY_VIEW_SENDS_CURRENT_STATUS = Pattern.compile(
            "currentstatus=<%=SafeEncode\\.forUriComponent\\(status\\)%>", Pattern.DOTALL);
    private static final Pattern DAY_VIEW_STATUS_NOT_FULL_PAGE_POST = Pattern.compile(
            "onclick=\"postViaForm\\('[^\"]*?displaymode=addstatus", Pattern.DOTALL);
    private static final Pattern DAY_VIEW_STATUS_HAS_DOUBLE_DELIMITER = Pattern.compile(
            "day=<%=day%>&amp;<%=viewString%>");

    private static final Pattern ADD_STATUS_DETECTS_AJAX = Pattern.compile(
            "\"XMLHttpRequest\"\\.equals\\(request\\.getHeader\\(\"X-Requested-With\"\\)\\)", Pattern.DOTALL);
    private static final Pattern ADD_STATUS_RETURNS_URL_FOR_AJAX = Pattern.compile(
            "out\\.print\\s*\\(\\s*displaypage\\s*\\)", Pattern.DOTALL);
    private static final Pattern ADD_STATUS_DELEGATES_ATOMIC_TRANSITION = Pattern.compile(
            "appointmentStatusTransitionService\\.transition\\s*\\(\\s*appointmentNo\\s*,"
                    + "\\s*providerNoParam\\s*,\\s*submittedCurrentStatus\\s*,"
                    + "\\s*appointmentStatus\\s*,\\s*curUser_no\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_MAPS_STALE_STATUS_TO_CONFLICT = Pattern.compile(
            "Reason\\.STALE_STATUS\\s*\\).*?sendStatusError\\s*\\([^;]*SC_CONFLICT",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_MAPS_MISSING_APPOINTMENT_TO_NOT_FOUND = Pattern.compile(
            "Reason\\.APPOINTMENT_NOT_FOUND\\s*\\).*?sendStatusError\\s*\\([^;]*SC_NOT_FOUND",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_HAS_DIRECT_PERSISTENCE_OR_EVENT = Pattern.compile(
            "appointmentArchiveDao|appointmentDao\\.merge|appointmentStatusChanged");
    private static final Pattern ADD_STATUS_AJAX_ERROR_SETS_STATUS = Pattern.compile(
            "if\\s*\\(\\s*ajaxRequest\\s*\\).*?response\\.setStatus\\s*\\(\\s*status\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_EXPOSES_INCLUDED_ERROR_STATUS = Pattern.compile(
            "request\\.setAttribute\\s*\\(\\s*\"providerAddStatusHttpStatus\"\\s*,\\s*status\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_EXPOSES_INCLUDED_REDIRECT = Pattern.compile(
            "request\\.setAttribute\\s*\\(\\s*\"providerAddStatusRedirectTarget\"\\s*,"
                    + "\\s*displaypage\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ADD_STATUS_AJAX_USES_PLAIN_TEXT = Pattern.compile(
            "if\\s*\\(\\s*ajaxRequest\\s*\\).*?"
                    + "response\\.setContentType\\s*\\(\\s*\"text/plain;charset=UTF-8\"\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern PROVIDER_CONTROL_APPLIES_AJAX_STATUS = Pattern.compile(
            "ajaxStatusRequest.*?request\\.getRequestDispatcher\\(includeTarget\\)\\.include"
                    + ".*?request\\.getAttribute\\(\"providerAddStatusHttpStatus\"\\)"
                    + ".*?response\\.sendError",
            Pattern.DOTALL);
    private static final Pattern PROVIDER_CONTROL_APPLIES_PLAIN_TEXT = Pattern.compile(
            "ajaxStatusRequest.*?response\\.setContentType"
                    + "\\(\"text/plain;charset=UTF-8\"\\)",
            Pattern.DOTALL);
    private static final Pattern PROVIDER_CONTROL_APPLIES_INCLUDED_REDIRECT = Pattern.compile(
            "statusRequest.*?request\\.getRequestDispatcher\\(includeTarget\\)\\.include"
                    + ".*?request\\.getAttribute\\(\"providerAddStatusRedirectTarget\"\\)"
                    + ".*?response\\.sendRedirect",
            Pattern.DOTALL);

    @Test
    @DisplayName("should update appointment status in place via AJAX helper")
    void shouldUpdateStatusInPlace_viaAjaxHelper() throws Exception {
        String script = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);
        String dayView = Files.readString(DAY_VIEW, StandardCharsets.UTF_8);
        String addStatus = Files.readString(ADD_STATUS, StandardCharsets.UTF_8);
        String providerControl = Files.readString(PROVIDER_CONTROL, StandardCharsets.UTF_8);
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
        // Malformed or legacy URLs with duplicate delimiters must not emit an
        // empty form field, which Tomcat 11 rejects before the action runs.
        assertThat(matches(updateApptStatusBody, SKIPS_EMPTY_QUERY_SEGMENTS)).isTrue();
        assertThat(script.split("if \\(!pairs\\[i\\]\\)", -1)).hasSize(3);

        // The day-view status icon must call the in-place helper, not the full-page POST.
        assertThat(matches(dayView, DAY_VIEW_USES_HELPER)).isTrue();
        assertThat(matches(dayView, DAY_VIEW_SENDS_CURRENT_STATUS)).isTrue();
        assertThat(matches(dayView, DAY_VIEW_STATUS_NOT_FULL_PAGE_POST)).isFalse();
        assertThat(matches(dayView, DAY_VIEW_STATUS_HAS_DOUBLE_DELIMITER)).isFalse();

        // The JSP must delegate the mutation to the atomic service instead of
        // coordinating persistence or event publication itself.
        assertThat(matches(addStatus, ADD_STATUS_DELEGATES_ATOMIC_TRANSITION)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_MAPS_STALE_STATUS_TO_CONFLICT)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_MAPS_MISSING_APPOINTMENT_TO_NOT_FOUND)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_HAS_DIRECT_PERSISTENCE_OR_EVENT)).isFalse();

        // The mutation JSP must return the refreshed URL on success. Failures
        // are explicit HTTP errors mapped from the service exception above.
        assertThat(matches(addStatus, ADD_STATUS_DETECTS_AJAX)).isTrue();
        assertThat(ajaxResponseBranches)
                .anyMatch(branch -> matches(branch, ADD_STATUS_RETURNS_URL_FOR_AJAX));
        assertThat(matches(addStatus, ADD_STATUS_AJAX_ERROR_SETS_STATUS)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_EXPOSES_INCLUDED_ERROR_STATUS)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_EXPOSES_INCLUDED_REDIRECT)).isTrue();
        assertThat(matches(addStatus, ADD_STATUS_AJAX_USES_PLAIN_TEXT)).isTrue();
        assertThat(matches(providerControl, PROVIDER_CONTROL_APPLIES_AJAX_STATUS)).isTrue();
        assertThat(matches(providerControl, PROVIDER_CONTROL_APPLIES_PLAIN_TEXT)).isTrue();
        assertThat(matches(providerControl, PROVIDER_CONTROL_APPLIES_INCLUDED_REDIRECT)).isTrue();
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
