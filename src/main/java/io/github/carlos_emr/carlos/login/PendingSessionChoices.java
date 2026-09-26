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
package io.github.carlos_emr.carlos.login;

import jakarta.servlet.http.HttpSession;

/**
 * Session-facing contract for a login waiting on the concurrent-session chooser (issue #3980).
 *
 * <p>The HTTP session holds only an opaque token. The authenticated payload lives in
 * {@link PendingSessionChoiceCache}. Logout and the session listener call
 * {@link #clearFromSession(HttpSession)} so an abandoned chooser cannot be resumed later.</p>
 */
public final class PendingSessionChoices {
    /** Session attribute holding the opaque {@link PendingSessionChoiceCache} token. */
    public static final String TOKEN_ATTR = "pendingSessionChoiceToken";

    private PendingSessionChoices() {
        // Static holder for the pending-choice session contract.
    }

    static void stage(HttpSession session, String token) {
        session.setAttribute(TOKEN_ATTR, token); // nosemgrep: tainted-session-from-http-request -- opaque server-generated cache token, not login state
    }

    /**
     * Returns the pending-choice token only when the session attribute has the expected type.
     *
     * @param session candidate session; may be {@code null}
     * @return the token, or {@code null}
     */
    public static String getToken(HttpSession session) {
        if (session == null) {
            return null;
        }
        return session.getAttribute(TOKEN_ATTR) instanceof String token ? token : null;
    }

    /**
     * Invalidates any cached pending login referenced by the session and removes the token.
     * Safe to call with {@code null}, expired or never-staged sessions.
     *
     * @param session session to clean; may be {@code null}
     */
    public static void clearFromSession(HttpSession session) {
        if (session == null) {
            return;
        }
        String token = getToken(session);
        if (token != null) {
            PendingSessionChoiceCache.getInstance().invalidate(token);
        }
        session.removeAttribute(TOKEN_ATTR);
    }
}
