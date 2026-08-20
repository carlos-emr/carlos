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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.email.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmailData#mergeMessage(boolean, String, String)} — the helper that folds the
 * two stored content channels (cleartext body / encrypted-PDF message) back into the single "Message"
 * value the compose screen seeds (issue #3118).
 */
@Tag("unit")
@DisplayName("EmailData.mergeMessage")
class EmailDataUnitTest {

    @Test
    @DisplayName("should prefer the encrypted-message channel when encryption is on")
    void shouldReturnEncryptedMessage_whenEncryptionOn() {
        assertThat(EmailData.mergeMessage(true, "cleartext body", "secret pdf content"))
                .isEqualTo("secret pdf content");
    }

    @Test
    @DisplayName("should fall back to the body channel when encrypted-message is empty and encryption is on")
    void shouldFallBackToBody_whenEncryptedMessageEmptyAndEncryptionOn() {
        assertThat(EmailData.mergeMessage(true, "cleartext body", "")).isEqualTo("cleartext body");
    }

    @Test
    @DisplayName("should prefer the body channel when encryption is off")
    void shouldReturnBody_whenEncryptionOff() {
        assertThat(EmailData.mergeMessage(false, "cleartext body", "secret pdf content"))
                .isEqualTo("cleartext body");
    }

    @Test
    @DisplayName("should fall back to the encrypted-message channel when body is empty and encryption is off")
    void shouldFallBackToEncryptedMessage_whenBodyEmptyAndEncryptionOff() {
        assertThat(EmailData.mergeMessage(false, null, "secret pdf content")).isEqualTo("secret pdf content");
    }

    @Test
    @DisplayName("should return an empty string when both channels are null")
    void shouldReturnEmpty_whenBothChannelsNull() {
        assertThat(EmailData.mergeMessage(true, null, null)).isEmpty();
        assertThat(EmailData.mergeMessage(false, null, null)).isEmpty();
    }
}
