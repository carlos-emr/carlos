/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import java.util.List;
import java.util.Map;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import io.github.carlos_emr.carlos.utility.xml.XmlWireProfile;

/** Selects the XML contract before JAXB reads any request body, on SOAP and both REST servers. */
public final class XmlProfileInInterceptor extends AbstractPhaseInterceptor<Message> {
    public XmlProfileInInterceptor() {
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
                if (!io.github.carlos_emr.carlos.utility.AsciiStrings.equalsIgnoreCase(XmlWireProfile.HEADER, header.getKey())) continue;
                if (requested != null || header.getValue() == null || header.getValue().size() != 1) {
                    throw invalidProfile();
                }
                requested = header.getValue().getFirst();
                if (requested == null) throw invalidProfile();
            }
        }
        try {
            XmlWireProfile.select(message, requested == null ? XmlWireProfile.PUBLISHED : XmlWireProfile.parse(requested));
        } catch (IllegalArgumentException invalid) {
            throw invalidProfile();
        }
    }

    private static Fault invalidProfile() {
        Fault fault = new Fault(new IllegalArgumentException("Unsupported XML compatibility profile"), Fault.FAULT_CODE_CLIENT);
        fault.setStatusCode(400);
        return fault;
    }
}
