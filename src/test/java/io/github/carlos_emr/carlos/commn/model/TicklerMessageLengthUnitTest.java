/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class TicklerMessageLengthUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"x", "é", "😀"})
    void shouldPreserveExactCapacityAndRefuseOverflowWithoutReplacingText_whenUtf8WidthsDiffer(String character) {
        int width = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String exact = character.repeat(65535 / width) + "x".repeat(65535 % width);
        Tickler tickler = new Tickler();
        tickler.setMessage(exact);
        assertThat(tickler.getMessage()).isEqualTo(exact);
        assertThatThrownBy(() -> tickler.setMessage(exact + "x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tickler.getMessage()).isEqualTo(exact);
    }
}
