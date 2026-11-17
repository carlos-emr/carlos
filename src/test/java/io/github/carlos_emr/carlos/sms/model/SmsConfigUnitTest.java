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
package io.github.carlos_emr.carlos.sms.model;

import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("model")
class SmsConfigUnitTest {
    private String originalKey;

    @BeforeEach
    void seedEncryptionKey() throws Exception {
        originalKey = EncryptionKeyTestSupport.seedFreshKey();
    }

    @AfterEach
    void restoreEncryptionKey() {
        EncryptionKeyTestSupport.restoreKey(originalKey);
    }

    @Test
    @DisplayName("stores the webhook secret encrypted and reads it back decrypted")
    void shouldEncryptWebhookSecret_atRest() {
        SmsConfig config = new SmsConfig();

        config.setWebhookSecret("webhook-value-123");

        String stored = (String) ReflectionTestUtils.getField(config, "webhookSecret");
        assertThat(stored).startsWith("{ENC}").doesNotContain("webhook-value-123");
        assertThat(config.getWebhookSecret()).isEqualTo("webhook-value-123");
        assertThat(config.hasWebhookSecret()).isTrue();
    }

    @Test
    @DisplayName("treats a blank webhook secret as no secret")
    void shouldClearWebhookSecret_whenBlank() {
        SmsConfig config = new SmsConfig();
        config.setWebhookSecret("webhook-value-123");

        config.setWebhookSecret(" ");

        assertThat(config.hasWebhookSecret()).isFalse();
        assertThat(config.getWebhookSecret()).isEmpty();
    }

    @Test
    @DisplayName("stores each credential value encrypted, keyed by field name")
    void shouldEncryptCredentialValues_atRest() {
        SmsConfig config = new SmsConfig();

        config.setCredential("field_one", "value-one");
        config.setCredential("field_two", "value two");

        String stored = (String) ReflectionTestUtils.getField(config, "credentialsJson");
        assertThat(stored).contains("field_one", "field_two", "{ENC}")
                .doesNotContain("value-one").doesNotContain("value two");
        assertThat(config.getCredential("field_two")).isEqualTo("value two");
        assertThat(config.hasCredential("field_one")).isTrue();
        assertThat(config.hasCredential("field_three")).isFalse();
    }

    @Test
    @DisplayName("keeps each provider's credentials and sender number in its own entry, the selected one's number also in its column")
    void shouldKeepEntryPerProvider_whenProviderChanges() {
        SmsConfig config = new SmsConfig();
        config.setProviderType(SmsProviderType.VOIPMS);
        config.setCredential("field_one", "voipms-value");
        config.setSenderNumber("+16135550100");

        config.setProviderType(SmsProviderType.CLOUDLI);
        config.setCredential("field_one", "cloudli-value");
        config.setSenderNumber("+16135550199");

        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("voipms-value");
        assertThat(config.getCredential("field_one")).as("the selected provider's").isEqualTo("cloudli-value");
        assertThat(config.storedSenderNumber(SmsProviderType.VOIPMS)).contains("+16135550100");
        assertThat(config.storedSenderNumber(SmsProviderType.CLOUDLI)).contains("+16135550199");
        assertThat(config.getSenderNumber()).isEqualTo("+16135550199");
        assertThat(config.credentialNames(SmsProviderType.VOIPMS)).as("the sender number is not a credential")
                .containsExactly("field_one");
        String stored = (String) ReflectionTestUtils.getField(config, "credentialsJson");
        assertThat(stored).contains("\"VOIPMS\":{", "\"CLOUDLI\":{", "\"@senderNumber\":\"+16135550100\"")
                .doesNotContain("voipms-value", "cloudli-value");
    }

