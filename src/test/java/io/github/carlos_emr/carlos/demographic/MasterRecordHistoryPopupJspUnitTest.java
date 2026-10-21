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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Master Record's address and phone history popups ({@code generateMarkup} in edit.jsp) show each listed value and
 * its date as text: both go through {@code historyText}, which replaces the five HTML-special characters.
 *
 * @since 2026-10-18
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("Master Record history popup")
class MasterRecordHistoryPopupJspUnitTest {

    private static String editJsp() throws IOException, URISyntaxException {
        Path current = Path.of(MasterRecordHistoryPopupJspUnitTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        while (current != null && !Files.isDirectory(current.resolve("src/main/webapp"))) {
            current = current.getParent();
        }
        assertThat(current).as("project root with src/main/webapp").isNotNull();
        return Files.readString(current.resolve("src/main/webapp/WEB-INF/jsp/demographic/edit.jsp"), StandardCharsets.UTF_8);
    }

    /** The source of the named page function, from its declaration to the next named function declaration. */
    private static String function(String jsp, String name) {
        int start = jsp.indexOf("function " + name + "(");
        assertThat(start).as("function " + name).isNotNegative();
        Matcher next = Pattern.compile("\\bfunction \\w+\\(").matcher(jsp);
        int end = next.find(start + 1) ? next.start() : jsp.length();
        return jsp.substring(start, end);
    }

    @Test
    void shouldPutEachDateAndValueThroughHistoryText_whenBuildingThePopup() throws Exception {
        String markup = function(editJsp(), "generateMarkup");

        assertThat(markup).contains("historyText(addresses[x].dateSeen)").contains("historyText(addresses[x].name)");
        assertThat(markup).doesNotContain("+ addresses[x].dateSeen +").doesNotContain("+ addresses[x].name +");
    }

    @Test
    void shouldReplaceEveryHtmlSpecialCharacter_inHistoryText() throws Exception {
        String historyText = function(editJsp(), "historyText");

        assertThat(historyText).contains("/[&<>\"']/g")
                .contains("'&': '&amp;'").contains("'<': '&lt;'").contains("'>': '&gt;'")
                .contains("'\"': '&quot;'").contains("\"'\": '&#39;'");
    }
}
