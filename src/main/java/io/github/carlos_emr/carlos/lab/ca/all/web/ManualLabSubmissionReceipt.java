/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.web;

import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Carries a manual submission's result notice across its POST/redirect/GET.
 * Receipts contain no lab or patient data, belong to one session and are consumed once.
 * Separate receipt IDs keep concurrently open lab windows from stealing each other's notices.
 */
public final class ManualLabSubmissionReceipt {
    private static final String SESSION_KEY = ManualLabSubmissionReceipt.class.getName();
    private static final int MAX_PENDING = 64;

    private ManualLabSubmissionReceipt() {
    }

    /** Notices may confirm storage, confirm a duplicate, or report an uncertain storage outcome. */
    public enum Outcome {
        /** The lab and its routing committed. */
        STORED,
        /** This file was already recorded; nothing new was stored. */
        ALREADY_RECORDED,
        /** Storage raised an exception, so the user must check the inbox before retrying. */
        UNKNOWN
    }

    private record Pending(LinkedHashMap<String, Outcome> outcomes) implements Serializable {
    }

    /**
     * Records a notice after the storage attempt. Abandoned receipts are bounded
     * per session; its oldest receipt is discarded when the limit is reached.
     *
     * @param session the submitting user's session
     * @param outcome the result of the storage attempt, including uncertainty after an exception
     * @return an opaque ID for the redirected form, containing no clinical data
     */
    public static String save(HttpSession session, Outcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("A submission receipt requires an outcome");
        }
        String id = UUID.randomUUID().toString();
        synchronized (session) {
            LinkedHashMap<String, Outcome> outcomes = copyPending(session);
            if (outcomes.size() >= MAX_PENDING) {
                outcomes.pollFirstEntry();
            }
            outcomes.put(id, outcome);
            session.setAttribute(SESSION_KEY, new Pending(outcomes));
        }
        return id;
    }

    /**
     * Consumes only the addressed receipt. Unknown, expired-session or previously consumed
     * IDs cannot fabricate a success notice or consume a different window's receipt.
     *
     * @param session the viewing user's existing session, or null
     * @param id the opaque receipt ID from the redirect, or null
     * @return the recorded outcome, or null when no matching receipt exists
     */
    public static Outcome consume(HttpSession session, String id) {
        if (session == null || id == null) {
            return null;
        }
        synchronized (session) {
            LinkedHashMap<String, Outcome> outcomes = copyPending(session);
            Outcome outcome = outcomes.remove(id);
            if (outcome != null) {
                if (outcomes.isEmpty()) {
                    session.removeAttribute(SESSION_KEY);
                } else {
                    session.setAttribute(SESSION_KEY, new Pending(outcomes));
                }
            }
            return outcome;
        }
    }

    private static LinkedHashMap<String, Outcome> copyPending(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof Pending pending
                ? new LinkedHashMap<>(pending.outcomes()) : new LinkedHashMap<>();
    }
}
