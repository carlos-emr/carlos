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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.tickler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the opener-dependent tickler add routes into Oscar.js's force-window
 * list so the "Open Encounters in Tab" preference cannot reopen the
 * Save-and-Write blank-window regression.
 *
 * @since 2026-04-20
 */
@DisplayName("Tickler popup routing regressions")
@Tag("unit")
@Tag("tickler")
@Tag("javascript")
class TicklerPopupRoutingRegressionTest {

    private static final Path OSCAR_JS = Path.of("src/main/webapp/share/javascript/Oscar.js");
    // Oscar.js keeps forceWindowPaths as a flat array of string literals; this
    // regression test intentionally matches that simple structure.
    private static final Pattern FORCE_WINDOW_PATHS_PATTERN = Pattern.compile(
            "(?:var|let|const)\\s+forceWindowPaths\\s*=\\s*\\[(?<body>[\\s\\S]*?)]\\s*;");
    private static final Pattern VIEW_ADD_TICKLER_PATTERN = Pattern.compile("['\"`]ViewAddTickler['\"`]");
    private static final Pattern FORWARD_DEMOGRAPHIC_TICKLER_PATTERN =
            Pattern.compile("['\"`]ForwardDemographicTickler['\"`]");

    @Test
    @DisplayName("should keep opener-dependent tickler add routes in the force-window list")
    void shouldKeepTicklerAddRoutes_inForceWindowList() throws IOException {
        String oscarJs = Files.readString(OSCAR_JS, StandardCharsets.UTF_8);
        Matcher matcher = FORCE_WINDOW_PATHS_PATTERN.matcher(oscarJs);

        assertThat(matcher.find())
                .as("Oscar.js should declare the forceWindowPaths list")
                .isTrue();

        String forceWindowPathsBody = matcher.group("body");
        assertThat(VIEW_ADD_TICKLER_PATTERN.matcher(forceWindowPathsBody).find()).isTrue();
        assertThat(FORWARD_DEMOGRAPHIC_TICKLER_PATTERN.matcher(forceWindowPathsBody).find()).isTrue();
    }
}
