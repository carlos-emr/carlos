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
package io.github.carlos_emr.carlos.email.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.EmailSendingException;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;

/**
 * Unit tests for {@link EmailConfigSecrets}, the at-rest protection for email transport secrets.
 *
 * <p>These tests mutate the process-global {@code EncryptionUtils.SECRET_KEY_SPEC} and the
 * {@code encryption.util.secret.key} property, seeding a fresh AES key for each test and restoring
 * the previous state afterwards so the suite does not pollute other tests.</p>
 */
@Tag("unit")
@Tag("fast")
class EmailConfigSecretsUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Field keySpecField;
    private Object originalKeySpec;
    private String originalProp;

    @BeforeEach
    void seedEncryptionKey() throws Exception {
        keySpecField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keySpecField.setAccessible(true);
        originalKeySpec = keySpecField.get(null);

        CarlosProperties props = CarlosProperties.getInstance();
        originalProp = props.getProperty(EncryptionUtils.SECRET_KEY_ENV_VAR);

        props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, EncryptionUtils.generateSecretKey());
        EncryptionUtils.prepareSecretKeySpec();
    }

    @AfterEach
    void restoreEncryptionKey() throws Exception {
        CarlosProperties props = CarlosProperties.getInstance();
        if (originalProp != null) {
            props.setProperty(EncryptionUtils.SECRET_KEY_ENV_VAR, originalProp);
        } else {
            props.remove(EncryptionUtils.SECRET_KEY_ENV_VAR);
        }
        keySpecField.set(null, originalKeySpec);
    }

    @Test
    @Tag("create")
    @DisplayName("should encrypt both secret fields and round-trip back to plaintext")
    void shouldEncryptSecrets_whenPlaintextPasswordAndApiKeyPresent() throws Exception {
        String json = "{\"host\":\"smtp.example.com\",\"port\":\"587\",\"username\":\"clinic\","
                + "\"password\":\"s3cr3t-pw\",\"api_key\":\"SG.live-key\"}";

        String encrypted = EmailConfigSecrets.encryptSecrets(json);

        JsonNode node = MAPPER.readTree(encrypted);
        assertThat(node.get("password").asText()).startsWith("{ENC}");
        assertThat(node.get("api_key").asText()).startsWith("{ENC}");
        // Non-secret fields are left untouched.
        assertThat(node.get("host").asText()).isEqualTo("smtp.example.com");
        assertThat(node.get("username").asText()).isEqualTo("clinic");
        // Round-trip: the send path decrypts back to the original secrets.
        assertThat(EmailConfigSecrets.decryptSecret(node.get("password").asText())).isEqualTo("s3cr3t-pw");
        assertThat(EmailConfigSecrets.decryptSecret(node.get("api_key").asText())).isEqualTo("SG.live-key");
    }

    @Test
    @Tag("read")
    @DisplayName("should return the same JSON when all secrets are already encrypted")
    void shouldReturnSameJson_whenSecretsAlreadyEncrypted() throws Exception {
        String once = EmailConfigSecrets.encryptSecrets("{\"password\":\"pw\",\"api_key\":\"key\"}");

        String twice = EmailConfigSecrets.encryptSecrets(once);

        // Idempotent: an already-migrated row is not rewritten.
        assertThat(twice).isSameAs(once);
    }

    @Test
    @Tag("update")
    @DisplayName("should encrypt only the plaintext secret when the population is mixed")
    void shouldEncryptOnlyPlaintext_whenMixedEncryptedAndPlaintext() throws Exception {
        String encryptedApiKey = EncryptionUtils.encrypt("SG.already");
        String json = "{\"password\":\"still-plain\",\"api_key\":\"" + encryptedApiKey + "\"}";

        String result = EmailConfigSecrets.encryptSecrets(json);

        JsonNode node = MAPPER.readTree(result);
        assertThat(node.get("password").asText()).startsWith("{ENC}");
        // The already-encrypted value is preserved verbatim, not re-encrypted.
        assertThat(node.get("api_key").asText()).isEqualTo(encryptedApiKey);
        assertThat(EmailConfigSecrets.decryptSecret(node.get("password").asText())).isEqualTo("still-plain");
    }

    @Test
    @Tag("read")
    @DisplayName("should return the input unchanged when there are no secret fields")
    void shouldReturnInputUnchanged_whenNoSecretFields() throws Exception {
        String json = "{\"host\":\"localhost\",\"port\":\"25\"}";

        assertThat(EmailConfigSecrets.encryptSecrets(json)).isSameAs(json);
    }

    @Test
    @Tag("read")
    @DisplayName("should return the input unchanged when the value is blank or null")
    void shouldReturnInputUnchanged_whenBlankOrNull() throws Exception {
        assertThat(EmailConfigSecrets.encryptSecrets(null)).isNull();
        assertThat(EmailConfigSecrets.encryptSecrets("   ")).isEqualTo("   ");
    }

    @Test
    @Tag("read")
    @DisplayName("should return the input unchanged when the value is not JSON")
    void shouldReturnInputUnchanged_whenNotJson() throws Exception {
        String notJson = "this is not json";

        assertThat(EmailConfigSecrets.encryptSecrets(notJson)).isSameAs(notJson);
    }

    @Test
    @Tag("read")
    @DisplayName("should pass legacy plaintext through when decrypting during the migration window")
    void shouldPassThroughPlaintext_whenDecryptingLegacyValue() throws Exception {
        assertThat(EmailConfigSecrets.decryptSecret("legacy-plain-pw")).isEqualTo("legacy-plain-pw");
    }

    @Test
    @Tag("read")
    @DisplayName("should return empty and null unchanged when decrypting blank values")
    void shouldReturnBlankUnchanged_whenDecryptingEmptyValue() throws Exception {
        assertThat(EmailConfigSecrets.decryptSecret("")).isEmpty();
        assertThat(EmailConfigSecrets.decryptSecret(null)).isNull();
    }

    @Test
    @Tag("read")
    @DisplayName("should throw EmailSendingException when a marked-encrypted value cannot be decrypted")
    void shouldThrowEmailSendingException_whenDecryptingCorruptedCiphertext() {
        // Carries the {ENC} marker but the payload is not valid ciphertext: this models the
        // send-time decryption-failure path (missing/rotated key or tampering).
        assertThatThrownBy(() -> EmailConfigSecrets.decryptSecret("{ENC}not-valid-ciphertext"))
                .isInstanceOf(EmailSendingException.class)
                .hasMessageContaining("Unable to decrypt email transport credentials");
    }
}
