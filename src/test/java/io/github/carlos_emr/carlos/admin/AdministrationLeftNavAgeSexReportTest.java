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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the administration left navigation routes the Age-Sex Report through
 * the Struts action that replaced the legacy public JSP controller.
 *
 * @since 2026-05-03
 */
@DisplayName("administration left nav age sex report tests")
@Tag("unit")
@Tag("fast")
@Tag("admin")
class AdministrationLeftNavAgeSexReportTest {

    private static final Path LEFT_NAV =
            Path.of("src/main/webapp/WEB-INF/jsp/administration/leftNav.jspf");

    @Test
    @DisplayName("should post age sex report to Struts action")
    void shouldPostAgeSexReport_toStrutsAction() throws Exception {
        String jsp = Files.readString(LEFT_NAV);

        assertThat(jsp)
                .as("Age-Sex Report must no longer load the removed public JSP controller")
                .doesNotContain("/oscarReport/dbReportAgeSex.jsp");
        assertThat(jsp)
                .as("Age-Sex Report link should target the migrated Struts action")
                .contains("rel=\"${carlos:forHtmlAttribute(ctx)}/oscarReport/DbReportAgeSex\"");
        assertThat(jsp)
                .as("Age-Sex Report must submit by POST so DbReportAgeSex2Action accepts the request")
                .contains("method=\"post\" action=\"${carlos:forHtmlAttribute(ctx)}/oscarReport/DbReportAgeSex\"");
        assertThat(jsp)
                .as("xlink handler should submit the hidden form into the iframe")
                .contains("data-submit-form=\"ageSexForm\"")
                .contains("form.submit();")
                .contains("title=\"Administration content\"");
    }
}
