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

import io.github.carlos_emr.carlos.utility.SafeEncode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The professional contact form (demographic/addEditProfessionalContact.jsp) hands a saved contact back to the Health
 * Care Team panel, with its name and specialty, from its own script (#4986). A contact named O'Brien has to reach the
 * panel like any other, so every value the script quotes passes through the JavaScript encoder. Unit tests have no EL
 * implementation, so this reads the page.
 *
 * @since 2026-10-18
 */
@DisplayName("Professional contact form script values")
@Tag("unit")
@Tag("demographic")
class ProfessionalContactFormJspUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/demographic/addEditProfessionalContact.jsp");

    /** The EL expressions that the page's script quotes as string values: {@code var x = '${...}';}. */
    private static List<String> quotedScriptValues() throws IOException {
        String page = Files.readString(PAGE, StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
        Matcher value = Pattern.compile("var\\s+\\w+\\s*=\\s*'(\\$\\{[^}]*\\})'").matcher(page);
        List<String> values = new ArrayList<>();
        while (value.find()) {
            values.add(value.group(1));
        }
        return values;
    }

    @Test
    void shouldHandBackTheSavedNameAndSpecialty_throughTheJavaScriptEncoder() throws IOException {
        assertThat(quotedScriptValues()).contains(
                "${carlos:forJavaScript(requestScope.contactName)}",
                "${carlos:forJavaScript(requestScope.contactRole)}",
                "${carlos:forJavaScript(requestScope.contactId)}");
    }

    @Test
    void shouldQuoteNoValueInThePageScript_withoutTheJavaScriptEncoder() throws IOException {
        List<String> values = quotedScriptValues();
        assertThat(values).as("the values the page's script quotes").hasSizeGreaterThanOrEqualTo(8);
        assertThat(values).allSatisfy(value -> assertThat(value).startsWith("${carlos:forJavaScript("));
    }

    @Test
    void shouldKeepANameWithAnApostrophe_inOneScriptString() {
        assertThat(SafeEncode.forJavaScript("O'Brien, Team")).doesNotContain("'").contains("Brien, Team");
    }
}
