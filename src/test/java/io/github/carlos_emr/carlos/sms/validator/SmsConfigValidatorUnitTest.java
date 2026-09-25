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
package io.github.carlos_emr.carlos.sms.validator;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.sms.dto.SmsConfigUpdateDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("validator")
class SmsConfigValidatorUnitTest {
    private static final Set<SmsProviderType> INSTALLED = Set.of(SmsProviderType.STUB);

    private final SmsConfigValidator validator = new SmsConfigValidator();

    @Test
    @DisplayName("accepts the stub provider with a valid sender number or none")
    void shouldAcceptSettings_whenValid() {
        assertThat(validator.validate(update(SmsProviderType.STUB, "416-555-1212"), INSTALLED)).isEmpty();
        assertThat(validator.validate(update(SmsProviderType.STUB, ""), INSTALLED)).isEmpty();
    }

    @Test
    @DisplayName("requires a provider")
    void shouldRejectSettings_whenProviderIsMissing() {
        assertThat(validator.validate(update(null, ""), INSTALLED))
                .containsExactly("sms.config.error.providerRequired");
    }

    @Test
    @DisplayName("refuses a provider that has no installed client, since sends through it would fail")
    void shouldRejectSettings_whenProviderHasNoClient() {
        assertThat(validator.validate(update(SmsProviderType.VOIPMS, ""), INSTALLED))
                .containsExactly("sms.config.error.providerNotInstalled");
    }

    @Test
    @DisplayName("refuses a sender number that is not a valid phone number")
    void shouldRejectSettings_whenSenderNumberIsInvalid() {
        assertThat(validator.validate(update(SmsProviderType.STUB, "not-a-number"), INSTALLED))
                .containsExactly("sms.config.error.senderNumber");
    }

    private static SmsConfigUpdateDto update(SmsProviderType providerType, String senderNumber) {
        return new SmsConfigUpdateDto(providerType, true, false, senderNumber, "", false, Map.of());
    }

    @Test
    @DisplayName("refuses a webhook secret longer than 256 characters, which would not fit once encrypted")
    void shouldRejectSettings_whenWebhookSecretIsTooLong() {
        SmsConfigUpdateDto update = new SmsConfigUpdateDto(
                SmsProviderType.STUB, true, false, "", "x".repeat(257), false, Map.of());

        assertThat(validator.validate(update, INSTALLED)).containsExactly("sms.config.error.webhookSecretTooLong");
        assertThat(validator.validate(new SmsConfigUpdateDto(
                SmsProviderType.STUB, true, false, "", "x".repeat(256), false, Map.of()), INSTALLED)).isEmpty();
    }

    @Test
    @DisplayName("counts the webhook secret in UTF-8 bytes, since that is what gets encrypted and stored")
    void shouldRejectSettings_whenMultiByteSecretExceedsByteLimit() {
        // 200 characters, 400 bytes: under the character count but over the stored-size limit.
        SmsConfigUpdateDto update = new SmsConfigUpdateDto(
                SmsProviderType.STUB, true, false, "", "\u00E9".repeat(200), false, Map.of());

        assertThat(validator.validate(update, INSTALLED)).containsExactly("sms.config.error.webhookSecretTooLong");
        assertThat(validator.validate(new SmsConfigUpdateDto(
                SmsProviderType.STUB, true, false, "", "\u00E9".repeat(128), false, Map.of()), INSTALLED)).isEmpty();
    }
}
