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
package io.github.carlos_emr.carlos.prevention;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.WebUtils;

/**
 * Session-scoped permission to save one rendered prevention form once (issue #4410).
 *
 * <p>{@code AddPreventionData.jsp} posts the classic way, so a double click, a double Enter or a
 * second click while a slow save is out sends the same form twice, and each request used to insert
 * its own prevention. Each rendering now carries a fresh token bound to the patient and to the
 * record it edits (empty for a new prevention). The first request to present it claims it; a
 * repeat of the same form is answered from that first request's outcome instead of writing again:</p>
 * <ul>
 *   <li>the first save committed (or may have): the repeat writes nothing and reports success, so
 *       the popup closes exactly as it would have;</li>
 *   <li>the first save is still running: the repeat waits a bounded time for its outcome;</li>
 *   <li>the first save provably wrote nothing (it never reached storage, or rolled back): the
 *       token is free again and the repeat saves, so a retry after an error is not suppressed.</li>
 * </ul>
 * <p>A later, intentional add opens the form again and so gets a new token. Callers that post
 * without a token (the Rh-injection forms) keep their previous behaviour; a token that is not
 * this session's, or not for this patient and record, is refused.</p>
 *
 * <p>Modelled on {@code EFormSubmissionGuard}. Entries are immutable records copied under the
 * session mutex, so the session attribute stays serializable and is never mutated in place.</p>
 *
 * @since 2026-10-08
 */
public final class PreventionSubmissionGuard {
    /** The request parameter, and hidden form field, that carries the token. */
    public static final String PARAMETER = "carlosPreventionSubmission";
    private static final String SESSION_KEY = PreventionSubmissionGuard.class.getName();
    private static final int MAX_PENDING = 64;
    private static final long POLL_MILLIS = 100;

    private enum State { ISSUED, SAVING, SAVED }

    /** {@code savedId} is the prevention the first submission saved, once it is {@code SAVED}. */
    private record Entry(String patient, String record, State state, Integer savedId) implements Serializable { }

    private record Pending(LinkedHashMap<String, Entry> entries) implements Serializable { }

    /** How a submission presenting a token is to be handled. */
    public enum Verdict {
        /** The token was free and is now claimed: save, then close the claim. */
        PROCEED,
        /** An earlier submission of this form saved (or may have): write nothing, report success. */
        ALREADY_SAVED,
        /** An earlier submission of this form is still saving after the wait: write nothing. */
        IN_PROGRESS,
        /** Not a token this session issued for this patient and record: write nothing. */
        STALE
    }

    /**
     * The verdict, and for {@link Verdict#PROCEED} the claim to close once the save is over.
     *
     * @param verdict how to handle the submission
     * @param claim the reservation, non-null only for {@link Verdict#PROCEED}
     * @param savedId for {@link Verdict#ALREADY_SAVED}, the prevention the first submission saved
     *        when it reported one (null when its outcome was not confirmed)
     */
    public record Attempt(Verdict verdict, Claim claim, Integer savedId) {
        Attempt(Verdict verdict, Claim claim) {
            this(verdict, claim, null);
        }
    }

    private PreventionSubmissionGuard() { }

