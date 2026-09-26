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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins soft wrapping on every encounter note editor (#3955).
 *
 * <p>A {@code wrap="hard"} textarea with a fixed {@code cols} makes the browser insert a CRLF
 * at every visual wrap point when the value is submitted; Firefox 145+ also does so for
 * script-driven submissions. The server stores those breaks verbatim, so a saved note came
 * back chopped at the editor width and reflowed badly in print, the note browser and exports.
 * The server-side save and print paths do not rely on hard breaks: the chart PDFs lay the
 * note out through iText phrases, which reflow on their own.
 *
 * <p>This is a source-pattern guard over the shipping JSP sources: it finds every
 * {@code caseNote_note} textarea, including the ones {@code newCaseManagementView.js.jsp}
 * builds as JavaScript strings, and requires {@code wrap} to be soft. The browser behaviour
 * (the submitted form value equals what the clinician typed, and the saved note carries no
 * inserted breaks) is asserted by {@code scripts/echart-note-soft-wrap-playwright-checks.js}.
 *
 * <p>Ported from open-osp/Open-O PR #137 (commit 3c81c0c795, Chitrank Davé), extended to the
 * classic {@code CaseManagementEntry.jsp} editor.
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("casemgmt")
@DisplayName("Case note textarea wrap regression")
class CaseNoteTextareaWrapRegressionTest {

    private static final Path NEW_CASE_MANAGEMENT_VIEW_JS =
            Path.of("src/main/webapp/js/newCaseManagementView.js.jsp");
    private static final Path CASE_MANAGEMENT_ENTRY =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp");
    private static final Path CHART_NOTES_AJAX =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotesAjax.jsp");

    /**
     * A textarea element up to its closing tag, whether written as JSP markup or inside a
     * JavaScript string ({@code <\/textarea>}). The span runs to the closing tag rather than
     * the first {@code >} because the JavaScript-built editors carry a nested
     * {@code <fmt:message .../>} in their aria-label, ahead of the name attribute.
     */
    private static final Pattern TEXTAREA_ELEMENT =
            Pattern.compile("<textarea\\b.*?<\\\\?/textarea>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WRAP_ATTRIBUTE =
            Pattern.compile("\\bwrap\\s*=\\s*['\"]?([A-Za-z]+)", Pattern.CASE_INSENSITIVE);

    static Stream<Path> noteEditorSources() {
        return Stream.of(NEW_CASE_MANAGEMENT_VIEW_JS, CASE_MANAGEMENT_ENTRY, CHART_NOTES_AJAX);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noteEditorSources")
    @DisplayName("should soft wrap every case note textarea for each note editor source")
    void shouldSoftWrapEveryCaseNoteTextarea_forEachNoteEditorSource(Path source) throws IOException {
        List<String> tags = caseNoteTextareaTags(Files.readString(source, StandardCharsets.UTF_8));

        assertThat(tags).as("caseNote_note textareas in %s", source).isNotEmpty();
        for (String tag : tags) {
            Matcher wrap = WRAP_ATTRIBUTE.matcher(tag);
            // An absent wrap attribute is the HTML default (soft) and would also be correct; the
            // explicit value is required so a later edit cannot slip "hard" back in unnoticed.
            assertThat(wrap.find()).as("wrap attribute on %s", tag).isTrue();
            assertThat(wrap.group(1)).as("wrap value on %s", tag).isEqualToIgnoringCase("soft");
        }
    }

    @Test
    @DisplayName("should build both eChart note editors with soft wrap when creating or editing a note")
    void shouldBuildBothEChartNoteEditorsWithSoftWrap_whenCreatingOrEditingNote() throws IOException {
        String js = Files.readString(NEW_CASE_MANAGEMENT_VIEW_JS, StandardCharsets.UTF_8);

        // editNote() and newNote() are the two editors the eChart builds client-side.
        assertThat(caseNoteTextareaTags(js)).hasSize(2);
        assertThat(js).doesNotContainPattern("(?i)wrap\\s*=\\s*['\"]?hard");
    }

    @Test
    @DisplayName("should close the classic note textarea directly after the note when rendering the entry form")
    void shouldCloseClassicNoteTextareaDirectlyAfterNote_whenRenderingEntryForm() throws IOException {
        String jsp = Files.readString(CASE_MANAGEMENT_ENTRY, StandardCharsets.UTF_8);

        // Whitespace between the value and </textarea> is textarea content: it was appended to
        // the note on every save from this form.
        assertThat(jsp).contains("${carlos:forHtmlContent(caseNote.note)}</textarea>");
    }

    private static List<String> caseNoteTextareaTags(String source) {
        List<String> tags = new ArrayList<>();
        Matcher matcher = TEXTAREA_ELEMENT.matcher(source);
        while (matcher.find()) {
            String tag = matcher.group();
            if (tag.contains("caseNote_note")) {
                tags.add(tag);
            }
        }
        return tags;
    }
}
