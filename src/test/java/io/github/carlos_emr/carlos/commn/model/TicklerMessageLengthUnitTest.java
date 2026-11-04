/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class TicklerMessageLengthUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"x", "é", "漢", "😀", "\uD800", "\uDC00", "x\uD800", "\uDC00x"})
    void shouldPreserveExactCapacityAndRefuseOverflowWithoutReplacingText_whenUtf8WidthsDiffer(String character) {
        int width = character.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        String exact = character.repeat(65535 / width) + "x".repeat(65535 % width);
        Tickler tickler = new Tickler();
        tickler.setMessage(exact);
        assertThat(tickler.getMessage()).isEqualTo(exact);
        assertThatThrownBy(() -> tickler.setMessage(exact + "x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tickler.getMessage()).isEqualTo(exact);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\uD800\uD800\uDC00", "\uDC00\uD800\uDC00", "\uD800x\uDC00", "\uD800\uDC00\uD800"})
    void shouldMatchJavaUtf8EncodingAtTheBoundary_whenSurrogatesAreMixed(String suffix) {
        for (int padding = 0; padding < 10; padding++) {
            String message = "x".repeat(65526 + padding) + suffix;
            boolean fits = message.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= Tickler.MESSAGE_MAX_UTF8_BYTES;
            assertThat(Tickler.isMessageWithinStorageLimit(message)).isEqualTo(fits);
        }
    }
}
