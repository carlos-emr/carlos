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
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Source pins for the eChart editor's encounter date (issue #4711).
 *
 * <p>{@code <fmt:formatDate>} formats in the browser's language and falls back to
 * {@code Date.toString()} when that language has no date format, which put the raw
 * {@code java.sql.Timestamp} into the field and made the save answer 500. The field and the
 * date shown after a note-switch save must come from {@link NoteObservationDate}, HTML-encoded,
 * and a refused save must show its reason and the unsaved draft.</p>
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("eChart encounter date JSP regression")
class NoteObservationDateJspRegressionTest {

    private static final Path NOTE_ISSUE_LIST = Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp");
    private static final Path CLOSE_WITH_ERROR = Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/closeWithError.jsp");
    private static final Pattern OBSERVATION_INPUT = Pattern.compile(
            "<input\\b[^>]*?\\bid=\"observationDate\"[^>]*?\\bvalue=\"(.*?)\">", Pattern.DOTALL);

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void shouldNeverFormatObservationDateWithJstl_forEditorFragment() throws IOException {
        String jsp = read(NOTE_ISSUE_LIST);
        assertThat(jsp).doesNotContainPattern("formatDate[^>]*observation_date");
        assertThat(jsp).contains("NoteObservationDate.format(");
    }

    @Test
    void shouldRenderEncodedCanonicalDate_forObservationDateInput() throws IOException {
        Matcher input = OBSERVATION_INPUT.matcher(read(NOTE_ISSUE_LIST));
        assertThat(input.find()).as("noteIssueList.jsp renders the observationDate input").isTrue();
        assertThat(input.group(1))
                .isEqualTo("<carlos:encode value=\"<%= observationDateText %>\" context=\"htmlAttribute\"/>");
        assertThat(input.group(0)).contains("name=\"observation_date\"");
    }

    @Test
    void shouldShowReasonAndDraft_whenObservationDateWasRejected() throws IOException {
        String jsp = read(CLOSE_WITH_ERROR);
        assertThat(jsp).contains("<c:when test=\"${observationDateRejected}\"><fmt:message key=\"encounter.observationDateUnreadable.msg\"/></c:when>");
        assertThat(jsp).contains("request.getAttribute(\"unsavedNoteText\")");
        assertThat(jsp).contains("if (unsavedNote == null) unsavedNote = request.getParameter(\"caseNote_note\")");
        assertThat(jsp).contains("<carlos:encode value=\"<%= unsavedNote %>\"/>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "es", "fr", "pl", "pt_BR"})
    void shouldTranslateRefusalMessage_forEveryShippedLanguage(String language) throws IOException {
        Properties bundle = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + language + ".properties"))) {
            bundle.load(in);
        }
        assertThat(bundle.getProperty("encounter.observationDateUnreadable.msg")).isNotBlank();
    }
}
