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
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Source checks for the Rx drug-info links.
 *
 * <p>Each Info link passes a stored drug name, drug id or DIN to {@code ShowDrugInfo}. A {@code javascript:}
 * link is percent-decoded before it runs, so encoding inside it does not hold: a name with a percent sequence
 * reaches the popup changed. The links carry the drug-info URL, with each value encoded for the URL, and open
 * the popup from {@code onclick}, with each value encoded for the JavaScript attribute.
 *
 * @since 2026-10-15
 */
@DisplayName("Rx drug-info link JSPs")
@Tag("unit")
@Tag("prescription")
class RxDrugInfoLinkJspRegressionTest {

    private static final Path RX = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "rx");
    private static final String URI_VALUE = "(<carlos:encode value='[^']+' context=\"uriComponent\"/>"
            + "|\\$\\{carlos:forUriComponent\\(rx2\\.\\w+\\)\\})";
    private static final String JS_VALUE = "(<carlos:encode value='[^']+' context=\"javaScriptAttribute\"/>"
            + "|\\$\\{carlos:forJavaScriptAttribute\\(rx2\\.\\w+\\)\\})";

    private static List<String> infoLinks(String jsp) {
        return Arrays.stream(jsp.split("\n"))
                .filter(l -> l.contains("/rx/drugInfo?") && l.contains("ShowDrugInfo"))
                .toList();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"StaticScript2.jsp, 1", "ViewScript2.jsp, 1", "WriteScript.jsp, 1", "ChooseDrug.jsp, 3"})
    @DisplayName("should open drug info from a real URL with each value intact")
    void shouldOpenDrugInfo_fromRealUrl(String page, int expectedLinks) throws IOException {
        String jsp = Files.readString(RX.resolve(page), StandardCharsets.UTF_8);

        assertThat(jsp).doesNotContain("href=\"javascript:ShowDrugInfo");
        List<String> links = infoLinks(jsp);
        assertThat(links).hasSize(expectedLinks);
        for (String link : links) {
            assertThat(link).as(link.strip())
                    .containsPattern("href=\"\\$\\{pageContext\\.request\\.contextPath\\}/rx/drugInfo\\?(GN|BN)=" + URI_VALUE)
                    .containsPattern("onclick=\"ShowDrugInfo(GN|BN)?\\('" + JS_VALUE + "'(, '" + JS_VALUE + "')?\\); return false;\"")
                    .doesNotContain("context=\"javaScript\"/>");
            if (link.contains("ShowDrugInfo('") && link.contains("', '")) {
                // The DIN rides along in the URL as it does in the popup call.
                assertThat(link).as(link.strip()).containsPattern("&amp;DIN=" + URI_VALUE);
            }
        }
    }
}
