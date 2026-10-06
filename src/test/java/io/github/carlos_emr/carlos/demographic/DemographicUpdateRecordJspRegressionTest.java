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
package io.github.carlos_emr.carlos.demographic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Guards the "update a record" page's inline scripts. A line break inside a JavaScript string literal is a
 * syntax error, so the whole script is skipped: the waiting-list confirmation once broke this way when the
 * patient already had an appointment ("/wa" and "itinglist" on two lines).
 */
@DisplayName("Demographic update-a-record JSP regression tests")
@Tag("unit")
@Tag("demographic")
class DemographicUpdateRecordJspRegressionTest {

    private static final Path UPDATE_RECORD_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicupdatearecord.jsp");
    private static final Pattern SCRIPT_BLOCK = Pattern.compile("(?is)<script\\b[^>]*>(.*?)</script>");
    /** JSP scriptlets/expressions and custom tags, whose attributes hold quotes of their own. */
    private static final Pattern JSP_MARKUP = Pattern.compile("(?s)<%.*?%>|</?[A-Za-z]+:[^>]*>");

    @Test
    @DisplayName("should post to the waiting-list action whether or not the patient has an appointment")
    void shouldBuildWaitingListUrl_onOneLine() throws IOException {
        String jsp = Files.readString(UPDATE_RECORD_JSP, StandardCharsets.UTF_8);

        assertThat(jsp.split("\"<%= request.getContextPath\\(\\) %>/waitinglist/Add2WaitingList\";", -1))
                .as("both script branches set the waiting-list action on one line")
                .hasSize(3);
    }

    @Test
    @DisplayName("should close every double-quoted string on the line where it opens in the page's scripts")
    void shouldCloseScriptStrings_onTheSameLine() throws IOException {
        String jsp = Files.readString(UPDATE_RECORD_JSP, StandardCharsets.UTF_8);
        List<String> openLines = new ArrayList<>();

        // Strip JSP markup first: a "%>" inside a script tag's src attribute would otherwise end the tag early.
        Matcher script = SCRIPT_BLOCK.matcher(JSP_MARKUP.matcher(jsp).replaceAll(""));
        while (script.find()) {
            for (String line : script.group(1).split("\\R")) {
                if (line.chars().filter(c -> c == '"').count() % 2 != 0) {
                    openLines.add(line.strip());
                }
            }
        }

        assertThat(openLines).as("script lines with an unclosed string").isEmpty();
    }
}
