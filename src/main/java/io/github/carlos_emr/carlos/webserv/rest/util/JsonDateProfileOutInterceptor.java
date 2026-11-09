/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;


/** Makes profile selection observable and prevents a shared cache mixing different JSON date contracts. */
public final class JsonDateProfileOutInterceptor extends AbstractPhaseInterceptor<Message> {
    public JsonDateProfileOutInterceptor() {
        super(Phase.PREPARE_SEND);
    }

    @Override
    public void handleMessage(Message message) {
        if (org.apache.cxf.message.MessageUtils.isRequestor(message)) return;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> existing = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (existing != null) headers.putAll(existing);
        headers.put(JsonDateProfile.HEADER, List.of(JsonDateProfile.selected(message).wireName()));
        List<String> vary = new ArrayList<>(headers.getOrDefault("Vary", List.of()));
        boolean present = vary.stream().flatMap(value -> java.util.Arrays.stream(value.split(",")))
                .anyMatch(value -> io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase(value.trim(), JsonDateProfile.HEADER) || value.trim().equals("*"));
        if (!present) vary.add(JsonDateProfile.HEADER);
        headers.put("Vary", vary);
        message.put(Message.PROTOCOL_HEADERS, headers);
    }
}
