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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("case management empty-state regressions")
@Tag("unit")
@Tag("casemgmt")
class CaseManagementEmptyStateRegressionTest {

    private static final Path SHOW_HISTORY_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/showHistory.jsp");
    private static final Path VIEW_NOTES_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/viewNotes.jsp");
    private static final Path ENCOUNTER_STYLES =
            Path.of("src/main/webapp/css/encounterStyles.css");
    private static final Path NEW_ENCOUNTER_LAYOUT =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterLayout.jsp");
    private static final Path RESOURCES_DIRECTORY = Path.of("src/main/resources");
    private static final String[] LOCALES = {"en", "es", "fr", "pl", "pt_BR"};

    @Test
    @DisplayName("issue history popup should explain when no history exists")
    void shouldRenderHistoryEmptyState_whenHistoryIsEmpty() throws IOException {
        String jsp = Files.readString(SHOW_HISTORY_JSP, StandardCharsets.UTF_8);

        assertConditionalMessage(jsp, "history", "casemgmt.showHistory.msgNoHistory");
        assertThat(jsp).contains("<c:forEach var=\"note\" items=\"${history}\"");
    }

    @Test
    @DisplayName("CPP panel should explain when no notes exist")
    void shouldRenderNotesEmptyState_whenNotesAreEmpty() throws IOException {
        String jsp = Files.readString(VIEW_NOTES_JSP, StandardCharsets.UTF_8);

        assertConditionalMessage(jsp, "Notes", "casemgmt.viewNotes.msgNoNotes");
        assertThat(jsp)
                .contains("<li class=\"cpp-empty-state\">")
                .contains("for (int i = 0; i < notes.size(); i++)");
    }

    @Test
    @DisplayName("CPP panel empty state should remain visible in both panel layouts")
    void shouldStyleNotesEmptyState_forCppPanels() throws IOException {
        String css = Files.readString(ENCOUNTER_STYLES, StandardCharsets.UTF_8);
        String layout = Files.readString(NEW_ENCOUNTER_LAYOUT, StandardCharsets.UTF_8);

        assertThat(css)
                .contains("#cppBoxes .topBox-notes")
                .contains("#rightNavBar .topBox-notes")
                .contains("div.topBox-notes ul li.cpp-empty-state");
        assertThat(layout).contains("/css/encounterStyles.css");
    }

    @Test
    @DisplayName("empty states should provide messages in every supported locale")
    void shouldProvideContextSpecificEnglishMessages() throws IOException {
        for (String locale : LOCALES) {
            String resources = Files.readString(
                    RESOURCES_DIRECTORY.resolve("oscarResources_" + locale + ".properties"),
                    StandardCharsets.UTF_8);

            assertThat(resources)
                    .as("localized empty-state messages for %s", locale)
                    .contains("casemgmt.showHistory.msgNoHistory=")
                    .contains("casemgmt.viewNotes.msgNoNotes=");
        }

        String english = Files.readString(
                RESOURCES_DIRECTORY.resolve("oscarResources_en.properties"),
                StandardCharsets.UTF_8);
        assertThat(english)
                .contains("casemgmt.showHistory.msgNoHistory=No history has been recorded for this issue.")
                .contains("casemgmt.viewNotes.msgNoNotes=No notes have been recorded for this section.");
    }

    private void assertConditionalMessage(String jsp, String collection, String messageKey) {
        String condition = "<c:if test=\"${empty " + collection + "}\">";
        int conditionStart = jsp.indexOf(condition);
        int conditionEnd = jsp.indexOf("</c:if>", conditionStart);
        int messagePosition = jsp.indexOf("<fmt:message key=\"" + messageKey + "\"/>");

        assertThat(conditionStart).as("empty-state condition for %s", collection).isGreaterThanOrEqualTo(0);
        assertThat(conditionEnd).as("closing empty-state condition for %s", collection).isGreaterThan(conditionStart);
        assertThat(messagePosition)
                .as("%s must render only inside its empty-state condition", messageKey)
                .isBetween(conditionStart, conditionEnd);
    }
}
