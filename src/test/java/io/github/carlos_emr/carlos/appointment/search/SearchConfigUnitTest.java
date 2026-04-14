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
package io.github.carlos_emr.carlos.appointment.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link SearchConfig} encrypt/decrypt methods.
 *
 * <p>Validates the AES/GCM/NoPadding cipher round-trip, IV randomness,
 * and null-key passthrough behaviour.</p>
 *
 * @since 2026-04-14
 */
@Tag("unit")
@Tag("fast")
@Tag("appointment")
@DisplayName("SearchConfig AES/GCM encrypt/decrypt")
class SearchConfigUnitTest {

    private SearchConfig config;

    @BeforeEach
    void setUp() throws Exception {
        config = new SearchConfig();
        config.genSecKey();
    }

    @Test
    @DisplayName("should round-trip encrypt then decrypt with AES/GCM cipher")
    void shouldRoundTrip_withAesGcmCipher() throws Exception {
        String plaintext = "appointmentType:42:providerA";
        String ct1 = config.encrypt(plaintext);
        String ct2 = config.encrypt(plaintext);

        assertThat(ct1).isNotEqualTo(ct2);                        // IV randomness
        assertThat(config.decrypt(ct1)).isEqualTo(plaintext);
        assertThat(config.decrypt(ct2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("should return plaintext unchanged when secret key is null")
    void shouldReturnPlaintext_whenSecretKeyIsNull() throws Exception {
        SearchConfig noKeyConfig = new SearchConfig();
        String plaintext = "someValue";

        assertThat(noKeyConfig.encrypt(plaintext)).isEqualTo(plaintext);
        assertThat(noKeyConfig.decrypt(plaintext)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("should handle empty string round-trip")
    void shouldHandleEmptyString_whenEncryptingAndDecrypting() throws Exception {
        String empty = "";
        String encrypted = config.encrypt(empty);
        assertThat(config.decrypt(encrypted)).isEqualTo(empty);
    }

    @Test
    @DisplayName("should handle unicode content round-trip")
    void shouldHandleUnicode_whenEncryptingAndDecrypting() throws Exception {
        String unicode = "provider:日本語テスト:42";
        String encrypted = config.encrypt(unicode);
        assertThat(config.decrypt(encrypted)).isEqualTo(unicode);
    }
}
