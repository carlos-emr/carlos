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
package io.github.carlos_emr.carlos.utility;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ImportZipInputStreamUnitTest {
    private static final class TrackedInput extends ByteArrayInputStream {
        private int reads;
        private boolean closed;

        private TrackedInput(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read() {
            reads++;
            return super.read();
        }

        @Override
        public synchronized int read(byte[] buffer, int offset, int length) {
            reads++;
            return super.read(buffer, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private byte[] archive(int... sizes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < sizes.length; i++) {
                zip.putNextEntry(new ZipEntry("entry-" + i));
                zip.write(new byte[sizes[i]]);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @ParameterizedTest
    @CsvSource({"0,16,2", "-1,16,2", "8,0,2", "8,-1,2", "8,16,0", "8,16,-1"})
    void shouldRejectNonPositiveLimits_beforeTakingOwnershipOfInput(long entryBytes, long totalBytes, int entries)
            throws Exception {
        var input = new TrackedInput(archive(8));
        int available = input.available();

        assertThatThrownBy(() -> new ImportZipInputStream(input, entryBytes, totalBytes, entries))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("ZIP import limits must be positive");

        assertThat(input.reads).isZero();
        assertThat(input.available()).isEqualTo(available);
        assertThat(input.closed).isFalse();
    }

    @Test
    void shouldPreserveZeroLengthReads_beforeAndAfterExactEntryLimits() throws Exception {
        var input = new TrackedInput(archive(8));
        try (var zip = new ImportZipInputStream(input, 8, 8, 1)) {
            assertZeroReadLeavesInputUntouched(zip, input);
            assertThat(zip.getNextEntry()).isNotNull();
            assertZeroReadLeavesInputUntouched(zip, input);
            assertThat(zip.readNBytes(8)).hasSize(8);
            assertZeroReadLeavesInputUntouched(zip, input);
            assertThat(zip.read()).isEqualTo(-1);
            assertZeroReadLeavesInputUntouched(zip, input);
            assertThat(zip.getNextEntry()).isNull();
            assertZeroReadLeavesInputUntouched(zip, input);
        }
        assertThat(input.closed).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void shouldMatchClosedZipReads_whenDestinationRangeIsValid(int length) throws Exception {
        byte[] bytes = archive(8);
        try (var nativeZip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            nativeZip.close();
            assertThatThrownBy(() -> nativeZip.read(new byte[1], 0, length)).isInstanceOf(IOException.class);
        }
        var input = new TrackedInput(bytes);
        try (var zip = new ImportZipInputStream(input, 8, 8, 1)) {
            zip.close();
            int reads = input.reads;
            assertThatThrownBy(() -> zip.read(new byte[1], 0, length)).isInstanceOf(IOException.class);
            assertThat(input.reads).isEqualTo(reads);
        }
    }

    @ParameterizedTest
    @CsvSource({"-1,0", "0,-1", "3,0", "1,2", "2147483647,1", "1,2147483647", "0,3"})
    void shouldRejectInvalidDestinationRanges_withoutConsumingInput(int offset, int length) throws Exception {
        var input = new TrackedInput(archive(8));
        try (var zip = new ImportZipInputStream(input, 8, 8, 1)) {
            assertThat(zip.getNextEntry()).isNotNull();
            int reads = input.reads;
            assertThatThrownBy(() -> zip.read(new byte[2], offset, length))
                    .isInstanceOf(IndexOutOfBoundsException.class);
            assertThat(input.reads).isEqualTo(reads);
            assertThat(zip.readAllBytes()).hasSize(8);
        }
    }

    @Test
    void shouldRejectNullDestination_withoutConsumingInput() throws Exception {
        var input = new TrackedInput(archive(8));
        try (var zip = new ImportZipInputStream(input, 8, 8, 1)) {
            assertThat(zip.getNextEntry()).isNotNull();
            int reads = input.reads;
            assertThatThrownBy(() -> zip.read(null, 0, 0)).isInstanceOf(NullPointerException.class);
            assertThat(input.reads).isEqualTo(reads);
            assertThat(zip.readAllBytes()).hasSize(8);
        }
    }

    private static void assertZeroReadLeavesInputUntouched(ImportZipInputStream zip, TrackedInput input)
            throws IOException {
        byte[] destination = {7};
        int reads = input.reads;
        assertThat(zip.read(destination, destination.length, 0)).isZero();
        assertThat(destination).containsExactly((byte) 7);
        assertThat(input.reads).isEqualTo(reads);
    }

    @Test
    void shouldAcceptExactLimits_whenReadingAndSkippingEntries() throws Exception {
        try (var zip = new ImportZipInputStream(new ByteArrayInputStream(archive(8, 8)), 8, 16, 2)) {
            assertThat(zip.getNextEntry().getSize()).isEqualTo(-1);
            assertThat(zip.read()).isZero();
            assertThat(zip.skip(7)).isEqualTo(7);
            assertThat(zip.read()).isEqualTo(-1);
            assertThat(zip.getNextEntry()).isNotNull();
            zip.closeEntry();
            assertThat(zip.getNextEntry()).isNull();
        }
    }

    @Test
    void shouldRejectExpandedBytes_whenDescriptorSizeIsUnknown() throws Exception {
        byte[] compressed = archive(1 << 20);
        assertThat(compressed).hasSizeLessThan(2048);
        try (var zip = new ImportZipInputStream(new ByteArrayInputStream(compressed), 32, 64, 2)) {
            assertThat(zip.getNextEntry().getSize()).isEqualTo(-1);
            ByteArrayOutputStream written = new ByteArrayOutputStream();
            assertThatThrownBy(() -> zip.transferTo(written)).isInstanceOf(IOException.class)
                    .hasMessageContaining("expanded byte limit");
            assertThat(written.size()).isLessThanOrEqualTo(32);
        }
    }

    @Test
    void shouldRejectTotalBytes_whenAdvancingPastUnreadEntries() throws Exception {
        try (var zip = new ImportZipInputStream(new ByteArrayInputStream(archive(8, 8)), 8, 15, 2)) {
            zip.getNextEntry();
            zip.getNextEntry();
            assertThatThrownBy(zip::getNextEntry).isInstanceOf(IOException.class)
                    .hasMessageContaining("expanded byte limit");
        }
    }

    @Test
    void shouldRejectExpandedBytes_whenSkippingAnOversizedEntry() throws Exception {
        try (var zip = new ImportZipInputStream(new ByteArrayInputStream(archive(1024)), 32, 64, 2)) {
            zip.getNextEntry();
            assertThatThrownBy(() -> zip.skip(1024)).isInstanceOf(IOException.class)
                    .hasMessageContaining("expanded byte limit");
        }
    }

    @Test
    void shouldRejectExcessEntries_whenAllEntriesAreEmpty() throws Exception {
        try (var zip = new ImportZipInputStream(new ByteArrayInputStream(archive(0, 0, 0)), 8, 16, 2)) {
            zip.getNextEntry();
            zip.getNextEntry();
            assertThatThrownBy(zip::getNextEntry).isInstanceOf(IOException.class)
                    .hasMessageContaining("entry count limit");
        }
    }

    @Test
    void shouldRejectFalseSmallSizes_whenActualPayloadExceedsBudget() throws Exception {
        byte[] compressed = archive(1 << 20);
        // Remove the descriptor flag and claim one byte in the local header. The real DEFLATE
        // body remains large; reject before ZIP CRC/size validation can run at the end.
        compressed[6] = 0;
        compressed[22] = 1;
        try (var zip = new ImportZipInputStream(new ByteArrayInputStream(compressed), 32, 64, 2)) {
            assertThat(zip.getNextEntry().getSize()).isEqualTo(1);
            assertThatThrownBy(() -> zip.transferTo(OutputStream.nullOutputStream()))
                    .isInstanceOf(IOException.class).hasMessageContaining("expanded byte limit");
        }
    }
}
