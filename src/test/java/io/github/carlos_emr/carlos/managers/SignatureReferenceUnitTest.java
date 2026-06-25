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
package io.github.carlos_emr.carlos.managers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Signature reference classification")
@Tag("unit")
class SignatureReferenceUnitTest {

    @Test
    @DisplayName("treats a 1-9 digit value as a stored id but not a 10-digit value")
    void shouldClassifyStoredId_atNineDigitBoundary() {
        assertThat(SignatureReference.isStoredId("1")).isTrue();
        assertThat(SignatureReference.isStoredId("123456789")).isTrue();   // 9 digits — int-safe
        assertThat(SignatureReference.isStoredId("1234567890")).isFalse(); // 10 digits — manual request id range
        assertThat(SignatureReference.isStoredId("12a")).isFalse();
        assertThat(SignatureReference.isStoredId("")).isFalse();
        assertThat(SignatureReference.isStoredId(null)).isFalse();
    }

    @Test
    @DisplayName("classifies a re-sign as a manual reference carrying the captured request id")
    void shouldClassifyManual_whenReSigning() {
        SignatureReference ref = SignatureReference.parse(true, "  ", "9999981000");

        assertThat(ref.isManual()).isTrue();
        assertThat(ref.value()).isEqualTo("9999981000");
    }

    @Test
    @DisplayName("prefers the submitted marker over the captured id for a manual re-sign")
    void shouldPreferSubmittedMarker_forManualReSign() {
        SignatureReference ref = SignatureReference.parse(true, "manual-abc", "9999981000");

        assertThat(ref.isManual()).isTrue();
        assertThat(ref.value()).isEqualTo("manual-abc");
    }

    @Test
    @DisplayName("classifies a numeric submitted value as a stored reference when not re-signing")
    void shouldClassifyStored_whenNotReSigningWithNumericId() {
        SignatureReference ref = SignatureReference.parse(false, "123", "");

        assertThat(ref.isStored()).isTrue();
        assertThat(ref.value()).isEqualTo("123");
    }

    @Test
    @DisplayName("classifies an empty submitted value as a stamp reference when not re-signing")
    void shouldClassifyStamp_whenNotReSigningWithoutStoredId() {
        SignatureReference ref = SignatureReference.parse(false, "", "");

        assertThat(ref.isStamp()).isTrue();
        assertThat(ref.value()).isEmpty();
    }

    @Test
    @DisplayName("rejects a direct STORED construction whose value is not a stored id")
    void shouldRejectConstruction_whenStoredValueIsNotAStoredId() {
        assertThatThrownBy(() -> new SignatureReference(SignatureReference.Kind.STORED, "abc"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a direct STAMP construction that carries a value")
    void shouldRejectConstruction_whenStampCarriesAValue() {
        assertThatThrownBy(() -> new SignatureReference(SignatureReference.Kind.STAMP, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
