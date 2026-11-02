/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.Converter;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector;
import io.github.carlos_emr.carlos.utility.xml.XmlCompatibilityAdapter;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

/** Keeps JAXB names and existing adapters while excluding conversions specific to XML compatibility. */
public final class RestXmlAnnotationIntrospector extends JakartaXmlBindAnnotationIntrospector {
    private static final long serialVersionUID = 1L;

    public RestXmlAnnotationIntrospector(TypeFactory typeFactory) {
        super(typeFactory);
    }

    @Override
    protected Converter<Object, Object> _converter(XmlAdapter<?, ?> adapter, boolean forSerialization) {
        // In particular, XML's empty NUL element must remain JSON's "\u0000", including list items.
        return adapter instanceof XmlCompatibilityAdapter<?, ?> ? null : super._converter(adapter, forSerialization);
    }
}
