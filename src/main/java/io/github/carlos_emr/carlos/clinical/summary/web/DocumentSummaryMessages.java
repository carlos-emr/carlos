/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Clinician-facing messages the summary actions set as plain text, resolved from oscarResources like the JSP's fmt:message. */
final class DocumentSummaryMessages {
    static final String BUNDLE = "oscarResources";
    static final String LOCALE_ATTRIBUTE = "documentSummaryLocale";
    static final String LANGUAGE_ATTRIBUTE = "documentSummaryLanguageTag";
    // There is no base oscarResources.properties, so the JDK's own fallback for a locale without a bundle
    // would be the server's default locale, whatever that happens to be. Resolve the browser's
    // preferences only, then English explicitly.
    private static final ResourceBundle.Control REQUEST_LOCALE_ONLY =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private DocumentSummaryMessages() { }

    static String text(HttpServletRequest request, String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, prepare(request), REQUEST_LOCALE_ONLY).getString(key);
        } catch (MissingResourceException keyMissingInThatBundle) {
            // A key the resolved bundle lacks reads in English; a key English lacks is a programming
            // error and still throws.
            return ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH, REQUEST_LOCALE_ONLY).getString(key);
        }
    }

    /** Resolve once per request for both action messages and the JSP's fmt locale and language. */
    static Locale prepare(HttpServletRequest request) {
        Object prepared = request.getAttribute(LOCALE_ATTRIBUTE);
        if (prepared instanceof Locale locale) return locale;
        Locale locale = bundleLocale(request);
        request.setAttribute(LOCALE_ATTRIBUTE, locale);
        request.setAttribute(LANGUAGE_ATTRIBUTE, locale.toLanguageTag());
        return locale;
    }

    /**
     * The first of the browser's preferred locales that has a bundle, else English: the same walk
     * JSTL's fmt:message makes, so a page and its action messages agree for "de, fr" as for "fr".
     * A container always answers the preference list; a mocked request in a unit test may answer
     * null for it, or for the single locale.
     */
    static Locale bundleLocale(HttpServletRequest request) {
        // Like JSTL's Util.getRequestLocales: no Accept-Language means no browser preference,
        // even when the container supplies its own default through getLocales().
        Enumeration<String> languageHeaders = request.getHeaders("accept-language");
        if (languageHeaders == null || !languageHeaders.hasMoreElements()) return Locale.ENGLISH;
        Enumeration<Locale> preferred = request.getLocales();
        if (preferred == null) {
            Locale single = request.getLocale();
            preferred = java.util.Collections.enumeration(single == null ? java.util.List.of() : java.util.List.of(single));
        }
        while (preferred.hasMoreElements()) {
            Locale candidate = preferred.nextElement();
            try {
                ResourceBundle.getBundle(BUNDLE, candidate, REQUEST_LOCALE_ONLY);
                return candidate;
            } catch (MissingResourceException noBundleForThisPreference) {
                // Try the browser's next choice.
            }
        }
        return Locale.ENGLISH;
    }
}
