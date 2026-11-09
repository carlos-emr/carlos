/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import java.io.StringWriter;
import javax.xml.stream.XMLStreamException;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@Tag("xml")
class LegacyDrugOrderWriterUnitTest {
    private static LegacyDrugOrderWriter drug(StringWriter output) throws Exception {
        var writer = new LegacyDrugOrderWriter(StaxUtils.createXMLStreamWriter(output));
        writer.writeStartElement("drug");
        writer.writeEmptyElement("archived");
        writer.writeEmptyElement("customInstructions");
        return writer;
    }

    @Test
    void shouldKeepNilAttributesAndNamespaces_whenDeferringAnEmptyElement() throws Exception {
        var output = new StringWriter();
        var writer = drug(output);
        writer.writeEmptyElement("eTreatmentType");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance", "nil", "true");
        writer.writeStartElement("unit"); writer.writeCharacters("mg"); writer.writeEndElement();
        writer.writeEndElement(); writer.writeEndDocument(); writer.flush();
        var document = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureDocumentBuilderFactory()
                .newDocumentBuilder().parse(new org.xml.sax.InputSource(new java.io.StringReader(output.toString())));
        var type = (org.w3c.dom.Element) document.getElementsByTagName("eTreatmentType").item(0);
        assertThat(type.getAttribute("xsi:nil")).isEqualTo("true");
        assertThat(document.getDocumentElement().getLastChild()).isEqualTo(type);
    }

    @Test
    void shouldCopyCharacterSlices_beforeTheCallerReusesItsBuffer() throws Exception {
        var output = new StringWriter();
        var writer = drug(output);
        writer.writeStartElement("eTreatmentType");
        char[] characters = "xé<&日本語y".toCharArray();
        writer.writeCharacters(characters, 1, characters.length - 2);
        java.util.Arrays.fill(characters, '?');
        writer.writeEndElement();
        writer.writeEmptyElement("unit"); writer.writeEndElement(); writer.flush();
        assertThat(output.toString()).contains("é&lt;&amp;日本語</eTreatmentType>").doesNotContain("?");
    }

    @Test
    void shouldLeaveUnrelatedTreatmentElementsInPlace() throws Exception {
        var output = new StringWriter();
        var writer = new LegacyDrugOrderWriter(StaxUtils.createXMLStreamWriter(output));
        writer.writeStartElement("other");
        writer.writeStartElement("eTreatmentType"); writer.writeCharacters("value"); writer.writeEndElement();
        writer.writeEmptyElement("unit"); writer.writeEndElement(); writer.flush();
        assertThat(output.toString().indexOf("eTreatmentType")).isLessThan(output.toString().indexOf("unit"));
    }

    @Test
    void shouldRejectIncompleteAndNonscalarOutput_insteadOfDroppingDeferredContent() throws Exception {
        var writer = drug(new StringWriter());
        writer.writeStartElement("eTreatmentType");
        assertThatThrownBy(writer::writeEndDocument).isInstanceOf(XMLStreamException.class);
        assertThatThrownBy(writer::close).isInstanceOf(XMLStreamException.class);
        assertThatThrownBy(() -> writer.writeStartElement("unexpected")).isInstanceOf(XMLStreamException.class);
    }

    @Test
    void shouldPropagateTheDestinationWriteFailure_whenReplayingDeferredContent() throws Exception {
        var sink = new org.apache.cxf.staxutils.DelegatingXMLStreamWriter(StaxUtils.createXMLStreamWriter(new StringWriter())) {
            @Override public void writeCharacters(String text) throws XMLStreamException {
                throw new XMLStreamException("destination failed");
            }
            @Override public void writeCharacters(char[] text, int offset, int length) throws XMLStreamException {
                throw new XMLStreamException("destination failed");
            }
        };
        var writer = new LegacyDrugOrderWriter(sink);
        writer.writeStartElement("drug"); writer.writeEmptyElement("archived"); writer.writeEmptyElement("customInstructions");
        writer.writeStartElement("eTreatmentType"); writer.writeCharacters("value"); writer.writeEndElement();
        assertThatThrownBy(writer::writeEndElement).isInstanceOf(XMLStreamException.class).hasMessageContaining("destination failed");
    }
}
