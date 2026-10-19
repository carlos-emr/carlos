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
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link LocaleUtils#resolveBundleLocale(jakarta.servlet.ServletRequest)}.
 *
 * <p>CARLOS installs no Spring {@code LocaleResolver} on the Struts/JSP request path, so the JVM
 * default locale is the <em>server's</em> language, not the clinician's. Every one of these cases
 * is set up with a server default that differs from the browser's preferences, so a regression
 * back to the JVM default fails rather than coincidentally passing.</p>
 *
 * @since 2026-09-20
 */
// Mutates process-wide Locale defaults; never overlap another test in this JVM.
@Isolated
@DisplayName("LocaleUtils browser locale resolution")
@Tag("unit")
@Tag("i18n")
class LocaleUtilsResolveBundleLocaleUnitTest {

    private Locale originalDefault;
    private String originalBaseName;

    @BeforeEach
    void rememberEnvironment() {
        originalDefault = Locale.getDefault();
        originalBaseName = LocaleUtils.BASE_NAME;
        // ContextStartupListener sets this at webapp start; unit tests get no listener.
        LocaleUtils.BASE_NAME = "oscarResources";
        // A server running in Polish: any answer that matches this rather than the request
        // is the defect this method exists to prevent.
        Locale.setDefault(Locale.forLanguageTag("pl"));
        ResourceBundle.clearCache();
    }

    @AfterEach
    void restoreEnvironment() {
        LocaleUtils.BASE_NAME = originalBaseName;
        Locale.setDefault(originalDefault);
        ResourceBundle.clearCache();
    }

    @Test
    @DisplayName("should pick the browser's first preference when it has a bundle")
    void shouldPickFirstPreference_whenItHasABundle() {
        Locale resolved = LocaleUtils.resolveBundleLocale(requestPreferring(Locale.FRENCH, Locale.ENGLISH));

        assertThat(resolved.getLanguage()).isEqualTo("fr");
    }

    @Test
    @DisplayName("should keep the country variant of a supported language")
    void shouldKeepCountryVariant_ofSupportedLanguage() {
        Locale resolved = LocaleUtils.resolveBundleLocale(requestPreferring(Locale.CANADA_FRENCH));

        // fr_CA has no bundle of its own but resolves through fr, so the region is kept for
        // downstream date/number formatting instead of being flattened away.
        assertThat(resolved).isEqualTo(Locale.CANADA_FRENCH);
    }

    @Test
    @DisplayName("should skip an unsupported preference in favour of the next one")
    void shouldSkipUnsupportedPreference_inFavourOfTheNext() {
        Locale resolved = LocaleUtils.resolveBundleLocale(
                requestPreferring(Locale.GERMANY, Locale.FRENCH));

        // Plain ResourceBundle.getBundle would answer German with the JVM default (Polish here)
        // and never look at the browser's second choice.
        assertThat(resolved.getLanguage()).isEqualTo("fr");
    }

    @Test
    @DisplayName("should fall back to English when no preference has a bundle")
    void shouldFallBackToEnglish_whenNoPreferenceHasABundle() {
        Locale resolved = LocaleUtils.resolveBundleLocale(requestPreferring(Locale.GERMANY, Locale.ITALY));

        assertThat(resolved).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("should fall back to English when the request states no preference")
    void shouldFallBackToEnglish_whenRequestStatesNoPreference() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        // Servlet containers expose their default when the HTTP header is absent.
        when(request.getLocales()).thenReturn(Collections.enumeration(List.of(Locale.getDefault())));

        assertThat(LocaleUtils.resolveBundleLocale(request)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldFallBackToEnglish_whenLanguageHeaderIsBlank() {
        HttpServletRequest request = requestPreferring(Locale.getDefault());
        when(request.getHeader("Accept-Language")).thenReturn("  ");

        assertThat(LocaleUtils.resolveBundleLocale(request)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void shouldNegotiateLocales_forNonHttpRequests() {
        ServletRequest request = mock(ServletRequest.class);
        when(request.getLocales()).thenReturn(Collections.enumeration(List.of(Locale.GERMAN, Locale.FRENCH)));

        assertThat(LocaleUtils.resolveBundleLocale(request)).isEqualTo(Locale.FRENCH);
    }

    @Test
    void shouldFallBackToEnglish_whenLocaleEnumerationIsEmpty() {
        ServletRequest request = mock(ServletRequest.class);
        when(request.getLocales()).thenReturn(Collections.emptyEnumeration());

        assertThat(LocaleUtils.resolveBundleLocale(request)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("should fall back to English for a null request")
    void shouldFallBackToEnglish_forNullRequest() {
        assertThat(LocaleUtils.resolveBundleLocale(null)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("should look messages up in the browser locale rather than the server locale")
    void shouldLookMessagesUp_inBrowserLocaleRatherThanServerLocale() {
        String french = LocaleUtils.getMessage(
                requestPreferring(Locale.FRENCH), "demographic.demographicaddrecordhtm.formSex");
        String english = LocaleUtils.getMessage(
                requestPreferring(Locale.ENGLISH), "demographic.demographicaddrecordhtm.formSex");

        assertThat(french).isEqualTo("Sexe");
        assertThat(english).isEqualTo("Sex");
    }

    private static HttpServletRequest requestPreferring(Locale... preferences) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Accept-Language")).thenReturn(String.join(",",
                Arrays.stream(preferences).map(Locale::toLanguageTag).toList()));
        when(request.getLocales()).thenReturn(Collections.enumeration(List.of(preferences)));
        return request;
    }
}
