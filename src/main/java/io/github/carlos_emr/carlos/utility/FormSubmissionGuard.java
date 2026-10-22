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

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.WebUtils;

/**
 * Session-scoped permission to save one rendered create form once (issue #4225).
 *
 * <p>Classic form posts repeat themselves: a double click, a double Enter, a second click while a
 * slow save is still out, or a reload of a page rendered straight from the POST all send the same
 * form again, and each of those requests used to create its own appointment, bill, patient or
 * consultation service. Each rendering of a guarded form now carries a fresh token bound to the
 * form's {@link Scope} and to a caller-chosen binding (for example the patient the form bills).
 * The first request presenting it claims it; a repeat of the same rendering is answered from that
 * first request's outcome instead of writing again:</p>
 * <ul>
 *   <li>the first save committed: the repeat writes nothing and ends as the first one did,
 *       using the {@linkplain Claim#saved(String) result} the first save recorded;</li>
 *   <li>the first save may have committed but did not say what it saved: the repeat writes
 *       nothing and is refused as {@link Verdict#UNCONFIRMED}, so the user checks the record;</li>
 *   <li>the first save is still running: the repeat waits a bounded time for its outcome;</li>
 *   <li>the first save provably wrote nothing (storage never started, or its transaction rolled
 *       back): the token is free again and the repeat saves, so a retry after an error is not
 *       suppressed.</li>
 * </ul>
 * <p>A later, intentional create renders the form again and so gets a new token. This is an
 * idempotency control, not an access control: a request that carries no token keeps the guarded
 * endpoint's previous behaviour ({@link Verdict#ABSENT}), and a token this session did not issue for
 * this scope and binding is refused ({@link Verdict#STALE}) without writing.</p>
 *
 * <p>Generalises {@code PreventionSubmissionGuard} (issue #4410) for forms that need no
 * form-specific state. Entries are immutable records copied under the session mutex, so the
 * session attribute stays serializable and is never mutated in place; each scope keeps at most
 * {@value #MAX_PENDING_PER_SCOPE} entries so one busy form cannot evict another's tokens.</p>
 *
 * @since 2026-10-15
 */
public final class FormSubmissionGuard {

    /** The request parameter, and hidden form field, that carries the token. */
    public static final String PARAMETER = "carlosFormSubmission";

    /** How long a repeat waits for a first submission that is still saving. */
    public static final long DEFAULT_WAIT_MILLIS = 20_000;

    /** The Struts result every guarded action maps to {@code /WEB-INF/jsp/common/formSubmissionRefused.jsp}. */
    public static final String REFUSED_RESULT = "submissionRefused";

    /** The request attribute naming the refusal's bundle key for that page. */
    static final String MESSAGE_KEY_ATTRIBUTE = "formSubmissionMessageKey";

    static final int MAX_PENDING_PER_SCOPE = 64;
    private static final String SESSION_KEY = FormSubmissionGuard.class.getName();
    private static final long POLL_MILLIS = 100;
    private static final int TOKEN_LENGTH = 36;

    /** The guarded forms. A token only ever answers for the scope it was issued for. */
    public enum Scope {
        /** {@code appointment/AddRecord} from the Add Appointment popup. */
        APPOINTMENT_ADD,
        /** {@code billing/CA/ON/BillingONSave} from the Ontario bill review page. */
        BILLING_ON_SAVE,
        /** {@code demographic/DemographicAddRecord} from the Add Demographic form. */
        DEMOGRAPHIC_ADD,
        /** {@code encounter/AddService} from Consultation configuration &gt; Add Service. */
        CONSULTATION_SERVICE_ADD
    }

    private enum State { ISSUED, SAVING, SAVED }

    /** {@code result} is what the first submission saved, once it is {@code SAVED} and recorded one. */
    private record Entry(Scope scope, String binding, State state, String result) implements Serializable { }

    private record Pending(LinkedHashMap<String, Entry> entries) implements Serializable { }

