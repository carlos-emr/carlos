/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under the GNU General Public License, version 2 or later.
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
    public Map<String, String> unmarshal(Entries value) {
        if (value == null) return null;
        Map<String, String> result = new LinkedHashMap<>();
        for (Entry entry : value.entry) result.put(entry.key, entry.value);
        return result;
    }
}
