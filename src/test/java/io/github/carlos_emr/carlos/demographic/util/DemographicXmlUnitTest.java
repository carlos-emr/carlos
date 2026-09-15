/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Demographic XML storage values")
@Tag("unit")
@Tag("demographic")
class DemographicXmlUnitTest {

    /**
     * Write-side: crafted input must not be able to close an element early and
     * rewrite the structure of the stored fragment.
     */
    @Nested
    @DisplayName("Writing fragments")
    class WritingFragments {

        @Test
        @DisplayName("should escape family doctor text when inputs contain XML markup")
        void shouldEscapeFamilyDoctorText_whenInputsContainXmlMarkup() {
            String familyDoctor = DemographicXml.familyDoctor(
                    "</rdohip><injected>EVIL</injected><rdohip>",
                    "O'Brien \"Smith\" & Sons <Clinic>",
                    "A ]]> B");

            assertThat(familyDoctor).isEqualTo(
                    "<rdohip>&lt;/rdohip&gt;&lt;injected&gt;EVIL&lt;/injected&gt;&lt;rdohip&gt;</rdohip>" +
                            "<rd>O'Brien \"Smith\" &amp; Sons &lt;Clinic&gt;</rd>" +
                            "<family_doc>A ]]&gt; B</family_doc>");
        }

        @Test
        @DisplayName("should escape user notes text when content contains XML markup")
        void shouldEscapeUserNotesText_whenContentContainsXmlMarkup() {
            String notes = DemographicXml.userNotes("</unotes><injected>EVIL</injected><unotes>");

            assertThat(notes).isEqualTo(
                    "<unotes>&lt;/unotes&gt;&lt;injected&gt;EVIL&lt;/injected&gt;&lt;unotes&gt;</unotes>");
        }

        @Test
        @DisplayName("should use empty XML text when inputs are null")
        void shouldUseEmptyXmlText_whenInputsAreNull() {
            assertThat(DemographicXml.familyDoctor(null, null, "Dr. Smith"))
                    .isEqualTo("<rdohip></rdohip><rd></rd><family_doc>Dr. Smith</family_doc>");
            assertThat(DemographicXml.userNotes(null)).isEqualTo("<unotes></unotes>");
        }

        @Test
        @DisplayName("should omit optional family doctor element when value is null")
        void shouldOmitOptionalFamilyDoctorElement_whenValueIsNull() {
            String familyDoctor = DemographicXml.familyDoctor("1234", "Dr. O'Brien & Co.", null);

            assertThat(familyDoctor).isEqualTo("<rdohip>1234</rdohip><rd>Dr. O'Brien &amp; Co.</rd>");
        }

        /**
         * Apostrophes and double quotes are legal raw inside an XML text node.
         * Encoding them would add four or five characters each to a fragment that
         * has to fit the 80-character {@code demographic.family_doctor} column.
         */
        @Test
        @DisplayName("should leave quotes unescaped when serializing text nodes")
        void shouldLeaveQuotesUnescaped_whenSerializingTextNodes() {
            assertThat(DemographicXml.escapeXmlText("O'Brien \"Jr\"")).isEqualTo("O'Brien \"Jr\"");
        }

        /**
         * Guards the worst case called out in review: the form allows a 6-character
         * OHIP number and a 40-character referring doctor name, and the assembled
         * fragment must still fit {@code Demographic.FAMILY_DOCTOR_MAX_LENGTH}.
         */
        @Test
        @DisplayName("should fit the family doctor column when name uses the full form length")
        void shouldFitTheFamilyDoctorColumn_whenNameUsesTheFullFormLength() {
            String fortyCharName = "O'Brien & Sons Family Medicine Clinic Ab";
            assertThat(fortyCharName).hasSize(40);

            String familyDoctor = DemographicXml.familyDoctor("123456", fortyCharName, null);

            assertThat(familyDoctor.length()).isLessThanOrEqualTo(80);
        }

