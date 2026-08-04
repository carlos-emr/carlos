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
package io.github.carlos_emr.carlos.admin.gate;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the context-relative provider-search route and its protected Struts mapping.
 *
 * @since 2026-07-29
 */
@DisplayName("Admin provider search routing regressions")
@Tag("unit")
@Tag("admin")
class AdminProviderSearchRoutingRegressionTest {

    private static final File WEBAPP_ROOT = PathValidationUtils.resolveTrustedPath(
            new File("src/main/webapp"), "test webapp root");
    private static final File PROVIDER_SEARCH_RECORDS_JSP =
            validatedWebappFile("WEB-INF/jsp/admin/providersearchrecordshtm.jsp");
    private static final File STRUTS_ADMIN_XML =
            validatedWebappFile("WEB-INF/classes/struts-admin.xml");

    private static File validatedWebappFile(String relativePath) {
        return PathValidationUtils.validateExistingPath(
                new File(WEBAPP_ROOT, relativePath), WEBAPP_ROOT);
    }

    private static String readWebappFile(File file) throws IOException {
        File validatedFile = PathValidationUtils.validateExistingPath(file, WEBAPP_ROOT);
        return Files.readString(validatedFile.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("should submit provider search within the application context")
    void shouldSubmitProviderSearch_withinApplicationContext() throws IOException {
        String providerSearchRecords = readWebappFile(PROVIDER_SEARCH_RECORDS_JSP);

        assertThat(providerSearchRecords)
                .contains("action=\"${pageContext.request.contextPath}/admin/ViewProviderSearchResults\"")
                .doesNotContain("action=\"/admin/ViewProviderSearchResults\"");
    }

    @Test
    @DisplayName("should load all matching providers for client-side pagination")
    void shouldLoadAllMatchingProviders_forClientSidePagination() throws IOException {
        String providerSearchRecords = readWebappFile(PROVIDER_SEARCH_RECORDS_JSP);

        assertThat(providerSearchRecords)
                .contains("<INPUT TYPE=\"hidden\" NAME=\"limit2\" VALUE=\"10000\">")
                .doesNotContain("<INPUT TYPE=\"hidden\" NAME=\"limit2\" VALUE=\"10\">");
    }

    @Test
    @DisplayName("should map provider search results to the protected action")
    void shouldMapProviderSearchResults_toProtectedAction() throws IOException {
        String strutsAdmin = readWebappFile(STRUTS_ADMIN_XML);

        assertThat(strutsAdmin)
                .contains("<action name=\"admin/ViewProviderSearchResults\" "
                        + "class=\"io.github.carlos_emr.carlos.admin.gate.ViewProviderSearchResults2Action\">")
                .contains("<result name=\"success\">/WEB-INF/jsp/admin/providersearchresults.jsp</result>");
    }
}
