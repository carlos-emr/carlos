/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.actions;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 409 texts AddEForm2Action answers with when a submission fails after the eForm is stored
 * (issue #4424): {@code eform.submitSavedStepIncomplete}, which names the step that did not complete,
 * and {@code eform.submitOutcomeUnknown}, for a commit whose outcome was never confirmed. Both are read
 * through Struts getText, which formats every message with {@link MessageFormat}, so each bundle must
 * escape its apostrophes, and neither may read as the replay refusal ({@code eform.submitUnavailable}).
 */
@Tag("unit")
@Tag("fast")
@DisplayName("eForm post-save failure messages")
class EFormPostSaveFailureMessageUnitTest {

    private static final List<String> STEP_KEYS = List.of(
            "eform.submitStep.followOn",
            "eform.submitStep.chartEntries",
            "eform.submitStep.addToDocuments",
            "eform.submitStep.fax",
            "eform.submitStep.download",
            "eform.submitStep.email");

    private static Properties bundle(String locale) throws IOException {
        Properties bundle = new Properties();
        try (Reader in = Files.newBufferedReader(
                Path.of("src/main/resources/oscarResources_" + locale + ".properties"), StandardCharsets.UTF_8)) {
            bundle.load(in);
        }
        return bundle;
    }

    private static String raw(Properties bundle, String locale, String key) {
        String message = bundle.getProperty(key);
        assertThat(message).as("%s in %s", key, locale).isNotBlank();
        return message;
    }

    private static String formatted(String locale, String message, Object... arguments) {
        return new MessageFormat(message, Locale.forLanguageTag(locale.replace('_', '-'))).format(arguments);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should name every step, with every apostrophe kept, in the saved-but-incomplete message")
    void shouldNameEachStep_whenSavedStepIncompleteIsFormatted(String locale) throws IOException {
        Properties bundle = bundle(locale);
        String template = raw(bundle, locale, "eform.submitSavedStepIncomplete");
        assertThat(template).as("the step placeholder in %s", locale).contains("{0}");

        for (String stepKey : STEP_KEYS) {
            String stepRaw = raw(bundle, locale, stepKey);
            // Step names are formatted by getText too before they are substituted.
            String step = formatted(locale, stepRaw);
            assertThat(step).as("%s in %s", stepKey, locale).isEqualTo(stepRaw.replace("''", "'"));
            assertThat(formatted(locale, template, step))
                    .isEqualTo(template.replace("''", "'").replace("{0}", step));
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should keep every apostrophe in the unconfirmed-save message and point to the patient's eForms")
    void shouldKeepApostrophes_whenOutcomeUnknownIsFormatted(String locale) throws IOException {
        String message = raw(bundle(locale), locale, "eform.submitOutcomeUnknown");

        assertThat(formatted(locale, message)).isEqualTo(message.replace("''", "'"))
                .contains("Check the patient's eForms");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should not reuse the replay refusal for a failure after the eForm is stored")
    void shouldDifferFromReplayRefusal_forPostSaveFailures(String locale) throws IOException {
        Properties bundle = bundle(locale);
        String replayFirstSentence = formatted(locale, raw(bundle, locale, "eform.submitUnavailable")).split("\\. ")[0];

        assertThat(formatted(locale, raw(bundle, locale, "eform.submitSavedStepIncomplete"), "Add to Documents"))
                .doesNotContain(replayFirstSentence);
        assertThat(formatted(locale, raw(bundle, locale, "eform.submitOutcomeUnknown")))
                .doesNotContain(replayFirstSentence);
    }
}
