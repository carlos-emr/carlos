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

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
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
 * Pins the notes list ({@code ChartNotesAjax.jsp}) to the patient its own request names
 * (issue #4673).
 *
 * <p>The list is rendered into every chart window when its notes load. It took the "someone
 * else's unsigned note" provider and, for layouts that pass no {@code pendingEncounterMessage},
 * the pending message from the login-wide {@code EctSessionBean}, which IncomingEncounter rewrites
 * for whichever chart was entered last; it then cleared that shared bean's message and reason, so
 * loading A's notes could append B's message to A's note and drop it from B's chart. The same
 * own-chart rule as {@code ChartNotes.jsp} ({@link ChartNotesPatientIsolationUnitTest}) applies.</p>
 *
 * @since 2026-10-16
 */
@DisplayName("eChart notes list patient isolation")
@Tag("unit")
@Tag("casemgmt")
class ChartNotesAjaxPatientIsolationUnitTest extends CarlosUnitTestBase {

    private static final Path CHART_NOTES_AJAX_JSP = Path.of(System.getProperty("basedir", ""))
            .toAbsolutePath().resolve("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotesAjax.jsp");
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern JAVA_LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");

    /** Executable source: JSP and full-line Java comments explain intent, not behaviour. */
    private static String source() throws IOException {
        String jsp = Files.readString(CHART_NOTES_AJAX_JSP, StandardCharsets.UTF_8);
        return JAVA_LINE_COMMENT.matcher(JSP_COMMENT.matcher(jsp).replaceAll("")).replaceAll("");
    }

    @Test
    @DisplayName("should take entry metadata only from this chart's own patient")
    void shouldUsePatientMetadata_whenAnotherChartIsOpen() throws IOException {
        String source = source();

        assertThat(source)
                .contains("chartPatientNo = EncounterPatientContext.requireDemographicNo(request);")
                .contains("EncounterPatientContext.entryOrLoggedIn(request, loggedInInfo, chartPatientNo)")
                .doesNotContain("session.getAttribute(\"EctSessionBean\")")
                .contains("String provNo = EncounterPatientContext.entryProvider(request, loggedInInfo, chartPatientNo);")
                .doesNotContain("provNo = ownChartBean")
                .doesNotContain("String provNo = bean.providerNo;");
    }

    @Test
    @DisplayName("should leave the pending message with the requesting chart window")
    void shouldUseWindowMessage_whenNotesListLoads() throws IOException {
        assertThat(source()).contains("pendingEncounterMessage")
                .doesNotContain("bean.oscarMsg", "bean.reason =");
    }

    @Test
    @DisplayName("should list encounter templates even when the session bean carries none")
    void shouldListTemplates_whenSessionBeanHasNone() throws IOException {
        assertThat(source())
                .contains("List<String> templateNames = bean.templateNames != null ? bean.templateNames : List.of();")
                .doesNotContain("bean.templateNames.size()");
    }
}
