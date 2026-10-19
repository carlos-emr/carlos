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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seven single-setting preference pages shown by {@code setProviderStaleDate} loaded the old calendar scripts
 * from a broken URL, so three requests failed on every view (issue #4897). None of them has a date field, so the
 * scripts, the calendar stylesheet and the copied {@code setup()} that would attach a calendar are gone.
 *
 * @since 2026-10-17
 */
@DisplayName("Preference page calendar scripts JSP regressions")
@Tag("unit")
@Tag("provider")
class PreferencePageCalendarScriptsJspRegressionTest {

    private static final Path PROVIDER_JSPS = Path.of("src/main/webapp/WEB-INF/jsp/provider");

    @ParameterizedTest
    @ValueSource(strings = {"setGenProperty.jsp", "setShowPatientDOB.jsp", "setCppSingleLine.jsp", "setDocDefaultQueue.jsp",
            "setGenRxPageSizeProperty.jsp", "setGenRxProfileViewProperty.jsp", "setLabAckComment.jsp"})
    void shouldNotLoadTheCalendar_onAPreferencePageWithoutADateField(String page) throws IOException {
        String jsp = Files.readString(PROVIDER_JSPS.resolve(page), StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("share/calendar/").doesNotContain("Calendar.setup(");
        // The page itself is unchanged: it still posts its one setting.
        assertThat(jsp).contains("action=\"${pageContext.request.contextPath}/setProviderStaleDate\" method=\"post\"");
    }
}
