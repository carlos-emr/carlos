/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.rx;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two Rx page regressions: the drug profile's Del said nothing when the delete was refused or failed, and the
 * static script compared the formatted pick-up time with the locale-dependent text "12:00 AM".
 */
@DisplayName("Rx Del refusal message and static script pick-up time")
@Tag("unit")
@Tag("rx")
@Tag("regression")
class RxDeleteAndPickupTimeJspRegressionTest {

    private static final Path SEARCH_DRUG_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp");
    private static final Path STATIC_SCRIPT_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/StaticScript2.jsp");
    private static final Path RX_RECORD_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/DisplayRxRecord.jsp");
    private static final String KEY = "SearchDrug.js.deleteRefused";

    @Test
    @DisplayName("should tell the prescriber when the Del request is refused or fails")
    void shouldReportRefusedDelete_whenDeleteRequestFails() throws IOException {
        String source = Files.readString(SEARCH_DRUG_JSP, StandardCharsets.UTF_8);
        int start = source.indexOf("function Delete2(element){");
        assertThat(start).isPositive();
        String delete2 = source.substring(start, source.indexOf("return false;", start));

        assertThat(delete2).contains("\"/rx/deleteRx?parameterValue=Delete2\"").contains("onFailure: reportRefusedDelete");
        assertThat(source).contains("<fmt:message key=\"" + KEY + "\"             var=\"msg_deleteRefused\"/>");
        assertThat(source).contains("deleteRefused: '${carlos:forJavaScript(msg_deleteRefused)}',");
        assertThat(source).containsPattern("function reportRefusedDelete\\(\\) \\{\\s*alert\\(jsMsg\\.deleteRefused\\);\\s*\\}");
    }

    @Test
    @DisplayName("should not compare the formatted pick-up time with locale-dependent text")
    void shouldNotCompareFormattedPickupTime_withLocaleText() throws IOException {
        String source = Files.readString(STATIC_SCRIPT_JSP, StandardCharsets.UTF_8);

        assertThat(source).doesNotContain("\"12:00 AM\"");
        assertThat(source).contains("if (!((drug.pickupTime).equals(\"\"))) {");
    }

    @Test
    @DisplayName("should show a midnight pick-up on the drug record as the date alone, by the same rule")
    void shouldShowMidnightPickupAsDateOnly_onDrugRecord() throws IOException {
        String source = Files.readString(RX_RECORD_JSP, StandardCharsets.UTF_8);

        assertThat(source).contains("SimpleDateFormat dateOnlyFormatter = new SimpleDateFormat(\"yyyy-MM-dd\");");
        assertThat(source).contains("(StaticScriptBean.hasPickupTime(drug.getPickUpDateTime()) ? dateTimeFormatter : dateOnlyFormatter)"
                + ".format(drug.getPickUpDateTime())");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pt_BR", "pl"})
    @DisplayName("should translate the Del refusal message in every shipped locale")
    void shouldTranslateDeleteRefusedMessage_forEveryLocale(String locale) throws IOException {
        String english = bundle("en").getProperty(KEY);
        String value = bundle(locale).getProperty(KEY);

        assertThat(english).isEqualTo("The prescription could not be deleted. It is still on the patient's profile. Please try again.");
        assertThat(value).isNotBlank();
        if (!"en".equals(locale)) {
            assertThat(value).isNotEqualTo(english);
        }
    }

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        // The bundles are plain ASCII with Unicode escapes (OscarResourcesBundleParseTest pins that).
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            properties.load(in);
        }
        return properties;
    }
}
