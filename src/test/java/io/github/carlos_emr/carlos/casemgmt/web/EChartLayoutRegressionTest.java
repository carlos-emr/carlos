package io.github.carlos_emr.carlos.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the eChart JSP composition.
 */
@DisplayName("eChart layout regression")
class EChartLayoutRegressionTest {

    private static final Path NEW_ENCOUNTER_LAYOUT =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/newEncounterLayout.jsp");
    private static final Path CHART_NOTES =
            Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/ChartNotes.jsp");

    @Test
    @DisplayName("should include clinical notes panel when rendering new encounter layout")
    void shouldIncludeClinicalNotesPanel_whenRenderingNewEncounterLayout() throws IOException {
        String jsp = Files.readString(NEW_ENCOUNTER_LAYOUT, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<jsp:include page=\"/WEB-INF/jsp/casemgmt/newCaseManagementView.jsp\"/>");
        assertThat(jsp).contains("<jsp:include page=\"/WEB-INF/jsp/casemgmt/ChartNotes.jsp\"/>");
    }

    @Test
    @DisplayName("should suppress duplicate shared scripts when notes are layout-included")
    void shouldSuppressDuplicateSharedScripts_whenNotesAreLayoutIncluded() throws IOException {
        String jsp = Files.readString(CHART_NOTES, StandardCharsets.UTF_8);

        assertThat(jsp).contains("eChartLayoutIncludesDependencies");
        assertThat(jsp).contains("if (!layoutIncludesDependencies)");
        assertThat(jsp).contains("newCaseManagementView.js.jsp");
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
