/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.utility.xml;


/** Keeps the character-valued XML contract established by MOXy before Metro was removed. */
public final class CharacterXmlAdapter extends XmlCompatibilityAdapter<String, Character> {
    @Override
    public Character unmarshal(String value) {
        if (value == null) return null;
        if (value.isEmpty()) return '\0';
        if (value.length() != 1) throw new IllegalArgumentException("Expected one XML character");
        return value.charAt(0);
    }

    @Override
    public String marshal(Character value) {
        // MOXy omits null but writes the NUL sentinel as an empty element, never a numeric zero.
        if (value == null) return null;
        return value == '\0' ? "" : value.toString();
    }
}
