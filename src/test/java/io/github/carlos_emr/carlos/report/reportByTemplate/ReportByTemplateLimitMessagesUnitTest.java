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
 * #5849: each Report by Template limit refusal, the note for a held-back CSV and the query label of a sequence
 * are shown in the user's language (D262) with their value.
 */
@DisplayName("Report by Template limit messages")
@Tag("unit")
@Tag("report")
class ReportByTemplateLimitMessagesUnitTest {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "oscarReport", "reportByTemplate");
    private static final String TRANSLATED_REFUSAL =
            "<fmt:message key=\"${errormsgKey}\"><fmt:param value=\"${errormsgArg}\"/></fmt:message>";

    /** Follows the refusal while the limit is below its maximum, naming the carlos.properties setting to change. */
    private static final String ADMINISTRATOR_CAN_RAISE = "<c:if test=\"${not empty errormsgSetting}\"> "
            + "<fmt:message key=\"report.limit.administratorCanRaise\"><fmt:param value=\"${errormsgSetting}\"/></fmt:message></c:if>";

    private static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (ReportResultBudget.Limit limit : ReportResultBudget.Limit.values()) {
            keys.add(limit.key());
        }
        keys.add("report.byTemplate.csvWithheld");
        keys.add("report.byTemplate.queryNumber");
        // The sentence added while an administrator can still raise the limit; its parameter is the setting's name.
        keys.add(ReportResultBudget.ADMINISTRATOR_CAN_RAISE_KEY);
        // The configured (form, antenatal and clinical) report limits, shown on the error page.
        for (io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.Limit limit
                : io.github.carlos_emr.carlos.report.data.ConfiguredReportQuery.Limit.values()) {
            keys.add(limit.messageKey());
        }
        return keys;
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should have every limit message, with its value, in every bundle")
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
    @DisplayName("should show the translated refusal on both report pages, and each held-back note on its query")
    void shouldShowTheTranslatedRefusal_onBothReportPages() throws IOException {
        String configuration = Files.readString(JSP.resolve("reportConfiguration.jsp"), StandardCharsets.UTF_8);
        String result = Files.readString(JSP.resolve("resultReport.jsp"), StandardCharsets.UTF_8);
        assertThat(configuration).contains(TRANSLATED_REFUSAL + ADMINISTRATOR_CAN_RAISE).contains("${carlos:forHtml(errormsg)}");
        assertThat(result).contains(TRANSLATED_REFUSAL + ADMINISTRATOR_CAN_RAISE)
                .contains("<%@ taglib uri=\"jakarta.tags.fmt\" prefix=\"fmt\" %>")
                .contains("<fmt:setBundle basename=\"oscarResources\"/>")
                // The note sits inside the loop over the results, flagged per query (csvWithheld-N in a sequence).
                .containsSubsequence("request.getAttribute(\"csvWithheld-\" + x)", "request.getAttribute(\"csvWithheld\")",
                        "<c:forEach items=\"${ htmlList }\" var=\"htmlOut\" varStatus=\"query\">",
                        "<c:if test=\"${csvWithheldList[query.index]}\">",
                        "<fmt:message key=\"report.byTemplate.queryNumber\"><fmt:param value=\"${query.count}\"/></fmt:message>",
                        "<fmt:message key=\"report.byTemplate.csvWithheld\"><fmt:param value=\"${csvWithheldMb}\"/></fmt:message>",
                        "</c:forEach>",
                        // Every export form of a sequence names its query.
                        "<fmt:message key=\"report.byTemplate.queryNumber\"><fmt:param value=\"${queryNumber}\"/></fmt:message>",
                        "name=\"getCSV\"")
                .doesNotContain("<%if (x > 1) { %>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should have the run-again page for a result that is no longer kept, in every bundle")
    void shouldHaveRunAgainMessages_inEveryBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }
        for (String key : List.of("report.byTemplate.resultUnavailableTitle", "report.byTemplate.resultUnavailable",
                "report.byTemplate.runAgain")) {
            assertThat(bundle.getProperty(key)).as("%s in %s", key, locale).isNotBlank().doesNotContain("{0}");
        }
        String unavailable = Files.readString(JSP.resolve("resultUnavailable.jsp"), StandardCharsets.UTF_8);
        assertThat(unavailable).contains("<fmt:message key=\"report.byTemplate.resultUnavailable\"/>",
                "<fmt:message key=\"report.byTemplate.runAgain\"/>", "<fmt:setBundle basename=\"oscarResources\"/>");
    }

    @Test
    @DisplayName("should give the result page only the kept result's token, never its CSV")
    void shouldCarryOnlyTheResultToken_onTheResultPage() throws IOException {
        String result = Files.readString(JSP.resolve("resultReport.jsp"), StandardCharsets.UTF_8);
        assertThat(result)
                .doesNotContain("name=\"csv\"")
                .doesNotContain("request.getAttribute(\"csv\")")
                .doesNotContain("request.getAttribute(\"csv-\" + x)")
                .containsSubsequence("<form style=\"display:inline;\"",
                        "name=\"resultToken\" value=\"${carlos:forHtmlAttribute(csvDownloadToken)}\"",
                        "name=\"resultIndex\"", "name=\"getCSV\"", "name=\"getXLS\"", "</form>")
                // The held-back note names the setting while the CSV limit can still be raised.
                .contains("<fmt:message key=\"report.byTemplate.csvWithheld\"><fmt:param value=\"${csvWithheldMb}\"/></fmt:message>"
                        + "<c:if test=\"${not empty csvWithheldSetting}\"> <fmt:message key=\"report.limit.administratorCanRaise\">"
                        + "<fmt:param value=\"${csvWithheldSetting}\"/></fmt:message></c:if>");
    }

    @Test
    @DisplayName("should say in English that an administrator can raise the limit, naming its setting")
    void shouldTellTheUserAnAdministratorCanRaiseTheLimit_withItsSetting() throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src", "main", "resources", "oscarResources_en.properties"))) {
            bundle.load(in);
        }
        String hint = new MessageFormat(bundle.getProperty(ReportResultBudget.ADMINISTRATOR_CAN_RAISE_KEY), Locale.ROOT)
                .format(new Object[]{"report_by_template.max_rows"});
        assertThat(hint).isEqualTo(
                "An administrator can raise this limit with the setting report_by_template.max_rows in carlos.properties.");
        // The English fallback (logs, and pages without the key) is the bundle text.
        assertThat(ReportResultBudget.ADMINISTRATOR_CAN_RAISE_ENGLISH)
                .isEqualTo(bundle.getProperty(ReportResultBudget.ADMINISTRATOR_CAN_RAISE_KEY));
        // The error page shows the configured-report limits the same way.
        String errorPage = Files.readString(Path.of("src", "main", "webapp", "WEB-INF", "jsp", "error", "errorpage.jsp"),
                StandardCharsets.UTF_8);
        assertThat(errorPage).contains("<fmt:message key=\"${carlosReportLimitKey}\"><fmt:param value=\"${carlosReportLimitValue}\"/></fmt:message>"
                + "<c:if test=\"${not empty carlosReportLimitSetting}\"> <fmt:message key=\"report.limit.administratorCanRaise\">"
                + "<fmt:param value=\"${carlosReportLimitSetting}\"/></fmt:message></c:if>");
    }
}
