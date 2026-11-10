/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import java.util.List;
import java.util.Map;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;


/** Selects the JSON date contract before JAXB reads any request body, on the OAuth REST server. */
public final class JsonDateProfileInInterceptor extends AbstractPhaseInterceptor<Message> {
    public JsonDateProfileInInterceptor() {
        super(Phase.RECEIVE);
    }

    @Override
    public void handleMessage(Message message) {
        if (org.apache.cxf.message.MessageUtils.isRequestor(message)) return;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> headers = (Map<String, List<String>>) message.get(Message.PROTOCOL_HEADERS);
        String requested = null;
        if (headers != null) {
            for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                if (!io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase(JsonDateProfile.HEADER, header.getKey())) continue;
                if (requested != null || header.getValue() == null || header.getValue().size() != 1) {
                    throw invalidProfile();
                }
                requested = header.getValue().getFirst();
                if (requested == null) throw invalidProfile();
            }
        }
        try {
            JsonDateProfile.select(message, requested == null ? JsonDateProfile.DECLARED : JsonDateProfile.parse(requested));
        } catch (IllegalArgumentException invalid) {
            throw invalidProfile();
        }
    }

    private static Fault invalidProfile() {
        Fault fault = new Fault(new IllegalArgumentException("Unsupported JSON date compatibility profile"), Fault.FAULT_CODE_CLIENT);
        fault.setStatusCode(400);
        return fault;
    }
}
