package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The limit {@link MessageTbl} puts on the chart PDF records of a message: what its {@code pdfattachment} column
 * (a 16,777,215-byte MEDIUMBLOB since migration V1.0.59.8) holds, counted in the bytes that are stored. And the
 * limit on its subject: what {@code thesubject} (a utf8mb4 VARCHAR(128)) holds, counted in code points.
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

    /** U+1F600, one character in utf8mb4 and two Java chars (a surrogate pair). */
    private static final String EMOJI = "\uD83D\uDE00";

    @Test
    @DisplayName("should hold a subject of 128 characters, the length of messagetbl.thesubject")
    void shouldBeTheLengthOfTheSubjectColumn_forTheSubjectLimit() {
        assertThat(MessageTbl.SUBJECT_MAX_LENGTH).isEqualTo(128);
    }

    @Test
    @DisplayName("should fit no subject, and a subject that fills the column exactly")
    void shouldFitSubject_whenItFillsTheColumnExactly() {
        assertThat(MessageTbl.subjectFits(null)).isTrue();
        assertThat(MessageTbl.subjectFits("")).isTrue();
        assertThat(MessageTbl.subjectFits("S".repeat(128))).isTrue();
    }

    @Test
    @DisplayName("should not fit a subject one character longer than the column, which the database would cut")
    void shouldNotFitSubject_whenOneCharacterOverTheColumn() {
        assertThat(MessageTbl.subjectFits("S".repeat(129))).isFalse();
    }

    @Test
    @DisplayName("should count an emoji as one character, as utf8mb4 does, not as two UTF-16 units")
    void shouldCountCodePoints_whenTheSubjectHasSupplementaryCharacters() {
        assertThat(MessageTbl.subjectFits(EMOJI.repeat(128))).isTrue();
        assertThat(MessageTbl.subjectFits(EMOJI.repeat(129))).isFalse();
        assertThat(MessageTbl.subjectFits("S".repeat(127) + EMOJI)).isTrue();
        assertThat(MessageTbl.subjectFits("S".repeat(128) + EMOJI)).isFalse();
    }

    @Test
    @DisplayName("should leave a subject that fits as it is")
    void shouldKeepSubject_whenItFits() {
        String full = "Re:" + "S".repeat(124) + EMOJI;
        assertThat(MessageTbl.fitSubject(full)).isSameAs(full);
        assertThat(MessageTbl.fitSubject(null)).isNull();
    }

    @Test
    @DisplayName("should cut a built subject to the column without splitting a character")
    void shouldCutSubjectToTheColumn_withoutSplittingASurrogatePair() {
        // Character 128 is an emoji: a cut after 128 Java chars would keep only its high surrogate.
        String built = "Re:" + "S".repeat(124) + EMOJI + EMOJI + "tail";
        String cut = MessageTbl.fitSubject(built);

        assertThat(cut).isEqualTo("Re:" + "S".repeat(124) + EMOJI);
        assertThat(cut.codePointCount(0, cut.length())).isEqualTo(128);
        assertThat(MessageTbl.subjectFits(cut)).isTrue();
    }
}
