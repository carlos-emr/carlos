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

import java.util.Date;
import java.util.GregorianCalendar;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Preserves MOXy's date-time lexical form, which removes trailing fractional-second zeroes.
 * The Date boundary matches existing JAXB DTO properties; replacing it with java.time would
 * change the adapter's bound type and stop it applying to those properties.
 */
public final class OptionalDateXmlAdapter extends XmlCompatibilityAdapter<String, Date> {
    /** Immutable and thread-safe; created once instead of once per marshalled value. */
    private static final DatatypeFactory DATATYPE_FACTORY = DatatypeFactory.newDefaultInstance();

    @Override
    public Date unmarshal(String value) {
        // CXF rejects JAXB validation events before an XMLGregorianCalendar adapter runs.
        // Read text first to retain MOXy's empty optional-date acceptance.
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return DATATYPE_FACTORY.newXMLGregorianCalendar(value.trim()).toGregorianCalendar().getTime();
        } catch (IllegalArgumentException malformed) {
            throw new XmlCompatibilityValueException("Invalid optional XML date");
        }
    }

    @Override
    public String marshal(Date value) {
        if (value == null) return null;
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTime(value);
        XMLGregorianCalendar result = DATATYPE_FACTORY.newXMLGregorianCalendar(calendar);
        if (result.getFractionalSecond() != null) {
            result.setFractionalSecond(result.getFractionalSecond().signum() == 0 ? null
                    : result.getFractionalSecond().stripTrailingZeros());
        }
        return result.toXMLFormat();
    }
}