    /** How a submission is to be handled. */
    public enum Verdict {
        /** No token was presented: the endpoint's previous, unguarded behaviour applies. */
        ABSENT,
        /** The token was free and is now claimed: save, then close the claim. */
        PROCEED,
        /** An earlier submission of this rendering saved {@link Attempt#result()}: write nothing, end as it did. */
        ALREADY_SAVED,
        /** An earlier submission may have saved but recorded no result: write nothing, ask the user to check. */
        UNCONFIRMED,
        /** An earlier submission of this rendering is still saving after the wait: write nothing. */
        IN_PROGRESS,
        /** Not a token this session issued for this scope and binding (or it was evicted): write nothing. */
        STALE
    }

    /**
     * The verdict, and for {@link Verdict#PROCEED} the claim to close once the save is over.
     *
     * @param verdict how to handle the submission
     * @param claim the reservation, non-null only for {@link Verdict#PROCEED}
     * @param result for {@link Verdict#ALREADY_SAVED}, what the first submission recorded as saved
     */
    public record Attempt(Verdict verdict, Claim claim, String result) {
        static Attempt of(Verdict verdict) {
            return new Attempt(verdict, null, null);
        }
    }

    private FormSubmissionGuard() { }

    /**
     * Issues a fresh token for one rendering of a guarded form.
     *
     * @param session the user's session
     * @param scope the form being rendered
     * @param binding what the form is bound to (for example a patient number); null means none
     * @return the token to render into the form's {@link #PARAMETER} field
     */
    public static String issue(HttpSession session, Scope scope, String binding) {
        String token = UUID.randomUUID().toString();
        synchronized (WebUtils.getSessionMutex(session)) {
            LinkedHashMap<String, Entry> entries = copyEntries(session);
            putBounded(entries, token, new Entry(scope, normalize(binding), State.ISSUED, null));
            session.setAttribute(SESSION_KEY, new Pending(entries));
        }
        return token;
    }

    /**
     * Claims {@code token} for a save, or says why this submission must not write. A token whose
     * first submission is still saving is polled until that save ends or {@code waitMillis} runs out.
     *
     * @param session the user's session
     * @param token the submitted token; null or blank is {@link Verdict#ABSENT}
     * @param scope the form the endpoint saves
     * @param binding what the submission is bound to, as passed to {@link #issue}
     * @param waitMillis how long to wait for an in-flight first submission
     * @return the verdict, with the claim when it is {@link Verdict#PROCEED}
     */
    public static Attempt attempt(HttpSession session, String token, Scope scope, String binding, long waitMillis) {
        if (token == null || token.isBlank()) {
            return Attempt.of(Verdict.ABSENT);
        }
        if (token.length() != TOKEN_LENGTH) {
            return Attempt.of(Verdict.STALE);
        }
        String bindingKey = normalize(binding);
        long deadline = System.nanoTime() + Math.max(0, waitMillis) * 1_000_000L;
        while (true) {
            synchronized (WebUtils.getSessionMutex(session)) {
                LinkedHashMap<String, Entry> entries = copyEntries(session);
                Entry entry = entries.get(token);
                if (entry == null || entry.scope() != scope || !entry.binding().equals(bindingKey)) {
                    return Attempt.of(Verdict.STALE);
                }
                switch (entry.state()) {
                    case ISSUED -> {
                        entries.put(token, new Entry(entry.scope(), entry.binding(), State.SAVING, null));
                        session.setAttribute(SESSION_KEY, new Pending(entries));
                        return new Attempt(Verdict.PROCEED, new Claim(session, token, entry), null);
                    }
                    case SAVED -> {
                        return entry.result() == null ? Attempt.of(Verdict.UNCONFIRMED)
                                : new Attempt(Verdict.ALREADY_SAVED, null, entry.result());
                    }
                    default -> {
                        // SAVING: the first submission is still writing; poll until it settles.
                        if (System.nanoTime() - deadline >= 0) {
                            return Attempt.of(Verdict.IN_PROGRESS);
                        }
                    }
                }
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Attempt.of(Verdict.IN_PROGRESS);
            }
        }
    }

    /**
     * Answers a repeat that must not write and cannot be answered with the first save's own
     * result: HTTP 409, never cached, and the localized reason for
     * {@code /WEB-INF/jsp/common/formSubmissionRefused.jsp}. Each guarded action maps
     * {@link #REFUSED_RESULT} to that page.
     *
     * @param request the refused request
     * @param response its response
     * @param verdict why it is refused ({@link Verdict#IN_PROGRESS}, {@link Verdict#UNCONFIRMED} or
     *        {@link Verdict#STALE})
     * @return {@link #REFUSED_RESULT}
     */
    public static String refuse(HttpServletRequest request, HttpServletResponse response, Verdict verdict) {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setHeader("Cache-Control", "no-store");
        request.setAttribute(MESSAGE_KEY_ATTRIBUTE, messageKey(verdict));
        return REFUSED_RESULT;
    }

