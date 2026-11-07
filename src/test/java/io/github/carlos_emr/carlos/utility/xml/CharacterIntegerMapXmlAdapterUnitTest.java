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
class CharacterIntegerMapXmlAdapterUnitTest extends CarlosUnitTestBase {
    private final CharacterIntegerMapXmlAdapter adapter = new CharacterIntegerMapXmlAdapter();

    @Test
    void shouldPreserveAbsentAndEmptyDistinction_whenConvertingMaps() {
        assertThat(adapter.marshal(null)).isNull();
        assertThat(adapter.marshal(Map.of()).entry).isEmpty();
        assertThat(adapter.unmarshal(null)).isNull();
        assertThat(adapter.unmarshal(new CharacterIntegerMapXmlAdapter.Entries())).isEmpty();
    }

    @Test
    void shouldPreserveInsertionOrderAndSentinels_whenRoundTripping() {
        Map<Character, Integer> original = new LinkedHashMap<>();
        original.put('é', 7);
        original.put('\0', 0);
        original.put(null, null);
        original.put('&', -1);
        var actual = adapter.unmarshal(adapter.marshal(original));
        assertThat(actual).containsExactlyEntriesOf(original);
        assertThat(actual.keySet()).containsExactly('é', '\0', null, '&');
    }

    @Test
    void shouldKeepLastDuplicateValue_whenXmlRepeatsAKey() {
        var entries = new CharacterIntegerMapXmlAdapter.Entries();
        for (int value : new int[]{1, 2}) {
            var entry = new CharacterIntegerMapXmlAdapter.Entry();
            entry.key = 'A';
            entry.value = value;
            entries.entry.add(entry);
        }
        assertThat(adapter.unmarshal(entries)).containsExactlyEntriesOf(Map.of('A', 2));
    }
}
