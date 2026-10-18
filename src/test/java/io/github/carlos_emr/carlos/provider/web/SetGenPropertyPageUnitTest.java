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
package io.github.carlos_emr.carlos.provider.web;

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
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generic preference page (provider/setGenProperty.jsp) shows a text box for the two preferences that
 * ProviderProperty2Action sends it without a drop-down list: the OntarioMD username and the consultation cut-off date.
 * The page's validateForm() and provider_form_validations.js refuse the box with id {@code numericFormField} unless it
 * is all digits. That id was fixed, so an OntarioMD username had to be all digits too. The page has no EL
 * implementation in unit tests, so this reads the box's id from the page and works it out for each preference. The
 * OntarioMD page's password box is a password field that the browser does not fill in with the CARLOS sign-in.
 *
 * @since 2026-10-18
 */
@DisplayName("Generic preference page text box id")
@Tag("unit")
@Tag("provider")
class SetGenPropertyPageUnitTest {

    private static final Path PAGE = Path.of("src/main/webapp/WEB-INF/jsp/provider/setGenProperty.jsp");

    /** The page without its JSP comments. */
    private static String page() throws IOException {
        return Files.readString(PAGE, StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
    }

    /** The text box's id attribute, as written in the page. */
    private static String textBoxId() throws IOException {
        String page = page();
        Matcher box = Pattern.compile("<input type=\"text\"\\s+id=\"([^\"]*)\"\\s+name=\"dateProperty\\.value\"").matcher(page);
        assertThat(box.find()).as("the text box for dateProperty.value").isTrue();
        return box.group(1);
    }

    /** The id the box gets for a preference: a fixed id, or {@code ${method eq '...' ? '...' : '...'}}. */
    private static String idFor(String method, String id) {
        Matcher choice = Pattern.compile("\\$\\{method eq '([^']*)' \\? '([^']*)' : '([^']*)'}").matcher(id);
        if (!choice.matches()) {
            assertThat(id).as("a fixed id").doesNotContain("${");
            return id;
        }
        return method.equals(choice.group(1)) ? choice.group(2) : choice.group(3);
    }

    @ParameterizedTest
    @CsvSource({"saveConsultationRequestCuffOffDate, true", "saveOntarioMDId, false"})
    void shouldCheckTheBoxAsANumber_onlyForTheConsultationCutOffDate(String method, boolean numeric) throws IOException {
        String id = idFor(method, textBoxId());
        assertThat(id.equals("numericFormField")).as("%s: id %s", method, id).isEqualTo(numeric);
        assertThat(id).isNotBlank();
    }

    @Test
    void shouldHideTheOntarioMdPassword_withoutFillingInTheSignInPassword() throws IOException {
        Matcher box = Pattern.compile("<input\\b[^>]*name=\"dateProperty2\\.value\"[^>]*>").matcher(page());
        assertThat(box.find()).as("the OntarioMD password box").isTrue();
        assertThat(box.group()).contains("type=\"password\"", "autocomplete=\"new-password\"", "aria-label=");
    }
}
