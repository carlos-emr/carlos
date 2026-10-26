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
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Add A Role (issue #5140): the page decides what to do by comparing the posted {@code submit} value
 * with "Search" and "Save", so its buttons must post those values in every language and translate only
 * their labels. With translated values, French, Spanish, Polish and Portuguese users could neither search
 * nor save a role. The browser check {@code translated-button-values} drives the page in French.
 */
@Tag("unit")
@Tag("web")
@DisplayName("Add A Role button values")
class ProviderAddRoleJspButtonValuesUnitTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/admin/providerAddRole.jsp");
    private static final Pattern SUBMIT_CONTROL = Pattern.compile("<(?:input|button)\\b[^>]*\\bname=\"submit\"[^>]*>");
    private static final Pattern VALUE = Pattern.compile("\\bvalue=\"([^\"]*)\"");
    private static final Pattern COMPARED = Pattern.compile("getParameter\\(\"submit\"\\)\\.equals\\(\"([^\"]+)\"\\)");

    private static String page() throws IOException {
        return Files.readString(JSP, StandardCharsets.UTF_8);
    }

    /** The value each control named "submit" posts, as written in the page. */
    private static List<String> postedValues(String page) {
        return SUBMIT_CONTROL.matcher(page).results().map(control -> {
            Matcher value = VALUE.matcher(control.group());
            assertThat(value.find()).as("%s has no value", control.group()).isTrue();
            return value.group(1);
        }).toList();
    }

    @Test
    @DisplayName("should post only the values the page compares, the same in every language")
    void shouldPostComparedValues_inEveryLanguage() throws IOException {
        String page = page();
        List<String> posted = postedValues(page);
        Set<String> compared = COMPARED.matcher(page).results().map(match -> match.group(1)).collect(Collectors.toSet());

        assertThat(posted).containsExactlyInAnyOrder("Search", "Save");
        assertThat(compared).containsAll(posted);
    }

    @Test
    @DisplayName("should translate the button labels, not the posted values")
    void shouldTranslateLabels_insteadOfValues() throws IOException {
        String page = page();

        assertThat(page).containsPattern(
                "<button[^>]*name=\"submit\" value=\"Search\"[^>]*>\\s*<fmt:message key=\"admin\\.provideraddrole\\.search\"/>\\s*</button>");
        assertThat(page).containsPattern(
                "<button[^>]*name=\"submit\" value=\"Save\"[^>]*>\\s*<fmt:message key=\"admin\\.resourcebaseurl\\.btnSave\"/>\\s*</button>");
    }
}
