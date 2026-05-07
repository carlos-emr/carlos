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

    @Test
    @DisplayName("should use POST form helper when unbill function called")
    void shouldUsePostFormHelper_whenUnbillFunctionCalled() throws Exception {
        String script = Files.readString(SCHEDULE_PAGE_SCRIPT);
        String untilNextFunction = "(?:(?!\\nfunction\\s+).)*";
        String untilOnUpdatebill = "(?:(?!function\\s+onUpdatebill).)*";

        // The local postViaForm helper must submit generated forms with POST.
        String postViaFormUsesPost = "function\\s+postViaForm\\s*\\(\\s*url\\s*,\\s*targetWindow\\s*\\)\\s*\\{"
                + untilNextFunction + "form\\.method\\s*=\\s*['\"]post['\"]";
        assertThat(matches(script, postViaFormUsesPost))
                .isTrue();
        // Appointment -B unbill must call postViaForm with the popup target.
        String onUnbilledPostsToTarget = "function\\s+onUnbilled\\s*\\(\\s*url\\s*\\)\\s*\\{"
                + untilOnUpdatebill + "targetWindow\\s*=\\s*['\"]unbilled['\"]"
                + untilOnUpdatebill + "postViaForm\\s*\\(\\s*url\\s*,\\s*targetWindow\\s*\\)";
        assertThat(matches(script, onUnbilledPostsToTarget))
                .isTrue();
        // Appointment -B unbill must not directly open the mutator URL with GET.
        String onUnbilledOpensGetPopup = "function\\s+onUnbilled\\s*\\(\\s*url\\s*\\)\\s*\\{"
                + untilOnUpdatebill + "popupPage\\s*\\(\\s*700\\s*,\\s*720\\s*,\\s*url\\s*\\)";
        assertThat(matches(script, onUnbilledOpensGetPopup))
                .isFalse();
    }

    private static boolean matches(String script, String regex) {
        return Pattern.compile(regex, Pattern.DOTALL).matcher(script).find();
    }
}
