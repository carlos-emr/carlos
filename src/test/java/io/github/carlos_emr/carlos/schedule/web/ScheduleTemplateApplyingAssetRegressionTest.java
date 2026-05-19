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
package io.github.carlos_emr.carlos.schedule.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the schedule template applying JSP serializer against regressions that
 * submit checked days without the selected template code.
 *
 * @since 2026-05-19
 */
@DisplayName("Schedule template applying asset regressions")
@Tag("unit")
@Tag("web")
@Tag("schedule")
class ScheduleTemplateApplyingAssetRegressionTest {

    private static final Path TEMPLATE_APPLYING_JSP =
            Path.of("src", "main", "webapp", "WEB-INF", "jsp", "schedule", "scheduletemplateapplying.jsp");

    @Test
    @DisplayName("should use selected template for checked days without per-day overrides")
    void shouldUseSelectedTemplate_forCheckedDaysWithoutPerDayOverrides() throws IOException {
        String jsp = Files.readString(TEMPLATE_APPLYING_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("function valueOrSelectedTemplate(field)");
        assertThat(jsp).contains("return field.value || selectedTemplateValue();");

        assertTemplateFallback(jsp, "SUN", "sunfrom1");
        assertTemplateFallback(jsp, "MON", "monfrom1");
        assertTemplateFallback(jsp, "TUE", "tuefrom1");
        assertTemplateFallback(jsp, "WED", "wedfrom1");
        assertTemplateFallback(jsp, "THU", "thufrom1");
        assertTemplateFallback(jsp, "FRI", "frifrom1");
        assertTemplateFallback(jsp, "SAT", "satfrom1");
        assertTemplateFallback(jsp, "SUN", "sunfrom2");
        assertTemplateFallback(jsp, "MON", "monfrom2");
        assertTemplateFallback(jsp, "TUE", "tuefrom2");
        assertTemplateFallback(jsp, "WED", "wedfrom2");
        assertTemplateFallback(jsp, "THU", "thufrom2");
        assertTemplateFallback(jsp, "FRI", "frifrom2");
        assertTemplateFallback(jsp, "SAT", "satfrom2");
    }

    private void assertTemplateFallback(String jsp, String dayTag, String fieldName) {
        assertThat(jsp).contains("<" + dayTag + ">\" + valueOrSelectedTemplate(document.schedule."
                + fieldName + ") + \"</" + dayTag + ">");
    }
}
