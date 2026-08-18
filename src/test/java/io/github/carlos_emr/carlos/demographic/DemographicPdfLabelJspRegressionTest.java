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

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("title=\"<carlos:encode value='<%=d.getDemographicNo()%>' context=\"htmlAttribute\"/>\"")
                .contains("<carlos:encode value='<%=d.getLastName()%>' context=\"html\"/>")
                .contains("<carlos:encode value='<%=d.getAddress()%>' context=\"html\"/>")
                .contains("<carlos:encode value='<%=d.getHin()%>' context=\"html\"/>")
                .contains("value=\"<carlos:encode value='<%=prop.getProperty(\"last_name\")+\",\"+prop.getProperty(\"first_name\")%>' context=\"htmlAttribute\"/>\"")
                .contains("context=\"javaScriptBlock\"")
                .contains("<carlos:encode value='<%=alert%>' context=\"html\"/>")
                .contains("<carlos:encode value='<%=notes%>' context=\"html\"/>");

        for (String rawSink : List.of(
                "<b>Record</b> (<%=d.getDemographicNo()%>) <%=d.getLastName()%>,",
                "title='<%=d.getDemographicNo()%>'><b><fmt:message key=\"demographic.demographiceditdemographic.formLastName\"/>: </b><%=d.getLastName()%>",
                "<td align=\"left\"><%=d.getFirstName()%>",
                "<td align=\"left\"><b><fmt:message key=\"demographic.demographiceditdemographic.formAddr\"/>: </b> <%=d.getAddress()%>",
                "<td align=\"left\"><b><fmt:message key=\"demographic.demographiceditdemographic.formHin\"/>: </b><%=d.getHin()%>",
                "<b><fmt:message key=\"demographic.demographiceditdemographic.formVer\"/></b> <%=d.getVer()%>",
                "<td align=\"left\"><%=d.getChartNo()%>",
                "<td><%=alert%>",
                "<td><%=notes%>",
                "if (refName == \"<%=prop.getProperty(\"last_name\")+\",\"+prop.getProperty(\"first_name\")%>\") {",
                "refNo = '<%=prop.getProperty(\"referral_no\", \"\")%>';")) {
            assertThat(jsp)
                    .as(rawSink)
                    .doesNotContain(rawSink);
        }
    }
}
