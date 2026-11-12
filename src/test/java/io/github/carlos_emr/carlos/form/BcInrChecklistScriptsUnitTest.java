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
package io.github.carlos_emr.carlos.form;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BC-INR and Chart Checklist carried the Labour and Birth Summary's date and time checks, which name fields these forms
 * do not have: every Save threw a TypeError and the form posted with none of its own dates checked (finding 429).
 * BC-INR's Print also ended in {@code return false()}, which throws after printing (finding 428).
 *
 * @since 2026-10-18
 */
@DisplayName("BC-INR and Chart Checklist Save and Print scripts")
@Tag("unit")
@Tag("form")
class BcInrChecklistScriptsUnitTest {

    private static final Path FORM_DIR = Path.of("src/main/webapp/WEB-INF/jsp/form");

    private static String page(String name) throws IOException {
        return Files.readString(FORM_DIR.resolve(name), StandardCharsets.UTF_8);
    }

    /** The body of a {@code function name() { ... }} block, up to its closing brace at the same indent. */
    private static String function(String jsp, String name) {
        Matcher matcher = Pattern.compile("\\n(\\s*)function " + name + "\\(\\) \\{\\n(.*?)\\n\\1\\}", Pattern.DOTALL).matcher(jsp);
        assertThat(matcher.find()).as("function %s()", name).isTrue();
        return matcher.group(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"formbcinr.jsp", "formbcclientchartchecklist.jsp"})
    void shouldCheckOnlyTheFormsOwnFields_inTheSaveChecks(String name) throws IOException {
        String jsp = page(name);
        for (String check : new String[] {"checkAllDates", "checkAllTimes"}) {
            String body = function(jsp, check);
            assertThat(body).as("%s %s()", name, check).doesNotContain("birFHR", "birTimeDate", "birTimeHour");
            Matcher field = Pattern.compile("document\\.forms\\[0\\]\\.(\\w+)").matcher(body);
            while (field.find()) {
                if ("elements".equals(field.group(1))) {
                    continue;
                }
                assertThat(jsp).as("%s %s() names %s", name, check, field.group(1)).contains("name=\"" + field.group(1));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"formbcinr.jsp", "formbcclientchartchecklist.jsp"})
    void shouldCheckTheDateBoxes_byTheFormsOwnNames(String name) throws IOException {
        String body = function(page(name), "checkAllDates");
        assertThat(body).contains("valDate(elements[i])").contains("document.forms[0].elements");
    }

    @Test
    void shouldEndTheInrPrintButtons_withoutCallingFalse() throws IOException {
        String jsp = page("formbcinr.jsp").replaceAll("(?s)<!--.*?-->", "");
        assertThat(jsp).doesNotContain("return false()").contains("onclick=\"javascript:window.print();return false;\"");
    }
}
