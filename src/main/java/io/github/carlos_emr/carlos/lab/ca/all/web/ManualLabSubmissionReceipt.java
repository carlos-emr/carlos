/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.web;

import io.github.carlos_emr.carlos.lab.FileUploadCheck.StoreOutcome;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Carries a completed manual submission's notice across its POST/redirect/GET.
 * Receipts contain no lab or patient data, belong to one session and are consumed once.
 * Separate receipt IDs keep concurrently open lab windows from stealing each other's notices.
 */
public final class ManualLabSubmissionReceipt {
    private static final String SESSION_KEY = ManualLabSubmissionReceipt.class.getName();
    private static final int MAX_PENDING = 64;

    private ManualLabSubmissionReceipt() {
    }

    private record Pending(LinkedHashMap<String, StoreOutcome> outcomes) implements Serializable {
    }

    /**
     * Records an outcome only after storage has completed. Abandoned receipts are bounded
     * per session; its oldest receipt is discarded when the limit is reached.
     *
     * @param session the submitting user's session
     * @param outcome a committed submission or an already recorded file
     * @return an opaque ID for the redirected form, containing no clinical data
     */
    public static String save(HttpSession session, StoreOutcome outcome) {
        if (outcome != StoreOutcome.STORED && outcome != StoreOutcome.ALREADY_RECORDED) {
            throw new IllegalArgumentException("Only completed submissions have receipts");
        }
        String id = UUID.randomUUID().toString();
        synchronized (session) {
            LinkedHashMap<String, StoreOutcome> outcomes = copyPending(session);
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
    public static StoreOutcome consume(HttpSession session, String id) {
        if (session == null || id == null) {
            return null;
        }
        synchronized (session) {
            LinkedHashMap<String, StoreOutcome> outcomes = copyPending(session);
            StoreOutcome outcome = outcomes.remove(id);
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

    private static LinkedHashMap<String, StoreOutcome> copyPending(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        return value instanceof Pending pending
                ? new LinkedHashMap<>(pending.outcomes()) : new LinkedHashMap<>();
    }
}
