/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/** Acknowledge only the three schedule sync operations; positions contain only returned-row metadata. */
public final class ScheduleSyncOutInterceptor extends AbstractPhaseInterceptor<Message> {
    public ScheduleSyncOutInterceptor() { super(Phase.PREPARE_SEND); }
    @Override public void handleMessage(Message message) {
        if (org.apache.cxf.message.MessageUtils.isRequestor(message) || message.getExchange() == null) return;
        Object state = message.getExchange().get(ScheduleSyncHeaders.KEY);
        if (!(state instanceof ScheduleSyncHeaders.Selection selected)) return;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> existing = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (existing != null) headers.putAll(existing);
        headers.put(ScheduleSyncHeaders.PROFILE, List.of(selected.wireName()));
        headers.put("Cache-Control", List.of("no-store"));
        headers.put("X-Carlos-Schedule-Reconciliation", List.of("required"));
        Object result = message.getExchange().get(ScheduleSyncHeaders.RESULT);
        if (result instanceof ScheduleSyncHeaders.Result page) {
            headers.put(ScheduleSyncHeaders.MORE, List.of(Boolean.toString(page.more())));
            if (page.next() != null) headers.put(ScheduleSyncHeaders.NEXT, List.of(page.next()));
        }
        message.put(Message.PROTOCOL_HEADERS, headers);
    }
}
