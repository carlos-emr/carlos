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
import java.util.regex.Pattern;

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
            projectRoot().resolve("src/main/webapp/WEB-INF/jsp/schedule/scheduletemplateapplying.jsp");
    private static final Pattern POST_SCHEDULE_FORM = Pattern.compile(
            "<form\\s+method=\"post\"\\s+name=\"schedule\"",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("delete should reuse the rendered POST form so CSRFGuard can include its token")
    void shouldReuseRenderedPostForm_forDelete() throws IOException {
        String jsp = Files.readString(TEMPLATE_APPLYING_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern(POST_SCHEDULE_FORM);
        assertThat(jsp).contains("var form = document.forms['schedule'];");
        assertThat(jsp).containsPattern("if \\(!form\\) \\{\\s+return;\\s+\\}");
        assertThat(jsp).contains("form.method = 'post';");
        assertThat(jsp).containsPattern("form\\.action\\s*=\\s*\"\\$\\{pageContext\\.request\\.contextPath}/schedule/TemplateApplying\";");
        assertThat(jsp).contains("setFormValue(form, 'provider_no', '<%= providerNoForJavaScript %>');");
        assertThat(jsp).contains("setFormValue(form, 'provider_name', '<%= providerNameForJavaScript %>');");
        assertThat(jsp).contains("setFormValue(form, 'delete', '1');");
        assertThat(jsp).contains("setFormValue(form, 'deldate', 'all');");
        assertThat(jsp).doesNotContain("var form = document.createElement('form');");
    }

    @Test
    @DisplayName("delete helper should update existing named controls before appending hidden inputs")
    void shouldUpdateExistingNamedControls_forDeleteFields() throws IOException {
        String jsp = Files.readString(TEMPLATE_APPLYING_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern("Array\\.prototype\\.slice\\.call\\(form\\.elements\\)");
        assertThat(jsp).containsPattern("return control\\.name === name;");
        assertThat(jsp).containsPattern("controls\\[0]\\.value = value;");
        assertThat(jsp).containsPattern("if \\(controls\\[i]\\.type === 'hidden'\\)");
    }

    @Test
    @DisplayName("page should use the current request locale for the HTML language")
    void shouldUseRequestLocale_forHtmlLanguage() throws IOException {
        String jsp = Files.readString(TEMPLATE_APPLYING_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).containsPattern(
                "<html\\s+lang=\"<%=\\s*SafeEncode\\.forHtmlAttribute\\(request\\.getLocale\\(\\)\\.toLanguageTag\\(\\)\\)\\s*%>\"");
        assertThat(jsp).doesNotContain("${pageContext.request.locale.language}");
        assertThat(jsp).doesNotContain("<html lang=\"en\">");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir")));
    }
}
