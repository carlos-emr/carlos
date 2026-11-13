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
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The team consultation list must say what happened when it is not a plain list (issue #5775).
 *
 * <p>The page used to call the loader and ignore its result. A load that failed, at the time
 * because one patient on the page was restricted, was drawn as an empty table: indistinguishable
 * from a team with no consultations, and with no Next button. The loader's own behaviour is covered
 * by {@code EctViewConsultationRequestsUtilUnitTest}; these tests pin the page's side of it, which
 * no compiler checks.</p>
 */
@DisplayName("Consultation list load outcome on the page (#5775)")
@Tag("unit")
@Tag("consultation")
class ViewConsultationRequestsLoadOutcomeJspRegressionTest {

    private static final Path LIST_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/ViewConsultationRequests.jsp");
    private static final Path PATIENT_LIST_JSP = Path.of(
            "src/main/webapp/WEB-INF/jsp/encounter/oscarConsultationRequest/DisplayDemographicConsultationRequests.jsp");
    private static final String KEY_PREFIX = "encounter.oscarConsultationRequest.ViewConsultationRequests.";
    private static final String LOAD_FAILED_KEY = KEY_PREFIX + "msgListLoadFailed";
    private static final String RESTRICTED_ROWS_KEY = KEY_PREFIX + "msgRestrictedRowsOmitted";
    private static final String RESULTS_TABLE = "<table class=\"table table-hover table-sm table-bordered consult-table\">";

    private static String page() throws IOException {
        return Files.readString(LIST_JSP, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should keep the loader's result instead of discarding it")
    void shouldKeepLoadResult_fromPageLoader() throws IOException {
        String page = page();

        assertThat(page).contains("boolean consultationListLoaded = theRequests.estConsultationPageByTeam(");
        // The call whose result the page discarded.
        assertThat(page).doesNotContain("theRequests.estConsultationVecByTeam(");
    }

    @Test
    @DisplayName("should report a failed load and withheld rows above the table")
    void shouldReportFailureAndWithheldRows_aboveTheTable() throws IOException {
        String page = page();
        int load = page.indexOf("theRequests.estConsultationPageByTeam(");
        int failed = page.indexOf("<% if (!consultationListLoaded) { %>");
        int failedMessage = page.indexOf("<fmt:message key=\"" + LOAD_FAILED_KEY + "\"/>");
        int restricted = page.indexOf("<% } else if (theRequests.hasRestrictedRows()) { %>");
        int restrictedMessage = page.indexOf("<fmt:message key=\"" + RESTRICTED_ROWS_KEY + "\"/>");
        int table = page.indexOf(RESULTS_TABLE);

        assertThat(table).as("results table").isPositive();
        // In this order: loaded before anything is said, and both notices before the table, where
        // an empty table cannot be read as "no consultations".
        assertThat(new int[] {load, failed, failedMessage, restricted, restrictedMessage, table})
                .doesNotContain(-1)
                .isSorted();
    }

    @Test
    @DisplayName("should offer the next page from the lookahead row, not from the rows it may show")
    void shouldOfferNextPage_fromLookaheadRow() throws IOException {
        String page = page();

        assertThat(page).contains("if (theRequests.hasMoreRows()) {");
        // Rows withheld for restricted patients made this false on a page that had a successor.
        assertThat(page).doesNotContain("theRequests.ids.size() > limit");
    }

    /** The patient's own list is a DataTable: empty, it reads "No data available in table". */
    @Test
    @DisplayName("should report a failed load above the patient's own consultation list too")
    void shouldReportFailure_aboveThePatientList() throws IOException {
        String page = Files.readString(PATIENT_LIST_JSP, StandardCharsets.UTF_8);
        int load = page.indexOf("boolean consultationListLoaded = theRequests.estConsultationVecByDemographic(");
        int failed = page.indexOf("<% if (!consultationListLoaded) { %>");
        int failedMessage = page.indexOf("<fmt:message key=\"" + LOAD_FAILED_KEY + "\"/>");
        int table = page.indexOf("<table id=\"consultTable\"");

        assertThat(new int[] {load, failed, failedMessage, table}).doesNotContain(-1).isSorted();
    }

    @ParameterizedTest(name = "oscarResources_{0}.properties")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should have both notices in every language")
    void shouldTranslateBothNotices_inEveryBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (Reader reader = Files.newBufferedReader(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.ISO_8859_1)) {
            bundle.load(reader);
        }

        assertThat(bundle.getProperty(LOAD_FAILED_KEY)).as(LOAD_FAILED_KEY).isNotBlank();
        assertThat(bundle.getProperty(RESTRICTED_ROWS_KEY)).as(RESTRICTED_ROWS_KEY).isNotBlank();
    }
}