        @Test
        @DisplayName("should escape a literal entity written by the user")
        void shouldEscapeALiteralEntity_whenWrittenByTheUser() {
            assertThat(DemographicXml.escapeXmlText("A &apos; B")).isEqualTo("A &amp;apos; B");
        }
    }

    /**
     * Read-side: the stored representation is escaped, so every reader has to
     * decode it exactly once. Skipping this is what turns {@code A & B} into
     * {@code A &amp;amp; B} after a save/reopen cycle.
     */
    @Nested
    @DisplayName("Reading fragments")
    class ReadingFragments {

        @Test
        @DisplayName("should round trip family doctor values through storage")
        void shouldRoundTripFamilyDoctorValues_throughStorage() {
            String stored = DemographicXml.familyDoctor("1234", "Dr. O'Brien & Co. <Clinic>", "A ]]> B");

            assertThat(DemographicXml.referralDoctorOhip(stored)).isEqualTo("1234");
            assertThat(DemographicXml.referralDoctor(stored)).isEqualTo("Dr. O'Brien & Co. <Clinic>");
            assertThat(DemographicXml.familyDoc(stored)).isEqualTo("A ]]> B");
        }

        @Test
        @DisplayName("should round trip user notes through storage")
        void shouldRoundTripUserNotes_throughStorage() {
            String stored = DemographicXml.userNotes("Allergy: penicillin & sulfa <urgent>");

            assertThat(DemographicXml.userNotesText(stored)).isEqualTo("Allergy: penicillin & sulfa <urgent>");
        }

        @Test
        @DisplayName("should not re-encode a value when it is saved a second time")
        void shouldNotReEncodeAValue_whenItIsSavedASecondTime() {
            String firstSave = DemographicXml.userNotes("A & B");
            String reopened = DemographicXml.userNotesText(firstSave);
            String secondSave = DemographicXml.userNotes(reopened);

            assertThat(secondSave).isEqualTo(firstSave);
            assertThat(DemographicXml.userNotesText(secondSave)).isEqualTo("A & B");
        }

        @Test
        @DisplayName("should keep injected markup inert when reading a crafted fragment")
        void shouldKeepInjectedMarkupInert_whenReadingACraftedFragment() {
            String stored = DemographicXml.familyDoctor("1", "</rd><rd>SPOOFED", null);

            assertThat(DemographicXml.referralDoctor(stored)).isEqualTo("</rd><rd>SPOOFED");
        }

        /**
         * Rows written before escaping existed hold raw text; decoding them must be
         * a no-op rather than mangling a bare ampersand.
         */
        @Test
        @DisplayName("should read legacy unescaped fragments unchanged")
        void shouldReadLegacyUnescapedFragments_unchanged() {
            String legacy = "<rdohip>1234</rdohip><rd>Dr. O'Brien & Co.</rd>";

            assertThat(DemographicXml.referralDoctor(legacy)).isEqualTo("Dr. O'Brien & Co.");
            assertThat(DemographicXml.referralDoctorOhip(legacy)).isEqualTo("1234");
        }

        /**
         * The element accessors propagate null to mirror `SxmlMisc.getXmlContent`, but
         * call sites that dereference the decoded value immediately use the
         * or-empty variant instead of carrying a defensive null ternary.
         */
        @Test
        @DisplayName("should decode to empty rather than null when using the or-empty variant")
        void shouldDecodeToEmptyRatherThanNull_whenUsingTheOrEmptyVariant() {
            assertThat(DemographicXml.unescapeXmlTextOrEmpty(null)).isEmpty();
            assertThat(DemographicXml.unescapeXmlTextOrEmpty("A &amp; B")).isEqualTo("A & B");
        }

        @Test
        @DisplayName("should preserve null and missing element semantics for callers")
        void shouldPreserveNullAndMissingElementSemantics_forCallers() {
            assertThat(DemographicXml.referralDoctor(null)).isNull();
            assertThat(DemographicXml.userNotesText(null)).isNull();
            assertThat(DemographicXml.familyDoc("<rdohip>1</rdohip><rd>x</rd>")).isEmpty();
            assertThat(DemographicXml.unescapeXmlText(null)).isNull();
        }
    }
}
