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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.documentManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for stored-XSS encoding in the HTML document editor JSP.
 *
 * @since 2026-06-22
 */
@DisplayName("Add/edit HTML document JSP encoding")
@Tag("unit")
@Tag("document")
@Tag("security")
class AddEditHtmlDocumentJspRegressionTest {
    private static final Path ADD_EDIT_HTML_DOCUMENT_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/documentManager/addedithtmldocument.jsp");

    @Test
    @DisplayName("should encode issue 2315 fields in their rendering contexts")
    void shouldEncodeIssue2315Fields_inRenderingContexts() throws IOException {
        String jsp = Files.readString(ADD_EDIT_HTML_DOCUMENT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("\"<carlos:encode value='<%= subClasses.get(i) %>' context='javaScript'/>\"")
                .contains("<td><carlos:encode value='<%= EDocUtil.getProviderName(formdata.getDocCreator()) %>' context='html'/>")
                .contains("value=\"<carlos:encode value='<%= formdata.getSource() %>' context='htmlAttribute'/>\"")
                .contains("value=\"<carlos:encode value='<%= formdata.getSourceFacility() %>' context='htmlAttribute'/>\"")
                .contains("Reviewed: &nbsp; <carlos:encode value='<%= EDocUtil.getProviderName(formdata.getReviewerId()) %>' context='html'/>")
                .doesNotContain("\"<%=subClasses.get(i)%>\"")
                .doesNotContain("\"<carlos:encode value='<%= subClasses.get(i) %>' context='javaScriptBlock'/>\"")
                .doesNotContain("<td><%=EDocUtil.getProviderName(formdata.getDocCreator())%>")
                .doesNotContain("value=\"<%=formdata.getSource()%>\"")
                .doesNotContain("value=\"<%=formdata.getSourceFacility()%>\"")
                .doesNotContain("Reviewed: &nbsp; <%=EDocUtil.getProviderName(formdata.getReviewerId())%>");
    }
}
