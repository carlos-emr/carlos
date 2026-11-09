/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

/** Explicit, request-scoped exceptions to the published OAuth JSON date contract. Never guesses from a value. */
public enum JsonDateProfile {
    DECLARED("declared"),
    LEGACY_VALUE_INFERENCE("legacy-value-inference");

    public static final String HEADER = "X-Carlos-JSON-Date-Profile";
    private static final String EXCHANGE_KEY = JsonDateProfile.class.getName();
    private final String wireName;

    JsonDateProfile(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    /** Unknown/empty names fail explicitly; no fallback can silently change date semantics. */
    public static JsonDateProfile parse(String value) {
        for (JsonDateProfile profile : values()) {
            if (profile.wireName.equals(value)) return profile;
        }
        throw new IllegalArgumentException("Unsupported JSON date compatibility profile");
    }

    public static void select(Message message, JsonDateProfile profile) {
        message.getExchange().put(EXCHANGE_KEY, java.util.Objects.requireNonNull(profile));
    }

    public static JsonDateProfile selected(Message message) {
        if (message == null || message.getExchange() == null) return DECLARED;
        Object selected = message.getExchange().get(EXCHANGE_KEY);
        return selected instanceof JsonDateProfile profile ? profile : DECLARED;
    }

    /** CXF owns the message lifecycle, including async output; no application ThreadLocal can leak. */
    public static JsonDateProfile current() {
        return selected(PhaseInterceptorChain.getCurrentMessage());
    }
}
