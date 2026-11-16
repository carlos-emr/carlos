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
package io.github.carlos_emr.carlos.lab;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A final word result filed as text (#6122) is labelled in the three views that read filed lab results,
 * and a lab graph says how many results it could not plot.
 *
 * @since 2026-11-11
 */
@Tag("unit")
@Tag("lab")
@DisplayName("Text lab results are labelled in the lab views")
class LabTextResultLabelJspRegressionTest {

    private static final String LABEL = "data-carlos-lab-text-result=\"true\"";

    @ParameterizedTest(name = "{0} labels a text result")
    @ValueSource(strings = {"WEB-INF/jsp/lab/CumulativeLabValues3.jsp", "WEB-INF/jsp/lab/DisplayLabValue.jsp",
            "WEB-INF/jsp/lab/CA/ON/labValues.jsp"})
    void shouldLabelTextResults_inEachLabView(String page) throws IOException {
        String jsp = read("src/main/webapp/" + page);

        assertThat(jsp).contains(LABEL, "<fmt:message key=\"lab.measurement.textResult\"/>",
                "CommonLabTestValues.isTextResult(");
    }

    /**
     * The value history and Row Display print each result as its report holds it. A final word result now reaches them
     * (#6122), and its lab line breaks arrive as {@code <br />} markers, which must show as line breaks, not as text.
     */
    @ParameterizedTest(name = "{0} shows a result's line breaks")
    @ValueSource(strings = {"WEB-INF/jsp/lab/DisplayLabValue.jsp", "WEB-INF/jsp/lab/CA/ON/labValues.jsp"})
    void shouldShowLineBreakMarkersAsBreaks_inEachHistoryView(String page) throws IOException {
        String jsp = read("src/main/webapp/" + page);

        assertThat(jsp).containsPattern("<carlos:encode value='<%= String\\.valueOf\\((h|hMap)\\.get\\(\"result\"\\)\\) %>'"
                + " context=\"htmlWithBreakMarkers\"/>");
        assertThat(jsp).doesNotContainPattern("get\\(\"result\"\\)\\) %>' context=\"html\"/>");
    }

    @Test
    void shouldLabelEveryGridCell_includingTheLatestValue() throws IOException {
        String grid = read("src/main/webapp/WEB-INF/jsp/lab/CumulativeLabValues3.jsp");

        assertThat(grid).contains("row['latestText']", "cell['text'] eq 'true'");
        assertThat(grid.split(LABEL, -1)).as("latest value and dated cells").hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepTheNewestObservation_whenOneReportRepeatsATest(boolean newestText, @TempDir Path scratch)
            throws Exception {
        String grid = read("src/main/webapp/WEB-INF/jsp/lab/CumulativeLabValues3.jsp");
        int selectionStart = grid.indexOf("for (int j = 0; j < labList.size(); j++) {");
        int selectionEnd = grid.indexOf("if (!labList.isEmpty())", selectionStart);
        int rowsStart = grid.indexOf("List<Map<String, Object>> tableRows =");
        int rowsEnd = grid.indexOf("pageContext.setAttribute(", rowsStart);
        assertThat(selectionStart).isPositive();
        assertThat(selectionEnd).isGreaterThan(selectionStart);
        assertThat(rowsStart).isPositive();
        assertThat(rowsEnd).isGreaterThan(rowsStart);
        // Execute the actual selection and cell-building scriptlets, using the DAO's newest-first order.
        String source = """
                import java.util.*;
                import io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues;
                import io.github.carlos_emr.carlos.util.StringUtils;
                public class LabGridFragment {
                    public static Map<String, Object> render(ArrayList labList) {
                        LinkedHashMap<String, Hashtable> idMap = new LinkedHashMap<>();
                        List<String> idList = new ArrayList<>();
                        List<Hashtable<String, String>> dateList = new ArrayList<>();
                """ + grid.substring(selectionStart, selectionEnd) + """
                        LinkedHashMap<String, String> nameMap = new LinkedHashMap<>();
                        nameMap.put("1234", "Synthetic test");
                        Map<String, LinkedHashMap<String, Hashtable>> measIdMap = new HashMap<>();
                        measIdMap.put("1234", idMap);
                """ + grid.substring(rowsStart, rowsEnd) + "return tableRows.getFirst(); }}";
        Path wrapper = scratch.resolve("LabGridFragment.java");
        Files.writeString(wrapper, source);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        assertThat(ToolProvider.getSystemJavaCompiler()).isNotNull();
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8",
                "-classpath", classpath, "-d", scratch.toString(), wrapper.toString())).isZero();
        Hashtable<String, String> newest = new Hashtable<>(Map.of("lab_no", "42", "date", "2026-11-12 12:00:00",
                "result", newestText ? "Pending" : "7.2", "abn", "N", "valueType", newestText ? "text" : ""));
        Hashtable<String, String> older = new Hashtable<>(Map.of("lab_no", "42", "date", "2026-11-11 12:00:00",
                "result", newestText ? "7.2" : "Pending", "abn", "A", "valueType", newestText ? "" : "text"));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{scratch.toUri().toURL()},
                getClass().getClassLoader())) {
            Map<?, ?> row = (Map<?, ?>) loader.loadClass("LabGridFragment").getMethod("render", ArrayList.class)
                    .invoke(null, new ArrayList<>(List.of(newest, older)));
            assertThat(row.get("latestVal")).isEqualTo(newest.get("result"));
            assertThat(row.get("latestDate")).isEqualTo("2026-11-12");
            assertThat(row.get("latestText")).isEqualTo(newestText);
            List<?> cells = (List<?>) row.get("cells");
            assertThat(cells).hasSize(1);
            Map<?, ?> cell = (Map<?, ?>) cells.getFirst();
            assertThat(cell.get("val")).isEqualTo(newest.get("result"));
            assertThat(cell.get("text")).isEqualTo(String.valueOf(newestText));
        }
    }

    @Test
    void shouldDrawTheGraphNote_underTheTitle() throws IOException {
        String draw = read("src/main/webapp/library/chartjs/carlos-chart-draw.js");

        assertThat(draw).contains("subtitle: noteOptions()", "display: !!spec.note");
    }

    @ParameterizedTest(name = "oscarResources_{0} has the label and the graph note")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    void shouldTranslateTheLabelAndTheNote_inEveryBundle(String language) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(projectRoot()
                .resolve("src/main/resources/oscarResources_" + language + ".properties"));
             Reader reader = new InputStreamReader(in, StandardCharsets.ISO_8859_1)) {
            bundle.load(reader);
        }

        assertThat(bundle.getProperty("lab.measurement.textResult")).isNotBlank();
        assertThat(bundle.getProperty("lab.graph.resultsNotShown")).contains("{0}").doesNotContain("'");
        // Pending is now filed as text, so the notices no longer name it as missing.
        assertThat(bundle.getProperty("lab.measurementGrid.scope")).doesNotContain("Pending");
        assertThat(bundle.getProperty("lab.measurementHistory.unavailable")).doesNotContain("Pending");
        if ("en".equals(language)) {
            // Reports received before the update were filed under the old rule: their word results are still absent.
            assertThat(bundle.getProperty("lab.measurementGrid.scope")).contains("before this update");
            assertThat(bundle.getProperty("lab.measurementHistory.unavailable")).contains("before this update");
        }
    }

    private static String read(String path) throws IOException {
        return Files.readString(projectRoot().resolve(path), StandardCharsets.UTF_8);
    }

    private static Path projectRoot() {
        try {
            Path location = Path.of(LabTextResultLabelJspRegressionTest.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path current = Files.isRegularFile(location) ? location.getParent() : location;
                 current != null; current = current.getParent()) {
                if (Files.isDirectory(current.resolve("src/main/webapp"))) {
                    return current;
                }
            }
        } catch (URISyntaxException invalid) {
            throw new IllegalStateException("Cannot locate the project root", invalid);
        }
        throw new IllegalStateException("Cannot locate the project root");
    }
}
