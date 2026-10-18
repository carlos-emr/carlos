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
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BC billing report centre's Billed report for a range with no bills (#4951): it shows its headings and a
 * translated "no results" row, as the Unbilled report does, instead of the bare text "failed!!!".
 */
@Tag("unit")
@Tag("billing")
@DisplayName("BC Billed report fragment")
class BillingReportBilledFragmentJspRegressionTest {

    private static final Path BILLED_JSPF = Path.of("src/main/webapp/WEB-INF/jsp/billing/CA/BC/billingReport_billed.jspf");
    private static final String NO_RESULTS_KEY = "admin.billingreferralAdmin.msgNoResults";

    @Test
    @DisplayName("should show the headings and the no-results row for an empty range")
    void shouldShowHeadingsAndNoResultsRow_whenRangeHasNoBills() throws IOException {
        String jspf = Files.readString(BILLED_JSPF, StandardCharsets.UTF_8);

        assertThat(jspf)
                .doesNotContain("failed!!!")
                .doesNotContainPattern("bs\\.size\\(\\)\\s*==\\s*0")
                .doesNotContain("No unbill items");
        // The headings come before the bill rows, and the "no results" row is the one written when none was listed.
        int headings = jspf.indexOf("SERVICE DATE");
        int emptyRow = jspf.indexOf("if (rowCount == 0)");
        int message = jspf.indexOf("<fmt:message key=\"" + NO_RESULTS_KEY + "\"/>");
        assertThat(headings).isPositive();
        assertThat(emptyRow).isGreaterThan(headings);
        assertThat(message).isGreaterThan(emptyRow);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "en, No results found",
            "fr, Aucun résultat trouvé",
            "es, No se encontraron resultados",
            "pl, Nie znaleziono wyników",
            "pt_BR, Nenhum resultado encontrado",
    })
    @DisplayName("should have the no-results message in every locale")
    void shouldTranslateNoResultsMessage_forEveryLocale(String locale, String expected) throws IOException {
        Properties bundle = new Properties();
        try (InputStream in = Files.newInputStream(Path.of("src/main/resources/oscarResources_" + locale + ".properties"))) {
            bundle.load(in);
        }

        assertThat(bundle.getProperty(NO_RESULTS_KEY)).isEqualTo(expected);
    }
}
