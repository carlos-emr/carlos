/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MoxyAppointmentOrderWriterUnitTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void shouldMoveOnlyTheSelectedScalar_beforeDeferredMembers(boolean empty) throws Exception {
        var output = new StringWriter(); var writer = new MoxyAppointmentOrderWriter(org.apache.cxf.staxutils.StaxUtils.createXMLStreamWriter(output));
        writer.writeStartElement("value");
        for (String field : java.util.List.of("appointmentNo", "demographicNo", "lastName", "location", "status")) {
            writer.writeStartElement(field); writer.writeCharacters("é<&"); writer.writeEndElement();
        }
        if (empty) writer.writeEmptyElement("hPhoneExt");
        else { writer.writeStartElement("hPhoneExt"); writer.writeCharacters("7"); writer.writeEndElement(); }
        writer.writeStartElement("wPhoneExt"); writer.writeCharacters("8"); writer.writeEndElement();
        writer.writeEndElement(); writer.close();
        var document = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder()
                .parse(new org.xml.sax.InputSource(new java.io.StringReader(output.toString())));
        var children = document.getDocumentElement().getChildNodes(); var names = new java.util.ArrayList<String>();
        for (int i = 0; i < children.getLength(); i++) names.add(children.item(i).getNodeName());
        assertThat(names).containsExactly("appointmentNo", "demographicNo", "hPhoneExt", "lastName", "location", "status", "wPhoneExt");
        assertThat(document.getElementsByTagName("lastName").item(0).getTextContent()).isEqualTo("é<&");
    }
    @Test void shouldPropagateOutputFailure_whenReplayingDeferredMembers() throws Exception {
        var sink = org.mockito.Mockito.spy(org.apache.cxf.staxutils.StaxUtils.createXMLStreamWriter(new StringWriter()));
        org.mockito.Mockito.doThrow(new javax.xml.stream.XMLStreamException("FAKE-output"))
                .when(sink).writeStartElement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("lastName"), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doThrow(new javax.xml.stream.XMLStreamException("FAKE-output")).when(sink).writeStartElement("lastName");
        org.mockito.Mockito.doThrow(new javax.xml.stream.XMLStreamException("FAKE-output"))
                .when(sink).writeStartElement(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("lastName"));
        var writer = new MoxyAppointmentOrderWriter(sink);
        writer.writeStartElement("value");
        for (String field : java.util.List.of("appointmentNo", "demographicNo", "lastName")) {
            writer.writeStartElement(field); writer.writeCharacters("7"); writer.writeEndElement();
        }
        assertThatThrownBy(writer::writeEndElement).isInstanceOf(javax.xml.stream.XMLStreamException.class);
    }
}
