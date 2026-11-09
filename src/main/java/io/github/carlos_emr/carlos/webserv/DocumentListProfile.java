/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.utility.AsciiStrings;
import java.util.List;
import java.util.Map;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

/** Metadata is the default bulk response; old inline consumers explicitly select compatibility. */
public enum DocumentListProfile {
    METADATA("metadata", false), LEGACY_INLINE("legacy-inline", true);
    public static final String HEADER = "X-Carlos-Document-List-Profile";
    private static final String KEY = DocumentListProfile.class.getName();
    private final String wireName;
    private final boolean inline;
    DocumentListProfile(String wireName, boolean inline) { this.wireName = wireName; this.inline = inline; }
    public boolean inline() { return inline; }
    public String wireName() { return wireName; }
    public static DocumentListProfile selected(Message message) {
        if (message == null || message.getExchange() == null) return null;
        Object value = message.getExchange().get(KEY);
        return value instanceof DocumentListProfile profile ? profile : null;
    }
    public static DocumentListProfile parse(List<String> values) {
        if (values == null) return METADATA;
        if (values.size() == 1) {
            for (DocumentListProfile profile : values()) if (profile.wireName.equals(values.getFirst())) return profile;
        }
        throw new SoapFault("Unsupported document list profile", SoapFault.FAULT_CODE_CLIENT);
    }
    public static DocumentListProfile current() {
        Message message = PhaseInterceptorChain.getCurrentMessage();
        if (message == null) return METADATA;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        List<String> values = null;
        if (headers != null) {
            for (var header : headers.entrySet()) {
                if (!AsciiStrings.equalsIgnoreCase(HEADER, header.getKey())) continue;
                if (values != null || header.getValue() == null) return parse(List.of());
                values = header.getValue();
            }
        }
        DocumentListProfile profile = parse(values);
        if (message.getExchange() != null) message.getExchange().put(KEY, profile);
        return profile;
    }
}
