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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.util.WebUtils;

/**
 * Holds the attachments of each open Messenger compose window under that window's own key
 * (issue #4673).
 *
 * <p>The attachments of the message being written used to live on the login-wide
 * {@link MsgSessionBean}. With two compose windows open, for two patients, the chart PDFs attached
 * in the second window went out with the message sent from the first, linked to the first
 * patient; and opening another compose window, the inbox or a patient's message list discarded
 * whatever the first window had attached. Each compose page now carries a random key in its form
 * and in the attachment chooser it opens; the chooser stages under that key, and the send takes
 * exactly that entry. A missing, malformed, already-sent or dropped key yields nothing, never
 * another window's attachments.</p>
 *
 * <p>Chart PDFs are staged with the patient they were rendered for, and the send refuses to put
 * them on a message linked to anyone else. The session holds at most {@value #MAX_ENTRIES}
 * entries, since they carry whole PDFs; staging for another window drops the oldest, whose window
 * then sends without attachments. As in {@code EmailAttachmentStaging}, the map in the session is
 * replaced, never changed in place, and every change happens under the session mutex.</p>
 *
 * @since 2026-10-16
 */
public final class MsgAttachmentStaging {

    /** Form and query parameter carrying a compose window's key. */
    public static final String KEY_PARAMETER = "attachmentKey";

    /** Request attribute by which an action hands the compose page the key to keep. */
    public static final String KEY_ATTRIBUTE = MsgAttachmentStaging.class.getName() + ".key";

    /** Compose windows with attachments per session; staging for another drops the oldest. */
    public static final int MAX_ENTRIES = 8;

    private static final String ATTRIBUTE = MsgAttachmentStaging.class.getName() + ".entries";

    /** 16 random bytes, URL-safe Base64 without padding. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{22}");

    private static final SecureRandom RANDOM = new SecureRandom();

    private MsgAttachmentStaging() {
    }

    /**
     * What one compose window has attached.
     *
     * @param pdfDemographicNo the patient the chart PDFs were rendered for, or {@code 0} when
     *                         there are none
     * @param pdfAttachment    the stored chart-PDF records ({@link MsgSessionBean} format), or null
     * @param itemAttachment   transferred chart items (document-transfer XML), or null
     */
    public record Staged(int pdfDemographicNo, String pdfAttachment, String itemAttachment) implements Serializable {

        /** @return whether nothing is attached */
        public boolean isEmpty() {
            return pdfAttachment == null && itemAttachment == null;
        }

        /** Redacted: the entry carries a patient's chart. */
        @Override
        public String toString() {
            return "MsgAttachmentStaging.Staged";
        }
    }

    /** @return a new random key for a compose window */
    public static String newKey() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** @return whether {@code key} has the shape of a compose key; says nothing about what is staged */
    public static boolean isKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /**
     * Replaces the chart PDFs of one compose window with {@code pdfAttachment}, rendered for
     * {@code demographicNo}. The window's other attachments stay. A null set clears the PDFs.
     *
     * @throws IllegalArgumentException if {@code key} is not a compose key
     */
    public static void stagePdfs(HttpSession session, String key, int demographicNo, String pdfAttachment) {
        requireKey(key);
        synchronized (WebUtils.getSessionMutex(session)) {
            Staged current = entries(session).get(key);
            String items = current == null ? null : current.itemAttachment();
            put(session, key, new Staged(pdfAttachment == null ? 0 : demographicNo, pdfAttachment, items));
        }
    }

    /**
     * Replaces the transferred chart items of one compose window. Its chart PDFs stay.
     *
     * @throws IllegalArgumentException if {@code key} is not a compose key
     */
    public static void stageItems(HttpSession session, String key, String itemAttachment) {
        requireKey(key);
        synchronized (WebUtils.getSessionMutex(session)) {
            Staged current = entries(session).get(key);
            put(session, key, current == null
                    ? new Staged(0, null, itemAttachment)
                    : new Staged(current.pdfDemographicNo(), current.pdfAttachment(), itemAttachment));
        }
    }

    /**
     * @return what the window with {@code key} has attached, or null when nothing is staged
     *         under it or the key is malformed; the entry stays staged
     */
    public static Staged peek(HttpSession session, String key) {
        if (session == null || !isKey(key)) {
            return null;
        }
        return entries(session).get(key);
    }

    /**
     * Takes what the window with {@code key} has attached, once.
     *
     * @return the entry, or null when the key is missing, malformed, already taken or dropped
     */
    public static Staged take(HttpSession session, String key) {
        if (session == null || !isKey(key)) {
            return null;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<String, Staged> entries = entries(session);
            if (!entries.containsKey(key)) {
                return null;
            }
            LinkedHashMap<String, Staged> next = new LinkedHashMap<>(entries);
            Staged staged = next.remove(key);
            publish(session, next);
            return staged;
        }
    }

    /**
     * Puts back an entry that {@link #take} handed to a send that did not go out, unless the
     * window has staged something newer under the same key since.
     */
    public static void restore(HttpSession session, String key, Staged staged) {
        if (session == null || !isKey(key) || staged == null || staged.isEmpty()) {
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            if (!entries(session).containsKey(key)) {
                put(session, key, staged);
            }
        }
    }

    /** Drops what the window with {@code key} has attached; a malformed key changes nothing. */
    public static void discard(HttpSession session, String key) {
        take(session, key);
    }

    private static void requireKey(String key) {
        if (!isKey(key)) {
            throw new IllegalArgumentException("Invalid compose key");
        }
    }

    /** Stores {@code staged} as the newest entry, or removes the key when it holds nothing. */
    private static void put(HttpSession session, String key, Staged staged) {
        LinkedHashMap<String, Staged> next = new LinkedHashMap<>(entries(session));
        next.remove(key);
        if (!staged.isEmpty()) {
            next.put(key, staged);
            Iterator<String> oldest = next.keySet().iterator();
            while (next.size() > MAX_ENTRIES) {
                oldest.next();
                oldest.remove();
            }
        }
        publish(session, next);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Staged> entries(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, Staged>) map : Map.of();
    }

    /** Replaces the session value, so a container that persists or replicates sessions sees the change. */
    private static void publish(HttpSession session, LinkedHashMap<String, Staged> next) {
        if (next.isEmpty()) {
            session.removeAttribute(ATTRIBUTE);
        } else {
            session.setAttribute(ATTRIBUTE, Collections.unmodifiableMap(next));
        }
    }
}
