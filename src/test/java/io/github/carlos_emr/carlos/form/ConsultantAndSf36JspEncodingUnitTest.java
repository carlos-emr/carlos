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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("consultation and SF-36 form JSP encoding")
@Tag("unit")
class ConsultantAndSf36JspEncodingUnitTest {

    private static final String FORM_DIR = "src/main/webapp/WEB-INF/jsp/form/";
    private static final String CONSULTANT = "formConsultant.jsp";
    private static final String SF36 = "formSF36.jsp";
    private static final String SF36_CAREGIVER = "formSF36caregiver.jsp";
    private static final String TAGLIB = "<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>";

    /** A stored form value written into the page. */
    private static final Pattern STORED_VALUE = Pattern.compile("<%=\\s*props\\.getProperty\\(");
    /** A stored form value written through the CARLOS encoder; group 1 is the context. */
    private static final Pattern ENCODED_VALUE =
            Pattern.compile("<carlos:encode value='<%= props\\.getProperty\\([^']*\\) %>' context=\"(\\w+)\"/>");
    /**
     * SF-36 checkbox state. Each of these names is a TINYINT(1) column, so the record supplies a
     * number or "checked='checked'", never typed text. Comment ("...Cmt") fields never qualify.
     */
    private static final Pattern SF36_CHECKBOX_STATE = Pattern.compile(
            "(?m)^\\s*<%= props\\.getProperty\\(\"\\w+(?<!Cmt)\", \"\"\\) %> /></t[dh]>\\s*$");
    /** A script line that fills one of the consultant ("t_") fields from the stored form. */
    private static final Pattern FIELD_FILL = Pattern.compile("document\\.forms\\[0]\\.(t_\\w+)\\.value = \"(.*)\";");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {CONSULTANT, SF36, SF36_CAREGIVER})
    @DisplayName("should write every stored text value through the encoder")
    void shouldEncodeStoredValues_onFormPage(String page) throws IOException {
        String jsp = read(page);
        String rest = ENCODED_VALUE.matcher(jsp).replaceAll("");
        if (!CONSULTANT.equals(page)) {
            rest = SF36_CHECKBOX_STATE.matcher(rest).replaceAll("");
        }

        assertThat(jsp).contains(TAGLIB);
        assertThat(linesMatching(rest, STORED_VALUE)).as("stored values on %s written as is", page).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {CONSULTANT, SF36, SF36_CAREGIVER})
    @DisplayName("should pick the encoder context from where the value lands")
    void shouldMatchEncoderContext_toPlacement(String page) throws IOException {
        String jsp = read(page);
        Matcher encoded = ENCODED_VALUE.matcher(jsp);
        List<String> mismatches = new ArrayList<>();
        int count = 0;
        while (encoded.find()) {
            count++;
            int lineStart = jsp.lastIndexOf('\n', encoded.start()) + 1;
            String before = jsp.substring(lineStart, encoded.start());
            String expected = FIELD_FILL.matcher(lineOf(jsp, encoded.start())).find() ? "javaScript"
                    : before.endsWith("value=\"") ? "htmlAttribute"
                    : "html";
            if (!expected.equals(encoded.group(1))) {
                mismatches.add("line " + lineNumber(jsp, encoded.start()) + ": " + encoded.group(1) + ", expected " + expected);
            }
        }
        assertThat(count).as("encoded values on %s", page).isPositive();
        assertThat(mismatches).as("encoder contexts on %s", page).isEmpty();
    }

    @Test
    @DisplayName("should fill only fields the consultation form has, keeping a saved address")
    void shouldFillExistingFields_onConsultationForm() throws IOException {
        String jsp = read(CONSULTANT);
        Matcher fill = FIELD_FILL.matcher(jsp);
        List<String> fields = new ArrayList<>();
        while (fill.find()) {
            fields.add(fill.group(1));
            assertThat(jsp).as("a %s field to fill", fill.group(1)).contains("name=\"" + fill.group(1) + "\"");
        }

        assertThat(fields).containsExactly("t_name", "t_address1", "t_phone", "t_fax");
        // A new form gets the referral doctor's address (t_address); a saved one keeps its own.
        assertThat(jsp).contains("props.getProperty(\"t_address\", props.getProperty(\"t_address1\", \"\"))");
    }

    private static String read(String page) throws IOException {
        return Files.readString(Path.of(FORM_DIR + page), StandardCharsets.UTF_8);
    }

    private static List<String> linesMatching(String text, Pattern pattern) {
        return text.lines().filter(line -> pattern.matcher(line).find()).map(String::strip).toList();
    }

    private static String lineOf(String text, int offset) {
        int start = text.lastIndexOf('\n', offset) + 1;
        int end = text.indexOf('\n', offset);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    private static int lineNumber(String text, int offset) {
        return (int) text.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
    }
}
