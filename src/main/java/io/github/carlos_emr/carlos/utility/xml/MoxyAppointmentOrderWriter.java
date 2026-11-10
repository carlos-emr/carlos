/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.cxf.staxutils.DelegatingXMLStreamWriter;
import org.apache.cxf.staxutils.StaxUtils;

/** Explicit MOXy AppointmentExtTo sequence: hPhoneExt precedes lastName.
 * Buffers only the intervening scalar members of a populated appointment row.
 * The required appointmentNo/demographicNo shape avoids changing unrelated DTOs.
 */
public final class MoxyAppointmentOrderWriter extends DelegatingXMLStreamWriter {
    private static final class Frame {
        boolean appointmentNo;
        boolean demographicNo;
        boolean buffering;
        boolean replayAfterChild;
        final java.util.List<String> deferred = new java.util.ArrayList<>();
    }
    private final Deque<Frame> frames = new ArrayDeque<>();
    private XMLStreamWriter scalar;
    private StringWriter buffer;
    private boolean emptyScalar;

    public MoxyAppointmentOrderWriter(XMLStreamWriter delegate) { super(delegate); }

    public static XMLStreamWriter wrap(XMLStreamWriter writer) {
        return writer instanceof MoxyAppointmentOrderWriter ? writer : new MoxyAppointmentOrderWriter(writer);
    }

    private XMLStreamWriter target() { return scalar == null ? delegate : scalar; }

