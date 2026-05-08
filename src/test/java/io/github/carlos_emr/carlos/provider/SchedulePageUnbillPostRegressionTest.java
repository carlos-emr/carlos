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

/** Guards the schedule -B unbill link against regressing to a GET request. */
@DisplayName("Schedule page unbill POST regression")
@Tag("unit")
@Tag("provider")
class SchedulePageUnbillPostRegressionTest {

    private static final Path SCHEDULE_PAGE_SCRIPT =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "schedulePage.js.jsp");
    private static final String UNTIL_NEXT_FUNCTION = "(?:(?!\\nfunction\\s+).)*";
    private static final Pattern POST_VIA_FORM_USES_POST = Pattern.compile(
            "function\\s+postViaForm\\s*\\(\\s*url\\s*,\\s*targetWindow\\s*\\)\\s*\\{"
                    + UNTIL_NEXT_FUNCTION + "form\\.method\\s*=\\s*['\"]post['\"]",
            Pattern.DOTALL);
    private static final Pattern ON_UNBILLED_FUNCTION = Pattern.compile(
            "function\\s+onUnbilled\\s*\\(\\s*url\\s*\\)\\s*\\{",
            Pattern.DOTALL);
    private static final Pattern ON_UNBILLED_CONFIRM_USES_SAFE_MESSAGE = Pattern.compile(
            "confirm\\s*\\(\\s*['\"]\\$\\{carlos:forJavaScript\\(onUnbilledConfirmMessage\\)}['\"]\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ON_UNBILLED_POSTS_TO_TARGET = Pattern.compile(
            "targetWindow\\s*=\\s*['\"]unbilled['\"].*?postViaForm\\s*\\(\\s*url\\s*,\\s*targetWindow\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ON_UNBILLED_PRESERVES_TAB_BEHAVIOR = Pattern.compile(
            "var\\s+popup\\s*=\\s*null\\s*;.*?"
                    + "if\\s*\\(\\s*openEncounterInTab\\s*&&\\s*!isForceWindowUrl\\(url\\)\\s*\\)\\s*\\{\\s*"
                    + "targetWindow\\s*=\\s*['\"]_blank['\"]\\s*;\\s*}\\s*else\\s*\\{\\s*"
                    + "popup\\s*=\\s*window\\.open\\s*\\(\\s*['\"]['\"]\\s*,\\s*targetWindow\\s*,\\s*windowProps\\s*\\)",
            Pattern.DOTALL);
    private static final Pattern ON_UNBILLED_OPENS_GET_POPUP = Pattern.compile(
            "popupPage\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern ON_UNBILLED_CONFIRM_MESSAGE_DECLARATION = Pattern.compile(
            "<fmt:message\\s+key=['\"]provider\\.appointmentProviderAdminDay\\.onUnbilled['\"]"
                    + "\\s+var=['\"]onUnbilledConfirmMessage['\"]\\s*/>",
            Pattern.DOTALL);

    @Test
    @DisplayName("should use POST form helper when unbill function called")
    void shouldUsePostFormHelper_whenUnbillFunctionIsCalled() throws Exception {
        String script = Files.readString(SCHEDULE_PAGE_SCRIPT);
        String onUnbilledBody = extractFunctionBody(script, ON_UNBILLED_FUNCTION);

        // The local postViaForm helper must submit generated forms with POST.
        assertThat(matches(script, POST_VIA_FORM_USES_POST))
                .isTrue();
        // Localized confirmation text must be declared and JavaScript-escaped before confirm().
        assertThat(matches(script, ON_UNBILLED_CONFIRM_MESSAGE_DECLARATION))
                .isTrue();
        assertThat(matches(onUnbilledBody, ON_UNBILLED_CONFIRM_USES_SAFE_MESSAGE))
                .isTrue();
        // Appointment -B unbill must call postViaForm with the popup target.
        assertThat(matches(onUnbilledBody, ON_UNBILLED_POSTS_TO_TARGET))
                .isTrue();
        // Appointment -B unbill must preserve tab preference and only open a named popup otherwise.
        assertThat(matches(onUnbilledBody, ON_UNBILLED_PRESERVES_TAB_BEHAVIOR))
                .isTrue();
        // Appointment -B unbill must not directly open the mutator URL with GET.
        assertThat(matches(onUnbilledBody, ON_UNBILLED_OPENS_GET_POPUP))
                .isFalse();
    }

    private static boolean matches(String script, Pattern pattern) {
        return pattern.matcher(script).find();
    }

    private static String extractFunctionBody(String script, Pattern functionPattern) {
        java.util.regex.Matcher matcher = functionPattern.matcher(script);
        assertThat(matcher.find())
                .as("expected to find the target function in schedulePage.js.jsp")
                .isTrue();

        int bodyStart = script.indexOf('{', matcher.start());
        assertThat(bodyStart)
                .as("expected the target function to have an opening brace")
                .isGreaterThanOrEqualTo(0);

        int depth = 0;
        for (int i = bodyStart; i < script.length(); i++) {
            char current = script.charAt(i);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return script.substring(bodyStart + 1, i);
                }
            }
        }

        throw new IllegalStateException("Unable to find end of target function body");
    }
}
