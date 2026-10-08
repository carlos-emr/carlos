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
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Pages that link to a patient or a claim by its number: the Open E-Chart page only accepts a numeric
 * patient number (anything else goes to the patient search), and the teleplan reports only link an
 * office number that is numeric. Each test takes the pattern the page itself uses and checks it
 * against numbers and non-numbers.
 *
 * @since 2026-10-08
 */
@DisplayName("Numeric patient and office numbers on lab and teleplan links")
@Tag("unit")
class NumericLinkNumberJspRegressionTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path JSP_DIR = resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp"));
    /** The pattern in a {@code value.matches("...")} call, as written in the JSP's Java source. */
    private static final Pattern MATCHES_CALL = Pattern.compile("\\.matches\\(\"((?:[^\"\\\\]|\\\\.)*)\"\\)");
    private static final String[] NOT_NUMBERS = {"", " 12", "12 ", "12a", "-12", "1.5", "1e3", "null",
        "1');x", "12%27", "١٢٣"};

    @Test
    @DisplayName("should send anything but a patient number to the patient search before the page is drawn")
    void shouldRedirectToPatientSearch_whenPatientNumberIsNotNumeric() throws IOException {
        String jsp = read("oscarMDS/OpenEChart.jsp");
        String read = "String demographicNo = request.getParameter(\"demographicNo\");";
        String guard = "if (demographicNo == null || !demographicNo.matches(";
        int readAt = jsp.indexOf(read);
        int guardAt = jsp.indexOf(guard);
        int redirectAt = jsp.indexOf("response.sendRedirect(redirectURL);", guardAt);
        int returnAt = jsp.indexOf("return;", redirectAt);

        assertThat(readAt).as("the page reads the patient number").isNotNegative();
        assertThat(guardAt).as("the check follows the read").isGreaterThan(readAt);
        assertThat(redirectAt).as("a failed check redirects").isGreaterThan(guardAt);
        assertThat(returnAt).as("and stops").isGreaterThan(redirectAt);
        assertThat(jsp.indexOf("<html>")).as("before anything is drawn").isGreaterThan(returnAt);
        assertThat(jsp.indexOf("demographicNo", readAt + read.length()))
                .as("no use of the number before the check").isEqualTo(guardAt + "if (".length());

        assertNumbersOnly(patternAt(jsp, guardAt));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "billing/CA/BC/genTAS00.jsp",
            "billing/CA/BC/genTAS01.jsp",
            "billing/CA/BC/genTAS00ByOfficeNo.jsp"})
    @DisplayName("should link an office number to its claim only when it is numeric")
    void shouldLinkOfficeNumber_onlyWhenNumeric(String page) throws IOException {
        String jsp = read(page);
        String declaration = "boolean officeNoLinked = result.getOfficeNo() != null && result.getOfficeNo().matches(";
        int declaredAt = jsp.indexOf(declaration);
        assertThat(declaredAt).as("the row decides whether its office number links").isNotNegative();
        assertNumbersOnly(patternAt(jsp, declaredAt));

        int linkAt = jsp.indexOf("reprocessBill?billingmaster_no=");
        assertThat(linkAt).as("the claim link").isGreaterThan(declaredAt);
        assertThat(jsp.indexOf("reprocessBill?billingmaster_no=", linkAt + 1)).as("one claim link per row").isNegative();
        int openAt = jsp.lastIndexOf("<% if (officeNoLinked) { %><a", linkAt);
        assertThat(openAt).as("the link's start tag is written only when linked").isGreaterThan(declaredAt);
        assertThat(jsp.substring(openAt, linkAt)).as("inside that check").doesNotContain("<% }");
        int startTagEnd = jsp.indexOf("\">", linkAt);
        assertThat(jsp.substring(startTagEnd)).as("the check ends with the start tag")
                .startsWith("\"><% } %>")
                .contains("<% if (officeNoLinked) { %></a><% } %>");
    }

    private static void assertNumbersOnly(Pattern numbers) {
        assertThat("123").matches(numbers);
        assertThat("0042").matches(numbers);
        for (String notANumber : NOT_NUMBERS) {
            assertThat(numbers.matcher(notANumber).matches()).as("accepts \"%s\"", notANumber).isFalse();
        }
    }

    /** The pattern of the first {@code .matches("...")} call at or after {@code from}, unescaped from Java source. */
    private static Pattern patternAt(String jsp, int from) {
        Matcher call = MATCHES_CALL.matcher(jsp);
        assertThat(call.find(from)).as("a .matches(\"...\") check").isTrue();
        return Pattern.compile(call.group(1).replace("\\\\", "\\"));
    }

    private static String read(String page) throws IOException {
        return Files.readString(JSP_DIR.resolve(page), StandardCharsets.UTF_8);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .normalize();
        for (int checkedParents = 0; current != null && checkedParents < 6; checkedParents++) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relativePath + " from "
                + System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")));
    }
}
