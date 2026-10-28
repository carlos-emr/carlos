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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The Mental Health Referral and Assessment pages, their print views, the Mental Health Act forms and the
 * Rourke 2006 pages, as the browser receives them.
 *
 * <ul>
 *   <li>#4683: Referral and Assessment wrote each saved value raw into its input's {@code value="..."}, its
 *   comments box and its print view, so a value with a double quote was cut off at the quote on reopen and
 *   print, and the next Save from that page stored the cut value. The print views also rendered the posted
 *   request parameters raw. Every value is now written through the encoder for its context, as the Outcome
 *   page's already are (#4630).</li>
 *   <li>#4595: the hidden {@code c_lastVisited} was an unquoted attribute directly before {@code />}, so the
 *   browser posted {@code referral/} or {@code p1/}; the reopen wrappers built the JSP name from it and every
 *   saved record opened HTTP 500. The attribute is quoted, and the wrappers resolve the stored value through
 *   {@link LastVisitedPageResolver}, which also reopens rows saved before the fix.</li>
 *   <li>#4597: Forms 14 and 42 checked the lab requisition's date fields and were titled "Laboratory
 *   Requisition".</li>
 * </ul>
 *
 * <p>The handlers themselves (Print, Save, Save and Exit, the date checks) are run in node by
 * {@code scripts/mental-health-chain-handlers.test.js} and
 * {@code scripts/mental-health-act-forms-date-validation.test.js}.</p>
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("form")
@DisplayName("Mental Health form pages")
class MentalHealthFormPagesJspRegressionTest {

    private static final Path FORM_DIR = Path.of("src/main/webapp/WEB-INF/jsp/form");
    /** Any expression output on the page; group 1 is the expression. */
    private static final Pattern EXPRESSION = Pattern.compile("<%=(.*?)%>", Pattern.DOTALL);
    /** A stored value written into a value attribute; group 1 is the opening quote, group 2 the encoder. */
    private static final Pattern STORED_VALUE_ATTRIBUTE =
            Pattern.compile("value=(\"?)<%=\\s*(?:SafeEncode\\.(\\w+)\\()?props\\.getProperty\\(");
    /** An attribute value written by a JSP expression or tag with no quote around it. */
    private static final Pattern UNQUOTED_ATTRIBUTE = Pattern.compile("\\bvalue=<");

    private static String jsp(String name) throws IOException {
        return Files.readString(FORM_DIR.resolve(name), StandardCharsets.UTF_8);
    }

