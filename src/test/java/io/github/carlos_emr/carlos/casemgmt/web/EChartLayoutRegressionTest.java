package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the eChart JSP composition.
 *
 * <p>The clinical notes panel ({@code ChartNotes.jsp}) is rendered exactly once per chart,
 * into the {@code #notCPP} container, by the {@code viewFullChart(false)} AJAX call the
 * layout issues on window load (and again by filter/save reloads). The layout once also
 * included the panel server-side, outside {@code #notCPP}; every chart then opened with two
 * Template Search / note editor panels and a duplicate {@code #notesLoading} throbber that
 * could never be hidden. These tests pin the single-render composition.
 */
@Tag("unit")
@DisplayName("eChart layout regression")
class EChartLayoutRegressionTest {

    private static final Path NEW_ENCOUNTER_LAYOUT =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterLayout.jsp");
    private static final Path NEW_CASE_MANAGEMENT_VIEW =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/newCaseManagementView.jsp");
    private static final Path CHART_NOTES =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp");

    @Test
    @DisplayName("should render notes panel once via AJAX when rendering new encounter layout")
    void shouldRenderNotesPanelOnceViaAjax_whenRenderingNewEncounterLayout() throws IOException {
        String layout = Files.readString(NEW_ENCOUNTER_LAYOUT, StandardCharsets.UTF_8);
        String view = Files.readString(NEW_CASE_MANAGEMENT_VIEW, StandardCharsets.UTF_8);

        assertThat(layout).contains("<jsp:include page=\"/WEB-INF/jsp/casemgmt/newCaseManagementView.jsp\"/>");
        // The window-load AJAX render is the only render of the notes panel.
        assertThat(layout).contains("viewFullChart(false);");
        assertThat(layout).doesNotContain("<jsp:include page=\"/WEB-INF/jsp/casemgmt/ChartNotes.jsp\"");
        assertThat(layout).doesNotContain("eChartLayoutIncludesDependencies");
        // viewFullChart() and the filter/save reloads all replace this container.
        assertThat(view).contains("<div id=\"notCPP\">");
    }

    @Test
    @DisplayName("should load shared scripts unconditionally for the notes fragment")
    void shouldLoadSharedScriptsUnconditionally_forNotesFragment() throws IOException {
        String jsp = Files.readString(CHART_NOTES, StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("layoutIncludesDependencies");
        assertThat(jsp).contains("newCaseManagementView.js.jsp");
        // Each render arms its own scroll poll; the previous one must be stopped first.
        assertThat(jsp).contains("stopNotesScrollCheck();");
    }

    @Test
    @DisplayName("should render notes panel when standalone filter data is absent")
    void shouldRenderNotesPanel_whenStandaloneFilterDataIsAbsent() throws IOException {
        String jsp = Files.readString(CHART_NOTES, StandardCharsets.UTF_8);

        assertThat(jsp).contains("if (providers != null)");
        assertThat(jsp).contains("if (roles != null)");
        assertThat(jsp).contains("if (issues != null)");
        assertThat(jsp).contains("id=\"encMainDivWrapper\"");
        assertThat(jsp).contains("id=\"newNoteImg\"");
    }
}
