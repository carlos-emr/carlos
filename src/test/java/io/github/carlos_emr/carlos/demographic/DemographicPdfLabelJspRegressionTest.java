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
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for patient-data encoding in the demographic PDF label JSP.
 *
 * @since 2026-05-29
 */
@DisplayName("Demographic PDF label JSP regressions")
@Tag("unit")
@Tag("demographic")
@Tag("security")
class DemographicPdfLabelJspRegressionTest {

    private static final Path JSP = Path.of("src/main/webapp/WEB-INF/jsp/demographic/demographicpdflabel.jsp");

    @Test
    @DisplayName("should encode demographic values in HTML, attribute, and JavaScript contexts")
    void shouldEncodeDemographicValues_inHtmlAttributeAndJavaScriptContexts() throws IOException {
        String jsp = Files.readString(JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>");

        for (String expression : List.of(
                "d.getLastName()",
                "d.getAddress()",
                "d.getHin()",
                "alert",
                "notes")) {
            assertEncodes(jsp, expression, "html");
        }
        assertEncodes(jsp, "d.getDemographicNo()", "htmlAttribute");
        assertEncodes(jsp, "referralDisplayName", "htmlAttribute");
        assertEncodes(jsp, "referralDisplayName", "javaScriptBlock");
        assertEncodes(jsp, "referralNo", "javaScriptBlock");

        for (String rawSink : List.of(
                "<b>Record</b> (<%=d.getDemographicNo()%>) <%=d.getLastName()%>,",
                "title='<%=d.getDemographicNo()%>'",
                "<td align=\"left\"><%=d.getFirstName()%>",
                "<td align=\"left\"><b><fmt:message key=\"demographic.demographiceditdemographic.formAddr\"/>: </b> <%=d.getAddress()%>",
                "<td align=\"left\"><b><fmt:message key=\"demographic.demographiceditdemographic.formHin\"/>: </b><%=d.getHin()%>",
                "<b><fmt:message key=\"demographic.demographiceditdemographic.formVer\"/></b> <%=d.getVer()%>",
                "<td align=\"left\"><%=d.getChartNo()%>",
                "<td><%=alert%>",
                "<td><%=notes%>",
                "if (refName == \"",
                "if (refName === \"<%=prop.getProperty",
                "refNo = \"<%=prop.getProperty")) {
            assertThat(jsp)
                    .as(rawSink)
                    .doesNotContain(rawSink);
        }
    }

    private static void assertEncodes(String jsp, String expression, String context) {
        String pattern = "<carlos:encode\\s+value=([\"'])<%=\\s*"
                + Pattern.quote(expression)
                + "\\s*%>\\1\\s+context=([\"'])"
                + Pattern.quote(context)
                + "\\2\\s*/>";
        assertThat(jsp)
                .as("%s encoded with %s", expression, context)
                .containsPattern(pattern);
    }
}
