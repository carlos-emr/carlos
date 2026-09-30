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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.sms.SmsProviderType;
import io.github.carlos_emr.carlos.test.util.EncryptionKeyTestSupport;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@Tag("model")
class SmsConfigUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
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
    @DisplayName("stores each credential value encrypted, grouped by provider and keyed by field name")
    void shouldEncryptCredentialValues_atRest() throws Exception {
        SmsConfig config = new SmsConfig();

        config.setCredential(SmsProviderType.VOIPMS, "field_one", "value-one");
        config.setCredential(SmsProviderType.VOIPMS, "field_two", "value two");

        String stored = storedJson(config);
        assertThat(stored).contains("field_one", "field_two", "{ENC}")
                .doesNotContain("value-one").doesNotContain("value two");
        JsonNode grouped = JSON.readTree(stored);
        assertThat(grouped.properties()).extracting(Map.Entry::getKey).containsExactly("VOIPMS");
        assertThat(grouped.get("VOIPMS").get("field_one").textValue()).startsWith("{ENC}");
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_two")).isEqualTo("value two");
        assertThat(config.hasCredential(SmsProviderType.VOIPMS, "field_one")).isTrue();
        assertThat(config.hasCredential(SmsProviderType.VOIPMS, "field_three")).isFalse();
        assertThat(config.storedCredentialsByProvider().get("VOIPMS"))
                .containsOnlyKeys("field_one", "field_two")
                .doesNotContainValue("value-one");
    }

    @Test
    @DisplayName("a credential set for one provider is not another provider's")
    void shouldNotShowCredential_toAnotherProvider() {
        SmsConfig config = new SmsConfig();

        config.setCredential(SmsProviderType.VOIPMS, "field_one", "value-one");

        assertThat(config.hasCredential(SmsProviderType.CLOUDLI, "field_one")).isFalse();
        assertThat(config.getCredential(SmsProviderType.CLOUDLI, "field_one")).isEmpty();
        assertThat(config.credentialNames(SmsProviderType.CLOUDLI)).isEmpty();
        assertThat(config.credentialNames(SmsProviderType.VOIPMS)).containsExactly("field_one");
    }

    @Test
    @DisplayName("keeps a field name that two providers share apart, one value for each")
    void shouldKeepSharedFieldNameApart_perProvider() {
        SmsConfig config = new SmsConfig();

        config.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value");
        config.setCredential(SmsProviderType.CLOUDLI, "field_one", "cloudli-value");

        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("voipms-value");
        assertThat(config.getCredential(SmsProviderType.CLOUDLI, "field_one")).isEqualTo("cloudli-value");
    }

    @Test
    @DisplayName("removes a credential when it is set blank, drops a provider left with none, and stores NULL when none are left")
    void shouldRemoveCredential_whenBlank() {
        SmsConfig config = new SmsConfig();
        config.setCredential(SmsProviderType.VOIPMS, "field_one", "value-one");
        config.setCredential(SmsProviderType.VOIPMS, "field_two", "value two");

        config.setCredential(SmsProviderType.VOIPMS, "field_two", "");

        assertThat(config.hasCredential(SmsProviderType.VOIPMS, "field_two")).isFalse();
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_two")).isEmpty();
        assertThat(config.credentialNames(SmsProviderType.VOIPMS)).containsExactly("field_one");

        config.setCredential(SmsProviderType.VOIPMS, "field_one", null);

        assertThat(config.storedCredentialsByProvider()).isEmpty();
        assertThat(storedJson(config)).isNull();
    }

    @Test
    @DisplayName("removing a provider's credentials leaves every other provider's as they are")
    void shouldRemoveOnlyThatProvidersCredentials_whenRemoving() {
        SmsConfig config = new SmsConfig();
        config.setCredential(SmsProviderType.VOIPMS, "field_one", "voipms-value");
        config.setCredential(SmsProviderType.VOIPMS, "field_two", "voipms-other");
        config.setCredential(SmsProviderType.CLOUDLI, "field_one", "cloudli-value");

        config.removeCredentials(SmsProviderType.VOIPMS);

        assertThat(config.credentialNames(SmsProviderType.VOIPMS)).isEmpty();
        assertThat(config.getCredential(SmsProviderType.CLOUDLI, "field_one")).isEqualTo("cloudli-value");
        assertThat(config.storedCredentialsByProvider()).containsOnlyKeys("CLOUDLI");

        config.removeCredentials(SmsProviderType.CLOUDLI);

        assertThat(storedJson(config)).isNull();
    }

    @Test
    @DisplayName("reads the earlier flat shape as the credentials of the provider the row was saved with")
    void shouldReadFlatShape_asStoredProvidersCredentials() throws Exception {
        SmsConfig config = flatConfig(SmsProviderType.VOIPMS);

        assertThat(config.credentialsReadable()).isTrue();
        assertThat(config.credentialNames(SmsProviderType.VOIPMS)).containsExactlyInAnyOrder("field_one", "field_two");
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("value-one");
        assertThat(config.hasCredential(SmsProviderType.CLOUDLI, "field_one")).isFalse();
        assertThat(config.storedCredentialsByProvider()).containsOnlyKeys("VOIPMS");
    }

    @Test
    @DisplayName("stores the earlier flat shape grouped by provider on the next write, keeping its values")
    void shouldStoreGroupedShape_whenFlatShapeIsWrittenNext() throws Exception {
        SmsConfig config = flatConfig(SmsProviderType.VOIPMS);
        String flatValue = JSON.readTree(storedJson(config)).get("field_one").textValue();

        config.setCredential(SmsProviderType.CLOUDLI, "field_one", "cloudli-value");

        JsonNode grouped = JSON.readTree(storedJson(config));
        assertThat(grouped.properties()).extracting(Map.Entry::getKey).containsExactly("CLOUDLI", "VOIPMS");
        assertThat(grouped.get("VOIPMS").get("field_one").textValue()).isEqualTo(flatValue);
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_two")).isEqualTo("value two");
        assertThat(config.getCredential(SmsProviderType.CLOUDLI, "field_one")).isEqualTo("cloudli-value");
    }

    @Test
    @DisplayName("keeps the earlier flat shape with the provider it was saved for when another provider is chosen")
    void shouldKeepFlatShapeWithStoredProvider_whenProviderChanges() throws Exception {
        SmsConfig config = flatConfig(SmsProviderType.VOIPMS);

        config.setProviderType(SmsProviderType.CLOUDLI);

        assertThat(config.credentialNames(SmsProviderType.CLOUDLI)).isEmpty();
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("value-one");
        assertThat(JSON.readTree(storedJson(config)).properties())
                .extracting(Map.Entry::getKey).containsExactly("VOIPMS");
    }

    @Test
    @DisplayName("leaves the stored text alone when nothing changes, so an unchanged save is not a credential change")
    void shouldLeaveStoredText_whenNothingChanges() throws Exception {
        SmsConfig config = flatConfig(SmsProviderType.VOIPMS);
        String flat = storedJson(config);

        config.setProviderType(SmsProviderType.VOIPMS);
        config.removeCredentials(SmsProviderType.CLOUDLI);
        config.setCredential(SmsProviderType.VOIPMS, "not_stored", "");

        assertThat(storedJson(config)).isEqualTo(flat);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{not json",
            "[\"{ENC}abc\"]",
            "\"{ENC}abc\"",
            "null",
            "{\"field_one\": 5}",
            "{\"VOIPMS\": {\"field_one\": 5}}",
            "{\"VOIPMS\": {\"field_one\": {\"deeper\": \"{ENC}abc\"}}}",
            "{\"field_one\": \"{ENC}abc\", \"CLOUDLI\": {\"field_one\": \"{ENC}abc\"}}"
    })
    @DisplayName("treats stored credentials it cannot read as unreadable: none shown, and a generic error on use")
    void shouldTreatCredentialsAsUnreadable_whenShapeIsUnknown(String stored) {
        SmsConfig config = new SmsConfig();
        config.setProviderType(SmsProviderType.VOIPMS);
        ReflectionTestUtils.setField(config, "credentialsJson", stored);

        assertThat(config.credentialsReadable()).isFalse();
        assertThat(config.hasCredential(SmsProviderType.VOIPMS, "field_one")).isFalse();
        assertThat(config.credentialNames(SmsProviderType.VOIPMS)).isEmpty();
        assertThat(config.storedCredentialsByProvider()).isEmpty();
        assertThatThrownBy(() -> config.getCredential(SmsProviderType.VOIPMS, "field_one"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Stored SMS credentials are unreadable; re-enter them in Administration > SMS.")
                .hasNoCause();
    }

    @Test
    @DisplayName("replaces unreadable stored credentials when one is set, and does not fail a provider change")
    void shouldReplaceUnreadableCredentials_whenOneIsSet() {
        SmsConfig config = new SmsConfig();
        ReflectionTestUtils.setField(config, "credentialsJson", "{not json");

        config.setProviderType(SmsProviderType.VOIPMS);
        config.removeCredentials(SmsProviderType.VOIPMS);

        assertThat(storedJson(config)).isEqualTo("{not json");

        config.setCredential(SmsProviderType.VOIPMS, "field_one", "value-one");

        assertThat(config.credentialsReadable()).isTrue();
        assertThat(config.storedCredentialsByProvider()).containsOnlyKeys("VOIPMS");
        assertThat(config.getCredential(SmsProviderType.VOIPMS, "field_one")).isEqualTo("value-one");
    }

    @Test
    @DisplayName("reads an empty stored object as no credentials")
    void shouldReadEmptyObject_asNoCredentials() {
        SmsConfig config = new SmsConfig();
        ReflectionTestUtils.setField(config, "credentialsJson", "{}");

        assertThat(config.credentialsReadable()).isTrue();
        assertThat(config.storedCredentialsByProvider()).isEmpty();
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
    @DisplayName("toString is redacted, so logging the settings never prints secrets or their ciphertext")
    void shouldRedactToString_forSecrets() {
        SmsConfig config = new SmsConfig();
        config.setWebhookSecret("webhook-value-123");
        config.setCredential(SmsProviderType.VOIPMS, "field_two", "value two");
        config.setCredential(SmsProviderType.CLOUDLI, "field_one", "value-one");

        assertThat(config.toString()).isEqualTo("SmsConfig[redacted]");
    }

    /** A row as saved before credentials were kept per provider: one flat object of name to encrypted value. */
    private static SmsConfig flatConfig(SmsProviderType storedProvider) throws Exception {
        SmsConfig config = new SmsConfig();
        config.setProviderType(storedProvider);
        Map<String, String> flat = new TreeMap<>();
        flat.put("field_one", EncryptionUtils.encrypt("value-one"));
        flat.put("field_two", EncryptionUtils.encrypt("value two"));
        ReflectionTestUtils.setField(config, "credentialsJson", JSON.writeValueAsString(flat));
        return config;
    }

    private static String storedJson(SmsConfig config) {
        return (String) ReflectionTestUtils.getField(config, "credentialsJson");
    }
}
