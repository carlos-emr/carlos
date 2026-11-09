/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
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

    /**
     * Creates the REST introspector with the mapper's type resolution configuration.
     *
     * @param typeFactory TypeFactory used to resolve adapter input and output types
     */
    public RestXmlAnnotationIntrospector(TypeFactory typeFactory) {
        super(typeFactory);
    }

    /** Explicit JSON inclusion can retain a JSON-only property excluded from published XML. */
    @Override
    public boolean hasIgnoreMarker(com.fasterxml.jackson.databind.introspect.AnnotatedMember member) {
        var json = member.getAnnotation(com.fasterxml.jackson.annotation.JsonIgnore.class);
        return json != null && !json.value() ? false : super.hasIgnoreMarker(member);
    }

    /**
     * Retains existing JSON adapter conversions while ignoring the XML-only compatibility adapter family.
     *
     * @param adapter XmlAdapter&lt;?, ?&gt; declared by the JAXB annotations
     * @param forSerialization boolean indicating serialization rather than deserialization
     * @return Converter&lt;Object, Object&gt; for ordinary adapters, or null for an XmlCompatibilityAdapter
     */
    @Override
    protected Converter<Object, Object> _converter(XmlAdapter<?, ?> adapter, boolean forSerialization) {
        // In particular, XML's empty NUL element must remain JSON's "\u0000", including list items.
        return adapter instanceof XmlCompatibilityAdapter<?, ?> ? null : super._converter(adapter, forSerialization);
    }
}
