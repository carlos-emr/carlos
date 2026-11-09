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

    private static boolean isCompatibilityFailure(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int depth = 0; failure != null && depth < 16 && seen.add(failure); depth++, failure = failure.getCause()) {
            if (failure instanceof XmlCompatibilityValueException) return true;
        }
        return false;
    }
}
