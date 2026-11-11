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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

    @Test
    void shouldLabelEveryGridCell_includingTheLatestValue() throws IOException {
        String grid = read("src/main/webapp/WEB-INF/jsp/lab/CumulativeLabValues3.jsp");

        assertThat(grid).contains("row['latestText']", "cell['text'] eq 'true'");
        assertThat(grid.split(LABEL, -1)).as("latest value and dated cells").hasSize(3);
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
