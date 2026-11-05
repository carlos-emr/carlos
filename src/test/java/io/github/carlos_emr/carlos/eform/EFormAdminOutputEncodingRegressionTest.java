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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the output encoding of administrator-entered names on the eForm administration screens
 * (issue #4184, finding 121). Role names, patient-independent instance names and subjects, and
 * eForm group names are stored text; printed raw, a stored name carrying markup ran as HTML on the
 * administration pages. The behavioural proof is the installed {@code xss-poison-eform} browser
 * check; these source contracts keep a raw print from coming back between package runs.
 *
 * @since 2026-10-19
 */
@DisplayName("eForm administration output encoding")
@Tag("unit")
@Tag("eform")
@Tag("security")
class EFormAdminOutputEncodingRegressionTest {

    private static final String JSP_DIR = "src/main/webapp/WEB-INF/jsp/eform/";

    private static String read(String relative) throws IOException {
        return Files.readString(Path.of(JSP_DIR + relative), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should encode role names in the upload form's role select for attribute and body")
    void shouldEncodeRoleNames_inUploadRoleSelect() throws IOException {
        String upload = read("partials/upload.jsp");

        assertThat(upload)
                .doesNotContain("<%=roleList.get(i) %>")
                .contains("<option value=\"<%= io.github.carlos_emr.carlos.utility.SafeEncode"
                        + ".forHtmlAttribute(roleList.get(i)) %>\">")
                .contains("SafeEncode.forHtmlContent(roleList.get(i))");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"efmmanageindependent.jsp", "efmmanageindependentdeleted.jsp"})
    @DisplayName("should encode patient-independent instance names and subjects")
    void shouldEncodeIndependentInstanceNames_forCurrentAndDeletedLists(String jsp) throws IOException {
        String page = read(jsp);

        assertThat(page)
                .doesNotContain("<%=curform.get(\"formName\")%>")
                .doesNotContain("<%=curform.get(\"formSubject\")%>")
                .contains("SafeEncode.forHtmlContent((String) curform.get(\"formName\"))")
                .contains("SafeEncode.forHtmlContent((String) curform.get(\"formSubject\"))");
    }

    @Test
    @DisplayName("should HTML-encode the group name before attribute-encoding it into the delete confirmation")
    void shouldDoubleEncodeGroupName_inDeleteConfirmation() throws IOException {
        // efmFooter.jspf renders data-confirm with .html(): the browser decodes the attribute first,
        // so attribute encoding alone hands .html() the raw stored name.
        String groups = read("efmmanageformgroups.jsp");
        String footer = read("efmFooter.jspf");

        assertThat(footer).contains("$('#confirmModalBody').html($(this).attr('data-confirm'));");
        assertThat(groups)
                .contains("delete group: <strong><carlos:encode value='<%= io.github.carlos_emr.carlos.utility"
                        + ".SafeEncode.forHtmlContent(groupName) %>' context=\"htmlAttribute\"/></strong>")
                .doesNotContain("delete group: <strong><carlos:encode value='<%= groupName %>'");
    }
}
