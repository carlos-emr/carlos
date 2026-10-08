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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * <p>An edit is two writes (add the replacement, archive the original). The ledger therefore
 * tracks the stage: a retry after the replacement was added but the archive failed resumes at
 * the archive instead of reporting success or adding again. Each token is also bound to a
 * fingerprint of the submitted values, so a retry whose values were changed after an unknown
 * outcome is refused rather than reported as saved with values that were never written.
 *
 * <p>The ledger lives in the HTTP session, so it cannot be used to probe other sessions, and
 * is bounded so it cannot grow without limit.
 */
final class AllergySaveTokens {

    /** Result of {@link #claim(HttpSession, String, String)}. */
    enum Claim {
        /** First time this token is seen (or none was supplied): the caller must save. */
        CLAIMED,
        /** The replacement was added earlier but archiving the original did not complete. */
        RESUME_ARCHIVE,
        /** An earlier request with this token already saved the allergy. */
        ALREADY_SAVED,
        /** Another request with this token is saving right now. */
        IN_PROGRESS,
        /** The token was used earlier with different submitted values. */
        PAYLOAD_MISMATCH
    }

    static final String SESSION_ATTRIBUTE = "rx.allergySaveTokens";
    private static final int MAX_TOKENS = 1000;
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9-]{16,64}");

    private enum Stage { PENDING_ADD, ADDED, PENDING_ARCHIVE, SAVED }

    // Serializable so Tomcat session persistence (restart, clustering) keeps the ledger instead of
    // dropping the attribute: losing it would let a post-restart retry add the allergy again.
    private static final class TokenState implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        final String fingerprint;
        Stage stage;

        TokenState(String fingerprint, Stage stage) {
            this.fingerprint = fingerprint;
            this.stage = stage;
        }
    }

    private AllergySaveTokens() {
    }

    /** Whether {@code token} is well formed. A blank token is allowed (legacy callers). */
    static boolean isAcceptable(String token) {
        return token == null || token.isEmpty() || FORMAT.matcher(token).matches();
    }

    /** Digest of the submitted values, so a token can be tied to what it was first used for. */
    static String fingerprint(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                byte[] bytes = (value == null ? "\u0000null" : value).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static Claim claim(HttpSession session, String token, String fingerprint) {
        if (token == null || token.isEmpty()) {
            return Claim.CLAIMED;
        }
        Map<String, TokenState> ledger = ledger(session);
        synchronized (ledger) {
            TokenState existing = ledger.get(token);
            if (existing == null) {
                ledger.put(token, new TokenState(fingerprint, Stage.PENDING_ADD));
                return Claim.CLAIMED;
            }
            if (!existing.fingerprint.equals(fingerprint)) {
                return Claim.PAYLOAD_MISMATCH;
            }
            switch (existing.stage) {
                case SAVED:
                    return Claim.ALREADY_SAVED;
                case ADDED:
                    existing.stage = Stage.PENDING_ARCHIVE;
                    return Claim.RESUME_ARCHIVE;
                default:
                    return Claim.IN_PROGRESS;
            }
        }
    }

    /** The replacement allergy is persisted; only archiving the original (if any) remains. */
    static void markAdded(HttpSession session, String token) {
        setStage(session, token, Stage.ADDED);
    }

    static void markSaved(HttpSession session, String token) {
        setStage(session, token, Stage.SAVED);
    }

    /** Frees a claimed token whose save did not complete so a retry can continue. */
    static void release(HttpSession session, String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        Map<String, TokenState> ledger = ledger(session);
        synchronized (ledger) {
            TokenState entry = ledger.get(token);
            if (entry == null) {
                return;
            }
            if (entry.stage == Stage.PENDING_ADD) {
                ledger.remove(token);
            } else if (entry.stage == Stage.PENDING_ARCHIVE) {
                entry.stage = Stage.ADDED;
            }
        }
    }

    private static void setStage(HttpSession session, String token, Stage stage) {
        if (token == null || token.isEmpty()) {
            return;
        }
        Map<String, TokenState> ledger = ledger(session);
        synchronized (ledger) {
            TokenState entry = ledger.get(token);
            if (entry != null) {
                entry.stage = stage;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, TokenState> ledger(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_ATTRIBUTE);
            if (existing == null) {
                Map<String, TokenState> created = Collections.synchronizedMap(new LinkedHashMap<>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, TokenState> eldest) {
                        return size() > MAX_TOKENS;
                    }
                });
                session.setAttribute(SESSION_ATTRIBUTE, created);
                return created;
            }
            return (Map<String, TokenState>) existing;
        }
    }
}
