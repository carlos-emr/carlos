/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.LinkedHashMap;
import java.util.Map;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("xml")
class StringMapXmlAdapterUnitTest extends CarlosUnitTestBase {
    private final StringMapXmlAdapter adapter = new StringMapXmlAdapter();

    @Test
    void shouldDistinguishAbsentAndPresentEmptyMaps_atXmlBoundary() {
        assertThat(adapter.marshal(null)).isNull();
        assertThat(adapter.marshal(Map.of())).isNull();
        assertThat(adapter.unmarshal(null)).isNull();
        assertThat(adapter.unmarshal(new StringMapXmlAdapter.Entries())).isEmpty();
    }

    @Test
    void shouldRetainOrderedUnicodeAndNullEntries_whenRoundTripping() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("é<&", "日本語");
        original.put(null, "FAKE-null-key");
        original.put("empty", "");
        original.put("absent", null);
        Map<String, String> actual = adapter.unmarshal(adapter.marshal(original));
        assertThat(actual).containsExactlyEntriesOf(original);
        assertThat(actual.keySet()).containsExactly("é<&", null, "empty", "absent");
    }

    @Test
    void shouldKeepLastDuplicateValue_withoutReorderingKeys() {
        var entries = new StringMapXmlAdapter.Entries();
        for (String value : new String[]{"first", "last"}) {
            var entry = new StringMapXmlAdapter.Entry();
            entry.key = "same";
            entry.value = value;
            entries.entry.add(entry);
        }
        assertThat(adapter.unmarshal(entries)).containsExactlyEntriesOf(Map.of("same", "last"));
    }
}
