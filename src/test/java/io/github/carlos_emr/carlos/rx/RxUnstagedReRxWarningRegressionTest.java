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
 * The Rx page's warning on Save when a ReRx box is ticked but its drug is not staged. The page used to put the
 * English word "There" in front of each locale's translated text ("There est 1 médicament ReRx non ajouté."). Each
 * locale's key now holds the whole first sentence, and the page adds nothing to it.
 */
@DisplayName("Rx unstaged-ReRx warning on Save")
@Tag("unit")
@Tag("rx")
@Tag("regression")
class RxUnstagedReRxWarningRegressionTest {

    private static final Path SEARCH_DRUG_JSP = Path.of("src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp");
    private static final String SINGLE = "SearchDrug.js.unstagedReRxSingle";
    private static final String MULTIPLE = "SearchDrug.js.unstagedReRxMultiple";

    @Test
    @DisplayName("should show each locale's sentence as it is, with no English word added")
    void shouldShowLocaleSentenceAsIs_whenBuildingWarning() throws IOException {
        String source = Files.readString(SEARCH_DRUG_JSP, StandardCharsets.UTF_8);
        int start = source.indexOf("function buildConfirmationMessage(count) {");
        assertThat(start).isPositive();
        String builder = source.substring(start, source.indexOf("\n    }", start));

        assertThat(builder).doesNotContain("\"There \"")
                .contains("return statusMessage + \"\\n\" + SAVE_WARNING + \"\\n\" + SAVE_PROMPT;");
        // The count still goes in by replacing the literal {0} in the page's copy of the key.
        assertThat(source).contains("MULTIPLE: (count) => jsMsg.unstagedReRxMultiple.replace('{0}', count)");
    }

    @Test
    @DisplayName("should hold the whole English sentences")
    void shouldHoldWholeEnglishSentences_forEnglish() throws IOException {
        Properties english = bundle("en");

        assertThat(english.getProperty(SINGLE)).isEqualTo("There is 1 unstaged ReRx drug.");
        assertThat(english.getProperty(MULTIPLE)).isEqualTo("There are {0} unstaged ReRx drugs.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pt_BR", "pl"})
    @DisplayName("should hold a whole sentence with one count placeholder in every shipped locale")
    void shouldHoldWholeSentence_forEveryLocale(String locale) throws IOException {
        Properties bundle = bundle(locale);
        String single = bundle.getProperty(SINGLE);
        String multiple = bundle.getProperty(MULTIPLE);

        assertThat(single).isNotBlank().endsWith(".").contains("1").doesNotContain("{0}");
        assertThat(multiple).isNotBlank().endsWith(".");
        assertThat(multiple.split("\\{0}", -1)).as("%s has one {0}", MULTIPLE).hasSize(2);
        // A sentence starts with a capital letter in every shipped language.
        assertThat(Character.isUpperCase(single.charAt(0))).isTrue();
        assertThat(Character.isUpperCase(multiple.charAt(0))).isTrue();
        if (!"en".equals(locale)) {
            assertThat(single).doesNotContain("There");
            assertThat(multiple).doesNotContain("There");
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
