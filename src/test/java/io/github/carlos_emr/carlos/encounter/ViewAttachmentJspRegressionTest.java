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
package io.github.carlos_emr.carlos.encounter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for encounter attachment message rendering.
 *
 * @since 2026-05-29
 */
@DisplayName("Encounter attachment JSP regressions")
@Tag("unit")
@Tag("encounter")
@Tag("security")
class ViewAttachmentJspRegressionTest {

    private static final Path VIEW_ATTACHMENT_JSP = projectRoot()
            .resolve("src/main/webapp/WEB-INF/jsp/encounter/ViewAttachment.jsp");

    @Test
    @DisplayName("should encode rendered message fields in HTML body context")
    void shouldEncodeRenderedMessageFields_inHtmlBodyContext() throws IOException {
        String jsp = Files.readString(VIEW_ATTACHMENT_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>")
                .contains("<carlos:encode value='<%= sentBy %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= remoteName %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= thesubject %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= thedate %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= theime %>' context=\"html\"/>")
                .contains("<carlos:encode value='<%= themessage %>' context=\"html\"/>")
                .doesNotContain("<%= sentBy%>")
                .doesNotContain("<%=remoteName%>")
                .doesNotContain("<%= thesubject%>")
                .doesNotContain("<%= thedate %>&nbsp;&nbsp; <%= theime %>")
                .doesNotContain("<%=themessage%>");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir")));
    }
}
