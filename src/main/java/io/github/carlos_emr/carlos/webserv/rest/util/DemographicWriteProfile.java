/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.utility.AsciiStrings;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.Map;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

/** Explicit compatibility for old full-record clients that intentionally clear identifiers with null. */
public enum DemographicWriteProfile {
    PRESERVE_HIDDEN_IDENTIFIERS("preserve-hidden-identifiers"),
    LEGACY_NULL_IDENTIFIERS("legacy-null-identifiers");

    public static final String HEADER = "X-Carlos-Demographic-Write-Profile";
    private static final String EXCHANGE_KEY = DemographicWriteProfile.class.getName();
    private final String wireName;
    DemographicWriteProfile(String wireName) { this.wireName = wireName; }

    public String wireName() { return wireName; }

    public static DemographicWriteProfile selected(Message message) {
        if (message == null || message.getExchange() == null) return null;
        Object value = message.getExchange().get(EXCHANGE_KEY);
        return value instanceof DemographicWriteProfile profile ? profile : null;
    }

    public static DemographicWriteProfile parse(List<String> values) {
        if (values == null) return PRESERVE_HIDDEN_IDENTIFIERS;
        if (values.size() == 1) {
            for (DemographicWriteProfile profile : values()) {
                if (profile.wireName.equals(values.getFirst())) return profile;
            }
        }
        throw invalid();
    }

    public static DemographicWriteProfile current() {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        if (message == null) return PRESERVE_HIDDEN_IDENTIFIERS;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        List<String> selected = null;
        if (headers != null) {
            for (var header : headers.entrySet()) {
                if (!AsciiStrings.equalsIgnoreCase(HEADER, header.getKey())) continue;
                if (selected != null || header.getValue() == null) throw invalid();
                selected = header.getValue();
            }
        }
        DemographicWriteProfile profile = parse(selected);
        if (message.getExchange() != null) message.getExchange().put(EXCHANGE_KEY, profile);
        return profile;
    }

    private static BadRequestException invalid() {
        return new BadRequestException("Unsupported demographic write profile");
    }
}
