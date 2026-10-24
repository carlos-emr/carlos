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
package io.github.carlos_emr.carlos.casemgmt.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the modern chart's notes panel ({@code ChartNotes.jsp}) to the patient its own request
 * names (issue #4556).
 *
 * <p>The panel is rendered into every chart window on open, filter and save. It used to build its
 * Browse Notes popup from the login-wide {@code EctSessionBean}, which IncomingEncounter rewrites
 * for whichever chart was entered last, so A's window could open B's note browser; it also hid
 * Bill whenever the last-entered chart came from the messenger. The live check is the
 * {@code echart-two-patient-isolation} refresh step.</p>
 *
 * @since 2026-10-15
 */
@DisplayName("eChart notes panel patient isolation")
@Tag("unit")
@Tag("casemgmt")
class ChartNotesPatientIsolationUnitTest {

    private static final Path CHART_NOTES_JSP = Path.of(System.getProperty("basedir", ""))
            .toAbsolutePath().resolve("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");

    /** Executable source: JSP and full-line Java comments explain intent, not behaviour. */
    private static String source() throws IOException {
        String jsp = Files.readString(CHART_NOTES_JSP, StandardCharsets.UTF_8);
        return JAVA_LINE_COMMENT.matcher(JSP_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
    }

    @Test
    @DisplayName("should build the Browse Notes popup from the request's own, encoded patient")
    void shouldBuildBrowseNotesLink_fromTheRequestPatient() throws IOException {
        String source = source();
        int target = source.indexOf("ViewNoteBrowser");
        assertThat(target).isNotNegative();
        int start = source.lastIndexOf("<button", target);
        int end = source.indexOf('>', target);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(target);
        // The target must sit inside that button's own start tag, not after an earlier tag closed.
        assertThat(source.substring(start, target)).doesNotContain(">");
        String browseButton = source.substring(start, end + 1);

        assertThat(browseButton)
                .contains("demographic_no=${carlos:forJavaScriptAttribute(carlos:forUriComponent(chartPatientNo))}")
                .doesNotContain("bean.");
        assertThat(source)
                .contains("chartPatientNo = EncounterPatientContext.requireDemographicNo(request);")
                .contains("pageContext.setAttribute(\"chartPatientNo\", chartPatientNo);");
    }

    @Test
    @DisplayName("should take entry metadata only from this chart's own patient")
    void shouldUsePatientMetadata_whenAnotherChartIsOpen() throws IOException {
        String source = source();

        assertThat(source)
                .contains("EncounterPatientContext.entryOrLoggedIn(request, loggedInInfo, chartPatientNo)")
                .doesNotContain("session.getAttribute(\"EctSessionBean\")")
                .contains("String provNo = EncounterPatientContext.entryProvider(request, loggedInInfo, chartPatientNo);")
                .doesNotContain("provNo = ownChartBean")
                .contains("if (bean.source == null) {")
                .doesNotContain("bean.demographicNo%>");
    }
}
