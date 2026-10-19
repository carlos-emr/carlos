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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("noteBrowser JSP security regression tests")
@Tag("unit")
@Tag("caseManagement")
class NoteBrowserJspSecurityRegressionTest {

    private static final Path NOTE_BROWSER = Path.of(
            "src/main/webapp/WEB-INF/jsp/casemgmt/noteBrowser.jsp");

    @Test
    @DisplayName("should not mutate documents directly from request parameters")
    void shouldNotMutateDocumentsDirectly_fromRequestParameters() throws Exception {
        String jsp = Files.readString(NOTE_BROWSER);

        assertThat(jsp)
                .doesNotContain("EDocUtil.deleteDocument(")
                .doesNotContain("EDocUtil.undeleteDocument(")
                .doesNotContain("EDocUtil.refileDocument(");
    }

    @Test
    @DisplayName("should post document mutations to dedicated note browser actions")
    void shouldPostDocumentMutations_toDedicatedActions() throws Exception {
        String jsp = Files.readString(NOTE_BROWSER);

        assertThat(jsp).containsIgnoringCase("<form name=\"DisplayDoc\" method=\"post\"");
        assertMutationFunctionPostsTo(jsp, "DeleteDoc", "/casemgmt/NoteBrowserDocumentDelete");
        assertMutationFunctionPostsTo(jsp, "UnDeleteDoc", "/casemgmt/NoteBrowserDocumentUndelete");
        assertMutationFunctionPostsTo(jsp, "RefileDoc", "/casemgmt/NoteBrowserDocumentRefile");
    }

    /**
     * Issue #4368: casemgmt/ViewNoteBrowser is a GET/HEAD-only gate, and DisplayDoc's default
     * action points at it. Any control that submits the form (an {@code <input type="image">},
     * a submit input, or a {@code <button>} without {@code type="button"}) replaces the note
     * browser with a 405, so the form must carry none.
     */
    @Test
    @DisplayName("should carry no submit control inside the DisplayDoc form")
    void shouldCarryNoSubmitControl_insideDisplayDocForm() throws Exception {
        String form = displayDocForm(Files.readString(NOTE_BROWSER));

        assertThat(form).doesNotContainPattern("(?i)<input\\b[^>]*\\btype\\s*=\\s*['\"]?(image|submit)\\b");
        Matcher buttons = Pattern.compile("(?is)<button\\b[^>]*>").matcher(form);
        int count = 0;
        while (buttons.find()) {
            count++;
            assertThat(buttons.group()).as("every <button> in DisplayDoc must be type=\"button\"")
                    .containsPattern("(?i)\\btype\\s*=\\s*['\"]button['\"]");
        }
        assertThat(count).as("the Print control is a <button>").isPositive();
    }

    @Test
    @DisplayName("should open the print popup from a non-submitting Print control")
    void shouldOpenPrintPopup_fromNonSubmittingControl() throws Exception {
        String form = displayDocForm(Files.readString(NOTE_BROWSER));

        Matcher print = Pattern.compile("(?is)<button\\b[^>]*\\bid=\"imgPrintEncounter\"[^>]*>").matcher(form);
        assertThat(print.find()).as("the Print control is a <button id=\"imgPrintEncounter\">").isTrue();
        assertThat(print.group())
                .contains("type=\"button\"")
                .contains("onclick=\"PrintEncounter();\"")
                .doesNotContain("submit");
    }

    /** The markup between {@code <form name="DisplayDoc"} and its {@code </form>}. */
    private static String displayDocForm(String jsp) {
        int start = jsp.indexOf("<form name=\"DisplayDoc\"");
        assertThat(start).as("DisplayDoc form").isNotNegative();
        int end = jsp.indexOf("</form>", start);
        assertThat(end).as("DisplayDoc form end").isGreaterThan(start);
        return jsp.substring(start, end);
    }

    private void assertMutationFunctionPostsTo(String jsp, String functionName, String actionPath) {
        Pattern mutationPostPattern = Pattern.compile(
                "function\\s+" + Pattern.quote(functionName) + "\\s*\\(\\)\\s*\\{"
                        + "(?s:.*?)document\\.DisplayDoc\\.action\\s*=\\s*'[^']*"
                        + Pattern.quote(actionPath)
                        + "'(?s:.*?)document\\.DisplayDoc\\.submit\\s*\\(\\s*\\)",
                Pattern.MULTILINE);

        assertThat(mutationPostPattern.matcher(jsp).find()).isTrue();
    }
}
