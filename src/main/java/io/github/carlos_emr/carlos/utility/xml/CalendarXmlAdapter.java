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
package io.github.carlos_emr.carlos.utility.xml;

import java.util.Calendar;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Preserves MOXy's {@link Calendar} lexical form.
 *
 * <p>MOXy decides the shape of a calendar value from {@link Calendar#isSet(int)}, never from the
 * field values themselves:
 * <ul>
 * <li>no time field set (a {@code clear()}ed calendar with only year, month and day assigned, such
 * as {@code Demographic.getBirthDay()}) marshals as a plain {@code xs:date} value;</li>
 * <li>otherwise the value is a full date-time, and the zone offset is written only when the zone
 * fields are set. Every calendar built through {@code setTime}/{@code setTimeInMillis} (all of
 * {@code DateUtils.toGregorianCalendar}) has them set and therefore carries its offset, for
 * example {@code 2026-07-15T09:30:00-04:00} or {@code 2026-07-15T13:30:00Z}.</li>
 * </ul>
 * Trailing fractional-second zeroes are removed and a zero fraction is omitted. A midnight value
 * with its time fields set stays {@code 2026-07-15T00:00:00-04:00}: the time is never dropped on
 * the strength of its value alone, because the published WSDLs declare these elements
 * {@code xs:dateTime}. Calendar is required by the existing DTO signatures and its field-set
 * state is part of the compatibility contract; java.time values cannot carry that state.
 */
@SuppressWarnings("java:S2143")
public final class CalendarXmlAdapter extends XmlCompatibilityAdapter<String, Calendar> {
    /** Immutable and thread-safe; created once instead of once per marshalled value. */
    private static final DatatypeFactory DATATYPE_FACTORY = DatatypeFactory.newDefaultInstance();

    @Override
    public Calendar unmarshal(String value) {
        // xs:dateTime collapses XML whitespace; String-valued adapters receive it verbatim.
        return value == null ? null : DATATYPE_FACTORY.newXMLGregorianCalendar(value.trim()).toGregorianCalendar();
    }

    @Override
    public String marshal(Calendar value) {
        if (value == null) return null;
        // Read the set-state before copying: the copy below marks every field as set.
        boolean hasTime = value.isSet(Calendar.HOUR_OF_DAY) || value.isSet(Calendar.HOUR)
                || value.isSet(Calendar.MINUTE) || value.isSet(Calendar.SECOND) || value.isSet(Calendar.MILLISECOND);
        boolean hasZone = value.isSet(Calendar.ZONE_OFFSET) || value.isSet(Calendar.DST_OFFSET);
        GregorianCalendar calendar = new GregorianCalendar(value.getTimeZone());
        calendar.setTimeInMillis(value.getTimeInMillis());
        XMLGregorianCalendar result = DATATYPE_FACTORY.newXMLGregorianCalendar(calendar);
        if (!hasZone) {
            result.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
        }
        if (!hasTime) {
            result.setTime(DatatypeConstants.FIELD_UNDEFINED, DatatypeConstants.FIELD_UNDEFINED,
                    DatatypeConstants.FIELD_UNDEFINED);
            result.setFractionalSecond(null);
        } else if (result.getFractionalSecond() != null) {
            result.setFractionalSecond(result.getFractionalSecond().signum() == 0 ? null
                    : result.getFractionalSecond().stripTrailingZeros());
        }
        return result.toXMLFormat();
    }
}
