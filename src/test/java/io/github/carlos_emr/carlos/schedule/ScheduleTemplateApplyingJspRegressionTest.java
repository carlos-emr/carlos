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
package io.github.carlos_emr.carlos.schedule;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks schedule template UI deletes to the CSRF-protected rendered form.
 *
 * @since 2026-05-19
 */
@DisplayName("Schedule template applying JSP regressions")
@Tag("unit")
@Tag("schedule")
class ScheduleTemplateApplyingJspRegressionTest {

    private static final Path TEMPLATE_APPLYING_JSP =
            Path.of("src/main/webapp/WEB-INF/jsp/schedule/scheduletemplateapplying.jsp");

    @Test
    @DisplayName("delete should reuse the rendered POST form so CSRFGuard can include its token")
    void shouldReuseRenderedPostForm_forDelete() throws IOException {
        String jsp = Files.readString(TEMPLATE_APPLYING_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<form method=\"post\" name=\"schedule\"");
        assertThat(jsp).contains("var form = document.forms['schedule'];");
        assertThat(jsp).contains("form.action = \"${pageContext.request.contextPath}/schedule/TemplateApplying\";");
        assertThat(jsp).contains("setFormValue(form, 'delete', '1');");
        assertThat(jsp).doesNotContain("var form = document.createElement('form');");
    }

    @Test
    @DisplayName("page should use the current request locale for the HTML language")
    void shouldUseRequestLocale_forHtmlLanguage() throws IOException {
        String jsp = Files.readString(TEMPLATE_APPLYING_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<html lang=\"${pageContext.request.locale.language}\">");
        assertThat(jsp).doesNotContain("<html lang=\"en\">");
    }
}
