/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Expanding a note folded onto one line (minView, or minStaleNoteView for the single-line stale-note format).
 * {@code xpandView} took the id of the clicked element; a click on the folded note's bold date landed on a
 * {@code <b>} with no id, so it looked up a note named "nnull" and threw a TypeError instead of expanding.
 *
 * @since 2026-10-17
 */
@Tag("unit")
@Tag("regression")
@DisplayName("eChart folded note expands from any click inside it")
class EChartFoldedNoteExpandScriptRegressionTest {

    private static final Path CASE_MANAGEMENT_JS = Path.of("src/main/webapp/js/newCaseManagementView.js.jsp");

    @Test
    @DisplayName("should expand the note whose div holds the click, not the clicked element's id")
    void shouldExpandNoteHoldingClick_whenClickLandsOnChildWithoutId() throws IOException {
        String source = Files.readString(CASE_MANAGEMENT_JS, StandardCharsets.UTF_8);
        int start = source.indexOf("function xpandView(e) {");
        assertThat(start).as("newCaseManagementView.js.jsp defines xpandView").isPositive();
        String xpandView = source.substring(start, source.indexOf("\n    function ", start + 1));

        assertThat(xpandView).doesNotContain("xpandViewById(Event.element(e).id)")
                .doesNotContain("var id = Event.element(e).id;")
                .contains("while (note != null && !(note.id && /^n\\d+$/.test(note.id)))")
                .contains("note = note.parentNode;")
                .contains("xpandViewById(note.id);");
    }
}
