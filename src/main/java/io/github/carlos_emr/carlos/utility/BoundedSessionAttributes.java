/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.util.WebUtils;

/** Bounds disposable per-patient entry metadata; never use this for unsaved clinical drafts. */
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
