/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
/** XML compatibility with the production MOXy mapping used before issue #5476. */
@XmlJavaTypeAdapters({
        @XmlJavaTypeAdapter(value = CharacterXmlAdapter.class, type = Character.class),
        @XmlJavaTypeAdapter(value = CharacterXmlAdapter.class, type = char.class),
        @XmlJavaTypeAdapter(value = CalendarXmlAdapter.class, type = java.util.Calendar.class),
        @XmlJavaTypeAdapter(value = DateXmlAdapter.class, type = java.util.Date.class)
})
@XmlSchemaTypes({
        @XmlSchemaType(name = "dateTime", type = java.util.Calendar.class),
        @XmlSchemaType(name = "dateTime", type = java.util.Date.class),
        @XmlSchemaType(name = "dateTime", type = javax.xml.datatype.XMLGregorianCalendar.class)
})
package io.github.carlos_emr.carlos.PMmodule.model;

import io.github.carlos_emr.carlos.utility.xml.CalendarXmlAdapter;
import io.github.carlos_emr.carlos.utility.xml.CharacterXmlAdapter;
import io.github.carlos_emr.carlos.utility.xml.DateXmlAdapter;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlSchemaTypes;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapters;
