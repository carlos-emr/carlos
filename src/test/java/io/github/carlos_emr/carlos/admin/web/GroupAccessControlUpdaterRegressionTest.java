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
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;
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
 * Issue #4813: Administration ▸ Access Control. The page's save stores one restriction row per ticked
 * provider and recorded that provider, not the one who saved, as the row's last updater.
 *
 * <p>The save runs in the page itself ({@code groupnoacl.jsp}), so this test reads the page.</p>
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("admin")
@DisplayName("Access Control restriction rows name who saved them (#4813)")
class GroupAccessControlUpdaterRegressionTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/admin/groupnoacl.jsp");

    @Test
    @DisplayName("should record the signed-in provider as each saved restriction's last updater")
    void shouldRecordSignedInProvider_asLastUpdater() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8);
        assertThat(page).containsPattern("String\\s+providerNo\\s*=\\s*loggedInInfo\\.getLoggedInProviderNo\\(\\)\\s*;");
        assertThat(page).containsPattern("mgra\\.setProviderNo\\(\\s*providerNoTemp\\s*\\)");

        // Every updater call on the page, not only one line, so a later wrong call cannot slip past.
        List<String> updaters = new ArrayList<>();
        Matcher call = Pattern.compile("setLastUpdateUser\\(([^)]*)\\)").matcher(page);
        while (call.find()) {
            updaters.add(call.group(1).trim());
        }
        assertThat(updaters).isNotEmpty().allMatch("providerNo"::equals);
    }
}
