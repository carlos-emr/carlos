/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform;

import java.io.Serializable;
import java.util.LinkedHashMap;
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
            if (entries.size() >= MAX_PENDING) {
                entries.pollFirstEntry();
            }
            entries.put(token, new Entry(fid, patient, false));
            session.setAttribute(SESSION_KEY, new Pending(entries));
        }
        return token;
    }

    /** Atomically reserves an issued token before any clinical or opener-session mutations. */
    public static Claim claim(HttpSession session, String token, String fid, String patient) {
        if (token == null || token.length() != 36) {
            return null;
        }
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Entry> entries = copyEntries(session);
            Entry entry = entries.get(token);
            if (entry == null || entry.claimed() || !entry.fid().equals(fid)
                    || !entry.patient().equals(patient)) {
                return null;
            }
            entries.put(token, new Entry(fid, patient, true));
            session.setAttribute(SESSION_KEY, new Pending(entries));
            return new Claim(session, token);
        }
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
        private volatile int completion = -1;

        private Claim(HttpSession session, String token) {
            this.session = session;
            this.token = token;
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
        public void close() {
            if (!canRetry()) {
                return;
            }
            synchronized (WebUtils.getSessionMutex(session)) {
                LinkedHashMap<String, Entry> entries = copyEntries(session);
                Entry entry = entries.get(token);
                if (entry != null) {
                    entries.put(token, new Entry(entry.fid(), entry.patient(), false));
                    session.setAttribute(SESSION_KEY, new Pending(entries));
                }
            }
        }
    }
}
