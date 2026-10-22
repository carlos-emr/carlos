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
package io.github.carlos_emr.carlos.admin.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source regressions for the two pages that use document description templates (issue #5057): the
 * template page applies the gate's own rules instead of an administration-only check, and Incoming
 * Docs reports a failed shortcut read instead of leaving an empty row.
 */
@DisplayName("Document description template JSP regressions")
@Tag("unit")
@Tag("security")
class DocumentDescriptionTemplateJspRegressionTest {

    private static final Path TEMPLATE_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/displayDocumentDescriptionTemplate.jsp");
    private static final Path INCOMING_DOCS_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/documentManager/incomingDocs.jsp");
    private static final List<String> LOCALES = List.of("en", "fr", "es", "pt_BR", "pl");

    @Test
    @DisplayName("should gate the template page with the shared rules, not an administration-only check")
    void shouldGateTemplatePage_withSharedRules() throws IOException {
        String jsp = Files.readString(TEMPLATE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("DocumentDescriptionTemplateAccess.requireOpenPage(templateSecurity, templateUser, clinicList)")
                .contains("DocumentDescriptionTemplateAccess.isClinicListRequest(request)")
                .contains("DocumentDescriptionTemplateAccess.canEditClinicList(templateSecurity, templateUser)")
                .doesNotContain("objectName=\"_admin\" rights=\"r\" reverse");
    }

    @Test
    @DisplayName("should never offer edit controls to a reader of the clinic-wide list who cannot change it")
    void shouldHideEditControls_whenTemplatesNotEditable() throws IOException {
        String jsp = Files.readString(TEMPLATE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("var templatesEditable = <%= templatesEditable %>;")
                .contains("control.style.visibility = visible && templatesEditable ? 'visible' : 'hidden';")
                .doesNotContain("addDescription.style.visibility = 'visible'")
                .doesNotContain("updateDescription.style.visibility = 'visible'")
                .doesNotContain("deleteDescription.style.visibility = 'visible'");
    }

    @Test
    @DisplayName("should encode the document types the template page lists")
    void shouldEncodeDocumentTypes_inTemplatePage() throws IOException {
        String jsp = Files.readString(TEMPLATE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("<option value=\"<carlos:encode value='<%= docType %>' context=\"htmlAttribute\"/>\">"
                        + "<carlos:encode value='<%= docType %>'/>")
                .doesNotContain("<%= docType%>");
    }

    @Test
    @DisplayName("should report a failed shortcut read on Incoming Docs in every bundled language")
    void shouldReportFailedShortcutRead_inIncomingDocs() throws IOException {
        String jsp = Files.readString(INCOMING_DOCS_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("if (!response.ok || response.redirected) throw new Error('Description shortcuts request failed');")
                .contains("dms.incomingDocs.descriptionShortcutsUnavailable");
        for (String locale : LOCALES) {
            Properties bundle = new Properties();
            try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
                bundle.load(in);
            }
            assertThat(bundle.getProperty("dms.incomingDocs.descriptionShortcutsUnavailable"))
                    .as("oscarResources_%s", locale)
                    .isNotBlank();
        }
    }
}
