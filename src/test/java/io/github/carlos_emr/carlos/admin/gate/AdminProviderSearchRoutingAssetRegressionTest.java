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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards provider-search admin routes that are otherwise only exercised through
 * JSP navigation.
 *
 * @since 2026-05-30
 */
@DisplayName("Admin provider search routing asset regressions")
@Tag("unit")
@Tag("admin")
class AdminProviderSearchRoutingAssetRegressionTest {

    private static final Path PROVIDER_SEARCH_RECORDS_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "admin", "providersearchrecordshtm.jsp");
    private static final Path STRUTS_ADMIN_XML =
            Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts-admin.xml");

    @Test
    @DisplayName("should submit provider search within the application context")
    void shouldSubmitProviderSearch_withinApplicationContext() throws IOException {
        String providerSearchRecords = Files.readString(PROVIDER_SEARCH_RECORDS_JSP, StandardCharsets.UTF_8);

        assertThat(providerSearchRecords)
                .contains("action=\"${pageContext.request.contextPath}/admin/ViewProviderSearchResults\"")
                .doesNotContain("action=\"/admin/ViewProviderSearchResults\"");
    }

    @Test
    @DisplayName("should map provider search results route to the protected results gate")
    void shouldMapProviderSearchResultsRoute_toProtectedResultsGate() throws IOException {
        String strutsAdmin = Files.readString(STRUTS_ADMIN_XML, StandardCharsets.UTF_8);

        assertThat(strutsAdmin)
                .contains("<action name=\"admin/ViewProviderSearchResults\" "
                        + "class=\"io.github.carlos_emr.carlos.admin.gate.ViewProviderSearchResults2Action\">")
                .contains("<result name=\"success\">/WEB-INF/jsp/admin/providersearchresults.jsp</result>");
    }
}
