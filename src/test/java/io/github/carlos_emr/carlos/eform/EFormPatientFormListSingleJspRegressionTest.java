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
package io.github.carlos_emr.carlos.eform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the output encoding of the "all versions of this eForm" list,
 * {@code efmpatientformlistsingle.jsp} (issue #3057, leaf #2231).
 *
 * <p>The page lists every saved instance of one eForm for one patient. The instance name and the
 * subject are stored text (the subject is typed by the user who saved the form), and the page
 * used to print both raw, so a subject carrying markup ran as HTML for every later reader of the
 * patient's eForm history. The sibling list {@code efmpatientformlist.jsp} already encoded the same
 * map; this test keeps the single-form list aligned with it. The behavioural proof is the
 * installed {@code xss-poison-eform} browser check, which seeds an instance with inert markup and
 * opens this page; these source contracts keep a raw print from coming back between package
 * runs.</p>
 *
 * @since 2026-11-26
 */
@DisplayName("eForm single-form version list output encoding")
@Tag("unit")
@Tag("eform")
@Tag("security")
class EFormPatientFormListSingleJspRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/eform/efmpatientformlistsingle.jsp");

    /** {@code <carlos:encode value='<%= (String) curform.get("field") %>' context="ctx"/>}. */
    private static final Pattern ENCODED_FIELD = Pattern.compile(
            "<carlos:encode value='<%= \\(String\\) curform\\.get\\(\"(\\w+)\"\\) %>' context=\"(\\w+)\"/>");

    /**
     * A scriptlet expression that prints a map value straight into the page. Only the expression
     * handed to a {@code <carlos:encode value='...'>} tag is the encoded form and is excluded; the
     * same expression in any other single-quoted attribute (an {@code <input value='...'>}) is raw.
     */
    private static final Pattern RAW_FIELD = Pattern.compile(
            "(?<!<carlos:encode value=')<%=\\s*\\(?\\s*(?:\\(String\\)\\s*)?curform\\.get\\(");

    private static String page;

    @BeforeAll
    static void readPage() throws IOException {
        page = Files.readString(JSP, StandardCharsets.UTF_8);
    }

    private static List<String> encodedContexts(String field) {
        List<String> contexts = new ArrayList<>();
        Matcher m = ENCODED_FIELD.matcher(page);
        while (m.find()) {
            if (m.group(1).equals(field)) {
                contexts.add(m.group(2));
            }
        }
        return contexts;
    }

    @Test
    @DisplayName("should declare the CARLOS encoder taglib the encoded fields need")
    void shouldDeclareCarlosTaglib_forEncodedFields() {
        assertThat(page).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");
    }

    private static boolean printsRawField(String jsp) {
        return RAW_FIELD.matcher(jsp).find();
    }

    @Test
    @DisplayName("should print no eForm instance value through a raw scriptlet expression")
    void shouldPrintNoRawInstanceValue_forAnyField() {
        assertThat(printsRawField(page))
                .as("efmpatientformlistsingle.jsp prints a curform value without encoding")
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "<td><%=curform.get(\"formSubject\")%></td>",
            "<input value='<%= curform.get(\"formSubject\") %>'>",
            "<input value='<%= (String) curform.get(\"formName\") %>'/>"
    })
    @DisplayName("should detect a raw print even inside a single-quoted attribute")
    void shouldDetectRawPrint_forUnencodedFixture(String fixture) {
        assertThat(printsRawField(fixture)).isTrue();
    }

    @Test
    @DisplayName("should not count the expression handed to the CARLOS encoder as a raw print")
    void shouldAcceptEncodedPrint_forCarlosEncodeFixture() {
        assertThat(printsRawField(
                "<td><carlos:encode value='<%= (String) curform.get(\"formSubject\") %>' context=\"html\"/></td>"))
                .isFalse();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "formName, html",
            "formSubject, html",
            "formDate, html",
            "fdid, uriComponent",
            "fdid, htmlAttribute"
    })
    @DisplayName("should encode each listed instance value for the context it is printed in")
    void shouldEncodeInstanceValue_forItsOutputContext(String field, String context) {
        assertThat(encodedContexts(field))
                .as("curform.get(\"%s\") encoded as %s", field, context)
                .contains(context);
    }

    @Test
    @DisplayName("should keep the stored name and subject in HTML body cells, never in a script context")
    void shouldEncodeNameAndSubject_onlyAsHtmlBodyText() {
        assertThat(encodedContexts("formName")).containsOnly("html");
        assertThat(encodedContexts("formSubject")).containsOnly("html");
    }
}
