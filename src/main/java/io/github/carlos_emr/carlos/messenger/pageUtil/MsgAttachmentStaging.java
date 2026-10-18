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
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
 * <p>A message view saves its message's chart items for forwarding under a key of the message,
 * {@link #forwardKey}: the same on every view of that message in the session, so the items saved
 * from its Attachment window survive the view being shown again. That key is only a source: each
 * compose page its Compose link, Reply, Reply All or Forward opens gets a key of its own with a
 * copy of those items ({@link #composeKey}), so two compose windows opened from one message keep
 * their own attachments too. A send from such a window releases the message's saved items
 * ({@link #releaseSource}), as sending cleared them from the messenger bean, so a later reply or
 * forward of that message does not attach them again; windows already open keep their copies.</p>
 *
 * <p>Chart PDFs are staged with the patient they were rendered for, and the send refuses to put
 * them on a message linked to anyone else. The session holds at most {@value #MAX_ENTRIES}
 * entries, since they carry whole PDFs; staging for another window drops the oldest. The dropped
 * key is remembered ({@link #claimDropped}), so that window's send is refused once with an
 * explanation instead of going out without the attachments its page showed. As in
 * {@code EmailAttachmentStaging}, the values in the session are replaced, never changed in place,
 * and every change happens under the session mutex.</p>
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

    /** Keys whose entries the cap dropped, until their window is told or stages again. */
    private static final String DROPPED_ATTRIBUTE = MsgAttachmentStaging.class.getName() + ".dropped";

    /** Dropped keys remembered; past this, the oldest is forgotten. */
    private static final int DROPPED_MEMORY = 4 * MAX_ENTRIES;

    /** Compose keys that started with a message's saved items, each with its message's forwarding key. */
    private static final String SOURCES_ATTRIBUTE = MsgAttachmentStaging.class.getName() + ".sources";

    /** The session's random secret from which {@link #forwardKey} derives each message's key. */
    private static final String FORWARD_SECRET_ATTRIBUTE = MsgAttachmentStaging.class.getName() + ".forwardSecret";

    private static final String FORWARD_KEY_ALGORITHM = "HmacSHA256";

    /** Key length in bytes: 16, URL-safe Base64 without padding. */
    private static final int KEY_BYTES = 16;

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
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * The key under which the views of message {@code messageId} save its chart items for
     * forwarding. It is the same on every view of that message in this session, so the items
     * saved from a view's Attachment window survive the view being shown again (linking a patient
     * shows it again), and every compose page opened from the message starts with a copy of them
     * ({@link #composeKey}). No compose window sends under it. It is derived from a random secret
     * kept in the session, so it cannot be predicted from the message id and differs from one
     * login to the next.
     *
     * @throws IllegalArgumentException if {@code messageId} is not positive
     */
    public static String forwardKey(HttpSession session, int messageId) {
        if (messageId <= 0) {
            throw new IllegalArgumentException("Invalid message id");
        }
        try {
            Mac mac = Mac.getInstance(FORWARD_KEY_ALGORITHM);
            mac.init(new SecretKeySpec(forwardSecret(session), FORWARD_KEY_ALGORITHM));
            byte[] digest = mac.doFinal(("forward:" + messageId).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(digest, KEY_BYTES));
        } catch (GeneralSecurityException unavailable) {
            // HmacSHA256 is one of the algorithms every Java platform must provide.
            throw new IllegalStateException(FORWARD_KEY_ALGORITHM + " is unavailable", unavailable);
        }
    }

    /**
     * A new compose window's key for a reply, forward or new message opened from message
     * {@code messageId}: a new random key that starts with a copy of the chart items saved for
     * that message ({@link #forwardKey}). The saved items stay where they are, for the message's
     * other windows, and what this window attaches and sends stays its own. Compose windows opened
     * from one message used to share the message's key, so the chart PDFs attached in one went
     * out with the message sent from another, to its recipients.
     *
     * @throws IllegalArgumentException if {@code messageId} is not positive
     */
    public static String composeKey(HttpSession session, int messageId) {
        String key = newKey();
        String source = forwardKey(session, messageId);
        synchronized (WebUtils.getSessionMutex(session)) {
            Staged saved = entries(session).get(source);
            if (saved != null && saved.itemAttachment() != null) {
                // The saved items are made the newest entry first, so a copy that overfills the
                // cap drops another window's entry, whose send then says so, and not the
                // message's own, whose loss no send would report.
                put(session, source, saved);
                put(session, key, new Staged(0, null, saved.itemAttachment()));
                rememberSource(session, key, source);
            } else if (dropped(session).contains(source)) {
                // The cap released the items saved for this message before this window opened:
                // its first send is refused with the reason, as the message's own key's would be.
                // The message stays marked, so every later window opened from it is told too
                // rather than sending without them unannounced.
                rememberDropped(session, key, List.of(key));
            }
        }
        return key;
    }

    /**
     * After a message was sent from the compose window with {@code key}: when that window was
     * opened from a message whose chart items were saved ({@link #composeKey}), drops those saved
     * items, as sending used to clear them from the messenger bean, so a later reply or forward of
     * that message does not attach them again. Compose windows already open keep their own copies.
     */
    public static void releaseSource(HttpSession session, String key) {
        if (session == null || !isKey(key)) {
            return;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            Map<String, String> sources = sources(session);
            String source = sources.get(key);
            if (source == null) {
                return;
            }
            LinkedHashMap<String, String> next = new LinkedHashMap<>(sources);
            next.remove(key);
            publishSources(session, next);
            take(session, source);
        }
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

    /**
     * Drops what the window with {@code key} has attached, and forgets that the cap dropped it
     * earlier; a malformed key changes nothing.
     */
    public static void discard(HttpSession session, String key) {
        take(session, key);
        claimDropped(session, key);
    }

    /**
     * Whether the cap ({@value #MAX_ENTRIES} windows) dropped what the window with {@code key}
     * had attached, with nothing staged under the key since. Answers true once: the send that is
     * refused for it tells the user, and the next send from that window goes out without them.
     */
    public static boolean claimDropped(HttpSession session, String key) {
        if (session == null || !isKey(key)) {
            return false;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            Set<String> dropped = dropped(session);
            if (!dropped.contains(key)) {
                return false;
            }
            LinkedHashSet<String> next = new LinkedHashSet<>(dropped);
            next.remove(key);
            publishDropped(session, next);
            return true;
        }
    }

    private static void requireKey(String key) {
        if (!isKey(key)) {
            throw new IllegalArgumentException("Invalid compose key");
        }
    }

    /**
     * Stores {@code staged} as the newest entry, or removes the key when it holds nothing, and
     * remembers the keys the cap drops to make room.
     */
    private static void put(HttpSession session, String key, Staged staged) {
        LinkedHashMap<String, Staged> next = new LinkedHashMap<>(entries(session));
        next.remove(key);
        List<String> evicted = new ArrayList<>();
        if (!staged.isEmpty()) {
            next.put(key, staged);
            Iterator<String> oldest = next.keySet().iterator();
            while (next.size() > MAX_ENTRIES) {
                evicted.add(oldest.next());
                oldest.remove();
            }
        }
        publish(session, next);
        rememberDropped(session, key, evicted);
    }

    /**
     * Adds the keys just {@code evicted} to the dropped ones, and forgets {@code restaged}, whose
     * window has just staged (or cleared) its attachments itself.
     */
    private static void rememberDropped(HttpSession session, String restaged, List<String> evicted) {
        Set<String> dropped = dropped(session);
        if (evicted.isEmpty() && !dropped.contains(restaged)) {
            return;
        }
        LinkedHashSet<String> next = new LinkedHashSet<>(dropped);
        next.remove(restaged);
        next.addAll(evicted);
        Iterator<String> oldest = next.iterator();
        while (next.size() > DROPPED_MEMORY) {
            oldest.next();
            oldest.remove();
        }
        publishDropped(session, next);
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

    /** Remembers which message's saved items {@code key} started with; the oldest are forgotten. */
    private static void rememberSource(HttpSession session, String key, String source) {
        LinkedHashMap<String, String> next = new LinkedHashMap<>(sources(session));
        next.put(key, source);
        Iterator<String> oldest = next.keySet().iterator();
        while (next.size() > DROPPED_MEMORY) {
            oldest.next();
            oldest.remove();
        }
        publishSources(session, next);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> sources(HttpSession session) {
        Object value = session.getAttribute(SOURCES_ATTRIBUTE);
        return value instanceof Map<?, ?> map ? (Map<String, String>) map : Map.of();
    }

    private static void publishSources(HttpSession session, LinkedHashMap<String, String> next) {
        if (next.isEmpty()) {
            session.removeAttribute(SOURCES_ATTRIBUTE);
        } else {
            session.setAttribute(SOURCES_ATTRIBUTE, Collections.unmodifiableMap(next));
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> dropped(HttpSession session) {
        Object value = session.getAttribute(DROPPED_ATTRIBUTE);
        return value instanceof Set<?> set ? (Set<String>) set : Set.of();
    }

    private static void publishDropped(HttpSession session, LinkedHashSet<String> next) {
        if (next.isEmpty()) {
            session.removeAttribute(DROPPED_ATTRIBUTE);
        } else {
            session.setAttribute(DROPPED_ATTRIBUTE, Collections.unmodifiableSet(next));
        }
    }

    /** The session's forwarding secret, made on first use. */
    private static byte[] forwardSecret(HttpSession session) {
        synchronized (WebUtils.getSessionMutex(session)) {
            if (session.getAttribute(FORWARD_SECRET_ATTRIBUTE) instanceof String encoded) {
                return Base64.getDecoder().decode(encoded);
            }
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            session.setAttribute(FORWARD_SECRET_ATTRIBUTE, Base64.getEncoder().encodeToString(secret));
            return secret;
        }
    }
}
