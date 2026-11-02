/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/** Preserves MOXy's local calendar date-time lexical form, including fractional seconds. */
public final class CalendarXmlAdapter extends XmlCompatibilityAdapter<String, Calendar> {
    @Override
    public Calendar unmarshal(String value) {
        return value == null ? null : DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(value).toGregorianCalendar();
    }

    @Override
    public String marshal(Calendar value) {
        if (value == null) return null;
        GregorianCalendar calendar = new GregorianCalendar(value.getTimeZone());
        calendar.setTimeInMillis(value.getTimeInMillis());
        XMLGregorianCalendar result = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(calendar);
        result.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        // MOXy uses a date-only lexical value for a calendar at local midnight.
        if (calendar.get(Calendar.HOUR_OF_DAY) == 0 && calendar.get(Calendar.MINUTE) == 0
                && calendar.get(Calendar.SECOND) == 0 && calendar.get(Calendar.MILLISECOND) == 0) {
            result.setHour(DatatypeConstants.FIELD_UNDEFINED);
            result.setMinute(DatatypeConstants.FIELD_UNDEFINED);
            result.setSecond(DatatypeConstants.FIELD_UNDEFINED);
            result.setFractionalSecond(null);
        }
        if (result.getFractionalSecond() != null) {
            result.setFractionalSecond(result.getFractionalSecond().signum() == 0 ? null
                    : result.getFractionalSecond().stripTrailingZeros());
        }
        return result.toXMLFormat();
    }
}
