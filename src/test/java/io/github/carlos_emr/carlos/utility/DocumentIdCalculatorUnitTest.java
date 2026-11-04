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

import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("fast")
@Tag("pdf")
@DisplayName("DocumentIdCalculator")
class DocumentIdCalculatorUnitTest {

    /** Bytes 1 to 32: a FAKE secret for these tests only. */
    private static final byte[] FAKE_SECRET = new byte[32];

    static {
        for (int i = 0; i < FAKE_SECRET.length; i++) {
            FAKE_SECRET[i] = (byte) (i + 1);
        }
    }

    private final Object originalSecret = CarlosProperties.getInstance().get(DocumentIdCalculator.SECRET_PROPERTY);

    @AfterEach
    void restoreSetting() {
        if (originalSecret == null) {
            CarlosProperties.getInstance().remove(DocumentIdCalculator.SECRET_PROPERTY);
        } else {
            CarlosProperties.getInstance().put(DocumentIdCalculator.SECRET_PROPERTY, originalSecret);
        }
    }

    @Test
    @DisplayName("should match an independently computed HMAC")
    void shouldMatchKnownAnswer_forAFixedSecret() {
        // HMAC-SHA256(bytes 1..32, "carlos-emr/document-id/v1" 0x00 "eform:42"), as base64url,
        // computed with Python's hmac module.
        assertThat(new DocumentIdCalculator(FAKE_SECRET).documentId("eform", "42"))
                .isEqualTo("NQP6VEqbRs3qDiu8FfzrbHJT80WWArgyZP7gAbGYPFw");
    }

    @Test
    @DisplayName("should give a stable ID that changes with the source, the record and the secret")
    void shouldBeStable_andDistinctPerRecord() {
        DocumentIdCalculator calculator = new DocumentIdCalculator(FAKE_SECRET);
        byte[] otherSecret = FAKE_SECRET.clone();
        otherSecret[0] = 99;

        assertThat(calculator.documentId("eform", "42")).isEqualTo(calculator.documentId("eform", "42"))
                .isNotEqualTo(calculator.documentId("document", "42"))
                .isNotEqualTo(calculator.documentId("eform", "43"))
                .isNotEqualTo(new DocumentIdCalculator(otherSecret).documentId("eform", "42"))
                .hasSize(43)
                .doesNotContain("42");
    }

    @Test
    @DisplayName("should refuse a short secret and a missing source or record")
    void shouldRefuseInvalidInput_forTheCalculator() {
        assertThatThrownBy(() -> new DocumentIdCalculator(new byte[31])).isInstanceOf(IllegalArgumentException.class);
        DocumentIdCalculator calculator = new DocumentIdCalculator(FAKE_SECRET);
        assertThatThrownBy(() -> calculator.documentId("", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.documentId("eform", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("should refuse a source with a colon, so no two pairs share an input")
    void shouldRefuseColon_inTheSource() {
        DocumentIdCalculator calculator = new DocumentIdCalculator(FAKE_SECRET);

        // ("a:b", "c") and ("a", "b:c") would both hash "a:b:c".
        assertThatThrownBy(() -> calculator.documentId("a:b", "c")).isInstanceOf(IllegalArgumentException.class);
        assertThat(calculator.documentId("a", "b:c")).hasSize(43);
    }

    @Test
    @DisplayName("should be absent when the secret setting is not set")
    void shouldBeEmpty_whenTheSecretIsNotSet() {
        CarlosProperties.getInstance().remove(DocumentIdCalculator.SECRET_PROPERTY);

        assertThat(DocumentIdCalculator.fromCarlosProperties()).isEmpty();
    }

    @Test
    @DisplayName("should read a base64 secret from the setting")
    void shouldReadTheSecret_fromTheSetting() {
        // The setting holds standard base64, built here from the FAKE bytes rather than written out.
        CarlosProperties.getInstance().setProperty(DocumentIdCalculator.SECRET_PROPERTY,
                Base64.getEncoder().encodeToString(FAKE_SECRET));

        Optional<DocumentIdCalculator> calculator = DocumentIdCalculator.fromCarlosProperties();

        assertThat(calculator).isPresent();
        assertThat(calculator.get().documentId("eform", "42")).isEqualTo("NQP6VEqbRs3qDiu8FfzrbHJT80WWArgyZP7gAbGYPFw");
    }

    @Test
    @DisplayName("should stop when the setting is not base64 or too short")
    void shouldFailClosed_whenTheSettingIsBroken() {
        CarlosProperties.getInstance().setProperty(DocumentIdCalculator.SECRET_PROPERTY, "not base64 !!");
        assertThatThrownBy(DocumentIdCalculator::fromCarlosProperties).isInstanceOf(IllegalStateException.class);

        CarlosProperties.getInstance().setProperty(DocumentIdCalculator.SECRET_PROPERTY, "AQIDBA==");
        assertThatThrownBy(DocumentIdCalculator::fromCarlosProperties).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
