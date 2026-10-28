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

import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * The Mental Health Outcome form must write every saved value back into its input exactly.
 *
 * <p>The form reopens a saved record by writing each stored value into an input's
 * {@code value="..."} attribute. Written raw, a value containing a double quote (a specialist
 * named {@code Dr. "Sam" Lee}, say) ended the attribute early: the input showed a cut-down value
 * and the rest of the text spilled into the page. Every stored value is now written through the
 * attribute encoder, so the browser shows and resubmits exactly what was saved.</p>
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("form")
@DisplayName("Mental Health Outcome form saved values")
class MentalHealthOutcomeFormJspEncodingUnitTest {

    private static final Path FORM_JSP = Path.of("src/main/webapp/WEB-INF/jsp/form/formmhoutcome.jsp");
    /** Any expression output on the page; group 1 is the expression. */
    private static final Pattern EXPRESSION = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);
    /** A stored value written into a value attribute; group 1 is the opening quote, group 2 the encoder. */
    private static final Pattern STORED_VALUE_ATTRIBUTE =
            Pattern.compile("value=(\"?)<%=\\s*(?:SafeEncode\\.(\\w+)\\()?props\\.getProperty\\(");

    private static String jsp() throws IOException {
        return Files.readString(FORM_JSP, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should write every stored value through an encoder")
    void shouldEncodeEveryStoredValue_onFormPage() throws IOException {
        List<String> raw = new ArrayList<>();
        Matcher expression = EXPRESSION.matcher(jsp());
        while (expression.find()) {
            String code = expression.group(1).trim();
            if (code.contains("props.getProperty(") && !code.startsWith("SafeEncode.")) {
                raw.add(code);
            }
        }

        assertThat(raw).as("stored values written without an encoder").isEmpty();
    }

    @Test
    @DisplayName("should quote and attribute-encode every stored value written into an input")
    void shouldAttributeEncodeStoredValues_inValueAttributes() throws IOException {
        Matcher attribute = STORED_VALUE_ATTRIBUTE.matcher(jsp());
        int count = 0;
        while (attribute.find()) {
            count++;
            assertThat(attribute.group(1)).as("quoted value attribute at %d", attribute.start()).isEqualTo("\"");
            assertThat(attribute.group(2)).as("encoder at %d", attribute.start()).isEqualTo("forHtmlAttribute");
        }

        // The readonly patient details, the visit fields, the catalogue codes and their "Other"
        // boxes, and the hidden record fields.
        assertThat(count).isEqualTo(30);
    }

    @Test
    @DisplayName("should keep a saved value with quotes inside its attribute")
    void shouldKeepQuotedValue_insideAttribute() {
        String encoded = SafeEncode.forHtmlAttribute("Dr. \"Sam\" O'Neil & Co");

        assertThat(encoded).doesNotContain("\"").doesNotContain("'").contains("&amp;");
    }
}
