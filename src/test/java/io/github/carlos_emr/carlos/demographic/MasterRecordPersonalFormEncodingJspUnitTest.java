/**
 * Copyright (c) 2026 CARLOS EMR Project Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the Master Record edit form's personal fields (edit-form-personal.jsp) print stored values into
 * {@code value="…"}: through the null-safe encoder in HTML attribute context, so a value holding a double quote,
 * {@code <}, {@code >} or {@code &} is shown and posted back exactly as stored instead of being cut off at the
 * quote and shortened by the next save. The hidden "…Orig" copies of four of them are pinned too; nothing on the
 * server reads them today, but they hold the same stored value and print it the same way.
 *
 * <p>The postal code, phone, work phone, health card number and version code lines are develop's lines
 * (c9b6346229, #2441), so the forward merge leaves them as develop has them.</p>
 *
 * @since 2026-11-17
 */
@DisplayName("Master Record personal form stored-value encoding")
@Tag("unit")
@Tag("demographic")
class MasterRecordPersonalFormEncodingJspUnitTest {

    private static final Path EDIT_FORM_PERSONAL_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit-form-personal.jsp");

    /** Each stored personal value printed into a field: what it is, its expression, and how often it is printed. */
    static Stream<Arguments> storedValues() {
        return Stream.of(
                Arguments.of("postal code", "StringUtils.trimToEmpty(demographic.getPostal())", 1),
                Arguments.of("residential postal code", "StringUtils.trimToEmpty(demographic.getResidentialPostal())", 1),
                Arguments.of("home phone", "StringUtils.trimToEmpty(StringUtils.trimToEmpty(demographic.getPhone()))", 1),
                Arguments.of("home phone extension and its original",
                        "StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get(\"hPhoneExt\")))", 2),
                Arguments.of("work phone", "StringUtils.trimToEmpty(demographic.getPhone2())", 1),
                Arguments.of("work phone extension and its original",
                        "StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get(\"wPhoneExt\")))", 2),
                Arguments.of("cell phone and its original", "StringUtils.trimToEmpty(demoExt.get(\"demo_cell\"))", 2),
                Arguments.of("health card number", "StringUtils.trimToEmpty(demographic.getHin())", 1),
                Arguments.of("health card version code", "StringUtils.trimToEmpty(demographic.getVer())", 1),
                Arguments.of("SIN", "(demographic.getSin()==null||demographic.getSin().equals(\"null\"))?\"\":demographic.getSin()", 1),
                Arguments.of("cytology number and its original", "StringUtils.trimToEmpty(demoExt.get(\"cytolNum\"))", 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("storedValues")
    @DisplayName("should print each stored personal value through the encoder, never raw")
    void shouldEncodeStoredValue_inFieldAttribute(String field, String expression, int occurrences) throws IOException {
        String jsp = read();

        assertThat(StringUtils.countMatches(jsp,
                "value=\"<carlos:encode value='<%= " + expression + " %>' context=\"htmlAttribute\"/>\""))
                .as(field).isEqualTo(occurrences);
        assertThat(jsp).as(field).doesNotContain("value=\"<%=" + expression + "%>\"");
    }

    @Test
    @DisplayName("should leave no stored value printed raw into a field value")
    void shouldLeaveNoRawStoredValue_inAnyFieldValue() throws IOException {
        // A field value written by a scriptlet expression (not the encoder tag's own value='...' attribute).
        // What may still be printed that way: an expression that encodes itself, the patient number, the date
        // parts this page formats from stored dates (numbers only), and option values drawn from fixed lists
        // (languages, provinces, countries).
        // Option values from the fixed lists are allowed raw only on an <option>: the page also binds `province`
        // to the patient's stored province, which must never be printed raw into a field.
        Pattern rawValue = Pattern.compile("(<option\\s+)?(?<!carlos:encode )value=[\"']<%=\\s*([^%]+?)\\s*%>");
        Matcher matcher = rawValue.matcher(read());
        int checked = 0;
        while (matcher.find()) {
            checked++;
            String expression = matcher.group(2);
            if (matcher.group(1) != null) {
                assertThat(expression).as(matcher.group()).matches("splang|pr2|province|cc\\.getCountryId\\(\\)");
            } else {
                assertThat(expression).as(matcher.group()).matches("demographic_no|(effDate|renewDate)(Year|Month|Day)|"
                        + "demographic\\.getEmail\\(\\) !=null \\? SafeEncode\\.forHtmlAttribute\\(demographic\\.getEmail\\(\\)\\) : \"\"");
            }
        }
        assertThat(checked).as("raw field values found by the scan").isPositive();
    }

    private static String read() throws IOException {
        return Files.readString(EDIT_FORM_PERSONAL_JSP, StandardCharsets.UTF_8);
    }
}
