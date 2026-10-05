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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the output encoding of stored values the patient chart prints from scriptlets, in line with
 * the OWASP encoding rules the other chart fields already follow: the read-only view encodes the
 * stored values it shows as HTML text, and the edit form encodes the stored values it places in
 * field values, including the hidden "original value" fields and configured custom fields.
 *
 * @since 2026-10-05
 */
@DisplayName("demographic chart scriptlet output encoding")
@Tag("unit")
@Tag("demographic")
class DemographicChartScriptletEncodingUnitTest {
    private static final Path EDIT_VIEW_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit-view.jsp");
    private static final Path EDIT_FORM_CLINICAL_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/demographic/edit-form-clinical.jsp");

    @Test
    @DisplayName("should encode the stored values the view prints as text")
    void shouldEncodeStoredValues_onReadOnlyView() throws Exception {
        String jsp = Files.readString(EDIT_VIEW_JSP);

        for (String value : List.of("archivedDate", "archivedProgram", "privacyConsent", "informedConsent",
                "usSigned", "rd", "rdohip")) {
            assertThat(jsp).as(value)
                    .doesNotContainPattern("(?<!value=')<%=\\s*" + value + "\\s*%>")
                    .contains("<carlos:encode value='<%= " + value + " %>' context=\"html\"/>");
        }
    }

    @Test
    @DisplayName("should encode the stored values the edit form places in field values")
    void shouldEncodeStoredValues_inEditFormFieldValues() throws Exception {
        String jsp = Files.readString(EDIT_FORM_CLINICAL_JSP);

        for (String expression : List.of("privacyConsent", "informedConsent", "rd", "rdohip",
                "paperChartIndicatorDate", "paperChartIndicatorProgram",
                "StringUtils.trimToEmpty(demographic.getChartNo())",
                "StringUtils.trimToEmpty(demoExt.get(\"rxInteractionWarningLevel\"))",
                "StringUtils.defaultString(apptMainBean.getString(demoExt.get(\"usSigned\")))")) {
            assertThat(jsp).as(expression)
                    .doesNotContainPattern("value=\"<%=\\s*" + Pattern.quote(expression) + "\\s*%>\"")
                    .contains("value=\"<%=SafeEncode.forHtmlAttribute(" + expression + ")%>\"");
        }
        // The meditech id is printed twice, in the field and in its hidden original.
        assertThat(StringUtils.countMatches(jsp, "value=\"<%=SafeEncode.forHtmlAttribute(OtherIdManager.getDemoOtherId("))
                .isEqualTo(2);
        assertThat(jsp).doesNotContain("value=\"<%=OtherIdManager.getDemoOtherId(");
    }

