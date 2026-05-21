/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.zip.ZipEntry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.util.JDBCUtil;

@Tag("unit")
@DisplayName("Form XML upload")
class FrmXmlUpload2ActionUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should accept bounded archive entries")
    void shouldAcceptBoundedArchiveEntries() throws Exception {
        ZipEntry entry = archiveEntry("formFoo_123_20260520145500.xml", 1024L, 512L);

        long entrySize = FrmXmlUpload2Action.validateArchiveEntry(entry, 1, 0L);

        assertThat(entrySize).isEqualTo(1024L);
    }

    @Test
    @DisplayName("should reject archives with too many entries")
    void shouldRejectArchivesWithTooManyEntries() {
        ZipEntry entry = archiveEntry("formFoo_123_20260520145500.xml", 1024L, 512L);

        assertThatThrownBy(() -> FrmXmlUpload2Action.validateArchiveEntry(
                entry, FrmXmlUpload2Action.MAX_XML_IMPORT_ENTRIES + 1, 0L))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    @Test
    @DisplayName("should reject archive entries with unknown size")
    void shouldRejectArchiveEntriesWithUnknownSize() {
        ZipEntry entry = new ZipEntry("formFoo_123_20260520145500.xml");

        assertThatThrownBy(() -> FrmXmlUpload2Action.validateArchiveEntry(entry, 1, 0L))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    @Test
    @DisplayName("should reject archive entries with high compression ratio")
    void shouldRejectArchiveEntriesWithHighCompressionRatio() {
        ZipEntry entry = archiveEntry("formFoo_123_20260520145500.xml",
                FrmXmlUpload2Action.MAX_XML_IMPORT_COMPRESSION_RATIO + 1, 1L);

        assertThatThrownBy(() -> FrmXmlUpload2Action.validateArchiveEntry(entry, 1, 0L))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    @Test
    @DisplayName("should reject archives exceeding total uncompressed size")
    void shouldRejectArchivesExceedingTotalUncompressedSize() {
        ZipEntry entry = archiveEntry("formFoo_123_20260520145500.xml", 1024L, 512L);

        assertThatThrownBy(() -> FrmXmlUpload2Action.validateArchiveEntry(
                entry, 1, FrmXmlUpload2Action.MAX_XML_IMPORT_TOTAL_BYTES - 1023L))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    private ZipEntry archiveEntry(String name, long size, long compressedSize) {
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(size);
        entry.setCompressedSize(compressedSize);
        return entry;
    }
}
