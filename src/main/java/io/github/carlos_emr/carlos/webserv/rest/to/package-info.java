/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
/** Preserve the date-time XML lexical form of REST response envelopes as well as their payloads. */
@jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter(
        value = io.github.carlos_emr.carlos.utility.xml.DateXmlAdapter.class, type = java.util.Date.class)
@jakarta.xml.bind.annotation.XmlSchemaType(name = "dateTime", type = javax.xml.datatype.XMLGregorianCalendar.class)
package io.github.carlos_emr.carlos.webserv.rest.to;
