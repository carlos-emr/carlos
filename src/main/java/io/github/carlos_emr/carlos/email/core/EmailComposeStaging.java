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
package io.github.carlos_emr.carlos.email.core;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.util.WebUtils;

/**
 * Stages each eForm email draft in the HTTP session under its own one-time key (#4101).
 *
 * <p>The eForm save stages a complete, immutable {@link Draft} and puts the key in its redirect to
 * the compose action, which takes exactly that draft. Two windows that save close together therefore
 * each open their own compose: a later save can no longer replace an earlier one, and a stale or
 * repeated redirect finds nothing rather than another window's draft. A draft is published whole, so
 * it cannot combine fields from overlapping saves either.</p>
 *
 * <p>The session holds at most {@value #MAX_DRAFTS} pending drafts; staging another drops the oldest.
 * The map in the session is replaced, never changed in place, and every change happens under the
 * session mutex. No database, PDF or network work happens under that lock.</p>
 *
 * @since 2026-10-01
 */
public final class EmailComposeStaging {
    /** Request parameter carrying a draft's key from the eForm save's redirect to the compose action. */
    public static final String DRAFT_PARAMETER = "draft";
    /** Pending drafts per session; staging another drops the oldest (its window then shows expired). */
    public static final int MAX_DRAFTS = 8;

    private static final String ATTRIBUTE = EmailComposeStaging.class.getName() + ".drafts";
    /** 16 random bytes, URL-safe Base64 without padding. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private EmailComposeStaging() { }

    /** Immutable, serializable session value; settings defensively copy all attachment ID arrays. */
    public record Draft(String fid, EmailAttachmentSettings settings) implements Serializable {
        public Draft {
            Objects.requireNonNull(settings, "settings");
        }

        /** Redacted: the draft carries a patient's message and identifiers. */
        @Override
        public String toString() {
            return "EmailComposeStaging.Draft";
        }
    }

    /**
     * Stages a draft under a new one-time key.
     *
     * @return the key, for the redirect to the compose action
     */
    public static String stage(HttpSession session, String fid, EmailAttachmentSettings settings) {
        Draft draft = new Draft(fid, settings);
        String key = newKey();
        put(session, key, draft);
        return key;
    }

    /**
     * Takes the draft staged under {@code key}, once.
     *
     * @return the draft, or {@code null} when the key is missing, malformed, already used or dropped
     */
    public static Draft take(HttpSession session, String key) {
        if (!isKey(key)) {
            return null;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<String, Draft> drafts = drafts(session);
            if (!drafts.containsKey(key)) {
                return null;
            }
            LinkedHashMap<String, Draft> next = new LinkedHashMap<>(drafts);
            Draft draft = next.remove(key);
            publish(session, next);
            return draft;
        }
    }

    /**
     * Puts a taken draft back under its own key, for example after its preparation failed, so the
     * same window can try again. Another window's draft is never touched.
     */
    public static void restore(HttpSession session, String key, Draft draft) {
        if (isKey(key) && draft != null) {
            put(session, key, draft);
        }
    }

    /** @return whether {@code key} has the shape of a staging key; says nothing about whether it is staged */
    public static boolean isKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    private static void put(HttpSession session, String key, Draft draft) {
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Draft> next = new LinkedHashMap<>(drafts(session));
            next.remove(key);
            next.put(key, draft);
            Iterator<String> oldest = next.keySet().iterator();
            while (next.size() > MAX_DRAFTS) {
                oldest.next();
                oldest.remove();
            }
            publish(session, next);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Draft> drafts(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, Draft>) map : Map.of();
    }

    /** Replaces the session value, so a container that persists or replicates sessions sees the change. */
    private static void publish(HttpSession session, LinkedHashMap<String, Draft> next) {
        if (next.isEmpty()) {
            session.removeAttribute(ATTRIBUTE);
        } else {
            session.setAttribute(ATTRIBUTE, Collections.unmodifiableMap(next));
        }
    }

    private static String newKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
