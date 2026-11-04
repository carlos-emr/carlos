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
package io.github.carlos_emr.carlos.email.admin;

import java.io.Serializable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.carlos.email.core.ClinicEmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailFooterHtml;

/**
 * Clinic footer text an administrator typed on Configure Email that was not saved, kept for the
 * page's next view (issue #3981).
 *
 * <p>The footer is edited in the Edit footer window and saved by its Save footer. A refused save
 * reloads the page: it keeps the typed text here, with the settings chosen beside it, in the
 * administrator's own session, and the next Configure Email view puts them back. The logo form, a
 * separate form, carries text put back that way and not yet saved again. A kept draft is shown once,
 * is never saved by itself, and carries the fingerprint of the footer the page showed when editing
 * began, so a later save still notices another administrator's change.</p>
 *
 * @param html the typed footer, cleaned as a save would clean it
 * @param fingerprint the fingerprint of the footer the page showed when editing began
 * @param stale whether the save was refused because another administrator changed the footer; the
 *     page then shows the current footer in the editor and this text beside it
 * @param mode the required/off choice sent with it ({@code required} or {@code off}), or {@code null}
 * @param logoShown the "Add a logo to emails" choice sent with it, or {@code null}
 * @since 2026-10-15
 */
public record ClinicFooterDraft(String html, String fingerprint, boolean stale, String mode, Boolean logoShown)
        implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Request parameter carrying unsaved footer text with the logo form. */
    public static final String HTML_PARAM = "clinicFooterDraft";
    /** Request parameter carrying the fingerprint the page showed, with the logo form. */
    public static final String FINGERPRINT_PARAM = "clinicFooterDraftFingerprint";

    private static final String SESSION_KEY = ClinicFooterDraft.class.getName();

    /** Typed text without settings: what the logo form carries. */
    public ClinicFooterDraft(String html, String fingerprint, boolean stale) {
        this(html, fingerprint, stale, null, null);
    }

    /**
     * Keeps typed footer text for the next view of Configure Email. Nothing is kept when the text is
     * what the page showed, has no visible text, is longer than a save would ever read, or comes
     * without a well-formed fingerprint.
     *
     * @param request the request whose session keeps the text
     * @param html the typed footer as the page sent it
     * @param shownFingerprint the fingerprint of the footer the page showed
     * @param stale whether the save was refused because the footer changed meanwhile
     */
    public static void keep(HttpServletRequest request, String html, String shownFingerprint, boolean stale) {
        keep(request, html, shownFingerprint, stale, null, null);
    }

    /**
     * Keeps typed footer text, with the settings chosen beside it in the Edit footer window, for the
     * next view of Configure Email. The settings come back only with the text: nothing is kept when
     * the text is not kept.
     *
     * @param request the request whose session keeps the text
     * @param html the typed footer as the page sent it
     * @param shownFingerprint the fingerprint of the footer the page showed
     * @param stale whether the save was refused because the footer changed meanwhile
     * @param mode the required/off choice sent with it, or {@code null}
     * @param logoShown the logo choice sent with it, or {@code null}
     */
    public static void keep(HttpServletRequest request, String html, String shownFingerprint, boolean stale,
            ClinicEmailFooterService.Mode mode, Boolean logoShown) {
        if (html == null || html.length() > 4 * EmailFooterHtml.MAX_HTML_LENGTH
                || shownFingerprint == null || !shownFingerprint.matches("[0-9a-f]{64}")) {
            return;
        }
        String cleaned = EmailFooterHtml.clean(html);
        if (cleaned.isEmpty() || ClinicEmailFooterService.fingerprint(cleaned).equals(shownFingerprint)) {
            return;
        }
        // nosemgrep: tainted-session-from-http-request, tainted-session-from-http-request-deepsemgrep -- FP (CWE-501): the administrator's own cleaned footer text, shown back only to their session
        request.getSession().setAttribute(SESSION_KEY,
                new ClinicFooterDraft(cleaned, shownFingerprint, stale, mode == null ? null : mode.value(), logoShown));
    }

    /**
     * Takes the kept draft, if any. It is removed as it is read, so it is shown on one view only.
     *
     * @param request the request for Configure Email
     * @return the draft, or {@code null} when none is kept
     */
    public static ClinicFooterDraft take(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object draft = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        return draft instanceof ClinicFooterDraft kept ? kept : null;
    }
}
