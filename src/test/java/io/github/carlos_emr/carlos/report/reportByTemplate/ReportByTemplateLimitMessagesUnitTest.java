/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #5849: each Report by Template limit refusal, and the note for a held-back CSV, is shown in the user's
 * language (D262) with the limit's value.
 */
@DisplayName("Report by Template limit messages")
@Tag("unit")
@Tag("report")
class ReportByTemplateLimitMessagesUnitTest {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "oscarReport", "reportByTemplate");
    private static final String TRANSLATED_REFUSAL =
            "<fmt:message key=\"${errormsgKey}\"><fmt:param value=\"${errormsgArg}\"/></fmt:message>";

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (ReportResultBudget.Limit limit : ReportResultBudget.Limit.values()) {
            keys.add(limit.key());
        }
        keys.add("report.byTemplate.csvWithheld");
        return keys;
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    void shouldHaveEveryLimitMessage_inEveryBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }
        for (String key : keys()) {
            String text = bundle.getProperty(key);
            assertThat(text).as("%s in %s", key, locale).isNotBlank();
            // These are formatted with a parameter, so MessageFormat would take a single quote as the start
            // of a quoted section and drop the rest of the sentence.
            assertThat(text).as("%s in %s", key, locale).doesNotContain("'").containsOnlyOnce("{0}");
            assertThat(new MessageFormat(text, Locale.ROOT).format(new Object[]{256})).contains("256").doesNotContain("{0}");
        }
    }

    @Test
    void shouldShowTheTranslatedRefusal_onBothReportPages() throws IOException {
        String configuration = Files.readString(JSP.resolve("reportConfiguration.jsp"), StandardCharsets.UTF_8);
        String result = Files.readString(JSP.resolve("resultReport.jsp"), StandardCharsets.UTF_8);
        assertThat(configuration).contains(TRANSLATED_REFUSAL).contains("${carlos:forHtml(errormsg)}");
        assertThat(result).contains(TRANSLATED_REFUSAL)
                .contains("<%@ taglib uri=\"jakarta.tags.fmt\" prefix=\"fmt\" %>")
                .contains("<fmt:setBundle basename=\"oscarResources\"/>")
                .containsSubsequence("${csvWithheld}", "<fmt:message key=\"report.byTemplate.csvWithheld\"><fmt:param value=\"${csvWithheldMb}\"/></fmt:message>");
    }
}
