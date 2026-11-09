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


/** Published literal characters by default; numeric code units require an explicit historical profile. */
public final class CharacterXmlAdapter extends XmlCompatibilityAdapter<String, Character> {
    @Override
    public Character unmarshal(String value) {
        if (value == null) return null;
        if (XmlWireProfile.current() == XmlWireProfile.GLASSFISH_NUMERIC_CHARACTERS) {
            if (!value.matches("[0-9]{1,5}")) throw new XmlCompatibilityValueException("Expected an unsigned XML character code");
            int code = Integer.parseInt(value);
            if (code > Character.MAX_VALUE) throw new XmlCompatibilityValueException("XML character code is out of range");
            return (char) code;
        }
        if (value.isEmpty()) return '\0';
        if (value.length() != 1) throw new XmlCompatibilityValueException("Expected one XML character");
        return value.charAt(0);
    }

    @Override
    public String marshal(Character value) {
        // MOXy omits null but writes the NUL sentinel as an empty element, never a numeric zero.
        if (value == null) return null;
        if (XmlWireProfile.current() == XmlWireProfile.GLASSFISH_NUMERIC_CHARACTERS) {
            return Integer.toString(value);
        }
        return value == '\0' ? "" : value.toString();
    }
}
