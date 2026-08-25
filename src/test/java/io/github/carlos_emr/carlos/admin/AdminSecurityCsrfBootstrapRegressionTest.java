/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Locks admin security JSP mutation pages onto the canonical CSRF token bootstrap.
 *
 * @since 2026-05-31
 */
@DisplayName("Admin security CSRF bootstrap regressions")
@Tag("unit")
@Tag("security")
@Tag("admin")
class AdminSecurityCsrfBootstrapRegressionTest {

    private static final String CSRF_BOOTSTRAP_INCLUDE = "<%@ include file=\"/WEB-INF/jspf/csrf-token.jspf\" %>";
    private static final String BODY_SCOPED_CSRF_QUERY =
            "var csrfEl = document.querySelector('body > input[name=\"CSRF-TOKEN\"]');";
    private static final String DOCUMENT_SCOPED_CSRF_QUERY =
            "var csrfEl = document.querySelector('input[name=\"CSRF-TOKEN\"]');";
    private static final Path SECURITY_UPDATE_SECURITY_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/admin/securityupdatesecurity.jsp");
    private static final List<Path> ADMIN_SECURITY_JSPS = List.of(
            SECURITY_UPDATE_SECURITY_JSP,
            Path.of("src/main/webapp/WEB-INF/jsp/admin/securityaddarecord.jsp"),
            Path.of("src/main/webapp/WEB-INF/jsp/admin/securityupdate.jsp"));

    @Test
    @DisplayName("admin security mutation pages should include canonical CSRF bootstrap")
    void shouldIncludeCsrfBootstrap_forAllAdminSecurityMutationPages() throws Exception {
        for (Path jspPath : ADMIN_SECURITY_JSPS) {
            String jsp = Files.readString(jspPath);

            assertThat(jsp)
                    .as(jspPath + " should include the canonical CSRF bootstrap exactly once")
                    .contains(CSRF_BOOTSTRAP_INCLUDE);
            assertThat(countOccurrences(jsp, CSRF_BOOTSTRAP_INCLUDE))
                    .as(jspPath + " should not include duplicate CSRF bootstraps")
                    .isEqualTo(1);
            assertThat(jsp.indexOf(CSRF_BOOTSTRAP_INCLUDE))
                    .as(jspPath + " should place CSRF bootstrap inside body")
                    .isGreaterThan(jsp.indexOf("<body"))
                    .isLessThan(jsp.indexOf("</body>"));
        }
    }

    @Test
    @DisplayName("MFA reset should read the canonical bootstrap CSRF token")
    void shouldQueryBodyScopedCsrfToken_forMfaReset() throws Exception {
        String jsp = Files.readString(SECURITY_UPDATE_SECURITY_JSP);

        assertThat(jsp)
                .contains(BODY_SCOPED_CSRF_QUERY)
                .doesNotContain(DOCUMENT_SCOPED_CSRF_QUERY);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = text.indexOf(needle);
        while (index >= 0) {
            count++;
            index = text.indexOf(needle, index + needle.length());
        }
        return count;
    }
}
