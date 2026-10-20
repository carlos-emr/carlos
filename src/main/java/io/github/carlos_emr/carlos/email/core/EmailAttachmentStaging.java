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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpSession;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import org.springframework.web.util.WebUtils;

/**
 * Holds the attachments each open email composer will send, under that composer's own one-time
 * key (#4425).
 *
 * <p>The attachments used to live in one session-wide attribute that every compose (and every
 * resend from Manage Emails) overwrote. With two patients' composers open, sending from the first
 * window emailed the patient a PDF prepared for the second. Each compose now stages its prepared
 * attachments here together with the patient they were prepared for, renders the key into its own
 * form, and the send takes exactly that entry, once. A missing, reused or dropped key yields
 * nothing, never another window's attachments.</p>
 *
 * <p>Staged attachments are detached copies holding only what the send needs (name, server-side
 * path, type, record id, size and preview token), so a staged entry never references a persistent
 * {@code EmailLog}. The session holds at most {@value #MAX_ENTRIES} entries; staging another drops
 * the oldest, whose window then refuses to send rather than send something else. As in
 * {@link EmailComposeStaging}, the map in the session is replaced, never changed in place, and every
 * change happens under the session mutex.</p>
 *
 * @since 2026-10-08
 */
public final class EmailAttachmentStaging {
    /** Form parameter carrying a composer's key from the compose page to the send action. */
    public static final String KEY_PARAMETER = "emailAttachmentKey";
    /** Request attribute the compose page reads the key from. */
    public static final String KEY_ATTRIBUTE = "emailAttachmentKey";
    /** Open composers per session; staging another drops the oldest (that window can no longer send). */
    public static final int MAX_ENTRIES = 16;

    private static final String ATTRIBUTE = EmailAttachmentStaging.class.getName() + ".entries";
    /** 16 random bytes, URL-safe Base64 without padding. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_-]{22}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private EmailAttachmentStaging() { }

    /**
     * The attachments one composer prepared, and the patient it prepared them for.
     *
     * @param demographicNo the patient the composer was opened for; the send refuses any other
     * @param attachments   detached, unmodifiable copies of the prepared attachments
     */
    public record Prepared(int demographicNo, List<EmailAttachment> attachments) implements Serializable {
        public Prepared {
            attachments = Collections.unmodifiableList(new ArrayList<>(attachments));
        }

        /** Redacted: the entry names a patient's record ids and files. */
        @Override
        public String toString() {
            return "EmailAttachmentStaging.Prepared";
        }
    }

    /**
     * Stages a composer's prepared attachments under a new one-time key.
     *
     * @param session       the provider's session
     * @param demographicNo the patient the attachments were prepared for
     * @param attachments   the prepared attachments; copied, so later changes to them are not seen
     * @return the staged entry, whose copies the compose page should display
     */
    public static Staged stage(HttpSession session, int demographicNo, List<EmailAttachment> attachments) {
        List<EmailAttachment> copies = new ArrayList<>();
        if (attachments != null) {
            for (EmailAttachment attachment : attachments) {
                copies.add(copyOf(attachment));
            }
        }
        Prepared prepared = new Prepared(demographicNo, copies);
        String key = newKey();
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Prepared> next = new LinkedHashMap<>(entries(session));
            next.put(key, prepared);
            Iterator<String> oldest = next.keySet().iterator();
            while (next.size() > MAX_ENTRIES) {
                oldest.next();
                oldest.remove();
            }
            publish(session, next);
        }
        return new Staged(key, prepared);
    }

    /**
     * Takes the entry staged under {@code key}, once.
     *
     * @return the entry, or {@code null} when the key is missing, malformed, already used or dropped
     */
    public static Prepared take(HttpSession session, String key) {
        if (!isKey(key)) {
            return null;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<String, Prepared> entries = entries(session);
            if (!entries.containsKey(key)) {
                return null;
            }
            LinkedHashMap<String, Prepared> next = new LinkedHashMap<>(entries);
            Prepared prepared = next.remove(key);
            publish(session, next);
            return prepared;
        }
    }

    /** @return whether {@code key} has the shape of a staging key; says nothing about whether it is staged */
    public static boolean isKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    /** A staged entry and the key that takes it. */
    public record Staged(String key, Prepared prepared) {
        @Override
        public String toString() {
            return "EmailAttachmentStaging.Staged";
        }
    }

    private static EmailAttachment copyOf(EmailAttachment source) {
        EmailAttachment copy = new EmailAttachment(source.getFileName(), source.getFilePath(),
                source.getDocumentType(), source.getDocumentId(), source.getFileSize());
        copy.setPreviewToken(source.getPreviewToken());
        return copy;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Prepared> entries(HttpSession session) {
        Object value = session.getAttribute(ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, Prepared>) map : Map.of();
    }

    /** Replaces the session value, so a container that persists or replicates sessions sees the change. */
    private static void publish(HttpSession session, LinkedHashMap<String, Prepared> next) {
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