    /**
     * Issues a fresh token for one rendering of the prevention form.
     *
     * @param session the clinician's session
     * @param patient the demographic number the form saves to
     * @param record the prevention id the form edits, or null/empty for a new prevention
     * @return the token to render into the form's {@link #PARAMETER} field
     */
    public static String issue(HttpSession session, String patient, String record) {
        String token = UUID.randomUUID().toString();
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Entry> entries = copyEntries(session);
            putBounded(entries, token, new Entry(String.valueOf(patient), normalize(record), State.ISSUED, null));
            session.setAttribute(SESSION_KEY, new Pending(entries));
        }
        return token;
    }

    /**
     * Claims {@code token} for a save, or says why this submission must not write. A token whose
     * first submission is still saving is polled until that save ends or {@code waitMillis} runs out.
     *
     * @param session the clinician's session
     * @param token the submitted token
     * @param patient the demographic number the submission saves to
     * @param record the prevention id it edits, or null/empty for a new prevention
     * @param waitMillis how long to wait for an in-flight first submission
     * @return the verdict, with the claim when it is {@link Verdict#PROCEED}
     */
    public static Attempt attempt(HttpSession session, String token, String patient, String record, long waitMillis) {
        if (token == null || token.length() != 36) {
            return new Attempt(Verdict.STALE, null);
        }
        String recordKey = normalize(record);
        long deadline = System.nanoTime() + Math.max(0, waitMillis) * 1_000_000L;
        while (true) {
            synchronized (WebUtils.getSessionMutex(session)) {
                LinkedHashMap<String, Entry> entries = copyEntries(session);
                Entry entry = entries.get(token);
                if (entry == null || !entry.patient().equals(String.valueOf(patient)) || !entry.record().equals(recordKey)) {
                    return new Attempt(Verdict.STALE, null);
                }
                switch (entry.state()) {
                    case ISSUED -> {
                        entries.put(token, new Entry(entry.patient(), entry.record(), State.SAVING, null));
                        session.setAttribute(SESSION_KEY, new Pending(entries));
                        return new Attempt(Verdict.PROCEED, new Claim(session, token, entry));
                    }
                    case SAVED -> {
                        return new Attempt(Verdict.ALREADY_SAVED, null, entry.savedId());
                    }
                    default -> {
                        // SAVING: the first submission is still writing; poll until it settles.
                        if (System.nanoTime() - deadline >= 0) {
                            return new Attempt(Verdict.IN_PROGRESS, null);
                        }
                    }
                }
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new Attempt(Verdict.IN_PROGRESS, null);
            }
        }
    }

    private static String normalize(String record) {
        return record == null || "null".equals(record) ? "" : record;
    }

    /**
     * Adds an entry, evicting first a finished one, then the oldest unclaimed one, and never one
     * whose save is in flight unless every entry is (a repeat of it then reads as stale and is
     * refused, which writes nothing).
     */
    private static void putBounded(LinkedHashMap<String, Entry> entries, String token, Entry entry) {
        if (!entries.containsKey(token) && entries.size() >= MAX_PENDING) {
            String evicted = firstIn(entries, State.SAVED);
            if (evicted == null) evicted = firstIn(entries, State.ISSUED);
            if (evicted == null) evicted = entries.firstEntry().getKey();
            entries.remove(evicted);
        }
        entries.put(token, entry);
    }

    private static String firstIn(LinkedHashMap<String, Entry> entries, State state) {
        return entries.entrySet().stream().filter(item -> item.getValue().state() == state)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static LinkedHashMap<String, Entry> copyEntries(HttpSession session) {
        return session.getAttribute(SESSION_KEY) instanceof Pending(var entries)
                ? new LinkedHashMap<>(entries) : new LinkedHashMap<>();
    }

    /**
     * A request-local reservation of one token. Closing it records the outcome: the token is
     * freed for a retry only when the save provably wrote nothing (storage never started, or its
     * transaction rolled back); otherwise, including a commit whose outcome is unknown, it is
     * marked saved so that no repeat of the form can write a second record.
     */
    public static final class Claim implements AutoCloseable {
        private final HttpSession session;
        private final String token;
        private final Entry issued;
        private boolean closed;
        private volatile int completion = -1;
        private Integer savedId;

        private Claim(HttpSession session, String token, Entry issued) {
            this.session = session;
            this.token = token;
            this.issued = issued;
        }

        /** Called first inside the save's transaction, before anything is written. */
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

        /**
         * Records the prevention this submission saved, so a repeat of the form can continue to
         * the same record (the DHIR review of a repeated "Save &amp; Submit").
         *
         * @param preventionId the saved prevention's id
         */
        public synchronized void saved(Integer preventionId) {
            this.savedId = preventionId;
        }

        /** Whether the transaction outcome proves that this attempt wrote nothing. */
        public boolean canRetry() {
            return completion == -1 || completion == TransactionSynchronization.STATUS_ROLLED_BACK;
        }

        @Override
        public synchronized void close() {
            if (closed) return;
            closed = true;
            boolean retry = canRetry();
            State outcome = retry ? State.ISSUED : State.SAVED;
            synchronized (WebUtils.getSessionMutex(session)) {
                LinkedHashMap<String, Entry> entries = copyEntries(session);
                putBounded(entries, token, new Entry(issued.patient(), issued.record(), outcome, retry ? null : savedId));
                session.setAttribute(SESSION_KEY, new Pending(entries));
            }
        }
    }
}
