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

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.util.WebUtils;

/**
 * Bounds disposable per-patient entry metadata; never use this for unsaved clinical drafts.
 *
 * @since 2026-10-24
 */
public final class BoundedSessionAttributes {
    /** Entry metadata for the most recently opened hundred patients is retained per category. */
    public static final int MAX_ENTRIES = 100;
    private static final String PREFIX = BoundedSessionAttributes.class.getName() + ".";

    private BoundedSessionAttributes() { }

    /** Stores a value and retires the oldest entry in this category when the limit is reached. */
    public static void put(HttpSession session, String category, String key, Object value) {
        synchronized (WebUtils.getSessionMutex(session)) {
            List<String> keys = new ArrayList<>();
            if (session.getAttribute(PREFIX + category) instanceof List<?> existing) {
                for (Object entry : existing) {
                    if (entry instanceof String name && !name.equals(key)
                            && session.getAttribute(name) != null) keys.add(name);
                }
            }
            keys.add(key);
            while (keys.size() > MAX_ENTRIES) session.removeAttribute(keys.remove(0));
            session.setAttribute(key, value);
            session.setAttribute(PREFIX + category, keys);
        }
    }
}
