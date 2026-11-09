/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.mock.web.MockHttpServletRequest;

/** Browser preference, action text and page language must agree without a server-locale fallback. */
@Tag("unit")
@Tag("fast")
class DocumentSummaryMessagesUnitTest {
    private static final String KEY = "documentSummary.review.correctionNotSaved";

    @Test
    void absentLanguageHeaderDoesNotSelectTheContainersFrenchDefault() {
        var request = new MockHttpServletRequest();
        request.setPreferredLocales(List.of(Locale.CANADA_FRENCH));
        request.removeHeader("Accept-Language");
        assertThat(request.getLocale()).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(request.getHeaders("accept-language").hasMoreElements()).isFalse();

        assertThat(DocumentSummaryMessages.prepare(request)).isEqualTo(Locale.ENGLISH);
        assertThat(DocumentSummaryMessages.text(request, KEY)).isEqualTo(english());
        assertThat(request.getAttribute(DocumentSummaryMessages.LOCALE_ATTRIBUTE)).isEqualTo(Locale.ENGLISH);
        assertThat(request.getAttribute(DocumentSummaryMessages.LANGUAGE_ATTRIBUTE)).isEqualTo("en");
    }

    @Test
    void laterSupportedBrowserPreferenceOwnsBothActionTextAndPageLanguage() {
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de, fr-CA;q=0.8");
        request.setPreferredLocales(List.of(Locale.GERMAN, Locale.CANADA_FRENCH));
        assertThat(request.getLocale()).isEqualTo(Locale.GERMAN);

        assertThat(DocumentSummaryMessages.prepare(request)).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(DocumentSummaryMessages.text(request, KEY))
                .isEqualTo(ResourceBundle.getBundle(DocumentSummaryMessages.BUNDLE, Locale.CANADA_FRENCH).getString(KEY))
                .isNotEqualTo(english());
        assertThat(request.getAttribute(DocumentSummaryMessages.LOCALE_ATTRIBUTE)).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(request.getAttribute(DocumentSummaryMessages.LANGUAGE_ATTRIBUTE)).isEqualTo("fr-CA");
    }

    @Test
    void unsupportedPreferencesUseTheSameExplicitEnglishFallback() {
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "de, zh;q=0.8");
        request.setPreferredLocales(List.of(Locale.GERMAN, Locale.CHINESE));

        assertThat(DocumentSummaryMessages.prepare(request)).isEqualTo(Locale.ENGLISH);
        assertThat(DocumentSummaryMessages.text(request, KEY)).isEqualTo(english());
        assertThat(request.getAttribute(DocumentSummaryMessages.LANGUAGE_ATTRIBUTE)).isEqualTo("en");
    }

    @Test
    void onePreparedLocaleRemainsTheLocaleOfTheWholeRenderedRequest() {
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-CA");
        request.setPreferredLocales(List.of(Locale.CANADA_FRENCH));
        assertThat(DocumentSummaryMessages.prepare(request)).isEqualTo(Locale.CANADA_FRENCH);
        String french = DocumentSummaryMessages.text(request, KEY);

        request.setPreferredLocales(List.of(Locale.ENGLISH));
        assertThat(DocumentSummaryMessages.prepare(request)).isEqualTo(Locale.CANADA_FRENCH);
        assertThat(DocumentSummaryMessages.text(request, KEY)).isEqualTo(french);
        assertThat(request.getAttribute(DocumentSummaryMessages.LANGUAGE_ATTRIBUTE)).isEqualTo("fr-CA");
    }

    private static String english() {
        return ResourceBundle.getBundle(DocumentSummaryMessages.BUNDLE, Locale.ENGLISH).getString(KEY);
    }
}
