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
package io.github.carlos_emr.carlos.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Source-level checks of two prevention pages that used to lose a record without saying so: the
 * clinic report skipped a row whose patient record was not found, and the record popup failed on
 * an id that no record has. The counting behind the report's notice is tested in
 * {@code PreventionReport2ActionUnitTest}; these checks pin what the pages do with it.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("prevention pages that show a missing record instead of losing it")
class PreventionUnreadablePagesJspUnitTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/prevention");

    @Test
    @DisplayName("report page should list a row whose patient record is not found, marked, before moving on")
    void shouldListTheRowAsCouldNotBeRead_whenItsPatientRecordIsNotFound() throws IOException {
        String jsp = Files.readString(JSP.resolve("PreventionReporting.jsp"), StandardCharsets.UTF_8);

        // The record comes from the action, which counted the missing ones for the notice.
        assertThat(jsp).contains("reportDemographics.get(dis.demographicNo)");
        int missing = jsp.indexOf("if (demo == null) {");
        int skip = jsp.indexOf("continue;", missing);
        assertThat(missing).isPositive();
        String row = jsp.substring(missing, skip);
        assertThat(row).contains("<tr class=\"prevention-report-unreadable\">",
                "<carlos:encode value='<%= dis.demographicNo.toString() %>' context=\"html\"/>",
                "<fmt:message key=\"prevention.reporting.patientUnreadable\"/>",
                "<fmt:message key=\"prevention.reporting.unreadable\"/>");
        // Nothing of the patient is read once the record is known to be missing.
        assertThat(row).doesNotContain("getNameAgeSexHashtable", "demo.get");
        assertThat(jsp).doesNotContain("demographic not found for demographicNo");
    }

    @Test
    @DisplayName("record popup should show a message-only page when the record is not found or cannot be read")
    void shouldShowAMessageOnlyPage_whenTheRecordIsNotFoundOrCannotBeRead() throws IOException {
        String jsp = Files.readString(JSP.resolve("AddPreventionData.jsp"), StandardCharsets.UTF_8);

        int read = jsp.indexOf("existingPrevention = PreventionData.getPreventionById(id);");
        int notFound = jsp.indexOf("recordUnavailableKey = \"prevention.unreadable.recordNotFound\";");
        int unreadable = jsp.indexOf("recordUnavailableKey = \"prevention.unreadable.record\";");
        int messagePage = jsp.indexOf("<fmt:message key=\"<%= recordUnavailableKey %>\"/>");
        int stop = jsp.indexOf("return;", messagePage);
        int firstUse = jsp.indexOf("existingPrevention.get(");
        assertThat(read).isPositive();
        assertThat(notFound).isGreaterThan(read);
        assertThat(unreadable).isGreaterThan(notFound);
        assertThat(messagePage).isGreaterThan(unreadable);
        // The page ends there: the entry form, and every use of the record, comes after.
        assertThat(stop).isGreaterThan(messagePage).isLessThan(firstUse);
    }
}
