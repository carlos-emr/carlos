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
 * Test cases ported from Open-O RxPharmacyDataPhoneTest
 * (openo-beta/Open-O PR #2494, Liam Stanziani, 2026) and extended by
 * CARLOS Contributors, 2026.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RxPharmacyData#composePharmacyPhone(PharmacyInfo)}, the helper behind the
 * {@code Tel:} segment of the "Rx faxed to" encounter note and the prescription PDF pharmacy block.
 *
 * <p>The helper must join phone1 and phone2 with a single space while skipping whichever is
 * absent, so neither output ever shows a stray separator, a literal "null" or a dangling label.
 * Stored formats are free text and must come back verbatim. The method is static and
 * {@link RxPharmacyData}'s Spring lookups are instance fields, so no bean registry is needed.</p>
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("rx")
@Tag("prescription")
@DisplayName("RxPharmacyData.composePharmacyPhone")
class RxPharmacyDataComposePhoneUnitTest {

    private static PharmacyInfo pharmacyWithPhones(String phone1, String phone2) {
        PharmacyInfo pharmacy = new PharmacyInfo();
        pharmacy.setPhone1(phone1);
        pharmacy.setPhone2(phone2);
        return pharmacy;
    }

    @Test
    @DisplayName("should join both numbers with a single space when both are present")
    void shouldJoinBothNumbers_whenBothPresent() {
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("(416) 269-4820", "416-555-0000")))
                .isEqualTo("(416) 269-4820 416-555-0000");
    }

    @Test
    @DisplayName("should trim both numbers and still join them with a single space")
    void shouldTrimBothNumbers_whenBothArePadded() {
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("  (416) 269-4820  ", "\t416-555-0000 ")))
                .isEqualTo("(416) 269-4820 416-555-0000");
    }

    @Test
    @DisplayName("should return phone1 alone when phone2 is null or empty")
    void shouldReturnPhone1Alone_whenPhone2Absent() {
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("(416) 269-4820", null)))
                .isEqualTo("(416) 269-4820");
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("(416) 269-4820", "")))
                .isEqualTo("(416) 269-4820");
    }

    @Test
    @DisplayName("should return phone2 alone without a leading separator when phone1 is blank")
    void shouldReturnPhone2Alone_whenPhone1Blank() {
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones(null, "416-555-0000")))
                .isEqualTo("416-555-0000");
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("   ", "416-555-0000")))
                .isEqualTo("416-555-0000");
    }

    @Test
    @DisplayName("should return empty when no phone numbers are on file")
    void shouldReturnEmpty_whenNoPhoneNumbersOnFile() {
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones(null, null))).isEmpty();
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("", ""))).isEmpty();
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("   ", "\r\n"))).isEmpty();
    }

    @Test
    @DisplayName("should return empty, not null, when the pharmacy is null")
    void shouldReturnEmpty_whenPharmacyIsNull() {
        assertThat(RxPharmacyData.composePharmacyPhone(null)).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("should preserve stored formatting verbatim for mixed formats")
    void shouldPreserveStoredFormatting_forMixedFormats() {
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("(604) 707-5989", "14162694819")))
                .isEqualTo("(604) 707-5989 14162694819");
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("416.555.0000 x12", null)))
                .isEqualTo("416.555.0000 x12");
    }

    @Test
    @DisplayName("should collapse embedded CR/LF runs so the note stays on one line")
    void shouldCollapseEmbeddedNewlines_forMultilinePhone() {
        String phone = RxPharmacyData.composePharmacyPhone(
                pharmacyWithPhones("416-555-0000\r\next 123", "416-555-0001\n\n"));

        assertThat(phone).isEqualTo("416-555-0000 ext 123 416-555-0001");
        assertThat(phone).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("should leave script-significant characters for the caller to encode")
    void shouldReturnRawText_forScriptSignificantCharacters() {
        // Encoding is context-specific and belongs to the caller (ViewScript2.jsp uses
        // SafeEncode.forJavaScript); the helper must not pre-escape and cause double encoding.
        assertThat(RxPharmacyData.composePharmacyPhone(pharmacyWithPhones("555'</script>\"", null)))
                .isEqualTo("555'</script>\"");
    }
}
