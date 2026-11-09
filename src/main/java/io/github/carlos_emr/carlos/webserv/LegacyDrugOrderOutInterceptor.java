/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.utility.xml.LegacyDrugOrderWriter;
import io.github.carlos_emr.carlos.utility.xml.XmlWireProfile;
import javax.xml.stream.XMLStreamWriter;
import org.apache.cxf.interceptor.StaxOutInterceptor;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

/** Applies the opt-in historical sequence before SOAP writes its envelope. REST also wraps its JAXB writer. */
public final class LegacyDrugOrderOutInterceptor extends AbstractPhaseInterceptor<Message> {
    public LegacyDrugOrderOutInterceptor() {
        super(Phase.PRE_STREAM);
        addAfter(StaxOutInterceptor.class.getName());
    }
    @Override public void handleMessage(Message message) {
        if (org.apache.cxf.message.MessageUtils.isRequestor(message)
                || XmlWireProfile.selected(message) != XmlWireProfile.GLASSFISH_LEGACY) return;
        // CXF otherwise lets JAXB write directly to OutputStream, bypassing the selected writer.
        message.put(org.apache.cxf.interceptor.AbstractOutDatabindingInterceptor.DISABLE_OUTPUTSTREAM_OPTIMIZATION, true);
        XMLStreamWriter writer = message.getContent(XMLStreamWriter.class);
        if (writer != null) message.setContent(XMLStreamWriter.class, LegacyDrugOrderWriter.wrap(writer));
    }
}
