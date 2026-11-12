/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/** Acknowledges the profile a document request named; a header-less request named none and gets no such header. */
public final class DocumentListProfileOutInterceptor extends AbstractPhaseInterceptor<Message> {
    public DocumentListProfileOutInterceptor() { super(Phase.PREPARE_SEND); }
    @Override public void handleMessage(Message message) {
        if (org.apache.cxf.message.MessageUtils.isRequestor(message)) return;
        DocumentListProfile profile = DocumentListProfile.selected(message);
        if (profile == null) return;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> existing = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (existing != null) headers.putAll(existing);
        if (profile.wireName() != null) headers.put(DocumentListProfile.HEADER, List.of(profile.wireName()));
        // Document operations use SOAP POST. Responses may contain private clinical metadata.
        headers.put("Cache-Control", List.of("no-store"));
        message.put(Message.PROTOCOL_HEADERS, headers);
    }
}
