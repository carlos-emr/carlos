/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class JdkStreamCompatibilityUnitTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8191, 8192, 1_048_577})
    void shouldCopyIdenticalBytes_forMemoryAndFileStreams(int size) throws IOException {
        byte[] bytes = new byte[size];
        new Random(5483).nextBytes(bytes);
        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        IOUtils.copy(new ByteArrayInputStream(bytes), legacy);
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        new ByteArrayInputStream(bytes).transferTo(actual);
        assertThat(actual.toByteArray()).isEqualTo(legacy.toByteArray());
        Path file = directory.resolve("synthetic.bin");
        Files.write(file, bytes);
        try (InputStream input = new java.io.FileInputStream(file.toFile())) {
            actual.reset();
            input.transferTo(actual);
            assertThat(actual.toByteArray()).isEqualTo(legacy.toByteArray());
        }
        assertThat(new ByteArrayInputStream(bytes).readAllBytes())
                .isEqualTo(IOUtils.toByteArray(new ByteArrayInputStream(bytes)));
    }

    @Test
    void shouldCopyBeyondIntegerLimit_whenCallerDiscardsCount() throws IOException {
        long size = (long) Integer.MAX_VALUE + 4096;
        CountingOutput legacy = new CountingOutput();
        assertThat(IOUtils.copy(new GeneratedInput(size), legacy)).isEqualTo(-1);
        CountingOutput actual = new CountingOutput();
        assertThat(new GeneratedInput(size).transferTo(actual)).isEqualTo(size);
        assertThat(actual.count).isEqualTo(legacy.count).isEqualTo(size);
    }

    @Test
    void shouldLeaveStreamsOpenAndUnflushed_afterCopy() throws IOException {
        InputStream input = new ByteArrayInputStream(new byte[]{1, 2, 3}) {
            @Override public void close() { throw new AssertionError("caller owns input"); }
        };
        OutputStream output = new OutputStream() {
            @Override public void write(int value) {
                // Discard bytes: this test observes ownership and flushing, not content.
            }
            @Override public void close() { throw new AssertionError("caller owns output"); }
            @Override public void flush() { throw new AssertionError("caller flushes output"); }
        };
        assertThat(input.transferTo(output)).isEqualTo(3);
    }

    @Test
    void shouldPropagateWriteErrors_likeCommonsCopy() {
        IOException failure = new IOException("synthetic write failure");
        OutputStream output = new OutputStream() {
            @Override public void write(int value) throws IOException { throw failure; }
        };
        assertThatThrownBy(() -> new ByteArrayInputStream(new byte[]{1}).transferTo(output)).isSameAs(failure);
        assertThatThrownBy(() -> IOUtils.copy(new ByteArrayInputStream(new byte[]{1}), output)).isSameAs(failure);
    }

    private static final class GeneratedInput extends InputStream {
        private long remaining;
        private GeneratedInput(long size) { remaining = size; }
        @Override public int read() { return remaining-- > 0 ? 0 : -1; }
        @Override public int read(byte[] bytes, int offset, int length) {
            if (remaining == 0) return -1;
            int count = (int) Math.min(length, remaining);
            Arrays.fill(bytes, offset, offset + count, (byte) 0);
            remaining -= count;
            return count;
        }
    }

    private static final class CountingOutput extends OutputStream {
        private long count;
        @Override public void write(int value) { count++; }
        @Override public void write(byte[] bytes, int offset, int length) { count += length; }
    }
}
