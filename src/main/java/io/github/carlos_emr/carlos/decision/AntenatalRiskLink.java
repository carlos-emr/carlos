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
package io.github.carlos_emr.carlos.decision;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The single link-safety rule for the antenatal risk configuration.
 *
 * <p>Two independent call sites need the same answer, and they must not be
 * allowed to drift apart:
 *
 * <ul>
 *   <li>{@link AntenatalRiskConfigService} rejects an unsafe {@code href} at
 *       save time, so a bad link never reaches the shared configuration file.</li>
 *   <li>{@link DesAntenatalPlannerRisksHandler_99_12} re-checks at render time,
 *       because the on-disk file is <em>not</em> a trusted artifact: it predates
 *       save-side validation, it is writable by anyone with filesystem access to
 *       {@code DOCUMENT_DIR}, and a payload planted through the pre-fix editor
 *       survives the upgrade. The renderer feeds the value to
 *       {@code popupPage(...)}, which calls {@code window.open(url)} — escaping
 *       the JavaScript string literal stops a breakout but does nothing about a
 *       {@code javascript:} or {@code data:} URL, which executes in the opener's
 *       origin.</li>
 * </ul>
 *
 * @since 2026-08-12
 */
final class AntenatalRiskLink {

    /** Longest accepted link; well past any legitimate clinical resource URL. */
    static final int MAX_LENGTH = 2048;

    private AntenatalRiskLink() {
    }

    /**
     * Decides whether a configured link may be used as a popup target.
     *
     * @param href raw {@code href} attribute value from the configuration document
     * @return {@code true} for an absolute HTTP(S) URL or a same-origin relative
     *         path; {@code false} for anything else, including {@code null}
     */
    // FindSecBugs IMPROPER_UNICODE: URI schemes are an ASCII protocol token and are folded with Locale.ROOT before an exact allowlist check.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-folding a parsed ASCII URI scheme with Locale.ROOT before an exact HTTP/HTTPS allowlist check")
    static boolean isSafe(String href) {
        // "//host" is protocol-relative (off-origin) and a backslash is normalized
        // to "/" by browsers but not by URI, so both are rejected before parsing.
        if (href == null || href.isBlank() || href.length() > MAX_LENGTH
                || href.startsWith("//") || href.indexOf('\\') >= 0) {
            return false;
        }
        try {
            URI uri = new URI(href);
            if (!uri.isAbsolute()) {
                return true;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return "http".equals(scheme) || "https".equals(scheme);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