    static String messageKey(Verdict verdict) {
        return switch (verdict) {
            case IN_PROGRESS -> "global.formSubmission.inProgress";
            case UNCONFIRMED -> "global.formSubmission.unconfirmed";
            default -> "global.formSubmission.stale";
        };
    }

    private static String normalize(String binding) {
        return binding == null || "null".equals(binding) ? "" : binding.trim();
    }

    /**
     * Adds an entry, evicting from the same scope first a finished one, then the oldest unclaimed
     * one, and never one whose save is in flight unless every entry of the scope is (a repeat of it
     * then reads as stale and is refused, which writes nothing).
     */
    private static void putBounded(LinkedHashMap<String, Entry> entries, String token, Entry entry) {
        if (!entries.containsKey(token)
                && entries.values().stream().filter(item -> item.scope() == entry.scope()).count() >= MAX_PENDING_PER_SCOPE) {
            String evicted = firstIn(entries, entry.scope(), State.SAVED);
            if (evicted == null) evicted = firstIn(entries, entry.scope(), State.ISSUED);
            if (evicted == null) evicted = firstIn(entries, entry.scope(), State.SAVING);
            entries.remove(evicted);
        }
        entries.put(token, entry);
    }

    private static String firstIn(LinkedHashMap<String, Entry> entries, Scope scope, State state) {
        return entries.entrySet().stream()
                .filter(item -> item.getValue().scope() == scope && item.getValue().state() == state)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private static LinkedHashMap<String, Entry> copyEntries(HttpSession session) {
        return session.getAttribute(SESSION_KEY) instanceof Pending(var entries)
                ? new LinkedHashMap<>(entries) : new LinkedHashMap<>();
    }

    /**
     * A request-local reservation of one token. Closing it records the outcome: the token is
     * freed for a retry only when the save provably wrote nothing (storage never started and no
     * result was recorded, or the save's transaction rolled back); otherwise, including a commit
     * whose outcome is unknown, it is marked saved so that no repeat of the form writes again.
     */
    public static final class Claim implements AutoCloseable {
        private static final int NOT_STARTED = -1;

        private final HttpSession session;
        private final String token;
        private final Entry issued;
        private boolean closed;
        private volatile int completion = NOT_STARTED;
        private volatile String result;

        private Claim(HttpSession session, String token, Entry issued) {
            this.session = session;
            this.token = token;
            this.issued = issued;
        }

        /**
         * Called first inside the save's transaction, before anything is written, so the claim
         * learns whether that transaction committed or rolled back. Outside a transaction the
         * outcome stays unknown and the token is never freed for a retry.
         */
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
         * Records what this submission durably saved (call it after the write committed), so a
         * repeat of the form can end exactly as this one does without writing again.
         *
         * @param savedResult an identifier of what was saved, for example the new record's id
         */
        public void saved(String savedResult) {
            this.result = savedResult == null ? "" : savedResult;
        }

        /** Whether the outcome proves that this attempt wrote nothing, so the form may be submitted again. */
        public boolean canRetry() {
            return result == null
                    && (completion == NOT_STARTED || completion == TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        @Override
        public synchronized void close() {
            // A repeated close must not overwrite what a later retry of this token recorded.
            if (closed) return;
            closed = true;
            boolean retry = canRetry();
            Entry outcome = new Entry(issued.scope(), issued.binding(), retry ? State.ISSUED : State.SAVED,
                    retry ? null : result);
            try {
                synchronized (WebUtils.getSessionMutex(session)) {
                    LinkedHashMap<String, Entry> entries = copyEntries(session);
                    putBounded(entries, token, outcome);
                    session.setAttribute(SESSION_KEY, new Pending(entries));
                }
            } catch (IllegalStateException invalidated) {
                // The session ended while the save ran (a logout in another tab). Its tokens went
                // with it, so there is nothing to record, and the save's own outcome must not be
                // turned into an error page.
            }
        }
    }
}
