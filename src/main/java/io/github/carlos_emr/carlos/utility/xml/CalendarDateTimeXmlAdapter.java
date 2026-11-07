/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Writes a Calendar property whose published XML contract requires a date-time and offset.
 * A date-only calendar still denotes midnight in its own zone. Unlike the legacy MOXy
 * adapter, this adapter materializes that time and offset even when their fields were
 * never explicitly assigned. Apply it only to properties with that published contract.
 */
public final class CalendarDateTimeXmlAdapter extends XmlCompatibilityAdapter<String, Calendar> {
    private static final DatatypeFactory DATATYPE_FACTORY = DatatypeFactory.newDefaultInstance();
    private static final CalendarXmlAdapter LEGACY_READER = new CalendarXmlAdapter();

    /** Retains accepted legacy date-only, date-time, blank and optional-null inputs. */
    @Override
    public Calendar unmarshal(String value) {
        return LEGACY_READER.unmarshal(value);
    }

    /** Writes the calendar's instant in its own zone without changing the caller's field state. */
    @Override
    public String marshal(Calendar value) {
        if (value == null) return null;
        Calendar copy = (Calendar) value.clone();
        GregorianCalendar calendar = new GregorianCalendar(copy.getTimeZone());
        calendar.setTimeInMillis(copy.getTimeInMillis());
        XMLGregorianCalendar result = DATATYPE_FACTORY.newXMLGregorianCalendar(calendar);
        if (result.getFractionalSecond() != null) {
            result.setFractionalSecond(result.getFractionalSecond().signum() == 0 ? null
                    : result.getFractionalSecond().stripTrailingZeros());
        }
        return result.toXMLFormat();
    }
}
