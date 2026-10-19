/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Published under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.fax.provider;

import java.io.IOException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.util.StreamReaderDelegate;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.apache.hc.core5.http.HttpEntity;

/** Reads middleware metadata while discarding any document echoed by the relay. */
final class FaxXmlResponseParser {
    private FaxXmlResponseParser() {
    }

    static FaxJob read(HttpEntity entity) throws IOException, JAXBException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
        try (FaxResponseStream input = FaxResponseStream.open(entity)) {
            var reader = factory.createXMLStreamReader(input);
            try {
                var metadata = new StreamReaderDelegate(reader) {
                    @Override
                    public int next() throws XMLStreamException {
                        int event = super.next();
                        if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) {
                            throw new XMLStreamException("DTD/entities are not allowed in fax responses");
                        }
                        if (event == XMLStreamConstants.START_ELEMENT && "document".equals(getLocalName())) {
                            input.document = true;
                            int depth = 1;
                            while (depth > 0) {
                                int skipped = super.next();
                                if (skipped == XMLStreamConstants.START_ELEMENT) {
                                    depth++;
                                } else if (skipped == XMLStreamConstants.END_ELEMENT) {
                                    depth--;
                                }
                            }
                            input.document = false;
                            return next();
                        }
                        return event;
                    }
                };
                Object result = JAXBContext.newInstance(FaxJob.class).createUnmarshaller().unmarshal(metadata);
                if (!(result instanceof FaxJob job)) {
                    throw new JAXBException("Invalid fax response");
                }
                while (metadata.hasNext()) {
                    metadata.next();
                }
                return job;
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new IOException("Invalid middleware fax XML", e);
        }
    }
}
