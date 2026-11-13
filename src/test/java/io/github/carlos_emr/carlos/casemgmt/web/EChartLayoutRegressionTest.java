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
    private static final Path ENCOUNTER_STYLES =
            Path.of("src/main/webapp/css/encounterStyles.css");
    private static final Path NOTES_PAGINATION_JS =
            Path.of("src/main/webapp/js/newCaseManagementView.js.jsp");

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
        // Each render goes through notesLoadFirstPage() (#3609): the pagination state lives in
        // the page script, so a filter/save reload must start the offset over or the batches
        // between the first page and where paging had reached are never shown again; and the
        // poll it arms must stop the previous one first, or a reload leaves a timer polling
        // for the life of the chart.
        assertThat(jsp).contains("notesLoadFirstPage();");
        assertThat(jsp).doesNotContain("setInterval(");
        String js = Files.readString(NOTES_PAGINATION_JS, StandardCharsets.UTF_8);
        assertThat(js)
                .containsPattern("function notesLoadFirstPage\\(\\)\\s*\\{\\s*notesOffset = 0;")
                .containsPattern("function startNotesScrollCheck\\(\\)\\s*\\{\\s*stopNotesScrollCheck\\(\\);");
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

    @Test
    @DisplayName("should keep both chart sidebars the same fixed width whatever they hold")
    void shouldKeepSidebarsSameFixedWidth_whateverTheyHold() throws IOException {
        String css = Files.readString(ENCOUNTER_STYLES, StandardCharsets.UTF_8);

        // OSCAR 19 lays the chart out 20% / 60% / 20%. With flex-grow and the default min-width:auto,
        // a sidebar with long rows (documents, consultations) grew wider than the other one; the
        // sidebars now neither grow nor shrink and long rows truncate inside them.
        for (String sidebar : new String[] {"div#leftNavBar {", "#rightNavBar {"}) {
            String rule = rule(css, sidebar);
            assertThat(rule).as(sidebar)
                    .contains("flex: 0 0 20%;")
                    .contains("min-width: 0;")
                    .doesNotContain("flex-grow")
                    .doesNotContain("flex-basis");
        }
        assertThat(rule(css, "#content {")).contains("flex: 1 1 0;").contains("min-width: 0;")
                .doesNotContain("flex-grow").doesNotContain("flex-basis");
    }

    /** The body of the first CSS rule that starts with exactly this selector line. */
    private static String rule(String css, String opening) {
        int start = css.indexOf("\n" + opening);
        assertThat(start).as("rule %s", opening).isNotNegative();
        return css.substring(start, css.indexOf('}', start));
    }
}
