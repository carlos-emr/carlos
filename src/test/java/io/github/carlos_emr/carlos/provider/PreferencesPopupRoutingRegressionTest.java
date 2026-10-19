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
 * Preferences must keep its opener in Tabs navigation mode: Save All reloads the schedule only through
 * {@code window.opener.refresh1()}, and a tab opened by {@code popupTab()} has none (issue #4891). Keeping
 * {@code ViewProviderPreference} in Oscar.js's force-window list opens it as a popup window in every mode.
 *
 * @since 2026-10-17
 */
@DisplayName("Preferences popup routing")
@Tag("unit")
@Tag("provider")
class PreferencesPopupRoutingRegressionTest {

    private static final Path OSCAR_JS = Path.of("src/main/webapp/share/javascript/Oscar.js");
    private static final Path SAVE_PAGE = Path.of("src/main/webapp/WEB-INF/jsp/provider/providerupdatepreference.jsp");
    // Oscar.js keeps forceWindowPaths as a flat array of string literals.
    private static final Pattern FORCE_WINDOW_PATHS = Pattern.compile(
            "(?:window\\.)?forceWindowPaths\\s*=\\s*(?:(?:window\\.)?forceWindowPaths\\s*\\|\\|\\s*)?\\[(?<body>[\\s\\S]*?)]");

    @Test
    void shouldKeepPreferences_inTheForceWindowList() throws IOException {
        Matcher list = FORCE_WINDOW_PATHS.matcher(Files.readString(OSCAR_JS, StandardCharsets.UTF_8));

        assertThat(list.find()).as("Oscar.js declares the forceWindowPaths list").isTrue();
        assertThat(Pattern.compile("['\"]ViewProviderPreference['\"]").matcher(list.group("body")).find())
                .as("forceWindowPaths includes the 'ViewProviderPreference' route")
                .isTrue();
    }

    @Test
    void shouldRefreshTheScheduleThroughItsOpener_whenPreferencesAreSaved() throws IOException {
        // The reason Preferences needs an opener: the save page reaches the schedule only through it.
        assertThat(Files.readString(SAVE_PAGE, StandardCharsets.UTF_8)).contains("self.opener.refresh1()");
    }
}
