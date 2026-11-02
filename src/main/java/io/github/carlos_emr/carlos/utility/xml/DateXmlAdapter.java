/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.Date;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/** Preserves MOXy's date-time lexical form, which removes trailing fractional-second zeroes. */
public final class DateXmlAdapter extends XmlCompatibilityAdapter<XMLGregorianCalendar, Date> {
    @Override
    public Date unmarshal(XMLGregorianCalendar value) {
        return value == null ? null : value.toGregorianCalendar().getTime();
    }

    @Override
    public XMLGregorianCalendar marshal(Date value) {
        if (value == null) return null;
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(value);
        XMLGregorianCalendar result = DatatypeFactory.newDefaultInstance().newXMLGregorianCalendar(calendar);
        if (result.getFractionalSecond() != null) {
            result.setFractionalSecond(result.getFractionalSecond().signum() == 0 ? null
                    : result.getFractionalSecond().stripTrailingZeros());
        }
        return result;
    }
}
