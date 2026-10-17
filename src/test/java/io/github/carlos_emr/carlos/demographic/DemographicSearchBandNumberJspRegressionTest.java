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
package io.github.carlos_emr.carlos.demographic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The patient search's Band Number mode (issue #4878): the search form still offers
 * {@code search_mode=search_band_number}, and the results page hands that mode to
 * {@code DemographicDao.searchForPatientList} as it is, which lists every record carrying the number
 * ({@code DemographicDaoIntegrationTest}). The page no longer turns the number into one demographic number.
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Patient search Band Number mode JSP regressions")
class DemographicSearchBandNumberJspRegressionTest {

    private static final Path SEARCH_FORM = Path.of("src/main/webapp/WEB-INF/jsp/demographic/zdemographicfulltitlesearch.jsp");
    private static final Path RESULTS = Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicsearchresults.jsp");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should keep offering the Band Number mode under its existing value")
    void shouldOfferBandNumberMode_onSearchForm() throws IOException {
        assertThat(read(SEARCH_FORM)).contains("<option value=\"search_band_number\"");
    }

    @Test
    @DisplayName("should pass the Band Number mode to the patient search unchanged")
    void shouldPassBandNumberMode_toPatientSearch() throws IOException {
        String page = read(RESULTS);

        assertThat(page).doesNotContain("getDemographicNumberWithBandNumber");
        assertThat(page).doesNotContain("searchMode = \"search_demographic_no\"");
        assertThat(page).containsSubsequence(
                "List<Demographic> doSearch(DemographicDao demographicDao, String searchMode,",
                "return demographicDao.searchForPatientList(new DemographicListSearch(searchMode, keyword,");
    }
}
