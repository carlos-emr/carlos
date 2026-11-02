/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class ContentDigestsUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "é漢字🩺", "recommendation:condition:0:null", "MSH|^~\\&\rPID|synthetic\n"})
    void shouldMatchCodec_whenHashingUtf8Text(String text) {
        assertThat(ContentDigests.sha256(text)).isEqualTo(DigestUtils.sha256(text));
        assertThat(ContentDigests.sha256Hex(text)).isEqualTo(DigestUtils.sha256Hex(text));
        assertThat(ContentDigests.sha256Hex(text)).matches("[0-9a-f]{64}");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1023, 1024, 8192, 1_048_577})
    void shouldMatchCodec_whenHashingBinaryStreams(int size) throws IOException {
        byte[] data = new byte[size];
        new Random(5483).nextBytes(data);
        assertThat(ContentDigests.sha256Hex(new ByteArrayInputStream(data)))
                .isEqualTo(DigestUtils.sha256Hex(new ByteArrayInputStream(data)));
        assertThat(ContentDigests.md5Hex(new ByteArrayInputStream(data)))
                .isEqualTo(DigestUtils.md5Hex(new ByteArrayInputStream(data)));
    }

    @Test
    void shouldKeepLeadingZeroes_forStoredDigestKeys() {
        assertThat(ContentDigests.sha256Hex("286"))
                .isEqualTo("00328ce57bbc14b33bd6695bc8eb32cdf2fb5f3a7d89ec14a42825e15d39df60");
    }

    @Test
    void shouldLeaveStreamOpen_afterHashing() throws IOException {
        boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream("synthetic".getBytes(StandardCharsets.UTF_8)) {
            @Override public void close() { closed[0] = true; }
        };
        ContentDigests.sha256Hex(input);
        assertThat(input.read()).isEqualTo(-1);
        assertThat(closed[0]).isFalse();
    }

    @Test
    void shouldPropagateReadFailure_withoutClosingStream() {
        IOException failure = new IOException("synthetic read failure");
        InputStream input = new InputStream() {
            @Override public int read() throws IOException { throw failure; }
            @Override public void close() { throw new AssertionError("caller owns stream"); }
        };
        assertThatThrownBy(() -> ContentDigests.sha256Hex(input)).isSameAs(failure);
        assertThatThrownBy(() -> ContentDigests.md5Hex(input)).isSameAs(failure);
    }

    @Test
    void shouldRejectNull_likeCodec() {
        assertThatThrownBy(() -> DigestUtils.sha256((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContentDigests.sha256(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContentDigests.sha256Hex((String) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContentDigests.sha256Hex((InputStream) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ContentDigests.md5Hex(null)).isInstanceOf(NullPointerException.class);
    }
}
