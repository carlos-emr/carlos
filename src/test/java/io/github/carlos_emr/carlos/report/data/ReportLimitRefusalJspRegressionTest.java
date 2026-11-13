/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #5850: a configured form report and a clinical report refused by their row, size or time
 * limit show a message on their own page instead of the generic error page.
 */
@DisplayName("Report limit refusal pages")
@Tag("unit")
@Tag("report")
class ReportLimitRefusalJspRegressionTest {

    private static final Path JSP = Path.of("src", "main", "webapp", "WEB-INF", "jsp", "report");
    private static final String MESSAGE = "<fmt:message key=\"report.limitRefused\"/>";

    private static String page(String name) throws IOException {
        // Comments do not count: a check left inside <%-- --%> would not run.
        return Files.readString(JSP.resolve(name), StandardCharsets.UTF_8).replaceAll("(?s)<%--.*?--%>", "");
    }

    @Test
    void shouldShowTheRefusalInsteadOfTheErrorPage_forAFormReport() throws IOException {
        String page = page("reportResult.jsp");
        assertThat(page).containsSubsequence(
                "catch (java.sql.SQLException e)",
                "if (!ReportLimitRefusal.isRefusal(e))",
                "throw e;",
                ".warn(\"Form report refused by its row, size or time limit\")",
                "reportLimitRefused = true;");
        assertThat(page).containsSubsequence(
                "<% if (reportLimitRefused) { %>", MESSAGE, "<% } else { %>", "<table", "</table>", "<% } %>");
        assertThat(page).as("other failures still reach the error page")
                .contains("errorPage=\"/WEB-INF/jsp/error/errorpage.jsp\"");
    }

    @Test
    void shouldShowTheRefusalAboveTheResults_forAClinicalReport() throws IOException {
        assertThat(page("ClinicalReports.jsp")).containsSubsequence(
                "Boolean.TRUE.equals(request.getAttribute(\"reportLimitRefused\"))", MESSAGE,
                "request.getAttribute(\"denominator\") != null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    void shouldHaveTheMessage_inEveryBundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (var in = Files.newInputStream(Path.of("src", "main", "resources", "oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }
        assertThat(bundle.getProperty("report.limitRefused")).isNotBlank().doesNotContain("&#");
    }
}
