package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limit {@link MessageTbl} puts on the chart PDF records of a message: what its {@code pdfattachment} column
 * (a 16,777,215-byte MEDIUMBLOB since migration V1.0.59.8) holds, counted in the bytes that are stored.
 *
 * @since 2026-10-30
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MessageTbl unit tests")
class MessageTblUnitTest {

    private static final int MAX = MessageTbl.PDF_ATTACHMENT_MAX_BYTES;

    @Test
    @DisplayName("should be the size of a MEDIUMBLOB, which is what the column is")
    void shouldBeTheSizeOfAMediumBlob_whichTheColumnIs() {
        assertThat(MAX).isEqualTo(16_777_215);
    }

    @Test
    @DisplayName("should fit no PDF records, and records of up to the column's size")
    void shouldFit_whenThereAreNoRecordsOrTheyFillTheColumnExactly() {
        assertThat(MessageTbl.pdfAttachmentFits(null)).isTrue();
        assertThat(MessageTbl.pdfAttachmentFits("")).isTrue();
        assertThat(MessageTbl.pdfAttachmentFits("A".repeat(MAX))).isTrue();
    }

    @Test
    @DisplayName("should not fit records of one byte more than the column holds, which the database would cut off")
    void shouldNotFit_whenOneByteOverTheColumn() {
        assertThat(MessageTbl.pdfAttachmentFits("A".repeat(MAX + 1))).isFalse();
    }

    @Test
    @DisplayName("should count bytes, not characters, so a title with accents is measured as it is stored")
    void shouldCountBytes_notCharacters() {
        // "é" is two bytes in UTF-8: half the column, rounded down, is one byte short of it, and one more is one over.
        assertThat(MessageTbl.pdfAttachmentFits("é".repeat(MAX / 2))).isTrue();
        assertThat(MessageTbl.pdfAttachmentFits("é".repeat(MAX / 2 + 1))).isFalse();
    }
}