    /** The expressions on the page that print a stored or posted value without an encoder. */
    private static List<String> rawStoredValues(String page) {
        List<String> raw = new ArrayList<>();
        Matcher expression = EXPRESSION.matcher(page);
        while (expression.find()) {
            String code = expression.group(1).trim();
            if (code.contains("props.getProperty(") && !code.startsWith("SafeEncode.")) {
                raw.add(code);
            }
        }
        return raw;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"formmhreferral.jsp", "formmhassessment.jsp", "formmhreferralprint.jsp",
            "formmhassessmentprint.jsp"})
    @DisplayName("should write every stored value through an encoder")
    void shouldEncodeEveryStoredValue_onPage(String name) throws IOException {
        assertThat(rawStoredValues(jsp(name))).as("stored values %s writes without an encoder", name).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"formmhreferral.jsp,31", "formmhassessment.jsp,29", "formmhoutcome.jsp,30"})
    @DisplayName("should quote and attribute-encode every stored value written into an input")
    void shouldAttributeEncodeStoredValues_inValueAttributes(String name, int expected) throws IOException {
        Matcher attribute = STORED_VALUE_ATTRIBUTE.matcher(jsp(name));
        int count = 0;
        while (attribute.find()) {
            count++;
            assertThat(attribute.group(1)).as("quoted value attribute at %d of %s", attribute.start(), name)
                    .isEqualTo("\"");
            assertThat(attribute.group(2)).as("encoder at %d of %s", attribute.start(), name)
                    .isEqualTo("forHtmlAttribute");
        }

        // The readonly patient details, the page's own fields, the catalogue numbers and their "Other" boxes,
        // and the hidden record fields, c_lastVisited among them.
        assertThat(count).as("stored values written into a value attribute of %s", name).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"formmhreferral.jsp,r_refComments", "formmhassessment.jsp,a_assComments",
            "formmhoutcome.jsp,o_outComments"})
    @DisplayName("should write the comments box's saved text as encoded content, flush against the tags")
    void shouldContentEncodeComments_inTextarea(String name, String comments) throws IOException {
        assertThat(jsp(name)).contains("name=\"" + comments + "\"><%= SafeEncode.forHtmlContent(props.getProperty(\""
                + comments + "\", \"\")) %></textarea>");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"formmhreferralprint.jsp,23,r_refComments,mhRefComments",
            "formmhassessmentprint.jsp,20,a_assComments,mhAssComments"})
    @DisplayName("should print every posted value encoded, and the comments with their line breaks")
    void shouldContentEncodePostedValues_inPrintView(String name, int expected, String comments, String styleClass)
            throws IOException {
        String page = jsp(name);
        Matcher encoded = Pattern.compile("SafeEncode\\.forHtmlContent\\(props\\.getProperty\\(").matcher(page);
        int count = 0;
        while (encoded.find()) {
            count++;
        }

        // The patient details, the three catalogue entries of each list, and the comments.
        assertThat(count).isEqualTo(expected);
        assertThat(page)
                .contains("<span class=\"" + styleClass + "\"><%= SafeEncode.forHtmlContent(props.getProperty(\""
                        + comments + "\", \"\")) %></span>")
                .containsPattern("\\." + styleClass + "\\s*\\{\\s*white-space:\\s*pre-wrap;\\s*}");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"formmhreferral.jsp", "formmhassessment.jsp", "formmhoutcome.jsp"})
    @DisplayName("should encode the catalogue entries listed beside the numbers")
    void shouldContentEncodeCatalogueEntries_onPage(String name) throws IOException {
        assertThat(jsp(name)).doesNotContainPattern("<%=\\s*\\w+\\[i]\\s*%>")
                .containsPattern("<%= SafeEncode\\.forHtmlContent\\(\\w+\\[i]\\) %>");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"formmhreferral.jsp", "formmhassessment.jsp", "formmhoutcome.jsp", "formrourke2006p1.jsp",
            "formrourke2006p2.jsp", "formrourke2006p3.jsp", "formrourke2006p4.jsp"})
    @DisplayName("should quote the hidden c_lastVisited so the page posts its name without a slash")
    void shouldQuoteLastVisited_onEveryPage(String name) throws IOException {
        String page = jsp(name);
        Matcher lastVisited = Pattern.compile("name=\"c_lastVisited\"\\s+value=(\\S)").matcher(page);

        assertThat(lastVisited.find()).as("hidden c_lastVisited on %s", name).isTrue();
        assertThat(lastVisited.group(1)).isEqualTo("\"");
        assertThat(UNQUOTED_ATTRIBUTE.matcher(page).find()).as("an unquoted value attribute on %s", name).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "formmentalhealth.jsp|LastVisitedPageResolver.resolve(props.getProperty(\"c_lastVisited\"), \"referral\", \"assessment\", \"outcome\")",
            "formrourke2006.jsp|LastVisitedPageResolver.resolve(props.getProperty(\"c_lastVisited\"), \"p1\", \"p2\", \"p3\", \"p4\")"})
    @DisplayName("should reopen a saved record on a page the form has")
    void shouldResolveStoredPage_beforeIncludingIt(String name, String resolution) throws IOException {
        String page = jsp(name);

        assertThat(page.replaceAll("\\s+", " ")).contains(resolution);
        assertThat(page).doesNotContainPattern("getRequestDispatcher\\([^;]*props\\.getProperty\\(\"c_lastVisited\"");
    }

    @Test
    @DisplayName("should leave the E-Chart that opened the Assessment page alone on Save")
    void shouldNotReloadOpener_whenAssessmentIsSaved() throws IOException {
        assertThat(jsp("formmhassessment.jsp")).doesNotContain("opener.location.reload");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"formmhreferral.jsp", "formmhassessment.jsp", "formmhoutcome.jsp"})
    @DisplayName("should submit Print through the form prototype, which the hidden submit input cannot shadow")
    void shouldSubmitPrint_throughFormPrototype(String name) throws IOException {
        String page = jsp(name);

        assertThat(page).contains("<input type=\"hidden\" name=\"submit\"")
                .doesNotContain("document.forms[0].submit()")
                .contains("HTMLFormElement.prototype.submit.call(document.forms[0]);");
    }

    @ParameterizedTest(name = "Form {0}")
    @CsvSource({"1,formMentalHealthForm1.jsp", "14,formMentalHealthForm14.jsp", "42,formMentalHealthForm42.jsp"})
    @DisplayName("should title each Mental Health Act form after itself, not the lab requisition")
    void shouldTitleForm_afterItself(String number, String name) throws IOException {
        assertThat(jsp(name)).doesNotContain("Laboratory Requisition")
                .contains("<title><fmt:message key=\"form.mentalHealthForm" + number + ".title\"/></title>");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"formMentalHealthForm14.jsp", "formMentalHealthForm42.jsp"})
    @DisplayName("should validate the form's own dates, not the lab requisition's")
    void shouldNotCheckLabRequisitionDates_onSave(String name) throws IOException {
        assertThat(jsp(name)).doesNotContain("b_dateSigned").doesNotContain("o_specimenCollectionDate")
                .contains("<fmt:message key=\"form.mentalHealthAct.invalidDate\" var=\"mentalHealthInvalidDateMessage\"/>");
    }
}
