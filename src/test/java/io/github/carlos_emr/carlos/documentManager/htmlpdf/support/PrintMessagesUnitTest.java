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
package io.github.carlos_emr.carlos.documentManager.htmlpdf.support;

import io.github.carlos_emr.carlos.documentManager.htmlpdf.dto.HtmlPdfRenderDegradation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PrintMessages} and for the {@code htmlpdf.*} entries of the five
 * {@code oscarResources} bundles.
 */
@Tag("unit")
@Tag("htmlpdf")
@DisplayName("PrintMessages Unit Tests")
class PrintMessagesUnitTest {

    private static final List<String> KEYS = List.of(PrintMessages.SAFE_MODE_KEY, PrintMessages.TEXT_ONLY_KEY,
            PrintMessages.IMAGES_OMITTED_KEY, PrintMessages.RTL_BEST_EFFORT_KEY, PrintMessages.IMAGE_UNAVAILABLE_KEY);

    private static final Set<HtmlPdfRenderDegradation> WITH_NOTE = EnumSet.of(HtmlPdfRenderDegradation.SAFE_MODE,
            HtmlPdfRenderDegradation.TEXT_ONLY, HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY,
            HtmlPdfRenderDegradation.RTL_BEST_EFFORT);

    private static Properties bundle(String locale) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = PrintMessagesUnitTest.class.getClassLoader()
                .getResourceAsStream("oscarResources_" + locale + ".properties")) {
            assertThat(in).as("oscarResources_" + locale + ".properties on the class path").isNotNull();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return properties;
    }

    @Test
    @DisplayName("should read the English sentences of the spec, whatever the default locale")
    void shouldReadEnglishNotes_regardlessOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);

            assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.SAFE_MODE)).contains(
                    "This document was printed with simplified formatting because its layout could not be rendered.");
            assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.TEXT_ONLY)).contains(
                    "This document was printed as plain text because its layout could not be rendered.");
            assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.IMAGES_OMITTED_FOR_MEMORY)).contains(
                    "Images were omitted because the server was busy. Print again to include them.");
            assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.RTL_BEST_EFFORT)).contains(
                    "Right-to-left text was converted for printing. Check the line order of longer right-to-left"
                            + " passages against the original.");
            assertThat(PrintMessages.imageUnavailable()).isEqualTo("[image unavailable]");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("should read the same words under a country-less and an unusual default locale")
    void shouldReadSameWords_underAnyDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : List.of(Locale.ROOT, Locale.of("pl"), Locale.of("pt", "BR"), Locale.of("tr", "TR"))) {
                Locale.setDefault(locale);
                assertThat(PrintMessages.imageUnavailable()).as(locale.toString()).isEqualTo("[image unavailable]");
                assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.TEXT_ONLY)).as(locale.toString())
                        .contains(PrintMessages.TEXT_ONLY_ENGLISH);
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    @ParameterizedTest
    @EnumSource(HtmlPdfRenderDegradation.class)
    @DisplayName("should give a note only for the degradations that need the reader to be told")
    void shouldGiveNote_onlyWhereTheReaderMustBeTold(HtmlPdfRenderDegradation degradation) {
        assertThat(PrintMessages.noteFor(degradation).isPresent()).isEqualTo(WITH_NOTE.contains(degradation));
    }

    @Test
    @DisplayName("should have no note for the degradations that keep the content")
    void shouldHaveNoNote_forContentPreservingDegradations() {
        assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.IMAGE_CONVERTED)).isEmpty();
        assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.IMAGE_DOWNSCALED)).isEmpty();
        assertThat(PrintMessages.noteFor(HtmlPdfRenderDegradation.MARGINS_REDUCED)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr", "es", "pl", "pt_BR"})
    @DisplayName("should define every key, non-blank, in each of the five locale bundles")
    void shouldDefineKeys_inEveryLocaleBundle(String locale) throws IOException {
        Properties properties = bundle(locale);

        for (String key : KEYS) {
            assertThat(properties.getProperty(key)).as(locale + " " + key).isNotBlank();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"fr", "es", "pl", "pt_BR"})
    @DisplayName("should translate the entries rather than copy the English ones")
    void shouldTranslateEntries_inNonEnglishBundles(String locale) throws IOException {
        Properties english = bundle("en");
        Properties translated = bundle(locale);

        for (String key : KEYS) {
            assertThat(translated.getProperty(key)).as(locale + " " + key).isNotEqualTo(english.getProperty(key));
        }
    }

    @Test
    @DisplayName("should keep the built-in English wording equal to the English bundle's")
    void shouldMatchBuiltInEnglish_withBundle() throws IOException {
        Properties english = bundle("en");

        assertThat(english.getProperty(PrintMessages.SAFE_MODE_KEY)).isEqualTo(PrintMessages.SAFE_MODE_ENGLISH);
        assertThat(english.getProperty(PrintMessages.TEXT_ONLY_KEY)).isEqualTo(PrintMessages.TEXT_ONLY_ENGLISH);
        assertThat(english.getProperty(PrintMessages.IMAGES_OMITTED_KEY)).isEqualTo(PrintMessages.IMAGES_OMITTED_ENGLISH);
        assertThat(english.getProperty(PrintMessages.RTL_BEST_EFFORT_KEY)).isEqualTo(PrintMessages.RTL_BEST_EFFORT_ENGLISH);
        assertThat(english.getProperty(PrintMessages.IMAGE_UNAVAILABLE_KEY)).isEqualTo(PrintMessages.IMAGE_UNAVAILABLE_ENGLISH);
    }

    @Test
    @DisplayName("should fall back to the built-in text when an entry is missing")
    void shouldUseBuiltInText_whenKeyMissing() {
        assertThat(PrintMessages.lookup("htmlpdf.no.such.key", "built-in")).isEqualTo("built-in");
        assertThat(PrintMessages.lookup(PrintMessages.SAFE_MODE_KEY, "unused")).isEqualTo(PrintMessages.SAFE_MODE_ENGLISH);
    }

    @Test
    @DisplayName("should use the built-in text when the bundle is absent, or its entry is blank")
    void shouldUseBuiltInText_whenBundleAbsentOrEntryBlank() throws IOException {
        ResourceBundle blank = new PropertyResourceBundle(new StringReader("empty=\nspaces=   \nreal=value"));

        assertThat(PrintMessages.lookup(null, "real", "built-in")).isEqualTo("built-in");
        assertThat(PrintMessages.lookup(blank, "empty", "built-in")).isEqualTo("built-in");
        assertThat(PrintMessages.lookup(blank, "spaces", "built-in")).isEqualTo("built-in");
        assertThat(PrintMessages.lookup(blank, "missing", "built-in")).isEqualTo("built-in");
        assertThat(PrintMessages.lookup(blank, "real", "built-in")).isEqualTo("value");
    }

    @Test
    @DisplayName("should read apostrophes raw, as one character, in the French entries")
    void shouldReadApostrophesRaw_inFrenchEntries() throws IOException {
        Properties french = bundle("fr");

        assertThat(french.getProperty(PrintMessages.SAFE_MODE_KEY)).contains("n'a pas pu");
        assertThat(french.getProperty(PrintMessages.SAFE_MODE_KEY)).doesNotContain("''");
    }
}
