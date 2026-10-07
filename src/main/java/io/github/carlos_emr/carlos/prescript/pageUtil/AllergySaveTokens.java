/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Per-session ledger of allergy-dialogue save tokens (issue #3488).
 *
 * <p>The add-allergy dialogue renders one random token per load and resubmits the same value
 * when a clinician retries after a failed save. Claiming the token immediately before the
 * persist, and keeping it once the persist succeeds, makes a retry idempotent: a request that
 * did succeed server-side but whose response was lost (timeout, dropped connection) is
 * recognised on the retry and answered with success instead of writing a second allergy.
 * A token whose save threw is released so the retry can really save.
 *
 * <p>The ledger lives in the HTTP session, so it cannot be used to probe other sessions, and
 * is bounded so it cannot grow without limit.
 */
final class AllergySaveTokens {

    /** Result of {@link #claim(HttpSession, String)}. */
    enum Claim {
        /** First time this token is seen (or none was supplied): the caller must save. */
        CLAIMED,
        /** An earlier request with this token already saved the allergy. */
        ALREADY_SAVED,
        /** Another request with this token is saving right now. */
        IN_PROGRESS
    }

    static final String SESSION_ATTRIBUTE = "rx.allergySaveTokens";
    private static final int MAX_TOKENS = 200;
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9-]{16,64}");

    private enum State { PENDING, SAVED }

    private AllergySaveTokens() {
    }

    /** Whether {@code token} is well formed. A blank token is allowed (legacy callers). */
    static boolean isAcceptable(String token) {
        return token == null || token.isEmpty() || FORMAT.matcher(token).matches();
    }

    static Claim claim(HttpSession session, String token) {
        if (token == null || token.isEmpty()) {
            return Claim.CLAIMED;
        }
        Map<String, State> ledger = ledger(session);
        synchronized (ledger) {
            State existing = ledger.get(token);
            if (existing == State.SAVED) {
                return Claim.ALREADY_SAVED;
            }
            if (existing == State.PENDING) {
                return Claim.IN_PROGRESS;
            }
            ledger.put(token, State.PENDING);
            return Claim.CLAIMED;
        }
    }

    static void markSaved(HttpSession session, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        Map<String, State> ledger = ledger(session);
        synchronized (ledger) {
            ledger.put(token, State.SAVED);
        }
    }

    /** Frees a claimed token whose save did not complete so a retry can save. */
    static void release(HttpSession session, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        Map<String, State> ledger = ledger(session);
        synchronized (ledger) {
            if (ledger.get(token) == State.PENDING) {
                ledger.remove(token);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, State> ledger(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing == null) {
                Map<String, State> created = Collections.synchronizedMap(new LinkedHashMap<>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, State> eldest) {
                        return size() > MAX_TOKENS;
                    }
                });
                session.setAttribute(SESSION_ATTRIBUTE, created);
                return created;
            }
            return (Map<String, State>) existing;
        }
    }
}
