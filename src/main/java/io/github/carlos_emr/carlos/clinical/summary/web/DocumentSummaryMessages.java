/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.ResourceBundle;

/** Clinician-facing messages the summary actions set as plain text, resolved from oscarResources like the JSP's fmt:message. */
final class DocumentSummaryMessages {
    private DocumentSummaryMessages() { }

    static String text(HttpServletRequest request, String key) {
        Locale locale = request.getLocale();
        return ResourceBundle.getBundle("oscarResources", locale == null ? Locale.ENGLISH : locale).getString(key);
    }
}
