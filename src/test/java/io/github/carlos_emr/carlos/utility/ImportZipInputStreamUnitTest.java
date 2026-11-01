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
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class ImportZipInputStreamUnitTest {
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
        assertThat(compressed.length).isLessThan(2048);
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
