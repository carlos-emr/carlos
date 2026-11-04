/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.pageUtil;

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
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Rx page's staging notices and its drug search's no-match note are shown in the user's language. createNewRx
 * used to leave fixed English sentences for the staging pane, and the search listed DrugRef's English "None found"
 * row as a product. createNewRx now leaves a bundle key, which prescribe.jsp looks up, and the search shows its own
 * translated note.
 */
@DisplayName("Rx staging notices and drug search no-match note in every locale")
@Tag("unit")
@Tag("i18n")
@Tag("prescript")
class RxStageNoticeI18nUnitTest {

    private static final List<String> KEYS = List.of(
            "SearchDrug.msgStageNotPrescribable",
            "SearchDrug.msgStageAlreadyStaged",
            "SearchDrug.msgStageLoadFailed",
            "SearchDrug.js.drugSearchNoMatch");
    private static final Path ACTION = Path.of(
            "src/main/java/io/github/carlos_emr/carlos/prescript/pageUtil/RxWriteScript2Action.java");
    private static final Path PRESCRIBE_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/prescribe.jsp");
    private static final Path SEARCH_DRUG_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp");

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pt_BR", "pl"})
    @DisplayName("should define each notice as a translated sentence in every shipped locale")
    void shouldDefineTranslatedNotices_forEveryLocale(String locale) throws IOException {
        Properties bundle = bundle(locale);
        Properties english = bundle("en");

        for (String key : KEYS) {
            String value = bundle.getProperty(key);
            assertThat(value).as("%s in %s", key, locale).isNotBlank().endsWith(".").doesNotContain("{");
            if (!"en".equals(locale)) {
                assertThat(value).as("%s in %s is translated", key, locale).isNotEqualTo(english.getProperty(key));
            }
        }
    }

    @Test
    @DisplayName("should hand the staging pane a bundle key, not English text")
    void shouldLeaveBundleKeys_forStagingPane() throws IOException {
        String action = Files.readString(ACTION, StandardCharsets.UTF_8);

        assertThat(action).doesNotContain("setAttribute(\"rxStageError\"")
                .doesNotContain("That prescription is already staged.")
                .doesNotContain("Could not load the selected drug.")
                .doesNotContain("could not be added as a prescription");
        assertThat(action).contains("STAGE_ERROR_KEY_ATTRIBUTE = \"rxStageErrorKey\"")
                .contains("\"SearchDrug.msgStageNotPrescribable\"")
                .contains("\"SearchDrug.msgStageAlreadyStaged\"")
                .contains("\"SearchDrug.msgStageLoadFailed\"");
    }

    @Test
    @DisplayName("should look the staging notice up in the user's bundle and encode it")
    void shouldLookUpNotice_inPrescribeJsp() throws IOException {
        String jsp = Files.readString(PRESCRIBE_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<fmt:message key=\"${requestScope.rxStageErrorKey}\" var=\"rxStageMessage\"/>")
                .contains("<carlos:encode value=\"${rxStageMessage}\"/>")
                .doesNotContain("getAttribute(\"rxStageError\")");
    }

    @Test
    @DisplayName("should give the drug search its translated no-match note")
    void shouldGiveNoMatchNote_toDrugSearch() throws IOException {
        String jsp = Files.readString(SEARCH_DRUG_JSP, StandardCharsets.UTF_8);

        assertThat(jsp).contains("<fmt:message key=\"SearchDrug.js.drugSearchNoMatch\" var=\"msg_drugSearchNoMatch\"/>")
                .contains("drugSearchNoMatch: '${carlos:forJavaScript(msg_drugSearchNoMatch)}',");
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
