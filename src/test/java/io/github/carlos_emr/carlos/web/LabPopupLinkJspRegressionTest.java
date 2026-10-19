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
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Source checks for lab popup links whose addresses carry URL-encoded values.
 *
 * <p>A {@code javascript:} link is percent-decoded before it runs, so the URL encoding of a value inside it is
 * undone. These links carry the popup's address as their {@code href}, encoded for the HTML attribute, and
 * open {@code this.href} from {@code onclick}.
 *
 * @since 2026-10-15
 */
@DisplayName("Lab popup link JSPs")
@Tag("unit")
@Tag("lab")
class LabPopupLinkJspRegressionTest {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "lab");

    @Test
    @DisplayName("should open the BC lab's patient search from a real URL")
    void shouldOpenBcLabPatientSearch_fromRealUrl() throws IOException {
        String jsp = Files.readString(JSP.resolve(Path.of("CA", "BC", "labDisplay.jsp")), StandardCharsets.UTF_8);

        // The patient name is URL-encoded into the address; decoded inside a javascript: link, it ended the string.
        assertThat(jsp)
                .doesNotContain("href=\"javascript:popupStart(360, 680, ")
                .contains("href=\"${pageContext.request.contextPath}/oscarMDS/SearchPatient?labType=BCP&amp;segmentID="
                        + "<carlos:encode value='${__enc_3}' context=\"htmlAttribute\"/>&amp;name=<carlos:encode value='"
                        + "<%= java.net.URLEncoder.encode(lab.pName, StandardCharsets.UTF_8) %>' context=\"htmlAttribute\"/>\" "
                        + "onclick=\"popupStart(360, 680, this.href, 'searchPatientWindow'); return false;\">");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CA/ALL/labDisplay.jsp, HL7", "CA/ALL/labDisplayAjax.jsp, HL7", "CA/ON/CMLDisplay.jsp, CML",
            "../oscarMDS/SegmentDisplay.jsp, MDS"})
    @DisplayName("should open each lab's patient search from a real URL")
    void shouldOpenLabPatientSearch_fromRealUrl(String page, String labType) throws IOException {
        String jsp = Files.readString(JSP.resolve(page).normalize(), StandardCharsets.UTF_8);

        // The lab's patient name is URL-encoded into the address; decoded inside a javascript: link, an
        // apostrophe ended the string.
        assertThat(jsp).doesNotContain("href=\"javascript:popupStart(360, 680, ");
        assertThat(jsp.lines().filter(l -> l.contains("/oscarMDS/SearchPatient?labType=" + labType + "&amp;segmentID=")
                && l.contains("&amp;name=") && l.contains("java.net.URLEncoder.encode(")
                && l.contains("\" onclick=\"popupStart(360, 680, this.href, 'searchPatientWindow'); return false;\">")))
                .hasSize(1);
    }

    @Test
    @DisplayName("should open each patient lab from a real URL")
    void shouldOpenPatientLab_fromRealUrl() throws IOException {
        String jsp = Files.readString(JSP.resolve("DemographicLab.jsp"), StandardCharsets.UTF_8);

        assertThat(jsp)
                .doesNotContain("href=\"javascript:reportWindow(")
                .contains("<a href=\"<%=SafeEncode.forHtmlAttribute(labUrl)%>\" onclick=\"reportWindow(this.href); return false;\">");
    }
}
