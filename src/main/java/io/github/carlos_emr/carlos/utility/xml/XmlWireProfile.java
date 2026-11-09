/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

/** Explicit, request-scoped exceptions to the published XML contract. Never guesses from a value. */
public enum XmlWireProfile {
    PUBLISHED("published"),
    MOXY_LEGACY("moxy-legacy"),
    MOXY_DATE_ONLY_BIRTHDAY("moxy-date-only-birthday"),
    GLASSFISH_NUMERIC_CHARACTERS("glassfish-numeric-characters"),
    GLASSFISH_LEGACY("glassfish-legacy");

    public static final String HEADER = "X-Carlos-XML-Profile";
    private static final String EXCHANGE_KEY = XmlWireProfile.class.getName();
    private final String wireName;

    XmlWireProfile(String wireName) {
        this.wireName = wireName;
    }

    public boolean numericCharacters() {
        return this == GLASSFISH_NUMERIC_CHARACTERS || this == GLASSFISH_LEGACY;
    }

    public String wireName() {
        return wireName;
    }

    /** Unknown/empty names fail explicitly; no fallback can silently change character identity. */
    public static XmlWireProfile parse(String value) {
        for (XmlWireProfile profile : values()) {
            if (profile.wireName.equals(value)) return profile;
        }
        throw new IllegalArgumentException("Unsupported XML compatibility profile");
    }

    public static void select(Message message, XmlWireProfile profile) {
        message.getExchange().put(EXCHANGE_KEY, java.util.Objects.requireNonNull(profile));
    }

    public static XmlWireProfile selected(Message message) {
        if (message == null || message.getExchange() == null) return PUBLISHED;
        Object selected = message.getExchange().get(EXCHANGE_KEY);
        return selected instanceof XmlWireProfile profile ? profile : PUBLISHED;
    }

    /** CXF owns the message lifecycle, including async output; no application ThreadLocal can leak. */
    public static XmlWireProfile current() {
        return selected(PhaseInterceptorChain.getCurrentMessage());
    }
}
