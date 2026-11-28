/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.util;

import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugTo1;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

/** The medication and drug-lookup DTOs both publish the XML root {@code <drug>}. */
public final class DrugXmlContextResolver implements ContextResolver<JAXBContext> {
    private final JAXBContext medicationContext;

    public DrugXmlContextResolver() throws JAXBException {
        medicationContext = JAXBContext.newInstance(DrugTo1.class);
    }

    @Override
    public JAXBContext getContext(Class<?> type) {
        return type == DrugTo1.class ? medicationContext : null;
    }
}
