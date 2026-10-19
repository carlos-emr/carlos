/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The eChart's single-line stale-note format ({@code cme_note_format}). ChartNotesAjax.jsp used to fold each
 * shortened note by clicking its Maximize image twice; since fullViewById adds a Minimize image of its own
 * beside that one, the second click hit the Maximize image again and the note stayed expanded. The page now
 * folds each shortened note directly with {@code minStaleNoteView}. Both ways of folding a note,
 * {@code minView} and {@code minStaleNoteView}, draw the Maximize image through one helper,
 * {@code addExpandImage}.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("regression")
@DisplayName("eChart single-line stale-note format script")
class EChartStaleNoteSingleLineScriptRegressionTest {

    private static final Path CHART_NOTES_JSP = Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotesAjax.jsp");
    private static final Path CASE_MANAGEMENT_JS = Path.of("src/main/webapp/js/newCaseManagementView.js.jsp");

    private static String chartNotes;
    private static String caseManagementJs;

    @BeforeAll
    static void readSources() throws IOException {
        chartNotes = Files.readString(CHART_NOTES_JSP, StandardCharsets.UTF_8);
        caseManagementJs = Files.readString(CASE_MANAGEMENT_JS, StandardCharsets.UTF_8);
    }

    /** The ready-script block that runs only for the single-line format. */
    private static String singleLineBlock() {
        int start = chartNotes.indexOf("if ('<%=singleLineFormat%>' == 'true') {");
        assertThat(start).as("ChartNotesAjax.jsp has the single-line format script").isPositive();
        return chartNotes.substring(start, chartNotes.indexOf("});", start));
    }

    /** The body of a function in newCaseManagementView.js.jsp, up to the next function. */
    private static String function(String name) {
        int start = caseManagementJs.indexOf("function " + name + "(");
        assertThat(start).as("newCaseManagementView.js.jsp defines %s", name).isPositive();
        int next = caseManagementJs.indexOf("\n    function ", start + 1);
        return caseManagementJs.substring(start, next < 0 ? caseManagementJs.length() : next);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }

    @Test
    @DisplayName("should fold each shortened note directly instead of clicking its Maximize image twice")
    void shouldFoldShortenedNotesDirectly_forSingleLineFormat() {
        String block = singleLineBlock();

        assertThat(block).contains("jQuery(\"img[name='fullViewTrigger'][id^='quitImg']\")")
                .contains("minStaleNoteView(this.id.substring('quitImg'.length));")
                .doesNotContain("trigger('click')");
    }

    @Test
    @DisplayName("should fold a shortened note onto one line with its date and load its full text for expanding")
    void shouldFoldNoteWithDateLine_whenMinStaleNoteViewRuns() {
        String fold = function("minStaleNoteView");

        assertThat(fold)
                // No second listener: a folded note expands through xpandView only.
                .contains("$(txt).removeEventListener('click', fullView);")
                .contains("Element.remove(trigger);")
                // The full text, so expanding shows the whole note, as after fullView.
                .contains("method=viewNote&raw=false&noteId=")
                .contains("$(\"full\" + nId).value = \"true\";")
                // The one-line form minView draws: the date line, the start of the text, the Maximize image.
                .contains("<div id='d\" + nId + \"'")
                .contains("<div id='c\" + nId + \"'")
                .contains("addExpandImage(txt, nId);");
    }

    @Test
    @DisplayName("should draw a folded note's Maximize image in one place, for the manual and the single-line fold")
    void shouldDrawExpandImageOnce_forManualAndSingleLineFold() {
        assertThat(function("addExpandImage"))
                .contains("id='xpImg\" + nId + \"'")
                .contains("name='expandViewTrigger'")
                .contains("Element.observe(txt, 'click', xpandView);");
        assertThat(function("minView")).contains("addExpandImage(txt, nId);");
        assertThat(function("minStaleNoteView")).contains("addExpandImage(txt, nId);");

        // At most one image per note exists at a time (xpandViewById removes it), so a second copy of the markup
        // is only a copy: SonarCloud's web analyzer reads it as a duplicate id (Web:S7930), rates it a critical
        // bug and fails the quality gate.
        assertThat(occurrences(caseManagementJs, "id='xpImg\" + nId + \"'"))
                .as("places in newCaseManagementView.js.jsp that build the Maximize image's id")
                .isEqualTo(1);
    }
}
