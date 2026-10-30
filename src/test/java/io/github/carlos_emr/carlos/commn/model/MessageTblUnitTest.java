package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limit {@link MessageTbl} puts on the chart PDF records of a message: what its {@code pdfattachment} column
 * (a 65,535-byte BLOB) holds, counted in the bytes that are stored.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MessageTbl unit tests")
class MessageTblUnitTest {

    @Test
    @DisplayName("should fit no PDF records, and records of up to 65,535 bytes")
    void shouldFit_whenThereAreNoRecordsOrTheyFillTheColumnExactly() {
        assertThat(MessageTbl.PDF_ATTACHMENT_MAX_BYTES).isEqualTo(65_535);
        assertThat(MessageTbl.pdfAttachmentFits(null)).isTrue();
        assertThat(MessageTbl.pdfAttachmentFits("")).isTrue();
        assertThat(MessageTbl.pdfAttachmentFits("A".repeat(65_535))).isTrue();
    }

    @Test
    @DisplayName("should not fit records of 65,536 bytes or more, which the database would cut off")
    void shouldNotFit_whenOneByteOverTheColumn() {
        assertThat(MessageTbl.pdfAttachmentFits("A".repeat(65_536))).isFalse();
        assertThat(MessageTbl.pdfAttachmentFits("A".repeat(110_000))).isFalse();
    }

    @Test
    @DisplayName("should count bytes, not characters, so a title with accents is measured as it is stored")
    void shouldCountBytes_notCharacters() {
        // "é" is two bytes in UTF-8: 32,767 of them are 65,534 bytes, 32,768 are 65,536.
        assertThat(MessageTbl.pdfAttachmentFits("é".repeat(32_767))).isTrue();
        assertThat(MessageTbl.pdfAttachmentFits("é".repeat(32_768))).isFalse();
    }
}
