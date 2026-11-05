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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;

/** Preserves MOXy's omission of an empty map while retaining the standard entry/key/value form. */
public final class StringMapXmlAdapter extends XmlCompatibilityAdapter<StringMapXmlAdapter.Entries, Map<String, String>> {
    /** XML container for ordered map entries; an empty container represents a present, empty map. */
    @jakarta.xml.bind.annotation.XmlType(name = "")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Entries {
        public List<Entry> entry = new ArrayList<>();
    }

    /** One XML entry with a {@link String} key and a {@link String} value. */
    @jakarta.xml.bind.annotation.XmlType(name = "")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Entry {
        public String key;
        public String value;
    }

    /**
     * Converts a map to XML entries, preserving MOXy's omission of null and empty maps.
     *
     * @param value Map&lt;String, String&gt; to serialize, or null
     * @return Entries containing the map entries, or null for a null or empty map so the element is omitted
     */
    @Override
    public Entries marshal(Map<String, String> value) {
        if (value == null || value.isEmpty()) return null;
        Entries result = new Entries();
        value.forEach((key, item) -> {
            Entry entry = new Entry();
            entry.key = key;
            entry.value = item;
            result.entry.add(entry);
        });
        return result;
    }

    /**
     * Converts a present XML container to an insertion-ordered map, including an empty map.
     *
     * @param value Entries from XML, or null when the element is absent
     * @return Map&lt;String, String&gt; of entries, or null only when the element is absent
     */
    @Override
    @SuppressWarnings("java:S1168") // JAXB null represents absent/nil XML, distinct from a present empty container.
    public Map<String, String> unmarshal(Entries value) {
        if (value == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Entry entry : value.entry) result.put(entry.key, entry.value);
        return result;
    }
}
