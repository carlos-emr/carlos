/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Clinician-facing messages the summary actions set as plain text, resolved from oscarResources like the JSP's fmt:message. */
final class DocumentSummaryMessages {
    static final String BUNDLE = "oscarResources";
    // There is no base oscarResources.properties, so the JDK's own fallback for a locale without a bundle
    // would be the server's default locale, whatever that happens to be. Resolve the request locale only,
    // then English explicitly.
    private static final ResourceBundle.Control REQUEST_LOCALE_ONLY =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private DocumentSummaryMessages() { }

    static String text(HttpServletRequest request, String key) {
        // A servlet container never answers null here; a mocked request in a unit test does.
        Locale locale = request.getLocale() == null ? Locale.ENGLISH : request.getLocale();
        try {
            return ResourceBundle.getBundle(BUNDLE, locale, REQUEST_LOCALE_ONLY).getString(key);
        } catch (MissingResourceException unsupportedLocaleOrKey) {
            // A locale with no bundle, or a key its bundle lacks, reads in English. A key English lacks is a
            // programming error and still throws.
            return ResourceBundle.getBundle(BUNDLE, Locale.ENGLISH, REQUEST_LOCALE_ONLY).getString(key);
        }
    }
}
