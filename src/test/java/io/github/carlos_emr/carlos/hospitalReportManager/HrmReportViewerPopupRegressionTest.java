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
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The HRM report viewer's patient buttons, and the size of the window it opens in (#3711).
 *
 * <p>The E-Chart button's inline handler once had a line break inside its URL string. A
 * JavaScript string cannot hold a raw line break, so the handler never compiled and the button did
 * nothing. The report page is also about 870 px wide, so the 800 px windows it opened in scrolled
 * sideways.
 */
@DisplayName("HRM report viewer popup regression tests")
@Tag("unit")
@Tag("regression")
class HrmReportViewerPopupRegressionTest {
    private static final Path VIEWER = Path.of("src/main/webapp/WEB-INF/jsp/hospitalReportManager/displayHRMReport.jsp");
    private static final Path DOC_LIST = Path.of("src/main/webapp/WEB-INF/jsp/hospitalReportManager/displayHRMDocList.jsp");
    private static final Path ECHART_SECTION = Path.of(
            "src/main/java/io/github/carlos_emr/carlos/encounter/pageUtil/EctDisplayHRM2Action.java");
    /** An inline event handler attribute such as {@code onClick="..."}, case-insensitive. */
    private static final Pattern INLINE_HANDLER = Pattern.compile("(?i)\\bon[a-z]+\\s*=\\s*\"([^\"]*)\"");

    @Test
    @DisplayName("should keep every inline handler of the report viewer on one line")
    void shouldKeepInlineHandlersOnOneLine_inReportViewer() throws IOException {
        String jsp = Files.readString(VIEWER);
        List<String> broken = new ArrayList<>();
        Matcher handler = INLINE_HANDLER.matcher(jsp);
        while (handler.find()) {
            if (handler.group(1).contains("\n")) {
                int line = jsp.substring(0, handler.start()).split("\n", -1).length;
                broken.add("line " + line + ": " + handler.group(1).lines().findFirst().orElse(""));
            }
        }
        assertThat(broken).as("inline handlers whose code spans lines").isEmpty();
    }

    @Test
    @DisplayName("should open the E-Chart from the report viewer with a well-formed encounter URL")
    void shouldBuildEncounterUrl_forEchartButton() throws IOException {
        String jsp = Files.readString(VIEWER);
        Matcher button = Pattern.compile("(?s)id=\"mainEchart_[^\"]*\".*?onClick=\"([^\"]*)\"").matcher(jsp);
        assertThat(button.find()).as("the E-Chart button and its handler").isTrue();
        assertThat(button.group(1))
                .contains("/encounter/IncomingEncounter?updateParent=false&reason=${carlos:forUriComponent(hrmEchartReason)}"
                        + "&curDate=<%=currentDate%>&appointmentNo=")
                .doesNotContain("%>>&");
    }

    @Test
    @DisplayName("should open the report in a window wide enough for it")
    void shouldOpenReportWideEnough_fromChartAndDocumentList() throws IOException {
        assertThat(Files.readString(ECHART_SECTION)).contains("popupPage(700,900,'").doesNotContain("popupPage(700,800,'");
        assertThat(Files.readString(DOC_LIST)).contains("height=700,width=900,").doesNotContain("height=700,width=800,");
    }
}
