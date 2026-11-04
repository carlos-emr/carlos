/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.admin;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.log.LogConst;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("logReport.jsp content filter")
@Tag("unit")
class LogReportJspRegressionTest {

    @Test
    @DisplayName("should offer the three AI content values with bundle keys defined in every locale")
    void shouldOfferAiContentOptions_withBundleKeys() throws IOException {
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/logReport.jsp"), StandardCharsets.UTF_8);
        String[][] options = {
                {LogConst.CON_AI_CHART_UPDATE, "admin.logReport.content.aiChartUpdate"},
                {LogConst.CON_AI_CLINICAL_SUMMARY, "admin.logReport.content.aiClinicalSummary"},
                {LogConst.CON_AI_DOCUMENT_SUMMARY, "admin.logReport.content.aiDocumentSummary"}};
        Properties english = bundle("en");
        for (String[] option : options) {
            assertThat(jsp).contains("<option value=\"" + option[0] + "\"><fmt:message key=\"" + option[1] + "\"/></option>");
            for (String locale : new String[] {"en", "fr", "es", "pl", "pt_BR"}) {
                String value = bundle(locale).getProperty(option[1]);
                assertThat(value).as("%s in %s", option[1], locale).isNotBlank();
                if (!"en".equals(locale)) assertThat(value).isNotEqualTo(english.getProperty(option[1]));
            }
        }
        assertThat(jsp).contains("<option value=\"admin\">", "<option value=\"login\">");
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