    @Test
    @DisplayName("should encode custom-field values and match configured snippets literally")
    void shouldEncodeCustomFieldValues_inEditFormAttributes() throws Exception {
        String jsp = Files.readString(EDIT_FORM_CLINICAL_JSP);

        // A configured text snippet gets its stored value in value="", once for each column.
        assertThat(StringUtils.countMatches(jsp,
                ".replace(\"value=\\\"\\\"\", \"value=\\\"\" + SafeEncode.forHtmlAttribute(")).isEqualTo(2);
        // A configured select is matched as plain text, never as a pattern, once for each column.
        assertThat(jsp).doesNotContain("propDemoExtForm[k].replaceAll(")
                .doesNotContain("propDemoExtForm[k + 1].replaceAll(");
        Matcher select = Pattern.compile("propDemoExtForm\\[k( \\+ 1)?\\]\\.replace\\(\"value=\\\\\"\" \\+ "
                + "StringUtils\\.trimToEmpty\\(.*\\+ \" selected\"\\)\\);").matcher(jsp);
        int selectBranches = 0;
        while (select.find()) {
            selectBranches++;
        }
        assertThat(selectBranches).isEqualTo(2);
        assertThat(jsp)
                .contains("name=\"<%=SafeEncode.forHtmlAttribute(propDemoExt[k].replace(' ', '_'))%>Orig\"")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(StringUtils.trimToEmpty("
                        + "demoExt.get(propDemoExt[k].replace(' ', '_'))))%>\"")
                .doesNotContain("name=\"<%=propDemoExt[k].replace(' ', '_')%>Orig\"");
    }

    @Test
    @DisplayName("should encode provider, program, status and list names on both chart pages")
    void shouldEncodeNamesAndStatuses_onBothChartPages() throws Exception {
        String form = Files.readString(EDIT_FORM_CLINICAL_JSP);
        String view = Files.readString(EDIT_VIEW_JSP);

        assertThat(StringUtils.countMatches(form,
                "<option value=\"<%=SafeEncode.forHtmlAttribute(p.getProviderNo())%>\"")).isEqualTo(5);
        assertThat(StringUtils.countMatches(form,
                "<%=SafeEncode.forHtmlContent(p.getLastName() + \",\" + p.getFirstName())%>")).isEqualTo(5);
        assertThat(StringUtils.countMatches(form, ">><%=SafeEncode.forHtmlContent(status)%>")).isEqualTo(2);
        assertThat(StringUtils.countMatches(form, "<%=SafeEncode.forHtmlContent(_p.getName())%>")).isEqualTo(2);
        assertThat(form)
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(rosterStatus)%>\"")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(patientStatus)%>\"")
                .contains("<option value=\"<%=SafeEncode.forHtmlAttribute(llItem.getValue())%>\" <%=selected%>>"
                        + "<%=SafeEncode.forHtmlContent(llItem.getLabel())%>")
                .contains("<%=SafeEncode.forHtmlContent(wln.getName())%>")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(wlnote)%>\"")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute(wlReferralDate)%>\"")
                .doesNotContainPattern("(?<!\\()(p\\.getLastName\\(\\) \\+ \",\" \\+ p\\.getFirstName\\(\\))%>")
                .contains("value=\"${carlos:forHtmlAttribute(consentClearLabel)}\"")
                .doesNotContain("value=\"<fmt:message key='demographic.demographiceditdemographic.clear'/>\"")
                .doesNotContain(">><%=status%>")
                .doesNotContain("<%=_p.getName()%>");

        assertThat(StringUtils.countMatches(view, "<%=SafeEncode.forHtmlContent(providerBean.getProperty(")).isEqualTo(8);
        assertThat(view)
                .contains("<input type=\"hidden\" name=\"<%=SafeEncode.forHtmlAttribute(key)%>\"")
                .contains("<%=SafeEncode.forHtmlContent(enrolledTo)%>")
                .contains("<%=SafeEncode.forHtmlContent(hasPrimaryCarePhysician)%>")
                .contains("<%=SafeEncode.forHtmlContent(employmentStatus)%>")
                .contains("<%=SafeEncode.forHtmlContent(adm.getProgramName())%>")
                .doesNotContain("<%=providerBean.getProperty(")
                .doesNotContain("<%=enrolledTo %>");
    }

    @Test
    @DisplayName("should encode the referral doctor values the edit form writes into its script")
    void shouldEncodeReferralDoctorValues_forJavaScript() throws Exception {
        String form = Files.readString(EDIT_FORM_CLINICAL_JSP);

        assertThat(form)
                .contains("if (refName == \"<%=SafeEncode.forJavaScript("
                        + "prop.getProperty(\"last_name\")+\",\"+prop.getProperty(\"first_name\"))%>\") {")
                .contains("refNo = '<%=SafeEncode.forJavaScript(prop.getProperty(\"referral_no\", \"\"))%>';")
                .contains("value=\"<%=SafeEncode.forHtmlAttribute("
                        + "prop.getProperty(\"last_name\")+\",\"+prop.getProperty(\"first_name\"))%>\"")
                .doesNotContain("refName == \"<%=prop.getProperty(")
                .doesNotContain("refNo = '<%=prop.getProperty(");
    }
}
