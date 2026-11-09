/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;

/** Rejects our explicit compatibility-value failures while retaining other legacy REST XML reader behavior. */
public final class CompatibilityJaxbProvider extends JAXBElementProvider<Object> {
    @Override
    protected Unmarshaller createUnmarshaller(Class<?> type, Type genericType, boolean collection) throws JAXBException {
        Unmarshaller reader = super.createUnmarshaller(type, genericType, collection);
        var original = reader.getEventHandler();
        reader.setEventHandler(event -> !isCompatibilityFailure(event.getLinkedException()) && original.handleEvent(event));
        return reader;
    }

    @Override
    protected void marshalToWriter(jakarta.xml.bind.Marshaller marshaller, Object value,
            javax.xml.stream.XMLStreamWriter writer, java.lang.annotation.Annotation[] annotations,
            jakarta.ws.rs.core.MediaType mediaType) throws Exception {
        super.marshalToWriter(marshaller, value, XmlWireProfile.current() == XmlWireProfile.GLASSFISH_LEGACY
                ? LegacyDrugOrderWriter.wrap(writer) : writer, annotations, mediaType);
    }

    @Override
    protected void marshalToOutputStream(jakarta.xml.bind.Marshaller marshaller, Object value,
            java.io.OutputStream stream, java.lang.annotation.Annotation[] annotations,
            jakarta.ws.rs.core.MediaType mediaType) throws Exception {
        if (XmlWireProfile.current() != XmlWireProfile.GLASSFISH_LEGACY) {
            super.marshalToOutputStream(marshaller, value, stream, annotations, mediaType);
            return;
        }
        String encoding = (String) marshaller.getProperty(jakarta.xml.bind.Marshaller.JAXB_ENCODING);
        javax.xml.stream.XMLStreamWriter writer = stream == null
                ? org.apache.cxf.staxutils.StaxUtils.createXMLStreamWriter(
                        (java.io.Writer) getStreamHandlerFromCurrentMessage(java.io.Writer.class))
                : org.apache.cxf.staxutils.StaxUtils.createXMLStreamWriter(stream, encoding);
        marshalToWriter(marshaller, value, writer, annotations, mediaType);
        writer.flush();
    }

    private static boolean isCompatibilityFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; failure != null && depth < 16 && seen.add(failure); depth++, failure = failure.getCause()) {
            if (failure instanceof XmlCompatibilityValueException) return true;
        }
        return false;
    }
}
