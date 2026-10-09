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
package io.github.carlos_emr.carlos.eform;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.WebUtils;

/**
 * Session-scoped permission to submit one rendered eForm once. Tokens are bound to the
 * template and patient, not the form contents: separately opened forms and later edits
 * are independent submissions. Evicted or foreign tokens fail closed.
 */
public final class EFormSubmissionGuard {
    public static final String PARAMETER = "carlosEformSubmission";
    private static final String SESSION_KEY = EFormSubmissionGuard.class.getName();
    private static final int MAX_PENDING = 64;

    private record Entry(String fid, String patient, boolean claimed) implements Serializable { }
    private record Pending(LinkedHashMap<String, Entry> entries) implements Serializable { }

    private EFormSubmissionGuard() { }

    /** Issues a fresh token for a new rendering, including a saved form opened for editing. */
    public static String issue(HttpSession session, String fid, String patient) {
        String token = UUID.randomUUID().toString();
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Entry> entries = copyEntries(session);
            putBounded(entries, token, new Entry(fid, patient, false));
            session.setAttribute(SESSION_KEY, new Pending(entries));
        }
        return token;
    }

    /** A null claim is a rejection; stale distinguishes unavailable identities from known in-flight/replayed submissions. */
    public record Attempt(Claim claim, boolean stale) { }

    /** Atomically reserves an issued token before any clinical or opener-session mutations. */
    public static Attempt attempt(HttpSession session, String token, String fid, String patient) {
        if (token == null || token.length() != 36) {
            return new Attempt(null, true);
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Entry> entries = copyEntries(session);
            Entry entry = entries.get(token);
            if (entry == null || !entry.fid().equals(fid) || !entry.patient().equals(patient)) {
                return new Attempt(null, true);
            }
            if (entry.claimed()) {
                return new Attempt(null, false);
            }
            entries.put(token, new Entry(fid, patient, true));
            session.setAttribute(SESSION_KEY, new Pending(entries));
            return new Attempt(new Claim(session, token, entry), false);
        }
    }

    private static void putBounded(LinkedHashMap<String, Entry> entries, String token, Entry entry) {
        if (!entries.containsKey(token) && entries.size() >= MAX_PENDING) {
            String consumed = entries.entrySet().stream().filter(item -> item.getValue().claimed())
                    .map(Map.Entry::getKey).findFirst().orElse(entries.firstEntry().getKey());
            entries.remove(consumed);
        }
        entries.put(token, entry);
    }

    private static LinkedHashMap<String, Entry> copyEntries(HttpSession session) {
        return session.getAttribute(SESSION_KEY) instanceof Pending(var entries)
                ? new LinkedHashMap<>(entries) : new LinkedHashMap<>();
    }

    /**
     * Request-local reservation. A failed save is retryable only before storage starts or
     * after a confirmed rollback. Commit acknowledgement failures and failures in later
     * chart-note, eDoc or rendering work must never permit the clinical save to run again.
     */
    public static final class Claim implements AutoCloseable {
        private final HttpSession session;
        private final String token;
        private final Entry retryEntry;
        private boolean closed;
        private volatile int completion = -1;

        private Claim(HttpSession session, String token, Entry retryEntry) {
            this.session = session;
            this.token = token;
            this.retryEntry = retryEntry;
        }

        /** Called first inside the transaction callback, before saving the form or its attachments. */
        public void storageStarted() {
            completion = TransactionSynchronization.STATUS_UNKNOWN;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        completion = status;
                    }
                });
            }
        }

        /** Whether the transaction outcome proves that this attempt can safely be retried. */
        public boolean canRetry() {
            return completion == -1 || completion == TransactionSynchronization.STATUS_ROLLED_BACK;
        }

        @Override
        public synchronized void close() {
            // A repeated close must not release a later retry's reservation.
            if (closed) return;
            closed = true;
            if (!canRetry()) {
                return;
            }
            synchronized (WebUtils.getSessionMutex(session)) {
                LinkedHashMap<String, Entry> entries = copyEntries(session);
                // Keep the original scope in this request-local claim. Even if new views evicted
                // its session entry while storage was in flight, a proven rollback can restore
                // the retry without exceeding the session bound. Never restore an uncertain save.
                putBounded(entries, token, retryEntry);
                session.setAttribute(SESSION_KEY, new Pending(entries));
            }
        }
    }
}
