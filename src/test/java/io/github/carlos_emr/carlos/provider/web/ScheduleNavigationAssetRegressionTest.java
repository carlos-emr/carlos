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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards schedule navigation JSP defaults that are otherwise only exercised
 * through provider schedule rendering.
 *
 * @since 2026-05-20
 */
@DisplayName("Schedule navigation asset regressions")
@Tag("unit")
@Tag("provider")
class ScheduleNavigationAssetRegressionTest {

    private static final Path SCHEDULE_PAGE_SCRIPT =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "schedulePage.js.jsp");
    private static final Path PROVIDER_PREFERENCE_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "provider", "providerpreference.jsp");

    @Test
    @DisplayName("should default schedule navigation to focused mode")
    void shouldDefaultScheduleNavigation_toFocusedMode() throws IOException {
        String scheduleScript = Files.readString(SCHEDULE_PAGE_SCRIPT, StandardCharsets.UTF_8);
        String providerPreference = Files.readString(PROVIDER_PREFERENCE_JSP, StandardCharsets.UTF_8);
        String normalizedScheduleScript = normalizeWhitespace(scheduleScript);
        String normalizedProviderPreference = normalizeWhitespace(providerPreference);

        assertThat(normalizedScheduleScript)
                .contains("String scheduleNavigationMode = UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED;")
                .contains("scheduleNavigationMode = tabProp != null && \"yes\".equalsIgnoreCase(tabProp.getValue()) "
                        + "? UserProperty.SCHEDULE_NAVIGATION_MODE_TAB"
                        + " : UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED;");
        assertThat(normalizedProviderPreference)
                .contains("encOpenInTab ? UserProperty.SCHEDULE_NAVIGATION_MODE_TAB"
                        + " : UserProperty.SCHEDULE_NAVIGATION_MODE_FOCUSED");
    }

    private static String normalizeWhitespace(String content) {
        return content.replaceAll("\\s+", " ").trim();
    }
}