    @Test
    @DisplayName("reads a row saved before per-provider entries as the selected provider's entry, and writes entries next")
    void shouldReadFlatCredentials_asSelectedProvidersEntry() {
        SmsConfig config = new SmsConfig();
        config.setProviderType(SmsProviderType.VOIPMS);
        config.setCredential("field_one", "voipms-value");
        String encrypted = ((String) ReflectionTestUtils.getField(config, "credentialsJson"))
                .replaceAll(".*\"field_one\":\"([^\"]+)\".*", "$1");
        ReflectionTestUtils.setField(config, "credentialsJson", "{\"field_one\":\"" + encrypted + "\"}");
        config.setSenderNumber("+16135550100");

        assertThat(config.credentialsReadable()).isTrue();
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("voipms-value");
        assertThat(config.credentialNames(SmsProviderType.CLOUDLI)).isEmpty();
        assertThat((String) ReflectionTestUtils.getField(config, "credentialsJson"))
                .as("written as entries").startsWith("{\"VOIPMS\":{");

        ReflectionTestUtils.setField(config, "credentialsJson", "{\"field_one\":\"" + encrypted + "\"}");
        config.setProviderType(SmsProviderType.CLOUDLI);
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one"))
                .as("still the provider it was saved for after a swap").isEqualTo("voipms-value");
        assertThat(config.storedSenderNumber(SmsProviderType.VOIPMS)).contains("+16135550100");
        assertThat(config.credentialNames()).isEmpty();
    }

    @Test
    @DisplayName("treats stored credentials that are neither entries nor the earlier flat map as unreadable")
    void shouldFailClosed_whenStoredCredentialsHaveAnotherShape() {
        for (String json : new String[]{"{not json", "[]", "\"text\"", "{\"VOIPMS\":{\"field_one\":1}}",
                "{\"field_one\":\"x\",\"VOIPMS\":{}}", "{\"VOIPMS\":[]}"}) {
            SmsConfig config = new SmsConfig();
            ReflectionTestUtils.setField(config, "credentialsJson", json);

            assertThat(config.credentialsReadable()).as(json).isFalse();
            assertThat(config.credentialNames()).as(json).isEmpty();
        }
    }

    @Test
    @DisplayName("refuses the sender number's reserved key as a credential name")
    void shouldRefuseReservedKey_asCredentialName() {
        SmsConfig config = new SmsConfig();
        config.setSenderNumber("+16135550100");

        assertThatThrownBy(() -> config.setCredential(SmsConfig.SENDER_NUMBER_KEY, "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(config.hasCredential(SmsConfig.SENDER_NUMBER_KEY)).isFalse();
        assertThat(config.getCredential(SmsConfig.SENDER_NUMBER_KEY)).isEmpty();
    }

    @Test
    @DisplayName("removes a credential when it is set blank")
    void shouldRemoveCredential_whenBlank() {
        SmsConfig config = new SmsConfig();
        config.setCredential("field_two", "value two");

        config.setCredential("field_two", "");

        assertThat(config.hasCredential("field_two")).isFalse();
        assertThat(config.getCredential("field_two")).isEmpty();
    }

    @Test
    @DisplayName("defaults to the stub provider with sending and the scheduler off")
    void shouldDefaultToStub_withEverythingOff() {
        SmsConfig config = new SmsConfig();

        assertThat(config.getProviderType()).isEqualTo(SmsProviderType.STUB);
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.isSchedulerEnabled()).isFalse();
    }

    @Test
    @DisplayName("without an encryption key, storing a secret fails with a dedicated error that names no secret")
    void shouldThrowEncryptionError_whenNoKeyIsConfigured() {
        SmsConfig config = new SmsConfig();
        EncryptionKeyTestSupport.restoreKey(null);

        assertThatThrownBy(() -> config.setWebhookSecret("webhook-value-123"))
                .isInstanceOf(SmsSecretEncryptionException.class)
                .hasNoCause()
                .hasMessageNotContaining("webhook-value-123");
        assertThatThrownBy(() -> config.setCredential("field_two", "value two"))
                .isInstanceOf(SmsSecretEncryptionException.class)
                .hasMessageNotContaining("value two");
        assertThat(config.hasWebhookSecret()).isFalse();
    }

    @Test
    @DisplayName("toString is redacted, so logging the settings never prints secrets or their ciphertext")
    void shouldRedactToString_forSecrets() {
        SmsConfig config = new SmsConfig();
        config.setWebhookSecret("webhook-value-123");
        config.setCredential("field_two", "value two");

        assertThat(config.toString()).isEqualTo("SmsConfig[redacted]");
    }
}
