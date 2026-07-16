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
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import jakarta.servlet.http.HttpSession;

import java.util.Properties;

/**
 * Resolves the session-scoped {@code providerBean} lookup map (provider number to display name)
 * shared by document-viewer JSPs ({@code showDocument.jsp}, {@code MultiPageDocDisplay.jsp}).
 *
 * <p>Most pages in the app seed this session attribute via
 * {@code <jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>}, which
 * auto-creates an empty {@code Properties} the first time it's touched in a session. The document
 * viewers instead read it directly so it can be null the first time a session opens a document
 * without having visited one of those other pages first — this is expected, not a misconfiguration,
 * so callers fall back to an empty map rather than failing.</p>
 */
public final class ProviderBeanResolver {

    private static final String SESSION_ATTR = "providerBean";
    private static final String MISSING_LOGGED_ATTR = "providerBeanMissingLogged";

    private ProviderBeanResolver() {
        // utility
    }

    /**
     * @param session current HTTP session
     * @param docId   document id being rendered, for diagnostic logging only
     * @return the session's {@code providerBean} map, or a new empty {@code Properties} if the
     *         session hasn't been seeded yet (callers' typical
     *         {@code getProperty(providerNo, providerNo)} pattern then falls back to displaying
     *         the raw provider number instead of a formatted name)
     */
    public static Properties resolve(HttpSession session, String docId) {
        Properties p = (Properties) session.getAttribute(SESSION_ATTR);
        if (p != null) {
            return p;
        }

        // Log at INFO, once per session, since this is routinely hit on a session's first
        // document view rather than being an unexpected failure — a WARN on every view would
        // just be noise, and logging every occurrence isn't needed to diagnose the fallback.
        Boolean alreadyLogged = (Boolean) session.getAttribute(MISSING_LOGGED_ATTR);
        if (alreadyLogged == null || !alreadyLogged) {
            MiscUtils.getLogger().info(
                    "providerBean missing from session while rendering linked providers for document {}; falling back to provider IDs",
                    docId);
            session.setAttribute(MISSING_LOGGED_ATTR, Boolean.TRUE);
        }
        return new Properties();
    }
}