    private void begin(String local, String namespace) throws XMLStreamException {
        finishEmpty();
        if (scalar != null) throw new XMLStreamException("Legacy appointment member must be scalar");
        Frame parent = frames.peek();
        boolean unqualified = namespace == null || namespace.isEmpty();
        if (parent != null && unqualified) {
            if ("hPhoneExt".equals(local) && parent.buffering) {
                parent.buffering = false;
                parent.replayAfterChild = true;
            } else {
                if ("wPhoneExt".equals(local) && parent.buffering) {
                    parent.buffering = false;
                    replayDeferred(parent);
                }
                if (parent.buffering || ("lastName".equals(local) && parent.appointmentNo && parent.demographicNo)) {
                    parent.buffering = true;
                    buffer = new StringWriter();
                    var factory = javax.xml.stream.XMLOutputFactory.newFactory();
                    factory.setProperty(javax.xml.stream.XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
                    scalar = factory.createXMLStreamWriter(buffer);
                    return;
                }
            }
            parent.appointmentNo |= "appointmentNo".equals(local);
            parent.demographicNo |= "demographicNo".equals(local);
        }
    }

    private void replayDeferred(Frame frame) throws XMLStreamException {
        for (String xml : frame.deferred) replay(xml);
        frame.deferred.clear();
    }

    private void finishScalar() throws XMLStreamException {
        scalar.writeEndDocument();
        scalar.flush();
        scalar.close();
        frames.peek().deferred.add(buffer.toString());
        scalar = null;
        buffer = null;
        emptyScalar = false;
    }

    private void finishEmpty() throws XMLStreamException {
        if (emptyScalar) finishScalar();
        Frame frame = frames.peek();
        if (scalar == null && frame != null && frame.replayAfterChild) {
            frame.replayAfterChild = false;
            replayDeferred(frame);
        }
    }

    private void replay(String xml) throws XMLStreamException {
        // StaxUtils configures its reader to disallow external entities. The source is our escaped scalar.
        var reader = StaxUtils.createXMLStreamReader(new StringReader(xml));
        try {
            reader.nextTag();
            StaxUtils.copy(reader, delegate);
        } finally { reader.close(); }
    }

    @Override public void writeEndElement() throws XMLStreamException {
        finishEmpty();
        if (scalar != null) {
            scalar.writeEndElement();
            finishScalar();
            return;
        }
        if (frames.isEmpty()) throw new XMLStreamException("Unbalanced MOXy compatibility XML output");
        Frame frame = frames.pop();
        replayDeferred(frame);
        delegate.writeEndElement();
        Frame parent = frames.peek();
        if (parent != null && parent.replayAfterChild) {
            parent.replayAfterChild = false;
            replayDeferred(parent);
        }
    }

    @Override public void writeEndDocument() throws XMLStreamException {
        finishEmpty();
        if (scalar != null || !frames.isEmpty()) throw new XMLStreamException("Incomplete MOXy compatibility XML output");
        delegate.writeEndDocument();
    }
    @Override public void close() throws XMLStreamException {
        finishEmpty();
        if (scalar != null || !frames.isEmpty()) throw new XMLStreamException("Incomplete MOXy compatibility XML output");
        delegate.close();
    }
    @Override public void writeStartElement(String local) throws XMLStreamException {
        begin(local, null);
        target().writeStartElement(local);
        if (scalar == null) frames.push(new Frame());
    }
    @Override public void writeStartElement(String namespace, String local) throws XMLStreamException {
        begin(local, namespace);
        target().writeStartElement(namespace, local);
        if (scalar == null) frames.push(new Frame());
    }
    @Override public void writeStartElement(String prefix, String local, String namespace) throws XMLStreamException {
        begin(local, namespace);
        target().writeStartElement(prefix, local, namespace);
        if (scalar == null) frames.push(new Frame());
    }
    @Override public void writeEmptyElement(String local) throws XMLStreamException {
        begin(local, null);
        target().writeEmptyElement(local);
        if (scalar != null) emptyScalar = true;
    }
    @Override public void writeEmptyElement(String namespace, String local) throws XMLStreamException {
        begin(local, namespace);
        target().writeEmptyElement(namespace, local);
        if (scalar != null) emptyScalar = true;
    }
    @Override public void writeEmptyElement(String prefix, String local, String namespace) throws XMLStreamException {
        begin(local, namespace);
        target().writeEmptyElement(prefix, local, namespace);
        if (scalar != null) emptyScalar = true;
    }
    @Override public void writeAttribute(String local, String value) throws XMLStreamException { target().writeAttribute(local, value); }
    @Override public void writeAttribute(String namespace, String local, String value) throws XMLStreamException { target().writeAttribute(namespace, local, value); }
    @Override public void writeAttribute(String prefix, String namespace, String local, String value) throws XMLStreamException { target().writeAttribute(prefix, namespace, local, value); }
    @Override public void writeNamespace(String prefix, String namespace) throws XMLStreamException { target().writeNamespace(prefix, namespace); }
    @Override public void writeDefaultNamespace(String namespace) throws XMLStreamException { target().writeDefaultNamespace(namespace); }
    @Override public void setPrefix(String prefix, String namespace) throws XMLStreamException { target().setPrefix(prefix, namespace); }
    @Override public void setDefaultNamespace(String namespace) throws XMLStreamException { target().setDefaultNamespace(namespace); }
    @Override public void setNamespaceContext(javax.xml.namespace.NamespaceContext context) throws XMLStreamException { target().setNamespaceContext(context); }
    @Override public void writeCharacters(String text) throws XMLStreamException { finishEmpty(); target().writeCharacters(text); }
    @Override public void writeCharacters(char[] text, int start, int length) throws XMLStreamException { finishEmpty(); target().writeCharacters(text, start, length); }
    @Override public void writeCData(String text) throws XMLStreamException { finishEmpty(); target().writeCData(text); }
    @Override public void writeComment(String text) throws XMLStreamException { finishEmpty(); target().writeComment(text); }
    @Override public void writeProcessingInstruction(String name) throws XMLStreamException { finishEmpty(); target().writeProcessingInstruction(name); }
    @Override public void writeProcessingInstruction(String name, String data) throws XMLStreamException { finishEmpty(); target().writeProcessingInstruction(name, data); }
    @Override public void writeEntityRef(String name) throws XMLStreamException { finishEmpty(); target().writeEntityRef(name); }
    @Override public void flush() throws XMLStreamException { target().flush(); }
    @Override public String getPrefix(String namespace) throws XMLStreamException { return target().getPrefix(namespace); }
    @Override public javax.xml.namespace.NamespaceContext getNamespaceContext() { return target().getNamespaceContext(); }
    @Override public Object getProperty(String name) { return target().getProperty(name); }
    @Override public void writeDTD(String dtd) throws XMLStreamException { throw new XMLStreamException("DTD output is not supported"); }
}
